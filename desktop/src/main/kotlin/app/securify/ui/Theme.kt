package app.securify.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme

/** The XDG desktop portal's appearance settings (GNOME 47+, KDE 6): the desktop's take on Monet. */
object SystemLook {
    val dark: Boolean?
    val accent: Color?

    init {
        dark = read("color-scheme")?.let { Regex("""uint32 (\d)|\bu (\d)""").find(it)?.groupValues?.drop(1)?.firstOrNull { g -> g.isNotEmpty() } }
            ?.let { when (it) { "1" -> true; "2" -> false; else -> null } }
        accent = read("accent-color")?.let { out ->
            Regex("""(\d*\.\d+)""").findAll(out).map { it.value.toFloat() }.toList().takeIf { it.size >= 3 }
                ?.takeIf { rgb -> rgb.all { it in 0f..1f } }?.let { Color(it[0], it[1], it[2]) }
        }
    }

    private fun read(key: String): String? = runCatching {
        val cmd = listOf(
            "gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop", "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.Settings.ReadOne", "org.freedesktop.appearance", key,
        )
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().also { p.waitFor() }.takeIf { p.exitValue() == 0 }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurifyTheme(content: @Composable () -> Unit) {
    val dark = SystemLook.dark ?: isSystemInDarkTheme()
    val scheme = rememberDynamicColorScheme(seedColor = SystemLook.accent ?: Color(0xFF006A60), isDark = dark)
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}
