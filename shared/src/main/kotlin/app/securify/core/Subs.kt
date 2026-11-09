package app.securify.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

fun parseBody(body: String): List<String> {
    val t = body.trim().removePrefix("﻿")
    if (t.startsWith("[")) return JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it).toString() } }
        .filter { parseEntry(it) != null }.distinct()
    if (t.startsWith("{")) return listOf(JSONObject(t).toString()).filter { parseEntry(it) != null }
    val text = if ("://" in t) t else runCatching { b64(t) }.getOrDefault(t)
    return text.lineSequence().map { it.trim() }.filter { "://" in it && parseEntry(it) != null }.distinct().toList()
}

fun headerText(v: String?): String? {
    val s = v?.trim() ?: return null
    val text = if (s.startsWith("base64:")) runCatching { b64(s.removePrefix("base64:")) }.getOrNull() else s
    return text?.trim()?.ifEmpty { null }
}

fun parseUserInfo(v: String?): Map<String, Long> = v.orEmpty().split(';').mapNotNull { part ->
    val k = part.substringBefore('=').trim().lowercase()
    part.substringAfter('=', "").trim().toDoubleOrNull()?.let { k to it.toLong() }
}.toMap()

fun subName(title: String?, disposition: String?, url: String): String {
    headerText(title)?.let { return it }
    disposition?.let { d ->
        val star = Regex("filename\\*=(?:UTF-8'')?\"?([^\";]+)", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)
        val plain = Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)
        (star ?: plain)?.let { java.net.URLDecoder.decode(it, "UTF-8").trim() }?.ifEmpty { null }?.let { return it }
    }
    return runCatching { URI(url).host }.getOrNull() ?: url
}

/** Per-provider HWID, so two panels can't match the same device by its id. */
fun hwidFor(deviceId: String, url: String): String {
    val host = runCatching { URI(url).host }.getOrNull() ?: url
    return MessageDigest.getInstance("SHA-256").digest("$deviceId|$host".toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32)
}

object Subs {
    fun hwid(url: String): String = hwidFor(Platform.deviceId, url)

    fun fetch(url: String, id: String = UUID.randomUUID().toString()): Sub {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", when (Store.state.value.prefs.agent) {
                SubAgent.SECURIFY -> "Securify/${Platform.appVersion}"
                SubAgent.V2RAYNG -> "v2rayNG/1.10.24"
                SubAgent.HAPP -> "Happ/3.2.1"
            })
            c.setRequestProperty("Accept", "*/*")
            if (Store.state.value.prefs.hwid) {
                c.setRequestProperty("x-hwid", hwid(url))
                c.setRequestProperty("x-device-os", Platform.os)
                c.setRequestProperty("x-ver-os", Platform.osVersion)
                c.setRequestProperty("x-device-model", Platform.model)
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val ui = parseUserInfo(c.getHeaderField("subscription-userinfo"))
            val info = SubInfo(
                upload = ui["upload"] ?: 0, download = ui["download"] ?: 0,
                total = ui["total"] ?: 0, expire = ui["expire"] ?: 0,
                intervalHours = c.getHeaderField("profile-update-interval")?.trim()?.toIntOrNull() ?: 0,
                announce = headerText(c.getHeaderField("announce")),
                supportUrl = headerText(c.getHeaderField("support-url")),
                webPageUrl = headerText(c.getHeaderField("profile-web-page-url")),
            )
            val name = subName(c.getHeaderField("profile-title"), c.getHeaderField("content-disposition"), url)
            return Sub(id, url, name, parseBody(body), info, System.currentTimeMillis())
        } finally {
            c.disconnect()
        }
    }

    fun refresh(sub: Sub) = Store.putSub(fetch(sub.url, sub.id))

    fun refreshDue() {
        val now = System.currentTimeMillis()
        Store.state.value.subs.filter { it.url.isNotEmpty() }.forEach { sub ->
            val every = (sub.info.intervalHours.takeIf { it > 0 } ?: 24) * 3_600_000L
            if (now - sub.updatedAt >= every) runCatching { refresh(sub) }
        }
    }

    sealed interface Imported {
        data class Subscription(val id: String, val name: String, val servers: Int) : Imported
        data class Servers(val count: Int) : Imported
        data object Nothing : Imported
    }

    fun import(text: String): Imported {
        val t = text.trim()
        val urls = t.lines().map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }
        if (urls.isNotEmpty()) {
            val existing = Store.state.value.subs.associateBy { it.url }
            val subs = urls.map { u -> fetch(u, existing[u]?.id ?: UUID.randomUUID().toString()) }
            subs.forEach(Store::putSub)
            return Imported.Subscription(subs.first().id, subs.first().name, subs.sumOf { it.servers.size })
        }
        val entries = parseBody(t)
        if (entries.isEmpty()) return Imported.Nothing
        val manual = Store.state.value.subs.firstOrNull { it.id == MANUAL_ID } ?: Sub(MANUAL_ID, "", "", emptyList())
        Store.putSub(manual.copy(entries = (manual.entries + entries).distinct(), updatedAt = System.currentTimeMillis()))
        return Imported.Servers(entries.size)
    }
}
