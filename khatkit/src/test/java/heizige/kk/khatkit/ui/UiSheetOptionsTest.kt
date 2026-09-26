package heizige.kk.khatkit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSheetOptionsTest {

    @Test
    fun `null and empty map keep legacy defaults`() {
        assertEquals(UiSheetOptions.DEFAULT, UiSheetOptions.from(null))
        assertEquals(UiSheetOptions.DEFAULT, UiSheetOptions.from(emptyMap()))
        assertFalse(UiSheetOptions.DEFAULT.usesLargeSheet)
    }

    @Test
    fun `fullscreen and landscape parsed from booleans`() {
        val options = UiSheetOptions.from(
            mapOf("fullscreen" to true, "landscape" to true),
        )
        assertTrue(options.fullscreen)
        assertTrue(options.landscape)
        assertTrue(options.usesLargeSheet)
    }

    @Test
    fun `string and number flags tolerated`() {
        val options = UiSheetOptions.from(
            mapOf("fullscreen" to "true", "landscape" to 1),
        )
        assertTrue(options.fullscreen)
        assertTrue(options.landscape)
    }

    @Test
    fun `height clamped to valid fraction`() {
        assertEquals(1f, UiSheetOptions.from(mapOf("height" to 2.0)).height)
        assertEquals(0.1f, UiSheetOptions.from(mapOf("height" to 0.01)).height)
        assertEquals(0.6f, UiSheetOptions.from(mapOf("height" to 0.6f)).height)
        assertTrue(UiSheetOptions.from(mapOf("height" to 0.6)).usesLargeSheet)
    }

    @Test
    fun `invalid height ignored`() {
        assertNull(UiSheetOptions.from(mapOf("height" to "tall")).height)
        assertNull(UiSheetOptions.from(mapOf("height" to 0)).height)
        assertNull(UiSheetOptions.from(mapOf("height" to -1)).height)
        assertNull(UiSheetOptions.from(mapOf("height" to Double.NaN)).height)
    }
}
