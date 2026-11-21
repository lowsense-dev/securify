package app.securify.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.securify.core.AppState
import app.securify.core.PING_PENDING
import app.securify.core.Prefs
import app.securify.core.Store
import app.securify.core.Sub
import app.securify.core.SubInfo
import app.securify.core.Traffic
import app.securify.core.VpnState
import app.securify.vpn.SecurifyVpn
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

private fun vless(host: String, name: String, transport: String = "tcp") =
    "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@$host:443?security=tls&type=$transport#$name"

private fun sampleState(): AppState {
    val now = System.currentTimeMillis()
    val main = Sub(
        id = "main", url = "https://panel.example.com/sub/abc", name = "North",
        entries = listOf(
            vless("nl1.example.com", "Amsterdam"),
            vless("de1.example.com", "Frankfurt"),
            vless("fi1.example.com", "Helsinki", "ws"),
            vless("pl1.example.com", "Warsaw"),
            vless("lv1.example.com", "Riga"),
            vless("ee1.example.com", "Tallinn"),
        ),
        info = SubInfo(
            upload = 34_200_000_000,
            download = 112_700_000_000,
            total = 300_000_000_000,
            expire = (now + 12L * 86_400_000) / 1000,
            announce = "Thanks for staying. New nodes in Amsterdam and Helsinki are live.",
        ),
        updatedAt = now - 42 * 60_000,
    )
    val work = Sub(
        id = "work", url = "https://work.example.com/api/sub", name = "Work",
        entries = listOf(
            vless("jp1.example.com", "Tokyo"),
            vless("sg1.example.com", "Singapore", "ws"),
            vless("kr1.example.com", "Seoul"),
        ),
        info = SubInfo(
            upload = 2_100_000_000, download = 6_300_000_000, expire = (now + 40L * 86_400_000) / 1000,
        ),
        updatedAt = now - 3 * 3_600_000,
    )
    val selected = main.servers.first().key
    Store.pings.value = mapOf(
        main.servers[0].key to 38L,
        main.servers[1].key to 64L,
        main.servers[2].key to 121L,
        main.servers[3].key to 210L,
        main.servers[4].key to 780L,
        main.servers[5].key to -1L,
        work.servers[0].key to 320L,
        work.servers[1].key to 410L,
        work.servers[2].key to 450L,
    )
    return AppState(subs = listOf(main, work), prefs = Prefs(selected = selected))
}

private fun setOff() {
    SecurifyVpn.state.value = VpnState.Off
    SecurifyVpn.traffic.value = Traffic()
    SecurifyVpn.exit.value = null
    SecurifyVpn.online.value = true
}

private fun setOn() {
    SecurifyVpn.state.value = VpnState.On("Amsterdam", System.currentTimeMillis() - 3_723_000)
    SecurifyVpn.traffic.value = Traffic(1_240_000, 4_810_000, 120_400_000, 1_508_000_000)
    SecurifyVpn.exit.value = "185.23.44.12 · Netherlands"
    SecurifyVpn.online.value = true
}

private fun shot(name: String, width: Int, height: Int, dark: Boolean = true, content: @Composable () -> Unit) {
    val density = 2f
    val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
        SecurifyTheme(darkOverride = dark) { content() }
    }
    // effects and enter animations need a few frames; the first one can still be transparent
    var image = scene.render(0L)
    for (t in listOf(16L, 200L, 900L, 1_600L)) image = scene.render(t * 1_000_000L)
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encode failed for $name")
    File(outDir, "$name.png").writeBytes(data.bytes)
    scene.close()
    println("preview: $name.png")
}

private val outDir = File(System.getProperty("previewOut") ?: "build/previews").apply { mkdirs() }

fun main() {
    Store.init(File(outDir.parentFile, "preview-data"))
    Store.state.value = sampleState()

    setOff()
    shot("home-off", 400, 600) { SecurifyApp() }

    setOn()
    shot("home-on", 400, 600) { SecurifyApp() }

    shot("servers", 400, 600) { SecurifyApp(initialTab = 1) }
    shot("settings", 400, 600) { SecurifyApp(initialTab = 2) }
    shot("home-wide", 1000, 700) { SecurifyApp() }
    shot("apps", 400, 600) { AppsScreen(onBack = {}) }
    shot("rules", 400, 600) { RulesScreen(onBack = {}) }
    shot("qr", 400, 600) { top.yukonga.miuix.kmp.basic.Scaffold { QrDialog("North", "https://panel.example.com/sub/abc", onDismiss = {}) } }

    setOff()
    shot("home-off-light", 400, 600, dark = false) { SecurifyApp() }
    setOn()
    shot("home-on-light", 400, 600, dark = false) { SecurifyApp() }
    shot("servers-light", 400, 600, dark = false) { SecurifyApp(initialTab = 1) }
    shot("settings-light", 400, 600, dark = false) { SecurifyApp(initialTab = 2) }

    val pings = Store.pings.value
    Store.pings.value = pings + Store.state.value.subs.first().servers.associate { it.key to PING_PENDING }
    shot("servers-pinging", 400, 600) { SecurifyApp(initialTab = 1) }
    Store.pings.value = pings

    SecurifyVpn.passwordPrompt.value = app.securify.vpn.PasswordPrompt(retry = false)
    shot("password", 400, 600) { SecurifyApp() }
    SecurifyVpn.passwordPrompt.value = null
}
