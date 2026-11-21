package app.securify.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Securify's own seed color, used below Android 12 where there is no Monet. */
private val Brand = Color(0xFF0E7A6A)

private fun on(color: Color) = if (color.luminance() > 0.55f) Color(0xFF111111) else Color.White

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = on(Brand),
    primaryVariant = Brand,
    primaryContainer = lerp(Color.White, Brand, 0.18f),
    onPrimaryContainer = Color(0xFF111111),
    disabledPrimary = lerp(Color.White, Brand, 0.30f),
    disabledOnPrimary = Color.White,
    disabledPrimaryButton = lerp(Color.White, Brand, 0.30f),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = lerp(Color.White, Brand, 0.35f),
    tertiaryContainer = lerp(Color.White, Brand, 0.12f),
    onTertiaryContainer = Brand,
    sliderKeyPointForeground = Brand,
)

private val Dark = darkColorScheme(
    primary = lerp(Brand, Color.White, 0.3f),
    onPrimary = on(lerp(Brand, Color.White, 0.3f)),
    primaryVariant = lerp(Brand, Color.White, 0.3f),
    primaryContainer = lerp(Color(0xFF242424), Brand, 0.35f),
    onPrimaryContainer = Color(0xFFEDEDED),
    disabledPrimary = lerp(Color(0xFF242424), Brand, 0.35f),
    disabledOnPrimary = Color(0xFF8C93B0),
    disabledPrimaryButton = lerp(Color(0xFF242424), Brand, 0.45f),
    disabledOnPrimaryButton = Color(0xCCFFFFFF),
    disabledPrimarySlider = lerp(Color(0xFF242424), Brand, 0.45f),
    tertiaryContainer = lerp(Color(0xFF242424), Brand, 0.22f),
    onTertiaryContainer = lerp(Brand, Color.White, 0.3f),
    sliderKeyPointForeground = lerp(Brand, Color.White, 0.3f),
)

@Composable
fun SecurifyTheme(content: @Composable () -> Unit) {
    // from Android 12 the wallpaper picks the colors, below it the brand green does
    val controller = remember {
        ThemeController(
            colorSchemeMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorSchemeMode.MonetSystem else ColorSchemeMode.System,
            lightColors = Light,
            darkColors = Dark,
        )
    }
    MiuixTheme(controller = controller, content = content)
}