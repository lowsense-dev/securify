package app.securify

import android.app.Application
import app.securify.core.Store
import app.securify.core.autoLabel
import app.securify.core.SubUpdateJob
import go.Seq
import libv2ray.Libv2ray

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        autoLabel = getString(R.string.auto)
        Store.init(this)
        Seq.setContext(this)
        Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        SubUpdateJob.schedule(this, Store.state.value.prefs.autoUpdate)
    }
}
