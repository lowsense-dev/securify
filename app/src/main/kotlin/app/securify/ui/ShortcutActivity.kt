package app.securify.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import app.securify.vpn.SecurifyVpn

/** Launcher shortcuts: connects or disconnects without showing the app, unless VPN permission is still needed. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            "app.securify.DISCONNECT" -> SecurifyVpn.stop(this)
            else -> if (VpnService.prepare(this) == null) SecurifyVpn.start(this)
            else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
