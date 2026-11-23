package app.securify.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Securify's own seed color, used when the system doesn't report a colorful accent. */
const val BRAND_ACCENT_ARGB = 0xFF0E7A6A.toInt()

/**
 * Normalizes a system/Monet accent so it is neither pastel/washed-out nor too dark on Miuix cards.
 * Neutral accents fall back to [BRAND_ACCENT_ARGB]; colorful ones keep their hue with clamped
 * saturation and perceptual luminance.
 */
fun tuneAccentArgb(argb: Int, dark: Boolean): Int {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val (h, s, l) = rgbToHsl(r, g, b)
    if (s < 0.05f) return tuneAccentArgb(BRAND_ACCENT_ARGB, dark)
    val sat = s.coerceIn(0.48f, 0.85f)
    val loLum = if (dark) 0.20f else 0.14f
    val hiLum = if (dark) 0.27f else 0.21f
    val initial = hslToArgb(h, sat, l)
    val curLum = relativeLuminance(initial)
    if (curLum in loLum..hiLum) return initial
    val target = if (curLum < loLum) loLum else hiLum
    var lo = 0.08f
    var hi = 0.92f
    repeat(14) {
        val mid = (lo + hi) * 0.5f
        if (relativeLuminance(hslToArgb(h, sat, mid)) < target) lo = mid else hi = mid
    }
    return hslToArgb(h, sat, (lo + hi) * 0.5f)
}

fun useDarkOnAccent(argb: Int): Boolean = relativeLuminance(argb) > 0.35f

fun relativeLuminance(argb: Int): Float {
    fun linear(channel: Int): Float {
        val c = (channel and 0xFF) / 255f
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * linear(argb ushr 16) + 0.7152f * linear(argb ushr 8) + 0.0722f * linear(argb)
}

private fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) * 0.5f
    val d = max - min
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f
    return Triple(if (h < 0f) h + 1f else h, s.coerceIn(0f, 1f), l)
}

private fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h * 6f) % 2f - 1f))
    val m = l - c * 0.5f
    val (r, g, b) = when ((h * 6f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val ri = ((r + m).coerceIn(0f, 1f) * 255f).roundToInt()
    val gi = ((g + m).coerceIn(0f, 1f) * 255f).roundToInt()
    val bi = ((b + m).coerceIn(0f, 1f) * 255f).roundToInt()
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}
