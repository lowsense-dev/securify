package app.securify.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import app.securify.BuildConfig
import app.securify.R
import app.securify.core.AppMode
import app.securify.core.Store
import app.securify.core.TUN_MTU
import app.securify.core.buildConfig
import app.securify.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject
import java.io.File
import java.util.Locale

sealed interface VpnState {
    data object Off : VpnState
    data object Connecting : VpnState
    data class On(val server: String, val since: Long) : VpnState
    data class Failed(val message: String) : VpnState
}

data class Traffic(val up: Long = 0, val down: Long = 0, val upTotal: Long = 0, val downTotal: Long = 0)

class SecurifyVpn : VpnService() {
    companion object {
        val state = MutableStateFlow<VpnState>(VpnState.Off)
        val traffic = MutableStateFlow(Traffic())

        val exit = MutableStateFlow<String?>(null)

        /** False when the periodic check through the running core fails. */
        val online = MutableStateFlow(true)

        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val NOTIF_ID = 1
        private const val CHANNEL = "vpn"

        val isActive get() = state.value.let { it is VpnState.On || it is VpnState.Connecting }

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, SecurifyVpn::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            if (isActive) ctx.startService(Intent(ctx, SecurifyVpn::class.java).setAction(ACTION_STOP))
        }

        fun restartIfOn(ctx: Context) {
            if (isActive) start(ctx)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch { session?.cancelAndJoin(); teardown(); finish(VpnState.Off) }
            else -> {
                foreground(getString(R.string.status_connecting))
                val prev = session
                session = scope.launch { prev?.cancelAndJoin(); run() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun run() {
        state.value = VpnState.Connecting
        exit.value = null
        online.value = true
        var newTun: ParcelFileDescriptor? = null
        var newCore: CoreController? = null
        try {
            val st = Store.state.value
            val server = st.selected ?: error(getString(R.string.error_no_server))
            check(!server.needsPin) { getString(R.string.error_insecure) }
            val sock = File(filesDir, "ipcheck-${System.currentTimeMillis()}.sock")
            val config = buildConfig(server, st.prefs.ruDirect, sock.path)
            ensureGeoFiles()
            val b = Builder()
                .setSession(server.name)
                .setMtu(TUN_MTU)
                .addAddress("172.19.0.1", 30)
                .addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setConfigureIntent(openApp())
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
            // our own traffic (Xray's outbound sockets, pings, subscription fetches) must bypass the tunnel or it loops
            val apps = st.prefs.apps - packageName
            when {
                st.prefs.appMode == AppMode.ONLY && apps.isNotEmpty() ->
                    apps.forEach { runCatching { b.addAllowedApplication(it) } }
                st.prefs.appMode == AppMode.BYPASS -> (apps + packageName).forEach { runCatching { b.addDisallowedApplication(it) } }
                else -> b.addDisallowedApplication(packageName)
            }
            // a second establish() takes over from the old interface without a gap, so switching servers never leaks
            val fd = b.establish() ?: error(getString(R.string.error_vpn_permission))
            newTun = fd
            val c = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup() = 0L
                override fun shutdown() = 0L
                override fun onEmitStatus(p0: Long, p1: String?) = 0L
            })
            newCore = c
            c.startLoop(config, fd.fd)
            teardown()
            core = c
            tun = fd
            filesDir.listFiles { f -> f.name.startsWith("ipcheck") && !f.name.startsWith(sock.name) }?.forEach { it.delete() }
            state.value = VpnState.On(server.name, System.currentTimeMillis())
            foreground(getString(R.string.notif_connected, server.name))
            coroutineScope {
                launch { exit.value = lookupExit(sock) ?: getString(R.string.exit_unknown) }
                launch { watchLink(c, st.prefs.pingUrl) }
                pollTraffic(c)
            }
        } catch (e: Exception) {
            if (core !== newCore) {
                runCatching { newCore?.stopLoop() }
                runCatching { newTun?.close() }
            }
            if (e is kotlinx.coroutines.CancellationException) throw e
            teardown()
            finish(VpnState.Failed(e.message ?: e.toString()))
        }
    }

    private suspend fun watchLink(c: CoreController, url: String) {
        while (true) {
            delay(60_000)
            online.value = (1..2).any { runCatching { c.measureDelay(url) >= 0 }.getOrDefault(false) }
        }
    }

    private suspend fun pollTraffic(c: CoreController) {
        var t = Traffic()
        traffic.value = t
        while (true) {
            delay(1000)
            var up = 0L
            var down = 0L
            c.queryAllOutboundTrafficStats().split(';').forEach { row ->
                val p = row.split(',')
                if (p.size == 3 && p[0] != "direct" && p[0] != "block") {
                    val v = p[2].toLongOrNull() ?: 0
                    if (p[1] == "uplink") up += v else down += v
                }
            }
            t = Traffic(up, down, t.upTotal + up, t.downTotal + down)
            traffic.value = t
        }
    }

    private suspend fun lookupExit(sock: File): String? {
        val lang = if (Locale.getDefault().language == "ru") "ru" else "en"
        repeat(3) { attempt ->
            if (attempt > 0) delay(1500)
            runCatching {
                LocalSocket().use { s ->
                    s.connect(LocalSocketAddress(sock.path, LocalSocketAddress.Namespace.FILESYSTEM))
                    s.soTimeout = 10_000
                    s.outputStream.write(
                        ("GET http://ip-api.com/json/?fields=status,country,countryCode,query&lang=$lang HTTP/1.0\r\n" +
                            "Host: ip-api.com\r\n\r\n").toByteArray(),
                    )
                    val j = JSONObject(s.inputStream.readBytes().decodeToString().substringAfter("\r\n\r\n"))
                    if (j.optString("status") == "success") {
                        return listOf(flag(j.optString("countryCode")) + " " + j.optString("country"), j.getString("query"))
                            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
                    }
                }
            }
        }
        return null
    }

    private fun flag(code: String) =
        if (code.length == 2 && code.all { it in 'A'..'Z' }) code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("") else ""

    /** Xray's geodata loader stats real files, so the geoip/geosite bundled in the APK must be copied to disk. */
    private fun ensureGeoFiles() {
        val stamp = File(filesDir, "geo.version")
        val version = BuildConfig.VERSION_CODE.toString()
        if (stamp.exists() && stamp.readText() == version) return
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val tmp = File(filesDir, "$name.tmp")
            assets.open(name).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(File(filesDir, name))
        }
        stamp.writeText(version)
    }

    private fun teardown() {
        runCatching { core?.stopLoop() }
        core = null
        runCatching { tun?.close() }
        tun = null
    }

    private fun finish(s: VpnState) {
        state.value = s
        traffic.value = Traffic()
        exit.value = null
        online.value = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        scope.launch { session?.cancelAndJoin(); teardown(); finish(VpnState.Off) }
    }

    override fun onDestroy() {
        scope.cancel()
        teardown()
        if (isActive) state.value = VpnState.Off
        super.onDestroy()
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun foreground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SecurifyVpn::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(null, getString(R.string.action_disconnect), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }
}
