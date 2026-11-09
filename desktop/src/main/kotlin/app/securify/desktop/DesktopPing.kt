package app.securify.desktop

import app.securify.core.PingHooks
import app.securify.core.Server
import app.securify.core.pingConfig
import app.securify.vpn.SecurifyVpn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object DesktopPing {
    fun install() {
        PingHooks.real = ::real
        // while the tunnel is up Xray answers ICMP and completes TCP handshakes itself, so pings must leave through the real interface
        PingHooks.icmpArgs = { if (SecurifyVpn.isActive) Env.physicalInterface()?.let { listOf("-I", it) }.orEmpty() else emptyList() }
        PingHooks.tcpReal = { !SecurifyVpn.isActive }
    }

    /** A short-lived Xray with one HTTP inbound; the request goes through it like libv2ray's measureOutboundDelay. */
    fun real(server: Server, url: String): Long {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val c = JSONObject(pingConfig(server))
            .put("log", JSONObject().put("loglevel", "none"))
            .put("inbounds", JSONArray().put(JSONObject().put("tag", "ping-in").put("listen", "127.0.0.1").put("port", port).put("protocol", "http")))
        // binding to an interface needs no root since Linux 5.7
        if (SecurifyVpn.isActive) Env.physicalInterface()?.let { iface ->
            val obs = c.getJSONArray("outbounds")
            for (i in 0 until obs.length()) {
                val ob = obs.getJSONObject(i)
                val st = ob.optJSONObject("streamSettings") ?: JSONObject().also { ob.put("streamSettings", it) }
                (st.optJSONObject("sockopt") ?: JSONObject().also { st.put("sockopt", it) }).put("interface", iface)
            }
        }
        val dir = File(Env.data, "ping").apply { mkdirs() }
        val f = File.createTempFile("ping", ".json", dir)
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"))
        f.writeText(c.toString())
        val pb = ProcessBuilder(Env.xray.path, "run", "-c", f.path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment()["XRAY_LOCATION_ASSET"] = Env.data.path
        val p = pb.start()
        try {
            val deadline = System.currentTimeMillis() + 5000
            while (!listening(port)) {
                if (!p.isAlive || System.currentTimeMillis() > deadline) return -1
                Thread.sleep(50)
            }
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
            var best = -1L
            repeat(2) {
                val t = System.nanoTime()
                val ok = runCatching {
                    val conn = URI(url).toURL().openConnection(proxy) as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    try { conn.responseCode in 200..399 } finally { conn.inputStream.use { it.readAllBytes() } }
                }.getOrDefault(false)
                val ms = (System.nanoTime() - t) / 1_000_000
                if (ok && (best < 0 || ms < best)) best = ms
            }
            return best
        } finally {
            p.destroy()
            f.delete()
        }
    }

    private fun listening(port: Int) = runCatching { Socket("127.0.0.1", port).close() }.isSuccess
}
