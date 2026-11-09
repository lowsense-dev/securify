package app.securify.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class Release(val version: String, val assetName: String, val assetUrl: String, val sumsUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Same formula as the release workflow: v1.4.0 -> 10400. */
fun tagVersionCode(tag: String): Int? = Regex("""v?(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(tag.trim())
    ?.destructured?.let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }

fun sha256From(sums: String, name: String): String? = sums.lines().map { it.trim().split(Regex("\\s+")) }
    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }?.get(0)?.lowercase()

/**
 * Checks GitHub Releases only when asked: the app's own traffic bypasses the tunnel, so every check shows GitHub the real IP.
 * Each platform says which release asset is its build ([pick]) and how to install the verified file ([apply]).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/lowsense-dev/securify/releases/latest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    var pick: (List<String>) -> String? = { null }
    var apply: (File) -> Unit = {}
    var downloadDir = File(System.getProperty("java.io.tmpdir"))

    private val busy get() = state.value.let { it is UpdateState.Checking || it is UpdateState.Downloading }

    fun check() {
        if (busy) return
        state.value = UpdateState.Checking
        scope.launch {
            state.value = runCatching {
                val j = JSONObject(open(LATEST).use { it.readBytes().decodeToString() })
                val tag = j.getString("tag_name")
                val code = tagVersionCode(tag) ?: error("bad tag $tag")
                if (code <= Platform.versionCode) return@runCatching UpdateState.Latest
                val assets = j.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                    .associate { it.getString("name") to it.getString("browser_download_url") }
                val asset = pick(assets.keys.toList()) ?: error("no build for this device in $tag")
                UpdateState.Available(Release(tag.removePrefix("v"), asset, assets.getValue(asset), assets["SHA256SUMS"] ?: error("no SHA256SUMS")))
            }.getOrElse { UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    fun install(r: Release) {
        if (busy) return
        state.value = UpdateState.Downloading(r, 0)
        scope.launch {
            runCatching {
                val want = sha256From(open(r.sumsUrl).use { it.readBytes().decodeToString() }, r.assetName) ?: error("no checksum for ${r.assetName}")
                val file = File(downloadDir, r.assetName + ".part")
                val md = MessageDigest.getInstance("SHA-256")
                val c = URL(r.assetUrl).openConnection() as HttpURLConnection
                try {
                    val total = c.contentLengthLong
                    c.inputStream.use { i ->
                        file.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                md.update(buf, 0, n)
                                done += n
                                if (total > 0) state.value = UpdateState.Downloading(r, (done * 100 / total).toInt())
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                if (md.digest().joinToString("") { "%02x".format(it) } != want) {
                    file.delete()
                    throw IOException("checksum mismatch")
                }
                apply(file)
                state.value = UpdateState.Available(r)
            }.onFailure { state.value = UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    private fun open(url: String): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Securify/${Platform.appVersion}")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream
    }
}
