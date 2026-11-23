package app.securify.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.securify.core.BRAND_ACCENT_ARGB
import app.securify.core.tuneAccentArgb
import app.securify.core.useDarkOnAccent
import app.securify.desktop.Os
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** The XDG desktop portal's appearance settings (GNOME 47+, KDE 6): the desktop's take on Monet. */
object SystemLook {
    val dark: Boolean?
    val accent: Color?

    init {
        dark = if (Os.windows) null else read("color-scheme")?.let { Regex("""uint32 (\d)|\bu (\d)""").find(it)?.groupValues?.drop(1)?.firstOrNull { g -> g.isNotEmpty() } }
            ?.let { when (it) { "1" -> true; "2" -> false; else -> null } }
        accent = if (Os.windows) null else read("accent-color")?.let { out ->
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

private fun on(color: Color) = if (useDarkOnAccent(color.toArgb())) Color(0xFF111111) else Color.White

@Composable
fun SecurifyTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: SystemLook.dark ?: isSystemInDarkTheme()
    val seedArgb = SystemLook.accent?.toArgb() ?: BRAND_ACCENT_ARGB
    val primary = Color(tuneAccentArgb(seedArgb, dark))
    // every primary role is derived, otherwise Miuix's blue defaults show through disabled buttons
    val colors = if (dark) {
        val base = Color(0xFF242424)
        darkColorScheme(
            primary = primary,
            onPrimary = on(primary),
            primaryVariant = primary,
            primaryContainer = lerp(base, primary, 0.35f),
            onPrimaryContainer = Color(0xFFEDEDED),
            disabledPrimary = lerp(base, primary, 0.35f),
            disabledOnPrimary = Color(0xFF8C93B0),
            disabledPrimaryButton = lerp(base, primary, 0.45f),
            disabledOnPrimaryButton = Color(0xCCFFFFFF),
            disabledPrimarySlider = lerp(base, primary, 0.45f),
            tertiaryContainer = lerp(base, primary, 0.22f),
            onTertiaryContainer = primary,
            sliderKeyPointForeground = primary,
        )
    } else {
        val base = Color.White
        lightColorScheme(
            primary = primary,
            onPrimary = on(primary),
            primaryVariant = primary,
            primaryContainer = lerp(base, primary, 0.18f),
            onPrimaryContainer = Color(0xFF111111),
            disabledPrimary = lerp(base, primary, 0.30f),
            disabledOnPrimary = Color.White,
            disabledPrimaryButton = lerp(base, primary, 0.30f),
            disabledOnPrimaryButton = Color.White,
            disabledPrimarySlider = lerp(base, primary, 0.35f),
            tertiaryContainer = lerp(base, primary, 0.12f),
            onTertiaryContainer = primary,
            sliderKeyPointForeground = primary,
        )
    }
    MiuixTheme(colors = colors, content = content)
}
