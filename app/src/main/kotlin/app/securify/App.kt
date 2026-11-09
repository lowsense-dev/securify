package app.securify

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.provider.Settings
import app.securify.core.Platform
import app.securify.core.PingHooks
import app.securify.core.Store
import app.securify.core.SubUpdateJob
import app.securify.core.Updater
import app.securify.core.autoLabel
import app.securify.core.installApk
import app.securify.core.pickApk
import app.securify.core.pingConfig
import go.Seq
import libv2ray.Libv2ray

class App : Application() {
    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        Platform.appVersion = BuildConfig.VERSION_NAME
        Platform.versionCode = BuildConfig.VERSION_CODE
        Platform.deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        Platform.os = "Android"
        Platform.osVersion = Build.VERSION.RELEASE
        Platform.model = Build.MODEL
        PingHooks.real = { s, url -> Libv2ray.measureOutboundDelay(pingConfig(s), url) }
        Updater.pick = { pickApk(it, Build.SUPPORTED_ABIS.toList()) }
        Updater.apply = { installApk(this, it) }
        Updater.downloadDir = cacheDir
        autoLabel = getString(R.string.auto)
        Store.init(filesDir)
        Seq.setContext(this)
        Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        SubUpdateJob.schedule(this, Store.state.value.prefs.autoUpdate)
    }
}
