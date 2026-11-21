package app.securify.vpn

import app.securify.core.Store
import app.securify.core.Traffic
import app.securify.core.VpnState
import app.securify.core.buildConfig
import app.securify.desktop.Env
import app.securify.desktop.Os
import app.securify.desktop.desktopConfig
import app.securify.ui.R
import app.securify.ui.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Asked from the app when there's no polkit agent to show pkexec's prompt; null answer means cancelled. */
class PasswordPrompt(val retry: Boolean, val answer: CompletableDeferred<String?> = CompletableDeferred())

/** The desktop counterpart of the Android VpnService: Xray creates the TUN interface itself, as root through pkexec. */
object SecurifyVpn {
    val state = MutableStateFlow<VpnState>(VpnState.Off)
    val traffic = MutableStateFlow(Traffic())
    val exit = MutableStateFlow<String?>(null)

    /** False when the periodic check through the running core fails. */
    val online = MutableStateFlow(true)

    /** Xray's output, newest last. */
    val log = MutableStateFlow<List<String>>(emptyList())

    val passwordPrompt = MutableStateFlow<PasswordPrompt?>(null)

    val isActive get() = state.value.let { it is VpnState.On || it is VpnState.Connecting }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var helper: Helper? = null
    @Volatile private var xray: Process? = null
    private val started = MutableStateFlow(0)
    private val exited = MutableStateFlow(emptySet<Int>())
    private var lastAutoRestart = 0L

    fun start() {
        val prev = session
        session = scope.launch { prev?.cancelAndJoin(); run() }
    }

    fun stop() {
        val prev = session
        session = scope.launch {
            prev?.cancelAndJoin()
            finish(runCatching { stopXray(); VpnState.Off }.getOrElse { VpnState.Failed(it.message.orEmpty()) })
        }
    }

    fun restartIfOn() {
        if (isActive) start()
    }

    /** Closing the helper's stdin is the only way to stop it: it runs as root, so we can't signal it. Called on quit. */
    fun closeHelper() {
        helper?.let { h -> runCatching { h.process.outputStream.close() } }
        helper = null
        xray?.let { p -> runCatching { p.destroy() } }
        xray = null
    }

    /** Keeps an authenticated helper for the next connection; one still asking for a password is dropped. */
    private fun stopXray() {
        if (xray != null) {
            xray?.let { p ->
                p.destroy()
                check(p.waitFor(5, TimeUnit.SECONDS)) { str(R.string.error_xray_stop_timeout) }
            }
            xray = null
            return
        }
        val h = helper
        if (h?.ready == true && !h.done) runCatching { h.write("stop") } else closeHelper()
    }

    private fun addLog(line: String) = log.update { (it + line).takeLast(1000) }

    private fun send(cmd: String) = (helper ?: error("helper is gone")).write(cmd)

    /**
     * Starts the core and returns its pid. On Linux the root helper owns it; on Windows the app is
     * already elevated (the launcher asks for it), so Xray is a plain child process.
     */
    private suspend fun startXray(config: File): Int {
        if (Os.windows) {
            val p = ProcessBuilder(Env.xray.path, "run", "-c", config.path)
                .directory(Env.xray.parentFile)
                .redirectErrorStream(true)
                .also { it.environment()["XRAY_LOCATION_ASSET"] = Env.data.path }
                .start()
            xray = p
            val pid = p.pid().toInt()
            exited.update { it - pid }
            thread(isDaemon = true, name = "xray-output") {
                try {
                    p.inputStream.bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).forEach(::addLog) }
                } finally {
                    exited.update { it + pid }
                }
            }
            return pid
        }
        ensureHelper()
        val before = started.value
        send("start")
        while (started.value == before && helper?.done == false) delay(50)
        if (started.value == before) error(str(R.string.error_xray_exited, lastError()))
        return started.value
    }

    /** pkexec or sudo running the helper script; its state is set by its own output reader. */
    private class Helper(val process: Process) {
        @Volatile var ready = false
        @Volatile var done = false
        @Volatile var asked = 0

        fun write(line: String) = with(process.outputStream) { write("$line\n".toByteArray()); flush() }
    }

    private fun spawn(cmd: List<String>): Helper {
        val h = Helper(ProcessBuilder(cmd).redirectErrorStream(true).start())
        thread(isDaemon = true, name = "xray-output") {
            h.process.inputStream.bufferedReader().forEachLine { line ->
                when {
                    line == "@ready" -> h.ready = true
                    line == "@password" -> h.asked++
                    line.startsWith("@started ") -> started.value = line.substringAfter(' ').trim().toIntOrNull() ?: 0
                    line.startsWith("@exited ") -> line.substringAfter(' ').trim().toIntOrNull()?.let { pid -> exited.update { it + pid } }
                    line.isNotBlank() -> addLog(line)
                }
            }
            h.done = true
        }
        return h
    }

    /** The root helper lives until the app quits, so the password is asked once per session. */
    private suspend fun ensureHelper() {
        helper?.let { if (it.ready && !it.done) return }
        closeHelper()
        Env.which("pkexec")?.let { pkexec ->
            val h = spawn(listOf(pkexec.path, Env.helper.path))
            helper = h
            // waits as long as the password prompt is open
            while (!h.ready && !h.done) delay(100)
            if (h.ready) return
            helper = null
            // pkexec exits with 126 when the prompt is dismissed; other failures (no polkit agent running) fall back to sudo
            if (runCatching { h.process.waitFor() }.getOrNull() == 126) error(str(R.string.error_auth_cancelled))
        }
        val sudo = Env.which("sudo") ?: error(str(R.string.error_no_pkexec))
        val h = spawn(listOf(sudo.path, "-S", "-p", "@password\n", Env.helper.path))
        helper = h
        var answered = 0
        while (!h.ready && !h.done) {
            if (h.asked > answered) {
                val prompt = PasswordPrompt(retry = answered > 0)
                answered = h.asked
                passwordPrompt.value = prompt
                val pw = try { prompt.answer.await() } finally { passwordPrompt.value = null }
                    ?: error(str(R.string.error_auth_cancelled))
                runCatching { h.write(pw) }
            }
            delay(100)
        }
        if (!h.ready) {
            helper = null
            error(str(R.string.error_auth, lastError().ifEmpty { "sudo ${runCatching { h.process.waitFor() }.getOrNull()}" }))
        }
    }

    private fun lastError() = log.value.lastOrNull { it.isNotBlank() }.orEmpty()

    private suspend fun run() {
        state.value = VpnState.Connecting
        exit.value = null
        try {
            val st = Store.state.value
            val server = st.selected ?: error(str(R.string.error_no_server))
            check(!server.needsPin) { str(R.string.error_insecure) }
            if (Os.windows) stopXray()
            // Linux hands Xray a unix socket so only this user can use the ipcheck proxy; Windows uses loopback TCP
            val port = if (Os.windows) ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort } else null
            val sock = if (port == null) File(Env.data, "ipcheck-${System.currentTimeMillis()}.sock") else null
            val password = port?.let { UUID.randomUUID().toString() }
            val probe = Probe(sock, port, password)
            val base = JSONObject(buildConfig(server, st.prefs, sock?.path, Store.pings.value, port))
            if (password != null) {
                val account = JSONObject().put("user", "securify").put("pass", password)
                base.getJSONArray("inbounds").getJSONObject(1)
                    .put("settings", JSONObject().put("accounts", JSONArray().put(account)))
            }
            val config = File(Env.data, "config.json")
            config.writeText(desktopConfig(base, server, st.prefs, Env.hasResolved()))
            if (!Os.windows) Files.setPosixFilePermissions(config.toPath(), PosixFilePermissions.fromString("rw-------"))

            val pid = startXray(config)
            // Xray is up once it listens on the ipcheck socket
            withTimeoutOrNull(20_000) {
                while (true) {
                    if (pid in exited.value || (!Os.windows && helper?.done != false)) error(str(R.string.error_xray_exited, lastError()))
                    if (probe.exists()) break
                    delay(100)
                }
            } ?: error(str(R.string.error_start_timeout))
            if (sock != null) Env.data.listFiles { f -> f.name.startsWith("ipcheck") && f.name != sock.name }?.forEach { it.delete() }
            state.value = VpnState.On(server.name, System.currentTimeMillis())
            coroutineScope {
                launch { exit.value = lookupExit(probe) ?: str(R.string.exit_unknown) }
                launch { watchLink(probe, st.prefs.pingUrl) }
                launch { watchNetwork() }
                launch {
                    exited.first { pid in it }
                    error(str(R.string.error_xray_exited, lastError()))
                }
                if (!Os.windows) pollTraffic()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            runCatching { stopXray() }
            finish(VpnState.Failed(e.message ?: e.toString()))
        }
    }

    private fun finish(s: VpnState) {
        state.value = s
        traffic.value = Traffic()
        exit.value = null
        online.value = true
    }

    /** Every minute, and right after waking from sleep, checks the link and reconnects once if it died. */
    private suspend fun watchLink(probe: Probe, url: String) {
        var last = System.currentTimeMillis()
        var checked = if (online.value) last else last - 60_000
        // the ipcheck inbound is a plain HTTP proxy, so the check uses http:// (generate_204 endpoints answer both)
        val plain = url.replaceFirst("https://", "http://")
        while (true) {
            delay(10_000)
            // the wall clock keeps counting through suspend, the coroutine delay doesn't
            val now = System.currentTimeMillis()
            val woke = now - last > 30_000
            last = now
            if (!woke && now - checked < 60_000) continue
            checked = now
            val ok = runCatching { withTimeout(12_000) { probe.request(plain).startsWith("HTTP/1.1 2") } }.getOrDefault(false)
            val was = online.value
            online.value = ok
            if (!ok && was) autoRestart()
        }
    }

    /** Xray rebinds to a new interface itself, but connections it pooled on the old one are dead. */
    private suspend fun watchNetwork() {
        if (Os.windows) return
        var current = Env.physicalInterface()
        while (true) {
            delay(5_000)
            val now = Env.physicalInterface() ?: continue
            if (current != null && now != current) autoRestart()
            current = now
        }
    }

    private fun autoRestart() {
        val now = System.currentTimeMillis()
        if (lastAutoRestart != 0L && now - lastAutoRestart < 60_000) return
        lastAutoRestart = now
        start()
    }

    private suspend fun pollTraffic() {
        var t = Traffic()
        traffic.value = t
        var prev = Env.tunBytes()
        while (true) {
            delay(1000)
            val cur = Env.tunBytes() ?: continue
            val p = prev ?: cur
            prev = cur
            val up = (cur.first - p.first).coerceAtLeast(0)
            val down = (cur.second - p.second).coerceAtLeast(0)
            t = Traffic(up, down, t.upTotal + up, t.downTotal + down)
            traffic.value = t
        }
    }

    /** One HTTP/1.0 request through the ipcheck proxy inbound; interruptible, so a stop never waits on it. */
    private class Probe(val sock: File?, val port: Int?, password: String?) {
        private val auth = password?.let { Base64.getEncoder().encodeToString("securify:$it".toByteArray()) }

        fun exists(): Boolean = port?.let { runCatching { Socket("127.0.0.1", it).close() }.isSuccess } ?: sock!!.exists()

        suspend fun request(url: String): String = runInterruptible(Dispatchers.IO) {
            val host = url.substringAfter("://").substringBefore('/')
            val header = auth?.let { "Proxy-Authorization: Basic $it\r\n" }.orEmpty()
            val head = "GET $url HTTP/1.0\r\nHost: $host\r\n${header}\r\n"
            if (port != null) {
                Socket("127.0.0.1", port).use { s ->
                    s.soTimeout = 10_000
                    s.getOutputStream().write(head.toByteArray())
                    s.getOutputStream().flush()
                    s.getInputStream().readBytes().toString(Charsets.UTF_8)
                }
            } else {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
                    ch.connect(UnixDomainSocketAddress.of(sock!!.toPath()))
                    ch.write(ByteBuffer.wrap(head.toByteArray()))
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteBuffer.allocate(8192)
                    while (ch.read(buf) >= 0) { out.write(buf.array(), 0, buf.position()); buf.clear() }
                    out.toString(Charsets.UTF_8)
                }
            }
        }
    }

    private suspend fun lookupExit(probe: Probe): String? {
        val lang = if (Locale.getDefault().language == "ru") "ru" else "en"
        repeat(3) { attempt ->
            if (attempt > 0) delay(1500)
            runCatching {
                withTimeout(10_000) {
                    val body = probe.request("http://ip-api.com/json/?fields=status,country,countryCode,query&lang=$lang").substringAfter("\r\n\r\n")
                    val j = runCatching { JSONObject(body) }.getOrNull() ?: return@withTimeout null
                    if (j.optString("status") == "success") listOf(flag(j.optString("countryCode")) + " " + j.optString("country"), j.getString("query"))
                        .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ") else null
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun flag(code: String) =
        if (code.length == 2 && code.all { it in 'A'..'Z' }) code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("") else ""
}
