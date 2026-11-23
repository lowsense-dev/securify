package app.securify.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccentTest {
    @Test
    fun paleAndDesaturatedMonetColorsBecomeReadableAndVivid() {
        val samples = listOf(
            BRAND_ACCENT_ARGB,
            0xFF6B7C68.toInt(), // muted forest green
            0xFFC4CCC0.toInt(), // pale Monet pastel sage
            0xFFDCF5D3.toInt(), // bright Monet mint
            0xFF277AF7.toInt(), // blue
            0xFFE5B84B.toInt(), // amber
            0xFF9C7CF4.toInt(), // violet
        )
        for (raw in samples) {
            val light = tuneAccentArgb(raw, dark = false)
            val dark = tuneAccentArgb(raw, dark = true)
            assertTrue(relativeLuminance(light) in 0.13f..0.22f, "light lum out of range for ${raw.toUInt().toString(16)}")
            assertTrue(relativeLuminance(dark) in 0.19f..0.28f, "dark lum out of range for ${raw.toUInt().toString(16)}")
            assertFalse(useDarkOnAccent(light))
            assertFalse(useDarkOnAccent(dark))
        }
    }

    @Test
    fun brandStaysExactInLightModeAndNeutralFallsBackToBrand() {
        assertEquals(BRAND_ACCENT_ARGB, tuneAccentArgb(BRAND_ACCENT_ARGB, dark = false))
        assertEquals(tuneAccentArgb(BRAND_ACCENT_ARGB, dark = true), tuneAccentArgb(0xFF787878.toInt(), dark = true))
    }
}
