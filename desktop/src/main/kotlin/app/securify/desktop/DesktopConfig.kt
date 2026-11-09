package app.securify.desktop

import app.securify.core.AppMode
import app.securify.core.Prefs
import app.securify.core.Server
import org.json.JSONArray
import org.json.JSONObject

const val DIRECT = "securify-direct"
private val notProxy = setOf("freedom", "blackhole", "dns", "loopback")

private fun isDomain(h: String) = h.any { it.isLetter() } && ':' !in h

/**
 * Turns the shared config into one for a Linux TUN interface that Xray creates itself.
 * Process rules make Xray scan /proc for every new connection, so they're only added for per-app mode.
 */
fun desktopConfig(c: JSONObject, server: Server, p: Prefs, dnsToGateway: Boolean): String {
    c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("settings")
        .put("name", TUN_NAME)
        .put("gateway", JSONArray(listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126")))
        .put("autoSystemRoutingTable", JSONArray(listOf("0.0.0.0/0", "::/0")))
        // Xray binds its own connections to the physical interface and follows network changes
        .put("autoOutboundsInterface", "auto")
        .put("autoSystemDnsToGateway", dnsToGateway)
    val inbounds = c.getJSONArray("inbounds")
    for (i in 0 until inbounds.length()) {
        val ib = inbounds.getJSONObject(i)
        // Xray runs as root: the socket must be reachable by us, the 0700 data dir keeps other users out
        if (ib.optString("tag") == "ipcheck") ib.put("listen", ib.getString("listen") + ",0666")
    }

    val outbounds = c.getJSONArray("outbounds")
    val proxies = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }.filter { it.optString("protocol") !in notProxy }
    // the system resolver points back into the tunnel, so server names are resolved by Xray over a direct DNS query
    proxies.forEach { ob ->
        if (ob.optString("protocol") == "wireguard") return@forEach
        val st = ob.optJSONObject("streamSettings") ?: JSONObject().also { ob.put("streamSettings", it) }
        val so = st.optJSONObject("sockopt") ?: JSONObject().also { st.put("sockopt", it) }
        if (!so.has("domainStrategy")) so.put("domainStrategy", "UseIP")
    }
    outbounds.put(JSONObject().put("tag", DIRECT).put("protocol", "freedom"))
    // the system resolver would answer from the tunnel (FakeDNS included), Xray's own DNS skips FakeDNS for direct dials
    for (i in 0 until outbounds.length()) outbounds.getJSONObject(i).takeIf { it.optString("protocol") == "freedom" }?.let { ob ->
        val s = ob.optJSONObject("settings") ?: JSONObject().also { ob.put("settings", it) }
        if (!s.has("domainStrategy")) s.put("domainStrategy", "UseIP")
    }
    val hasDnsOut = (0 until outbounds.length()).any { outbounds.getJSONObject(it).optString("protocol") == "dns" }
    if (!hasDnsOut) outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
    val dnsTag = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }.first { it.optString("protocol") == "dns" }.optString("tag")

    val dns = c.optJSONObject("dns") ?: JSONObject().also { c.put("dns", it) }
    val servers = dns.optJSONArray("servers")?.takeIf { it.length() > 0 } ?: JSONArray().put("1.1.1.1")
    val hosts = server.group.ifEmpty { listOf(server) }.map { it.host }.filter(::isDomain).distinct()
    dns.put("servers", JSONArray().apply {
        if (hosts.isNotEmpty()) for (a in listOf("1.1.1.1", "8.8.8.8")) put(JSONObject().put("address", a)
            .put("domains", JSONArray(hosts.map { "full:$it" })).put("skipFallback", true).put("tag", "dns-direct"))
        for (i in 0 until servers.length()) put(servers.get(i))
    })

    val routing = c.optJSONObject("routing") ?: JSONObject().also { c.put("routing", it) }
    val old = routing.optJSONArray("rules") ?: JSONArray()
    val rules = (0 until old.length()).map { old.getJSONObject(it) }.toMutableList()
    val ipcheck = rules.firstOrNull { it.optJSONArray("inboundTag")?.optString(0) == "ipcheck" }?.also { rules.remove(it) }
    val dnsHijack = rules.firstOrNull { it.optString("port") == "53" && it.has("inboundTag") }?.also { rules.remove(it) }
        ?: JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun")).put("port", "53").put("outboundTag", dnsTag)
    val apps = p.apps.toList()
    val target = { r: JSONObject ->
        if (server.group.isNotEmpty()) r.put("balancerTag", "auto") else r.put("outboundTag", server.outbound.optString("tag"))
    }
    val first = listOfNotNull(
        ipcheck,
        JSONObject().put("type", "field").put("inboundTag", JSONArray().put("dns-direct")).put("outboundTag", DIRECT),
        dnsHijack,
    )
    val rest = when {
        p.appMode == AppMode.BYPASS && apps.isNotEmpty() ->
            listOf(JSONObject().put("type", "field").put("process", JSONArray(apps)).put("outboundTag", DIRECT)) + rules
        // only the picked apps get the usual routing, everything else goes direct
        p.appMode == AppMode.ONLY && apps.isNotEmpty() -> rules.map { it.put("process", JSONArray(apps)) } + listOf(
            target(JSONObject().put("type", "field").put("process", JSONArray(apps))),
            JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", DIRECT),
        )
        else -> rules
    }
    routing.put("rules", JSONArray(first + rest))
    return c.toString()
}
