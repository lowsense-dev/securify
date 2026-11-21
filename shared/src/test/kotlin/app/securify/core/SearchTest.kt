package app.securify.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun matchesSubsequenceIgnoringCase() {
        assertTrue(fuzzyMatch("Frankfurt", "frf"))
        assertTrue(fuzzyMatch("Франкфурт", "фрф"))
    }

    @Test
    fun rejectsMissingOrOutOfOrderCharacters() {
        assertFalse(fuzzyMatch("Frankfurt", "urtf"))
        assertFalse(fuzzyMatch("Frankfurt", "xyz"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(fuzzyMatch("Frankfurt", "  "))
    }
}
