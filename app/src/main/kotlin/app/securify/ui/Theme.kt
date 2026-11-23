package app.securify.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.securify.core.BRAND_ACCENT_ARGB
import app.securify.core.tuneAccentArgb
import app.securify.core.useDarkOnAccent
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private fun on(argb: Int) = if (useDarkOnAccent(argb)) Color(0xFF111111) else Color.White

private fun buildLight(seedArgb: Int) = tuneAccentArgb(seedArgb, dark = false).let { argb ->
    val primary = Color(argb)
    lightColorScheme(
        primary = primary,
        onPrimary = on(argb),
        primaryVariant = primary,
        primaryContainer = lerp(Color.White, primary, 0.18f),
        onPrimaryContainer = Color(0xFF111111),
        disabledPrimary = lerp(Color.White, primary, 0.30f),
        disabledOnPrimary = Color.White,
        disabledPrimaryButton = lerp(Color.White, primary, 0.30f),
        disabledOnPrimaryButton = Color.White,
        disabledPrimarySlider = lerp(Color.White, primary, 0.35f),
        tertiaryContainer = lerp(Color.White, primary, 0.12f),
        onTertiaryContainer = primary,
        sliderKeyPointForeground = primary,
    )
}

private fun buildDark(seedArgb: Int) = tuneAccentArgb(seedArgb, dark = true).let { argb ->
    val primary = Color(argb)
    val base = Color(0xFF242424)
    darkColorScheme(
        primary = primary,
        onPrimary = on(argb),
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
}

@Composable
fun SecurifyTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    // Take only the wallpaper accent from Monet instead of Miuix's MonetSystem, which turns
    // dark-mode containers pastel and washes out primary contrast.
    val seedArgb = remember(config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { ctx.getColor(android.R.color.system_accent1_500) }.getOrDefault(BRAND_ACCENT_ARGB)
        } else {
            BRAND_ACCENT_ARGB
        }
    }
    val controller = remember(seedArgb) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            lightColors = buildLight(seedArgb),
            darkColors = buildDark(seedArgb),
        )
    }
    MiuixTheme(controller = controller, content = content)
}
