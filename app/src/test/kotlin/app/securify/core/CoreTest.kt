package app.securify.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CoreTest {
    private fun enc(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private val vlessReality = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
        "?type=xhttp&security=reality&pbk=PUBKEY&sid=ab12&sni=www.microsoft.com&fp=chrome" +
        "&flow=&path=%2Fxh&mode=auto&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D#%F0%9F%87%B3%F0%9F%87%B1%20Amsterdam"

    @Test fun vlessRealityXhttp() {
        val s = parseEntry(vlessReality)!!
        assertEquals("🇳🇱 Amsterdam", s.name)
        assertEquals("vless", s.protocol)
        assertEquals("example.com", s.host)
        assertEquals(443, s.port)
        val st = s.outbound.getJSONObject("streamSettings")
        assertEquals("xhttp", st.getString("network"))
        assertEquals("/xh", st.getJSONObject("xhttpSettings").getString("path"))
        assertEquals("100-1000", st.getJSONObject("xhttpSettings").getJSONObject("extra").getString("xPaddingBytes"))
        val r = st.getJSONObject("realitySettings")
        assertEquals("PUBKEY", r.getString("publicKey"))
        assertEquals("www.microsoft.com", r.getString("serverName"))
        val set = s.outbound.getJSONObject("settings")
        assertEquals("none", set.getString("encryption"))
        assertFalse(set.has("flow"))
    }

    @Test fun vlessVisionTcp() {
        val s = parseEntry("vless://uuid@1.2.3.4:8443?security=reality&pbk=K&flow=xtls-rprx-vision&sni=a.com#n")!!
        assertEquals("tcp", s.transport)
        assertEquals("xtls-rprx-vision", s.outbound.getJSONObject("settings").getString("flow"))
    }

    @Test fun realityWithoutKeyIsRejected() {
        assertNull(parseEntry("vless://uuid@1.2.3.4:443?security=reality&sni=a.com#n"))
    }

    @Test fun trojanWsTls() {
        val s = parseEntry("trojan://pa%40ss@[2001:db8::1]:443?type=ws&host=cdn.example.com&path=%2Fws%3Fed%3D2048#T")!!
        assertEquals("2001:db8::1", s.host)
        assertEquals("pa@ss", s.outbound.getJSONObject("settings").getString("password"))
        val st = s.outbound.getJSONObject("streamSettings")
        assertEquals("tls", st.getString("security"))
        assertEquals("cdn.example.com", st.getJSONObject("tlsSettings").getString("serverName"))
        assertEquals("/ws?ed=2048", st.getJSONObject("wsSettings").getString("path"))
    }

    @Test fun vmessBase64() {
        val j = """{"v":"2","ps":"VM","add":"vm.example.com","port":"443","id":"uuid","aid":"0","scy":"auto",
            "net":"grpc","type":"multi","path":"svc","tls":"tls","sni":"vm.example.com"}"""
        val s = parseEntry("vmess://" + enc(j))!!
        assertEquals("VM", s.name)
        val g = s.outbound.getJSONObject("streamSettings").getJSONObject("grpcSettings")
        assertEquals("svc", g.getString("serviceName"))
        assertTrue(g.getBoolean("multiMode"))
    }

    @Test fun shadowsocksForms() {
        val sip002 = parseEntry("ss://" + enc("chacha20-ietf-poly1305:secret") + "@ss.example.com:8388#SS")!!
        assertEquals("secret", sip002.outbound.getJSONObject("settings").getString("password"))
        val plain = parseEntry("ss://2022-blake3-aes-128-gcm:a%2Bb%3D@1.1.1.1:443#P")!!
        assertEquals("a+b=", plain.outbound.getJSONObject("settings").getString("password"))
        val legacy = parseEntry("ss://" + enc("aes-256-gcm:pw@2.2.2.2:1234") + "#L")!!
        assertEquals(1234, legacy.port)
        assertEquals("aes-256-gcm", legacy.outbound.getJSONObject("settings").getString("method"))
    }

    @Test fun hysteria2Salamander() {
        val s = parseEntry("hy2://auth@hy.example.com:443,20000-30000?sni=hy.example.com&obfs=salamander&obfs-password=ob&insecure=1#H")!!
        assertEquals("hysteria", s.protocol)
        assertTrue(s.isUdp)
        assertEquals(443, s.port)
        val st = s.outbound.getJSONObject("streamSettings")
        assertEquals("auth", st.getJSONObject("hysteriaSettings").getString("auth"))
        assertEquals("salamander", st.getJSONObject("finalmask").getJSONArray("udp").getJSONObject(0).getString("type"))
        assertTrue(st.getJSONObject("tlsSettings").getBoolean("allowInsecure"))
    }

    @Test fun garbageIsNull() {
        assertNull(parseEntry("hello"))
        assertNull(parseEntry("vless://@:0"))
        assertNull(parseEntry("socks://1.2.3.4:1080"))
    }

    @Test fun bodyBase64AndPlain() {
        val links = "$vlessReality\nnot a link\ntrojan://p@t.com:443#T\n"
        assertEquals(2, parseBody(enc(links)).size)
        assertEquals(2, parseBody(links).size)
    }

    @Test fun bodyXrayJson() {
        val cfg = JSONObject().put("remarks", "JSON server").put("outbounds", JSONArray()
            .put(JSONObject().put("protocol", "vless").put("tag", "proxy")
                .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().put("address", "j.com").put("port", 443)))))
            .put(JSONObject().put("protocol", "freedom").put("tag", "direct")))
        val entries = parseBody(JSONArray().put(cfg).put(cfg).toString())
        assertEquals(1, entries.size)
        val s = parseEntry(entries[0])!!
        assertEquals("JSON server", s.name)
        assertEquals("j.com", s.host)
        assertNotNull(s.config)
    }

    @Test fun headers() {
        assertEquals("My VPN", headerText("base64:" + enc("My VPN")))
        assertEquals("plain", headerText(" plain "))
        assertNull(headerText(""))
        val ui = parseUserInfo("upload=100; download=200; total=1.073741824E10; expire=1893456000")
        assertEquals(300L, ui["upload"]!! + ui["download"]!!)
        assertEquals(10737418240L, ui["total"])
        assertEquals("Panel", subName("Panel", null, "https://x.com/sub"))
        assertEquals("file name", subName(null, "attachment; filename=\"file name\"", "https://x.com/sub"))
        assertEquals("x.com", subName(null, null, "https://x.com/sub/abc"))
    }

    @Test fun configTunAndRuRouting() {
        val s = parseEntry(vlessReality)!!
        val c = JSONObject(buildConfig(s, ruDirect = true))
        val inb = c.getJSONArray("inbounds")
        assertEquals(1, inb.length())
        assertEquals("tun", inb.getJSONObject(0).getString("protocol"))
        assertEquals("proxy", c.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
        assertTrue(c.getJSONObject("routing").toString().contains("geoip:ru"))
        assertFalse(JSONObject(buildConfig(s, ruDirect = false)).getJSONObject("routing").toString().contains("geoip:ru"))
        assertTrue(c.getJSONObject("policy").getJSONObject("system").getBoolean("statsOutboundUplink"))
    }

    @Test fun ipCheckInboundAlwaysExitsThroughProxy() {
        val c = JSONObject(buildConfig(parseEntry(vlessReality)!!, ruDirect = true, ipCheckSocket = "/data/ip.sock"))
        val ib = c.getJSONArray("inbounds").getJSONObject(1)
        assertEquals("/data/ip.sock", ib.getString("listen"))
        assertFalse(ib.has("port"))
        val first = c.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("ipcheck", first.getJSONArray("inboundTag").getString(0))
        assertEquals("proxy", first.getString("outboundTag"))
        val cfg = JSONObject().put("outbounds", JSONArray().put(JSONObject().put("tag", "out-1").put("protocol", "vless")
            .put("settings", JSONObject().put("address", "j.com").put("port", 443))))
            .put("routing", JSONObject().put("rules", JSONArray().put(JSONObject().put("outboundTag", "custom"))))
        val rules = JSONObject(buildConfig(parseEntry(cfg.toString())!!, false, "/s")).getJSONObject("routing").getJSONArray("rules")
        assertEquals("out-1", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("custom", rules.getJSONObject(1).getString("outboundTag"))
    }

    @Test fun configFromJsonSubKeepsRoutingReplacesInbounds() {
        val cfg = JSONObject().put("remarks", "J")
            .put("inbounds", JSONArray().put(JSONObject().put("protocol", "socks").put("port", 10808)))
            .put("outbounds", JSONArray().put(JSONObject().put("protocol", "trojan")
                .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", "t.com").put("port", 443))))))
            .put("routing", JSONObject().put("rules", JSONArray().put(JSONObject().put("outboundTag", "custom"))))
        val c = JSONObject(buildConfig(parseEntry(cfg.toString())!!, ruDirect = true))
        assertEquals("tun", c.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertTrue(c.getJSONObject("routing").toString().contains("custom"))
        assertFalse(JSONObject(pingConfig(parseEntry(cfg.toString())!!)).has("inbounds"))
    }

    @Test fun stateRoundTrip() {
        val st = AppState(
            listOf(Sub("id", "https://x", "X", listOf(vlessReality), SubInfo(1, 2, 3, 4, 12, "hi", null, "w"), 99)),
            Prefs(selected = vlessReality, ruDirect = true, appMode = AppMode.BYPASS, apps = setOf("a.b"), ping = PingMethod.REAL),
        )
        val back = decode(JSONObject(encode(st).toString()))
        assertEquals(st, back)
        assertEquals(vlessReality, back.selected?.key)
        assertEquals(AppState(), decode(JSONObject()))
    }
}
