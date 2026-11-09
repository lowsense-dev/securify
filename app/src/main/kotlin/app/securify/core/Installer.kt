package app.securify.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import app.securify.R
import java.io.File

/** Picks the APK built for the first ABI the device supports, the universal one otherwise. */
fun pickApk(names: List<String>, abis: List<String>): String? =
    abis.firstNotNullOfOrNull { abi -> names.firstOrNull { it.endsWith("-$abi.apk") } }
        ?: names.firstOrNull { it.endsWith("-universal.apk") }

/** Android itself refuses an APK signed with another key, so a swapped file can't replace the app. */
fun installApk(ctx: Context, apk: File) {
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
