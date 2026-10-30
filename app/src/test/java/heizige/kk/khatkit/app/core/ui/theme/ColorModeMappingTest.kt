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

    /**
     * 逐档把「SYSTEM 跟随 / LIGHT 固定浅 / DARK 固定深」钉死，且与映射出来的 mochi 模式一一对应。
     *
     * 这里**不调用** mochi 的 `ColorSchemeMode.isDark(...)`：那是 `ui.feice.mochi.theme` 的
     * 顶层扩展函数（不是枚举成员），早先直接当成员调用导致 :app:compileDebugUnitTestKotlin 编译不过。
     * 语义一致性改用「映射到哪个常量 + 该档的期望明暗」来锁：常量名就是 mochi 的契约。
     */
    @Test
    fun resolveDarkIsConsistentForEveryMode() {
        val expectedMochiMode = mapOf(
            ColorMode.SYSTEM to ColorSchemeMode.System,
            ColorMode.LIGHT to ColorSchemeMode.Light,
            ColorMode.DARK to ColorSchemeMode.Dark,
        )
        ColorMode.entries.forEach { mode ->
            assertEquals(expectedMochiMode[mode], mode.toMochiColorSchemeMode())
            val expectedDark = when (mode) {
                ColorMode.SYSTEM -> null // 跟随系统：两种输入各得其所
                ColorMode.LIGHT -> false
                ColorMode.DARK -> true
            }
            booleanArrayOf(true, false).forEach { systemDark ->
                val actual = mode.resolveDark(systemDark)
                if (expectedDark == null) {
                    assertEquals("SYSTEM 应跟随系统 systemDark=$systemDark", systemDark, actual)
                } else {
                    assertEquals("$mode 应强覆盖系统 systemDark=$systemDark", expectedDark, actual)
                }
            }
        }
    }
}
