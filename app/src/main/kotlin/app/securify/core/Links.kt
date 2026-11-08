package app.securify.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

class Server(
    val key: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val transport: String,
    val security: String,
    val outbound: JSONObject,
    val config: JSONObject? = null,
    /** insecure=1 without a certificate pin: Xray 26 removed allowInsecure, so this can't connect. */
    val needsPin: Boolean = false,
) {
    val isUdp get() = protocol == "hysteria"
}

fun b64(s: String): String {
    val t = s.trim().replace('-', '+').replace('_', '/').filterNot { it.isWhitespace() }.trimEnd('=')
    return String(Base64.getDecoder().decode(t + "=".repeat((4 - t.length % 4) % 4)))
}

// percent-decode but keep '+', share links use %20 for spaces and '+' can be part of a password
private fun pct(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

fun parseEntry(raw: String): Server? = runCatching {
    val s = raw.trim()
    if (s.startsWith("{")) parseJsonConfig(s) else parseLink(s)
}.getOrNull()

private class Parts(val user: String, val host: String, val port: Int, val q: Map<String, String>, val name: String)

private fun split(rest0: String): Parts {
    var rest = rest0
    val name = pct(rest.substringAfter('#', "")).trim()
    rest = rest.substringBefore('#')
    val query = rest.substringAfter('?', "")
    rest = rest.substringBefore('?')
    val user = if ('@' in rest) pct(rest.substringBeforeLast('@')) else ""
    val hp = rest.substringAfterLast('@').substringBefore('/')
    val host: String
    val port: String
    if (hp.startsWith("[")) {
        host = hp.substring(1).substringBefore(']')
        port = hp.substringAfter("]:", "")
    } else {
        host = hp.substringBeforeLast(':')
        port = hp.substringAfterLast(':', "")
    }
    val q = query.split('&').filter { it.isNotEmpty() }
        .associate { pct(it.substringBefore('=')) to pct(it.substringAfter('=', "")) }
    val p = port.substringBefore(',').substringBefore('-').toInt()
    require(host.isNotEmpty() && p in 1..65535)
    return Parts(user, host, p, q, name)
}

private fun parseLink(s: String): Server? {
    val scheme = s.substringBefore("://").lowercase()
    val rest = s.substringAfter("://")
    return when (scheme) {
        "vless" -> split(rest).let { p ->
            require(p.user.isNotEmpty())
            val settings = JSONObject().put("address", p.host).put("port", p.port).put("id", p.user)
                .put("encryption", p.q["encryption"]?.ifEmpty { null } ?: "none")
            p.q["flow"]?.takeIf { it.isNotEmpty() }?.let { settings.put("flow", it) }
            server(s, p, "vless", settings, p.q["type"] ?: "tcp", p.q["security"] ?: "none")
        }
        "trojan" -> split(rest).let { p ->
            require(p.user.isNotEmpty())
            val settings = JSONObject().put("address", p.host).put("port", p.port).put("password", p.user)
            server(s, p, "trojan", settings, p.q["type"] ?: "tcp", p.q["security"] ?: "tls")
        }
        "vmess" -> vmess(s, rest)
        "ss" -> shadowsocks(s, rest)
        "hy2", "hysteria2" -> hysteria2(s, rest)
        else -> null
    }
}

private fun server(raw: String, p: Parts, protocol: String, settings: JSONObject, net: String, sec: String): Server {
    val ob = JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings)
        .put("streamSettings", stream(net, sec, p.q, p.host))
    val n = ob.getJSONObject("streamSettings").getString("network")
    return Server(
        raw, p.name.ifEmpty { "${p.host}:${p.port}" }, protocol, p.host, p.port, n, sec, ob,
        needsPin = sec == "tls" && needsPin(p.q, p.q["pcs"]),
    )
}

private fun needsPin(q: Map<String, String>, pin: String?) =
    (q["allowInsecure"] == "1" || q["insecure"] == "1") && pin.isNullOrEmpty()

private fun isDomain(h: String) = h.any { it.isLetter() } && ':' !in h

private fun stream(net0: String, sec: String, q: Map<String, String>, address: String): JSONObject {
    val net = when (net0) { "raw" -> "tcp"; "splithttp" -> "xhttp"; "mkcp" -> "kcp"; "" -> "tcp"; else -> net0 }
    val s = JSONObject().put("network", net)
    val host = q["host"].orEmpty()
    val path = q["path"].orEmpty().ifEmpty { "/" }
    when (net) {
        "tcp" -> if (q["headerType"] == "http") {
            val req = JSONObject().put("path", JSONArray().put(path))
                .put("headers", JSONObject().put("Host", JSONArray(host.split(',').filter { it.isNotBlank() })))
            s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", req)))
        }
        "ws" -> s.put("wsSettings", JSONObject().put("path", path).put("host", host))
        "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", path).put("host", host))
        "xhttp" -> s.put("xhttpSettings", JSONObject().put("path", path).put("host", host)
            .put("mode", q["mode"]?.ifEmpty { null } ?: "auto")
            .apply { q["extra"]?.let { e -> runCatching { put("extra", JSONObject(e)) } } })
        "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"].orEmpty())
            .put("authority", q["authority"].orEmpty()).put("multiMode", q["mode"] == "multi"))
        "kcp" -> {
            val masks = JSONArray().put(JSONObject().put("type", "mkcp-legacy")
                .apply { q["seed"]?.takeIf { it.isNotEmpty() }?.let { put("settings", JSONObject().put("value", it)) } })
            q["headerType"]?.takeIf { it.isNotEmpty() && it != "none" }?.let { h ->
                masks.put(JSONObject().put("type", "mkcp-legacy").put("settings",
                    JSONObject().put("header", if (h == "wechat-video") "wechat" else h)))
            }
            s.put("kcpSettings", JSONObject()).put("finalmask", JSONObject().put("udp", masks))
        }
        else -> error("unsupported transport $net")
    }
    val sni = q["sni"]?.ifEmpty { null } ?: q["peer"]?.ifEmpty { null }
        ?: host.split(',').first().takeIf { isDomain(it) } ?: address.takeIf { isDomain(it) }
    val fp = q["fp"]?.ifEmpty { null }
    when (sec) {
        "tls" -> s.put("security", "tls").put("tlsSettings", JSONObject().apply {
            sni?.let { put("serverName", it) }
            fp?.let { put("fingerprint", it) }
            q["alpn"]?.takeIf { it.isNotEmpty() }?.let { put("alpn", JSONArray(it.split(',').map(String::trim))) }
            q["ech"]?.takeIf { it.isNotEmpty() }?.let { put("echConfigList", it) }
            q["pcs"]?.takeIf { it.isNotEmpty() }?.let { put("pinnedPeerCertSha256", it) }
        })
        "reality" -> s.put("security", "reality").put("realitySettings", JSONObject().apply {
            put("serverName", sni.orEmpty())
            put("fingerprint", fp ?: "chrome")
            put("publicKey", requireNotNull(q["pbk"]?.ifEmpty { null }))
            put("shortId", q["sid"].orEmpty())
            put("spiderX", q["spx"].orEmpty())
            q["pqv"]?.takeIf { it.isNotEmpty() }?.let { put("mldsa65Verify", it) }
        })
        "", "none" -> {}
        else -> error("unsupported security $sec")
    }
    return s
}

private fun vmess(raw: String, rest: String): Server? {
    val j = runCatching { JSONObject(b64(rest.substringBefore('#'))) }.getOrNull()
    if (j == null) {
        val p = split(rest)
        val settings = JSONObject().put("address", p.host).put("port", p.port).put("id", p.user)
            .put("security", p.q["encryption"] ?: "auto")
        return server(raw, p, "vmess", settings, p.q["type"] ?: "tcp", p.q["security"] ?: "none")
    }
    val net = j.optString("net").ifEmpty { "tcp" }
    val type = j.optString("type")
    val path = j.optString("path")
    val q = buildMap {
        put("host", j.optString("host"))
        put("path", path)
        put("serviceName", path)
        put("seed", path)
        put("headerType", type)
        put("mode", if (net == "grpc") type else j.optString("mode"))
        put("sni", j.optString("sni"))
        put("alpn", j.optString("alpn"))
        put("fp", j.optString("fp"))
    }
    val host = j.getString("add")
    val port = j.get("port").toString().toInt()
    val p = Parts(j.getString("id"), host, port, q, j.optString("ps").trim())
    val settings = JSONObject().put("address", host).put("port", port).put("id", p.user)
        .put("security", j.optString("scy").ifEmpty { "auto" })
    return server(raw, p, "vmess", settings, net, j.optString("tls").ifEmpty { "none" })
}

private fun shadowsocks(raw: String, rest0: String): Server {
    var rest = rest0
    if ('@' !in rest.substringBefore('#')) rest = b64(rest.substringBefore('#').substringBefore('?')) +
        rest.substring(rest.substringBefore('#').length)
    val p = split(rest)
    val userinfo = if (':' in p.user) p.user else b64(p.user)
    val settings = JSONObject().put("address", p.host).put("port", p.port)
        .put("method", userinfo.substringBefore(':')).put("password", userinfo.substringAfter(':'))
    require(p.q["plugin"].isNullOrEmpty()) { "ss plugins are not supported by Xray" }
    return server(raw, p, "shadowsocks", settings, "tcp", "none")
}

private fun hysteria2(raw: String, rest: String): Server {
    val p = split(rest)
    val q = p.q
    val tls = JSONObject().put("serverName", q["sni"]?.ifEmpty { null } ?: p.host).put("alpn", JSONArray().put("h3"))
    q["pinSHA256"]?.takeIf { it.isNotEmpty() }?.let { tls.put("pinnedPeerCertSha256", it.replace(":", "")) }
    val stream = JSONObject().put("network", "hysteria").put("security", "tls").put("tlsSettings", tls)
        .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", p.user))
    if (q["obfs"] == "salamander") stream.put("finalmask", JSONObject().put("udp", JSONArray().put(
        JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", q["obfs-password"].orEmpty())))))
    val ob = JSONObject().put("tag", "proxy").put("protocol", "hysteria")
        .put("settings", JSONObject().put("version", 2).put("address", p.host).put("port", p.port))
        .put("streamSettings", stream)
    return Server(
        raw, p.name.ifEmpty { "${p.host}:${p.port}" }, "hysteria", p.host, p.port, "quic", "tls", ob,
        needsPin = needsPin(q, q["pinSHA256"]),
    )
}

private val notProxy = setOf("freedom", "blackhole", "dns", "loopback")

private fun parseJsonConfig(s: String): Server {
    val c = JSONObject(s)
    val obs = c.getJSONArray("outbounds")
    val ob = (0 until obs.length()).map { obs.getJSONObject(it) }.first { it.optString("protocol") !in notProxy }
    val st = ob.optJSONObject("settings") ?: JSONObject()
    val target = st.optJSONArray("vnext")?.optJSONObject(0) ?: st.optJSONArray("servers")?.optJSONObject(0)
        ?: st.optJSONArray("peers")?.optJSONObject(0) ?: st
    val addr = target.optString("address").ifEmpty { target.optString("endpoint").substringBeforeLast(':') }
    val port = target.optInt("port").takeIf { it > 0 } ?: target.optString("endpoint").substringAfterLast(':').toIntOrNull() ?: 0
    val ss = ob.optJSONObject("streamSettings")
    return Server(
        s, c.optString("remarks").trim().ifEmpty { "$addr:$port" }, ob.getString("protocol"), addr, port,
        ss?.optString("network")?.ifEmpty { null } ?: "tcp", ss?.optString("security")?.ifEmpty { null } ?: "none",
        ob, c,
    )
}
