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
        val udp = st.getJSONObject("finalmask").getJSONArray("udp")
        assertEquals("salamander", udp.getJSONObject(0).getString("type"))
        assertEquals("udphop", udp.getJSONObject(1).getString("type"))
        assertEquals("443,20000-30000", udp.getJSONObject(1).getJSONObject("settings").getString("remotePorts"))
        assertFalse(parseEntry("hy2://a@h.com:443#H")!!.outbound.getJSONObject("streamSettings").has("finalmask"))
        assertFalse(st.getJSONObject("tlsSettings").has("allowInsecure"))
        assertTrue(s.needsPin)
    }

    @Test fun insecureUsesPinInsteadOfAllowInsecure() {
        val hy = parseEntry("hy2://a@h.com:443?insecure=1&pinSHA256=AB:CD:EF#H")!!
        val tls = hy.outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings")
        assertEquals("ABCDEF", tls.getString("pinnedPeerCertSha256"))
        assertFalse(tls.has("allowInsecure"))
        assertFalse(hy.needsPin)
        val tr = parseEntry("trojan://p@t.com:443?allowInsecure=1&pcs=0011#T")!!
        assertEquals("0011", tr.outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("pinnedPeerCertSha256"))
        assertFalse(tr.needsPin)
        assertTrue(parseEntry("trojan://p@t.com:443?allowInsecure=1#T")!!.needsPin)
        assertFalse(parseEntry("vless://u@v.com:443?security=reality&pbk=K&insecure=1#R")!!.needsPin)
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
        val c = JSONObject(buildConfig(s, Prefs(ruDirect = true)))
        val inb = c.getJSONArray("inbounds")
        assertEquals(1, inb.length())
        assertEquals("tun", inb.getJSONObject(0).getString("protocol"))
        assertEquals("proxy", c.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
        assertTrue(c.getJSONObject("routing").toString().contains("geoip:ru"))
        assertFalse(JSONObject(buildConfig(s, Prefs())).getJSONObject("routing").toString().contains("geoip:ru"))
        assertTrue(c.getJSONObject("policy").getJSONObject("system").getBoolean("statsOutboundUplink"))
    }

    @Test fun ipCheckInboundAlwaysExitsThroughProxy() {
        val c = JSONObject(buildConfig(parseEntry(vlessReality)!!, Prefs(ruDirect = true), ipCheckSocket = "/data/ip.sock"))
        val ib = c.getJSONArray("inbounds").getJSONObject(1)
        assertEquals("/data/ip.sock", ib.getString("listen"))
        assertFalse(ib.has("port"))
        val first = c.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("ipcheck", first.getJSONArray("inboundTag").getString(0))
        assertEquals("proxy", first.getString("outboundTag"))
        val cfg = JSONObject().put("outbounds", JSONArray().put(JSONObject().put("tag", "out-1").put("protocol", "vless")
            .put("settings", JSONObject().put("address", "j.com").put("port", 443))))
            .put("routing", JSONObject().put("rules", JSONArray().put(JSONObject().put("outboundTag", "custom"))))
        val rules = JSONObject(buildConfig(parseEntry(cfg.toString())!!, Prefs(), "/s")).getJSONObject("routing").getJSONArray("rules")
        assertEquals("out-1", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("custom", rules.getJSONObject(1).getString("outboundTag"))
    }

    @Test fun configFromJsonSubKeepsRoutingReplacesInbounds() {
        val cfg = JSONObject().put("remarks", "J")
            .put("inbounds", JSONArray().put(JSONObject().put("protocol", "socks").put("port", 10808)))
            .put("outbounds", JSONArray().put(JSONObject().put("protocol", "trojan")
                .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", "t.com").put("port", 443))))))
            .put("routing", JSONObject().put("rules", JSONArray().put(JSONObject().put("outboundTag", "custom"))))
        val c = JSONObject(buildConfig(parseEntry(cfg.toString())!!, Prefs(ruDirect = true)))
        assertEquals("tun", c.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertTrue(c.getJSONObject("routing").toString().contains("custom"))
        assertFalse(JSONObject(pingConfig(parseEntry(cfg.toString())!!)).has("inbounds"))
    }

    @Test fun hwidIsPerProviderHost() {
        val a = hwidFor("dev1", "https://panel.one/sub/abc")
        assertEquals(32, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertEquals(a, hwidFor("dev1", "https://panel.one/sub/other"))
        assertFalse(a == hwidFor("dev1", "https://panel.two/sub/abc"))
        assertFalse(a == hwidFor("dev2", "https://panel.one/sub/abc"))
    }

    @Test fun pingHostCantBeAnOption() {
        assertTrue(isPlainHost("example.com"))
        assertTrue(isPlainHost("2001:db8::1"))
        assertFalse(isPlainHost("-i0.0001"))
        assertFalse(isPlainHost("a.com;reboot"))
        assertFalse(isPlainHost(""))
    }

    @Test fun stateRoundTrip() {
        val st = AppState(
            listOf(Sub("id", "https://x", "X", listOf(vlessReality), SubInfo(1, 2, 3, 4, 12, "hi", null, "w"), 99)),
            Prefs(
                selected = vlessReality, ruDirect = true, appMode = AppMode.BYPASS, apps = setOf("a.b"), ping = PingMethod.REAL,
                fragment = true, proxyRules = "a.com", directRules = "1.2.3.4", blockRules = "ads.com", dns = "tls://1.1.1.1",
                fakeDns = true, agent = SubAgent.HAPP, geo = GeoSource.RUNETFREEDOM, geoUpdated = 5,
            ),
        )
        val back = decode(JSONObject(encode(st).toString()))
        assertEquals(st, back)
        assertEquals(vlessReality, back.selected?.key)
        assertEquals(AppState(), decode(JSONObject()))
    }

    private fun rules(c: JSONObject) = c.getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test fun wireguardLink() {
        val s = parseEntry("wireguard://c2VjcmV0%2Bkey%3D@[2001:db8::2]:51820?publickey=PUB&presharedkey=PSK" +
            "&address=172.16.0.2/32,fd01::2/128&reserved=1,2,3&mtu=1280#WG")!!
        assertEquals("wireguard", s.protocol)
        assertTrue(s.isUdp)
        val set = s.outbound.getJSONObject("settings")
        assertEquals("c2VjcmV0+key=", set.getString("secretKey"))
        assertEquals(2, set.getJSONArray("address").length())
        assertEquals(1280, set.getInt("mtu"))
        assertEquals(3, set.getJSONArray("reserved").getInt(2))
        assertTrue(set.getBoolean("noKernelTun"))
        val peer = set.getJSONArray("peers").getJSONObject(0)
        assertEquals("[2001:db8::2]:51820", peer.getString("endpoint"))
        assertEquals("PSK", peer.getString("preSharedKey"))
        assertNull(parseEntry("wg://key@1.2.3.4:51820#no-public-key"))
    }

    @Test fun autoBalancesLinkServersFastestFirst() {
        val json = JSONObject().put("outbounds", JSONArray().put(JSONObject().put("tag", "x").put("protocol", "vless")
            .put("settings", JSONObject().put("address", "j.com").put("port", 443)))).toString()
        val sub = Sub("s1", "https://x", "Prov", listOf(vlessReality, "trojan://p@t.com:443#T", "ss://" + enc("aes-256-gcm:pw") + "@2.2.2.2:1#S", json))
        val auto = sub.auto!!
        assertEquals(3, auto.group.size)
        assertEquals("Auto · Prov", auto.name)
        assertEquals(auto.key, AppState(listOf(sub), Prefs(selected = auto.key)).selected?.key)
        assertNull(Sub("s2", "", "", listOf(vlessReality)).auto)

        val pings = mapOf("trojan://p@t.com:443#T" to 40L, vlessReality to -1L)
        val c = JSONObject(buildConfig(auto, Prefs(ruDirect = true), "/s", pings))
        val obs = c.getJSONArray("outbounds")
        assertEquals("proxy-0", obs.getJSONObject(0).getString("tag"))
        assertEquals("trojan", obs.getJSONObject(0).getString("protocol"))
        assertEquals("proxy", auto.group[1].outbound.getString("tag"))
        val b = c.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0)
        assertEquals("leastPing", b.getJSONObject("strategy").getString("type"))
        assertEquals("proxy-0", b.getString("fallbackTag"))
        assertEquals("proxy-", c.getJSONObject("observatory").getJSONArray("subjectSelector").getString(0))
        val r = rules(c)
        assertEquals("auto", r.first().getString("balancerTag"))
        assertEquals("tcp,udp", r.last().getString("network"))
        assertEquals("auto", r.last().getString("balancerTag"))
    }

    @Test fun antiDpiMasksWithoutTouchingTheServer() {
        val s = parseEntry(vlessReality)!!
        val tcp = JSONObject(buildConfig(s, Prefs(fragment = true))).getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("finalmask").getJSONArray("tcp").getJSONObject(0)
        assertEquals("fragment", tcp.getString("type"))
        assertEquals("1-3", tcp.getJSONObject("settings").getString("packets"))
        assertFalse(s.outbound.getJSONObject("streamSettings").has("finalmask"))
        assertFalse(JSONObject(buildConfig(s, Prefs())).toString().contains("fragment"))
        val hy = JSONObject(buildConfig(parseEntry("hy2://a@h.com:443#H")!!, Prefs(fragment = true)))
        assertEquals("noise", hy.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
            .getJSONObject("finalmask").getJSONArray("udp").getJSONObject(0).getString("type"))
        val salamander = parseEntry("hy2://a@h.com:443?obfs=salamander&obfs-password=x#H")!!
        assertFalse(buildConfig(salamander, Prefs(fragment = true)).contains("noise"))
        assertFalse(buildConfig(parseEntry("ss://" + enc("aes-256-gcm:pw") + "@2.2.2.2:1#S")!!, Prefs(fragment = true)).contains("finalmask"))
    }

    @Test fun customRulesComeBeforeRuDirect() {
        val p = Prefs(ruDirect = true, blockRules = "ads.com\n# comment\n", proxyRules = "full:x.ru\n10.0.0.0/8\n2001:db8::/32", directRules = "geoip:cn\ngeosite:apple")
        val r = rules(JSONObject(buildConfig(parseEntry(vlessReality)!!, p)))
        assertEquals("block", r[0].getString("outboundTag"))
        assertEquals("domain:ads.com", r[0].getJSONArray("domain").getString(0))
        assertEquals("full:x.ru", r[1].getJSONArray("domain").getString(0))
        assertEquals("proxy", r[1].getString("outboundTag"))
        assertEquals(2, r[2].getJSONArray("ip").length())
        assertEquals("geosite:apple", r[3].getJSONArray("domain").getString(0))
        assertEquals("geoip:cn", r[4].getJSONArray("ip").getString(0))
        assertEquals("direct", r[4].getString("outboundTag"))
        assertEquals("geoip:private", r[5].getJSONArray("ip").getString(0))
    }

    @Test fun dnsHijackAndFakeDns() {
        val s = parseEntry(vlessReality)!!
        assertFalse(JSONObject(buildConfig(s, Prefs())).has("dns"))
        val c = JSONObject(buildConfig(s, Prefs(dns = "https://dns.google/dns-query", fakeDns = true), "/s"))
        val servers = c.getJSONObject("dns").getJSONArray("servers")
        assertEquals("fakedns", servers.getString(0))
        assertEquals("https://dns.google/dns-query", servers.getString(1))
        assertEquals("198.18.0.0/15", c.getJSONArray("fakedns").getJSONObject(0).getString("ipPool"))
        val r = rules(c)
        assertEquals("ipcheck", r[0].getJSONArray("inboundTag").getString(0))
        assertEquals("dns-out", r[1].getString("outboundTag"))
        assertEquals("53", r[1].getString("port"))
        assertTrue(c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getJSONArray("destOverride").toString().contains("fakedns"))
        assertEquals("1.1.1.1", JSONObject(buildConfig(s, Prefs(fakeDns = true))).getJSONObject("dns").getJSONArray("servers").getString(1))
    }

    @Test fun updateAssets() {
        assertEquals(10400, tagVersionCode("v1.4.0"))
        assertEquals(21203, tagVersionCode("2.12.3"))
        assertNull(tagVersionCode("v1.4.0-beta"))
        val names = listOf("securify-1.5.0-arm64-v8a.apk", "securify-1.5.0-armeabi-v7a.apk", "securify-1.5.0-universal.apk", "SHA256SUMS")
        assertEquals("securify-1.5.0-arm64-v8a.apk", pickApk(names, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("securify-1.5.0-universal.apk", pickApk(names, listOf("riscv64")))
        val sums = "AB12  securify-1.5.0-arm64-v8a.apk\ncd34 *securify-1.5.0-universal.apk\n"
        assertEquals("ab12", sha256From(sums, "securify-1.5.0-arm64-v8a.apk"))
        assertEquals("cd34", sha256From(sums, "securify-1.5.0-universal.apk"))
        assertNull(sha256From(sums, "securify-1.5.0-x86_64.apk"))
    }
}
