package app.securify.core

import android.content.Context
import app.securify.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest

enum class GeoSource(val base: String?) {
    BUNDLED(null),
    LOYALSOLDIER("https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/"),
    RUNETFREEDOM("https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/"),
}

object Geo {
    private val names = listOf("geoip.dat", "geosite.dat")
    private const val DOWNLOADED = "downloaded"

    /** Xray's geodata loader stats real files, so the geoip/geosite bundled in the APK must be copied to disk. */
    @Synchronized
    fun ensure(ctx: Context) {
        val stamp = File(ctx.filesDir, "geo.version")
        val version = BuildConfig.VERSION_CODE.toString()
        if (stamp.exists() && stamp.readText().let { it == version || it == DOWNLOADED }) return
        for (name in names) {
            val tmp = File(ctx.filesDir, "$name.tmp")
            ctx.assets.open(name).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(File(ctx.filesDir, name))
        }
        stamp.writeText(version)
    }

    /** Downloads both files and swaps them in only if every checksum matches. */
    @Synchronized
    fun update(ctx: Context, source: GeoSource) {
        val stamp = File(ctx.filesDir, "geo.version")
        val base = source.base ?: run {
            stamp.delete()
            ensure(ctx)
            return Store.prefs { it.copy(geo = source, geoUpdated = 0) }
        }
        val files = names.map { name ->
            val want = get(base + name + ".sha256sum") { it.readBytes() }.decodeToString().trim().substringBefore(' ').lowercase()
            val tmp = File(ctx.filesDir, "$name.dl")
            val md = MessageDigest.getInstance("SHA-256")
            get(base + name) { i -> DigestOutputStream(tmp.outputStream(), md).use { i.copyTo(it) } }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (got != want) { tmp.delete(); throw IOException("$name: checksum mismatch") }
            tmp to File(ctx.filesDir, name)
        }
        files.forEach { (tmp, dst) -> tmp.renameTo(dst) }
        stamp.writeText(DOWNLOADED)
        Store.prefs { it.copy(geo = source, geoUpdated = System.currentTimeMillis()) }
    }

    private fun <T> get(url: String, read: (java.io.InputStream) -> T): T {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use(read)
        } finally {
            c.disconnect()
        }
    }
}
