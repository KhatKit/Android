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

    @Test
    fun `hide bars accept underscore camel and hyphen spellings`() {
        assertTrue(UiSheetOptions.from(mapOf("hide_topbar" to true)).hideTopBar)
        assertTrue(UiSheetOptions.from(mapOf("hideTopBar" to true)).hideTopBar)
        assertTrue(UiSheetOptions.from(mapOf("hide-top-bar" to 1)).hideTopBar)
        assertTrue(UiSheetOptions.from(mapOf("hide_bottombar" to true)).hideBottomBar)
        assertTrue(UiSheetOptions.from(mapOf("hideBottomBar" to "true")).hideBottomBar)
        assertTrue(UiSheetOptions.from(mapOf("hide-bottom-bar" to true)).hideBottomBar)
        assertFalse(UiSheetOptions.DEFAULT.hideTopBar)
        assertFalse(UiSheetOptions.DEFAULT.hideBottomBar)
        // 隐藏任一栏都要换自绘弹层，否则默认 BottomSheet 藏不掉自己的栏
        assertTrue(UiSheetOptions.from(mapOf("hide_topbar" to true)).usesLargeSheet)
        assertTrue(UiSheetOptions.from(mapOf("hide_bottombar" to true)).usesLargeSheet)
    }

    @Test
    fun `nav parses items selection and forces the controlled sheet`() {
        val options = UiSheetOptions.from(
            mapOf(
                "bottom_nav" to mapOf(
                    "selected" to "challenge",
                    "items" to listOf(
                        mapOf("id" to "dashboard", "label" to "今日", "icon" to "calendar_month"),
                        mapOf("event" to "challenge", "label" to "挑战"),
                        mapOf("id" to "history", "label" to "历史", "icon" to "history"),
                    ),
                ),
            ),
        )
        val navigation = options.navigation!!
        assertEquals("challenge", navigation.selected)
        assertEquals(listOf("dashboard", "challenge", "history"), navigation.items.map { it.event })
        assertEquals("calendar_month", navigation.items[0].icon)
        assertNull(navigation.items[1].icon)
        assertTrue(options.usesLargeSheet)
    }

    @Test
    fun `nav drops invalid entries and rejects too few or too many`() {
        assertNull(
            UiSheetOptions.from(mapOf("nav" to mapOf("items" to listOf(
                mapOf("id" to "a", "label" to "A"))))).navigation,
        )
        assertNull(
            UiSheetOptions.from(mapOf("nav" to mapOf("items" to (1..6).map {
                mapOf("id" to "i$it", "label" to "L$it")
            }))).navigation,
        )
        val mixed = UiSheetOptions.from(mapOf("nav" to mapOf("items" to listOf(
            mapOf("id" to "a", "label" to "A"),
            mapOf("label" to "no id"),
            mapOf("id" to "b", "label" to "B", "icon" to "nope"),
            mapOf("id" to "c", "label" to "C", "disabled" to true),
        )))).navigation!!
        assertEquals(listOf("a", "b", "c"), mixed.items.map { it.event })
        assertNull(mixed.items[1].icon)
        assertFalse(mixed.items[2].enabled)
        assertNull(mixed.selected)
    }

    @Test
    fun `large top bar and top actions force the controlled sheet`() {
        val options = UiSheetOptions.from(
            mapOf(
                "topbar" to "large",
                "top_actions" to listOf(
                    mapOf("id" to "refresh", "label" to "刷新", "icon" to "refresh"),
                    mapOf("event" to "settings", "label" to "设置", "icon" to "nope"),
                ),
            ),
        )
        assertTrue(options.largeTopBar)
        assertTrue(options.usesLargeSheet)
        assertEquals(listOf("refresh", "settings"), options.topActions.map { it.event })
        assertTrue(options.topActions[0].icon != null)
        assertNull(options.topActions[1].icon)
        assertTrue(UiSheetOptions.from(mapOf("large_topbar" to true)).largeTopBar)
        assertFalse(UiSheetOptions.from(mapOf("topbar" to "small")).largeTopBar)
        assertFalse(UiSheetOptions.DEFAULT.largeTopBar)
    }

    @Test
    fun `nav accepts a bare item array without selection`() {
        val options = UiSheetOptions.from(mapOf("nav" to listOf(
            mapOf("id" to "a", "label" to "A"),
            mapOf("id" to "b", "label" to "B"),
        )))
        assertEquals(2, options.navigation!!.items.size)
        assertNull(options.navigation!!.selected)
    }

    @Test
    fun `locked means not dismissable by scrim back or drag`() {
        assertFalse(UiSheetOptions.from(mapOf("dismissable" to false)).dismissable)
        assertFalse(UiSheetOptions.from(mapOf("dismissible" to false)).dismissable)
        assertFalse(UiSheetOptions.from(mapOf("closable" to false)).dismissable)
        assertFalse(UiSheetOptions.from(mapOf("locked" to true)).dismissable)
        assertFalse(UiSheetOptions.from(mapOf("dismissable" to "false")).dismissable)
        assertTrue(UiSheetOptions.from(mapOf("dismissable" to true)).dismissable)
        assertTrue(UiSheetOptions.DEFAULT.dismissable)
        assertTrue(UiSheetOptions.from(mapOf("locked" to false)).dismissable)
        assertTrue(UiSheetOptions.from(mapOf("dismissable" to false)).usesLargeSheet)
    }

    @Test
    fun `orientation accepts three values plus legacy landscape flag`() {
        assertEquals(
            UiSheetOptions.ORIENTATION_LANDSCAPE,
            UiSheetOptions.from(mapOf("orientation" to "landscape")).orientation,
        )
        assertEquals(
            UiSheetOptions.ORIENTATION_PORTRAIT,
            UiSheetOptions.from(mapOf("orientation" to "竖屏")).effectiveOrientation,
        )
        assertEquals(
            UiSheetOptions.ORIENTATION_AUTO,
            UiSheetOptions.from(mapOf("orientation" to "sensor")).effectiveOrientation,
        )
        assertNull(UiSheetOptions.from(mapOf("orientation" to "diagonal")).orientation)
        // 旧写法：landscape = true 等价 orientation = landscape
        assertEquals(
            UiSheetOptions.ORIENTATION_LANDSCAPE,
            UiSheetOptions.from(mapOf("landscape" to true)).effectiveOrientation,
        )
        // 显式 orientation 优先于旧 landscape 布尔
        assertEquals(
            UiSheetOptions.ORIENTATION_PORTRAIT,
            UiSheetOptions.from(mapOf("landscape" to true, "orientation" to "portrait"))
                .effectiveOrientation,
        )
        assertNull(UiSheetOptions.DEFAULT.effectiveOrientation)
        // 只改方向不需要自绘弹层：默认 BottomSheet 也能用
        assertFalse(UiSheetOptions.from(mapOf("orientation" to "portrait")).usesLargeSheet)
        assertFalse(UiSheetOptions.from(mapOf("landscape" to true)).usesLargeSheet)
    }
}
