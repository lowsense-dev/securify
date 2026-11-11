package app.securify.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
