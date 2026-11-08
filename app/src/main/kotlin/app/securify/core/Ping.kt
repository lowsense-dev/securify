package app.securify.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS = 3000

suspend fun ping(server: Server, method: PingMethod, url: String): Long = withContext(Dispatchers.IO) {
    runCatching {
        when (if (method == PingMethod.TCP && server.isUdp) PingMethod.ICMP else method) {
            PingMethod.TCP -> {
                val addr = InetAddress.getByName(server.host)
                val t = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(addr, server.port), TIMEOUT_MS) }
                (System.nanoTime() - t) / 1_000_000
            }
            PingMethod.ICMP -> {
                require(isPlainHost(server.host))
                val p = ProcessBuilder("ping", "-c", "1", "-W", "3", server.host).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(5, TimeUnit.SECONDS)
                Regex("time=([\\d.]+)").find(out)!!.groupValues[1].toDouble().toLong().coerceAtLeast(1)
            }
            PingMethod.REAL -> Libv2ray.measureOutboundDelay(pingConfig(server), url)
        }
    }.getOrDefault(-1L).let { if (it < 0) -1L else it }
}

fun isPlainHost(h: String) = h.isNotEmpty() && h[0] != '-' && h.all { it.isLetterOrDigit() || it in ".-:_" }

const val PING_PENDING = -2L

private val pingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun pingAll(servers: List<Server>, method: PingMethod, url: String) {
    val gate = Semaphore(if (method == PingMethod.REAL) Runtime.getRuntime().availableProcessors() * 2 else 32)
    Store.pings.update { it + servers.associate { s -> s.key to PING_PENDING } }
    servers.forEach { s ->
        pingScope.launch { gate.withPermit { ping(s, method, url).let { ms -> Store.pings.update { it + (s.key to ms) } } } }
    }
}
