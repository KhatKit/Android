package heizige.kk.khatkit.app.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ui.feice.mochi.theme.ColorSchemeMode

/**
 * ColorMode 三态 → mochi 配色模式 / 明暗 的代码级回归（T3）。
 *
 * 口径：SYSTEM 跟随系统，LIGHT/DARK 强覆盖系统；两者必须同源，否则会出现
 * 「设置浅色 + 系统深色 ⇒ 配色深、图标/代码高亮浅」的错位（T1 已修的根因 A）。
 */
class ColorModeMappingTest {

    @Test
    fun mochiColorSchemeModeMapping() {
        assertEquals(ColorSchemeMode.System, ColorMode.SYSTEM.toMochiColorSchemeMode())
        assertEquals(ColorSchemeMode.Light, ColorMode.LIGHT.toMochiColorSchemeMode())
        assertEquals(ColorSchemeMode.Dark, ColorMode.DARK.toMochiColorSchemeMode())
        // 每个枚举值都必须有映射，加新档时这条会先炸
        assertEquals(ColorMode.entries.size, ColorMode.entries.map { it.toMochiColorSchemeMode() }.toSet().size)
    }

    @Test
    fun systemFollowsSystemDark() {
        assertTrue(ColorMode.SYSTEM.resolveDark(systemDark = true))
        assertFalse(ColorMode.SYSTEM.resolveDark(systemDark = false))
    }

    @Test
    fun lightAndDarkOverrideSystem() {
        assertFalse(ColorMode.LIGHT.resolveDark(systemDark = true))
        assertFalse(ColorMode.LIGHT.resolveDark(systemDark = false))
        assertTrue(ColorMode.DARK.resolveDark(systemDark = true))
        assertTrue(ColorMode.DARK.resolveDark(systemDark = false))
    }

    @Test
    fun resolveDarkMatchesMochiModeSemantics() {
        // mochi 侧 isDark(systemDark) 的语义必须与本仓 resolveDark 一致
        ColorMode.entries.forEach { mode ->
            val mochiMode = mode.toMochiColorSchemeMode()
            booleanArrayOf(true, false).forEach { systemDark ->
                assertEquals(
                    "$mode 在 systemDark=$systemDark 下两处判定不一致",
                    mode.resolveDark(systemDark),
                    mochiMode.isDark(systemDark),
                )
            }
        }
    }
}
