package app.securify.core

import org.json.JSONArray
import org.json.JSONObject

const val TUN_MTU = 1500

private fun arr(vararg v: Any) = JSONArray(v.toList())

private fun tunInbound(fakeDns: Boolean) = JSONObject()
    .put("tag", "tun").put("port", 0).put("protocol", "tun")
    .put("settings", JSONObject().put("name", "xray0").put("mtu", TUN_MTU))
    .put("sniffing", JSONObject().put("enabled", true).put("routeOnly", true)
        .put("destOverride", if (fakeDns) arr("http", "tls", "quic", "fakedns") else arr("http", "tls", "quic")))

private fun rule(key: String, values: JSONArray, out: String) =
    JSONObject().put("type", "field").put(key, values).put("outboundTag", out)

/** Sends matching traffic to the balancer for an Auto entry, or to the server's outbound. */
private fun JSONObject.toProxy(server: Server) =
    if (server.group.isNotEmpty()) put("balancerTag", "auto") else put("outboundTag", server.outbound.optString("tag"))

private val domainPrefixes = listOf("domain:", "full:", "regexp:", "keyword:", "geosite:", "ext:", "dotless:")

private fun isIpRule(s: String) = s.startsWith("geoip:") || s.substringBefore('/').let { h ->
    h.isNotEmpty() && (h.all { it.isDigit() || it == '.' } || (':' in h && h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }))
}

/** One entry per line: a domain (plain means it and its subdomains), a prefixed Xray matcher, an IP/CIDR or geoip:. */
fun customRules(text: String, target: (JSONObject) -> JSONObject): List<JSONObject> {
    val items = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    val (ips, domains) = items.partition(::isIpRule)
    return listOfNotNull(
        domains.takeIf { it.isNotEmpty() }?.let { d ->
            target(JSONObject().put("type", "field").put("domain", JSONArray(d.map { if (domainPrefixes.any(it::startsWith)) it else "domain:$it" })))
        },
        ips.takeIf { it.isNotEmpty() }?.let { target(JSONObject().put("type", "field").put("ip", JSONArray(it))) },
    )
}

/** Splits the TLS ClientHello over TCP and sends junk before UDP handshakes, unless the link set its own masks. */
private fun antiDpi(ob: JSONObject): JSONObject {
    val udp = ob.optString("protocol") == "wireguard"
    val st = ob.optJSONObject("streamSettings") ?: if (udp) JSONObject().also { ob.put("streamSettings", it) } else return ob
    val fm = st.optJSONObject("finalmask") ?: JSONObject()
    val sec = st.optString("security")
    when {
        udp || st.optString("network") == "hysteria" -> if (!fm.has("udp")) fm.put("udp", arr(JSONObject().put("type", "noise")
            .put("settings", JSONObject().put("noise", arr(JSONObject().put("rand", "10-20").put("delay", "10-16"))))))
        st.optString("network") != "kcp" && (sec == "tls" || sec == "reality") -> if (!fm.has("tcp")) fm.put("tcp", arr(JSONObject()
            .put("type", "fragment").put("settings", JSONObject().put("packets", if (sec == "reality") "1-3" else "tlshello")
                .put("length", "50-100").put("delay", "10-20").put("maxSplit", "10"))))
    }
    if (fm.length() > 0) st.put("finalmask", fm)
    return ob
}

private fun ownConfig(server: Server, p: Prefs, pings: Map<String, Long>): JSONObject {
    // the best server we already know about goes first: it is the fallback until the observatory's first probe
    val group = server.group.sortedBy { s -> pings[s.key]?.takeIf { it >= 0 } ?: Long.MAX_VALUE }
    val proxies = if (group.isEmpty()) listOf(server.outbound)
    else group.mapIndexed { i, s -> JSONObject(s.outbound.toString()).put("tag", "proxy-$i") }
    val outbounds = JSONArray()
    proxies.forEach { outbounds.put(if (p.fragment) antiDpi(JSONObject(it.toString())) else it) }
    outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
    outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
    val target = { r: JSONObject -> r.toProxy(server) }
    val rules = JSONArray()
    customRules(p.blockRules) { it.put("outboundTag", "block") }.forEach(rules::put)
    customRules(p.proxyRules, target).forEach(rules::put)
    customRules(p.directRules) { it.put("outboundTag", "direct") }.forEach(rules::put)
    rules.put(rule("ip", arr("geoip:private"), "direct"))
    if (p.ruDirect) {
        rules.put(rule("domain", arr("geosite:category-ru", "domain:ru", "domain:su", "domain:xn--p1ai"), "direct"))
        rules.put(rule("ip", arr("geoip:ru"), "direct"))
    }
    val routing = JSONObject().put("domainStrategy", "AsIs")
    if (group.isNotEmpty()) {
        rules.put(target(JSONObject().put("type", "field").put("network", "tcp,udp")))
        routing.put("balancers", arr(JSONObject().put("tag", "auto").put("selector", arr("proxy-"))
            .put("strategy", JSONObject().put("type", "leastPing")).put("fallbackTag", "proxy-0")))
    }
    val c = JSONObject().put("outbounds", outbounds).put("routing", routing.put("rules", rules))
    if (group.isNotEmpty()) c.put("observatory", JSONObject().put("subjectSelector", arr("proxy-"))
        .put("probeURL", p.pingUrl).put("probeInterval", "2m").put("enableConcurrency", true))
    if (p.dns.isNotBlank() || p.fakeDns) {
        c.put("dns", JSONObject().put("servers", JSONArray().apply {
            if (p.fakeDns) put("fakedns")
            put(p.dns.trim().ifEmpty { "1.1.1.1" })
        }))
        if (p.fakeDns) c.put("fakedns", arr(JSONObject().put("ipPool", "198.18.0.0/15").put("poolSize", 65535)))
        outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
        c.put("routing", routing.put("rules", JSONArray().put(
            JSONObject().put("type", "field").put("inboundTag", arr("tun")).put("port", "53").put("outboundTag", "dns-out"),
        ).apply { for (i in 0 until rules.length()) put(rules.get(i)) }))
    }
    return c
}

/** JSON subscriptions are used as is: only the inbounds, stats and the ipcheck rule are replaced or added. */
fun buildConfig(server: Server, prefs: Prefs = Prefs(), ipCheckSocket: String? = null, pings: Map<String, Long> = emptyMap()): String {
    val own = server.config == null
    val c = server.config?.let { JSONObject(it.toString()) } ?: ownConfig(server, prefs, pings)
    c.put("log", JSONObject().put("loglevel", "warning"))
    c.put("inbounds", arr(tunInbound(own && prefs.fakeDns)))
    if (ipCheckSocket != null) {
        c.getJSONArray("inbounds").put(JSONObject().put("tag", "ipcheck").put("listen", ipCheckSocket).put("protocol", "http"))
        if (server.group.isNotEmpty() || server.outbound.optString("tag").isNotEmpty()) {
            val routing = c.optJSONObject("routing") ?: JSONObject().also { c.put("routing", it) }
            val rules = routing.optJSONArray("rules") ?: JSONArray()
            routing.put("rules", JSONArray().put(JSONObject().put("type", "field").put("inboundTag", arr("ipcheck")).toProxy(server)).apply {
                for (i in 0 until rules.length()) put(rules.get(i))
            })
        }
    }
    c.put("stats", JSONObject())
    c.put("policy", (c.optJSONObject("policy") ?: JSONObject()).put("system",
        JSONObject().put("statsOutboundUplink", true).put("statsOutboundDownlink", true)))
    return c.toString()
}

fun pingConfig(server: Server): String =
    (server.config?.let { JSONObject(it.toString()).apply { remove("inbounds") } }
        ?: JSONObject().put("outbounds", arr(server.outbound))).toString()
