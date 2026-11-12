package app.securify.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.securify.core.Platform
import app.securify.core.Store
import app.securify.core.Subs
import app.securify.core.autoLabel
import app.securify.ui.BuildInfo
import app.securify.ui.R
import app.securify.ui.SecurifyApp
import app.securify.ui.SecurifyTheme
import app.securify.ui.str
import app.securify.core.VpnState
import app.securify.vpn.SecurifyVpn
import java.awt.Dimension
import java.awt.SystemTray
import java.awt.Toolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cmd = when {
        "--connect" in args -> "connect"
        "--disconnect" in args -> "disconnect"
        else -> "show"
    }
    Env.data.mkdirs()
    if (Instance.forward(cmd)) exitProcess(0)
    // matches StartupWMClass, so docks group the window under the menu entry
    runCatching {
        Toolkit.getDefaultToolkit().javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, "securify")
    }

    Env.setup()
    Platform.appVersion = BuildInfo.VERSION_NAME
    Platform.versionCode = BuildInfo.VERSION_CODE
    Platform.deviceId = Env.deviceId()
    Platform.os = "Linux"
    Platform.osVersion = Env.osVersion()
    Platform.model = runCatching { java.io.File("/sys/class/dmi/id/product_name").readText().trim() }.getOrNull()?.ifEmpty { null } ?: "PC"
    autoLabel = str(R.string.auto)
    Store.init(Env.data)
    DesktopPing.install()
    setupUpdater { SecurifyVpn.isActive }
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        while (true) {
            if (Store.state.value.prefs.autoUpdate) Subs.refreshDue()
            delay(3_600_000)
        }
    }

    var visible by mutableStateOf(true)
    val onCommand = { c: String ->
        when (c) {
            "connect" -> if (!SecurifyVpn.isActive) SecurifyVpn.start()
            "disconnect" -> SecurifyVpn.stop()
            else -> {}
        }
        if (c == "show" || c == "connect") visible = true
    }
    // a unix socket path is limited to 108 bytes; with a longer data dir the app just isn't single-instance
    runCatching { Instance.listen(onCommand) }
    if (cmd == "connect") onCommand(cmd)
    val sni = SniTray(onOpen = { visible = true }, onQuit = { exitProcess(0) }).takeIf { it.start() }
    val awtTray = sni == null && SystemTray.isSupported()
    val tray = sni != null || awtTray
    Runtime.getRuntime().addShutdownHook(Thread { SecurifyVpn.closeHelper() })

    application {
        val quit = { SecurifyVpn.closeHelper(); exitApplication() }
        val state by SecurifyVpn.state.collectAsState()
        val icon = painterResource("securify.svg")
        val prompt by SecurifyVpn.passwordPrompt.collectAsState()
        LaunchedEffect(prompt) { if (prompt != null) visible = true }
        LaunchedEffect(state) { sni?.update(state) }
        if (awtTray) Tray(
            icon = icon,
            tooltip = "Securify",
            onAction = { visible = true },
            menu = {
                Item(str(R.string.tray_open), onClick = { visible = true })
                if (state is VpnState.On || state is VpnState.Connecting) Item(str(R.string.action_disconnect), onClick = SecurifyVpn::stop)
                else Item(str(R.string.action_connect), onClick = SecurifyVpn::start)
                Separator()
                Item(str(R.string.tray_quit), onClick = quit)
            },
        )
        val windowState = rememberWindowState(size = DpSize(440.dp, 820.dp))
        Window(
            // with no tray to come back from, closing the window quits
            onCloseRequest = { if (tray) visible = false else quit() },
            visible = visible,
            state = windowState,
            title = "Securify",
            icon = icon,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(360, 560) }
            var shown by remember { mutableStateOf(visible) }
            LaunchedEffect(visible) {
                if (visible && !shown) window.toFront()
                shown = visible
            }
            SecurifyTheme { SecurifyApp() }
        }
    }
    exitProcess(0)
}
