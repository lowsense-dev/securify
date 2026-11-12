package app.securify.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PingTest {
    @Test
    fun stoppingClearsPendingAndOldResultsCannotOverwriteNewOnes() = runBlocking {
        val server = Server("server", "server", "vless", "example.com", 443, "", "", JSONObject())
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val oldFinished = CountDownLatch(1)
        val oldHook = PingHooks.real
        try {
            Store.pings.value = mapOf("finished" to 42L)
            PingHooks.real = { _, url ->
                if (url == "old") {
                    started.countDown()
                    while (release.count > 0) runCatching { release.await() }
                    oldFinished.countDown()
                    100L
                } else 10L
            }
            pingAll(listOf(server), PingMethod.REAL, "old")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            stopPing()
            assertEquals(mapOf("finished" to 42L), Store.pings.value)

            pingAll(listOf(server), PingMethod.REAL, "new")
            withTimeout(5000) { Store.pings.first { it[server.key] == 10L } }
            release.countDown()
            assertTrue(oldFinished.await(5, TimeUnit.SECONDS))
            assertEquals(10L, Store.pings.value[server.key])
        } finally {
            release.countDown()
            stopPing()
            PingHooks.real = oldHook
            Store.pings.value = emptyMap()
        }
    }

    @Test
    fun icmpHandlesMissingTimeInOutput() = runBlocking {
        val oldTcp = PingHooks.tcpReal
        val oldArgs = PingHooks.icmpArgs
        try {
            PingHooks.tcpReal = { false }
            PingHooks.icmpArgs = { emptyList() }
            val server = Server("s", "s", "vless", "127.0.0.1", 1, "", "", JSONObject())
            // exercises ICMP branch and safe parse (loopback often gives no "time=")
            val res = ping(server, PingMethod.TCP, "")
            assertTrue(res == -1L || res > 0, "icmp parse must not throw, got $res")
        } finally {
            PingHooks.tcpReal = oldTcp
            PingHooks.icmpArgs = oldArgs
            stopPing()
        }
    }

    @Test
    fun parseEntryAcceptsValidLinksAndRejectsBad() {
        val goodVless = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@example.com:443?security=tls#test"
        assertNotNull(parseEntry(goodVless))
        val goodTrojan = "trojan://pass@host.example:443?security=tls#troj"
        assertNotNull(parseEntry(goodTrojan))
        assertNull(parseEntry("not-a-link"))
        assertNull(parseEntry("vless://bad"))
    }

    @Test
    fun parseBodyHandlesTextAndBase64() {
        val text = "vless://uuid@ex.com:443\n trojan://p@h:443"
        val parsed = parseBody(text)
        assertTrue(parsed.size >= 2)
        val b64 = "dmxlc3M6Ly91dWlkQGV4LmNvbTo0NDM=" // vless://uuid@ex.com:443
        assertTrue(parseBody(b64).isNotEmpty())
    }

    @Test
    fun buildConfigProducesValidXrayJson() {
        val srv = Server("k", "n", "vless", "ex.com", 443, "tcp", "tls", org.json.JSONObject())
        val cfg = buildConfig(srv)
        assertTrue(cfg.contains("\"tun\""))
        assertTrue(cfg.contains("\"outbounds\""))
    }

    @Test
    fun storeEncodeDecodeRoundtrips() {
        val original = AppState(
            subs = listOf(Sub("id1", "https://ex/sub", "name", listOf("vless://u@h:1"))),
            prefs = Prefs(selected = "k", ruDirect = true)
        )
        val j = encode(original)
        val back = decode(j)
        assertEquals(original.subs.size, back.subs.size)
        assertEquals(original.prefs.ruDirect, back.prefs.ruDirect)
    }

    @Test
    fun vpnStateMovedToCoreNoBreak() {
        val s = VpnState.On("srv", System.currentTimeMillis())
        assertTrue(s is VpnState.On)
        assertEquals("srv", s.server)
        val t = Traffic(down = 42)
        assertEquals(42, t.down)
    }

    @Test
    fun perfSmokeParseManyEntries() {
        val start = System.nanoTime()
        repeat(100) {
            parseEntry("vless://uuid@example.com:443?security=tls#srv$it")
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 100, "100 parses took ${ms}ms, too slow")
    }
}
