package app.securify.core

import org.json.JSONArray
import org.json.JSONObject

const val TUN_MTU = 1500

private fun arr(vararg v: Any) = JSONArray(v.toList())

private fun tunInbound() = JSONObject()
    .put("tag", "tun").put("port", 0).put("protocol", "tun")
    .put("settings", JSONObject().put("name", "xray0").put("mtu", TUN_MTU))
    .put("sniffing", JSONObject().put("enabled", true).put("destOverride", arr("http", "tls", "quic")).put("routeOnly", true))

private fun rule(key: String, values: JSONArray, out: String) =
    JSONObject().put("type", "field").put(key, values).put("outboundTag", out)

fun buildConfig(server: Server, ruDirect: Boolean, ipCheckSocket: String? = null): String {
    val c = server.config?.let { JSONObject(it.toString()) } ?: JSONObject()
        .put("outbounds", arr(
            server.outbound,
            JSONObject().put("tag", "direct").put("protocol", "freedom"),
            JSONObject().put("tag", "block").put("protocol", "blackhole"),
        ))
        .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray().apply {
            put(rule("ip", arr("geoip:private"), "direct"))
            if (ruDirect) {
                put(rule("domain", arr("geosite:category-ru", "domain:ru", "domain:su", "domain:xn--p1ai"), "direct"))
                put(rule("ip", arr("geoip:ru"), "direct"))
            }
        }))
    c.put("log", JSONObject().put("loglevel", "warning"))
    c.put("inbounds", arr(tunInbound()))
    if (ipCheckSocket != null) {
        c.getJSONArray("inbounds").put(JSONObject().put("tag", "ipcheck").put("listen", ipCheckSocket).put("protocol", "http"))
        server.outbound.optString("tag").takeIf { it.isNotEmpty() }?.let { tag ->
            val routing = c.optJSONObject("routing") ?: JSONObject().also { c.put("routing", it) }
            val rules = routing.optJSONArray("rules") ?: JSONArray()
            routing.put("rules", JSONArray().put(rule("inboundTag", arr("ipcheck"), tag)).apply {
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
