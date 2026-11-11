package app.securify.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS = 3000

/** Android measures the real delay in-process with libv2ray, the desktop app with a short-lived Xray. */
object PingHooks {
    var real: (Server, String) -> Long = { _, _ -> -1L }

    /** Extra `ping` arguments, e.g. the physical interface while a desktop tunnel would answer ICMP itself. */
    var icmpArgs: () -> List<String> = { emptyList() }

    /** False while a TCP connect would be answered by the local tunnel instead of the server. */
    var tcpReal: () -> Boolean = { true }
}

suspend fun ping(server: Server, method: PingMethod, url: String): Long = runInterruptible(Dispatchers.IO) {
    runCatching {
        when (if (method == PingMethod.TCP && (server.isUdp || !PingHooks.tcpReal())) PingMethod.ICMP else method) {
            PingMethod.TCP -> {
                val addr = InetAddress.getByName(server.host)
                val t = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(addr, server.port), TIMEOUT_MS) }
                (System.nanoTime() - t) / 1_000_000
            }
            PingMethod.ICMP -> {
                require(isPlainHost(server.host))
                val p = ProcessBuilder(listOf("ping", "-c", "1", "-W", "3") + PingHooks.icmpArgs() + server.host)
                    .redirectErrorStream(true).start()
                try {
                    val out = p.inputStream.bufferedReader().readText()
                    p.waitFor(5, TimeUnit.SECONDS)
                    Regex("time=([\\d.]+)").find(out)!!.groupValues[1].toDouble().toLong().coerceAtLeast(1)
                } finally { p.destroy() }
            }
            PingMethod.REAL -> PingHooks.real(server, url)
        }
    }.getOrDefault(-1L).let { if (it < 0) -1L else it }
}

fun isPlainHost(h: String) = h.isNotEmpty() && h[0] != '-' && h.all { it.isLetterOrDigit() || it in ".-:_" }

const val PING_PENDING = -2L

private val pingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val pingLock = Any()
private var pingJob: Job? = null

fun stopPing() = synchronized(pingLock) {
    pingJob?.cancel()
    pingJob = null
    Store.pings.update { it.filterValues { ms -> ms != PING_PENDING } }
}

fun pingAll(servers: List<Server>, method: PingMethod, url: String) = synchronized(pingLock) {
    stopPing()
    val job = Job()
    pingJob = job
    val gate = Semaphore(if (method == PingMethod.REAL) Runtime.getRuntime().availableProcessors() * 2 else 32)
    Store.pings.update { it + servers.associate { s -> s.key to PING_PENDING } }
    servers.forEach { s ->
        pingScope.launch(job) {
            gate.withPermit {
                val ms = ping(s, method, url)
                synchronized(pingLock) { if (job.isActive) Store.pings.update { it + (s.key to ms) } }
            }
        }
    }
}
