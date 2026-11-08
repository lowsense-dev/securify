package app.securify.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import app.securify.BuildConfig
import app.securify.R
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

class Release(val version: String, val apkName: String, val apkUrl: String, val sumsUrl: String)

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

/** Picks the APK built for the first ABI the device supports, the universal one otherwise. */
fun pickApk(names: List<String>, abis: List<String>): String? =
    abis.firstNotNullOfOrNull { abi -> names.firstOrNull { it.endsWith("-$abi.apk") } }
        ?: names.firstOrNull { it.endsWith("-universal.apk") }

fun sha256From(sums: String, name: String): String? = sums.lines().map { it.trim().split(Regex("\\s+")) }
    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }?.get(0)?.lowercase()

/** Checks GitHub Releases only when asked: the app is outside the VPN, so every check shows GitHub the real IP. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/lowsense-dev/securify/releases/latest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    private val busy get() = state.value.let { it is UpdateState.Checking || it is UpdateState.Downloading }

    fun check() {
        if (busy) return
        state.value = UpdateState.Checking
        scope.launch {
            state.value = runCatching {
                val j = JSONObject(open(LATEST).use { it.readBytes().decodeToString() })
                val tag = j.getString("tag_name")
                val code = tagVersionCode(tag) ?: error("bad tag $tag")
                if (code <= BuildConfig.VERSION_CODE) return@runCatching UpdateState.Latest
                val assets = j.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                    .associate { it.getString("name") to it.getString("browser_download_url") }
                val apk = pickApk(assets.keys.toList(), Build.SUPPORTED_ABIS.toList()) ?: error("no APK in $tag")
                UpdateState.Available(Release(tag.removePrefix("v"), apk, assets.getValue(apk), assets["SHA256SUMS"] ?: error("no SHA256SUMS")))
            }.getOrElse { UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    fun install(ctx: Context, r: Release) {
        if (busy) return
        state.value = UpdateState.Downloading(r, 0)
        scope.launch {
            runCatching {
                val want = sha256From(open(r.sumsUrl).use { it.readBytes().decodeToString() }, r.apkName) ?: error("no checksum for ${r.apkName}")
                val file = File(ctx.cacheDir, "update.apk")
                val md = MessageDigest.getInstance("SHA-256")
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection
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
                commit(ctx, file)
                state.value = UpdateState.Available(r)
            }.onFailure { state.value = UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    /** Android itself refuses an APK signed with another key, so a swapped file can't replace the app. */
    private fun commit(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        params.setSize(apk.length())
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("securify.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pi.intentSender)
        }
        apk.delete()
    }

    private fun open(url: String): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Securify/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream
    }
}

/** Gets the install session's result and shows the system confirmation screen. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {}
            else -> Toast.makeText(
                ctx, ctx.getString(R.string.update_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "$status"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
