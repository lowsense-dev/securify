package app.securify.core

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopSupportTest {
    @Test
    fun tcpProbeListensOnLoopbackAndUsesSelectedServer() {
        val server = assertNotNull(parseEntry("vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@example.com:443"))
        val config = JSONObject(buildConfig(server, ipCheckPort = 39000))
        val probe = config.getJSONArray("inbounds").getJSONObject(1)
        val rule = config.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("127.0.0.1", probe.getString("listen"))
        assertEquals(39000, probe.getInt("port"))
        assertEquals("ipcheck", rule.getJSONArray("inboundTag").getString(0))
        assertEquals(server.outbound.getString("tag"), rule.getString("outboundTag"))
    }

    @Test
    fun parsesWindowsPingWithoutRelyingOnLanguage() {
        assertEquals(1L, parseIcmpTime("Reply from 127.0.0.1: bytes=32 time<1ms TTL=128", windows = true))
        assertEquals(12L, parseIcmpTime("Ответ от 127.0.0.1: число байт=32 время=12мс TTL=128", windows = true))
        assertEquals(-1L, parseIcmpTime("Request timed out.", windows = true))
        assertEquals(4L, parseIcmpTime("64 bytes: time=4.31 ms", windows = false))
    }
}
