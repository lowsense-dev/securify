package app.securify.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.securify.R
import app.securify.core.Store
import app.securify.core.VpnState
import app.securify.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class QsTile : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = MainScope().launch {
            combine(SecurifyVpn.state, Store.state) { s, app -> s to app.selected?.name }.collect { (s, name) -> render(s, name) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
    }

    override fun onClick() {
        when {
            SecurifyVpn.isActive -> SecurifyVpn.stop(this)
            VpnService.prepare(this) == null -> SecurifyVpn.start(this)
            else -> openApp()
        }
    }

    private fun render(s: VpnState, server: String?) {
        val tile = qsTile ?: return
        tile.state = if (s is VpnState.On || s is VpnState.Connecting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = (s as? VpnState.On)?.server ?: server ?: getString(R.string.app_name)
        val status = getString(
            when (s) {
                is VpnState.On -> R.string.status_on
                VpnState.Connecting -> R.string.status_connecting
                else -> R.string.status_off
            },
        )
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = status
        tile.contentDescription = "${tile.label}, $status"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
