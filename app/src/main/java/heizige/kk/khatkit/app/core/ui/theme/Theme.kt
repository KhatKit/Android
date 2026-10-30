package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberCurrentColorMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberUserSettingsState
import heizige.kk.khatkit.uikit.mochi.LocalOptionSwitchExpressiveEnabled
import heizige.kk.khatkit.uikit.mochi.LocalOptionSwitchScale

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()

val LocalDarkMode = compositionLocalOf { false }

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * 组合根主题（P2 起 theme 目录只讲 mochi 词汇）。
 *
 * 顺序：数据（调色板/字阶）→ [MochiTheme]（颜色/字阶/形状/系统栏图标对比度来源）→
 * 本仓自有 CompositionLocal（暗色标记、扩展色、卡片圆角、回弹、khromia 开关）→
 * material3 兼容层（见 `themecompat/CompatMaterial3.kt`，P6/P7 删除）。
 *
 * 系统栏图标对比度（原 `WindowCompat` 的 InsetsController 那段）已交给
 * [MochiTheme] 的 `systemBarAppearance`，本文件不再碰 Window/InsetsController。
 */
@Composable
fun KhatKitTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    val isMiuix = false
    val listCardStyle = remember(
        settings.listCardLargeCorner,
        settings.listCardSmallCorner,
        settings.listCardGap,
        isMiuix,
    ) {
        if (isMiuix) {
            MiuixListCardStyle
        } else {
            ListCardStyle(
                largeCorner = settings.listCardLargeCorner.dp,
                smallCorner = settings.listCardSmallCorner.dp,
                gap = settings.listCardGap.dp,
            )
        }
    }

    // 回弹（overscroll）：MD3 要**平台默认**（Android 12+ 拉伸回弹、以下 glow），
    // 只有 Miuix 才继续屏蔽。
    //
    // Miuix 侧的效果不由这里负责：`KedgeMiuixTheme` → `MiuixMaterialThemeBridge`
    // （Kedge/…/theme/MiuixMaterialBridge.kt:137）会在**更内层** provide
    // `MiuixOverscrollFactory`（内层覆盖外层），页面再用 `Modifier.overScrollVertical()`
    // （见 `core/ui/components/ui/miuix/MiuixSettingsPage.kt:152` 并配 `:158 overscrollEffect = null`）
    // 自己画 Miuix 风格的回弹。两条都不要抢，所以 Miuix 分支保持今天的 `null` 不变。
    //
    // 为什么不能再写死 `provides null`（foundation 1.12.0 源码逐条）：
    //  · `Overscroll.kt:343-347` `rememberOverscrollEffect()` 第一行就是
    //    `val overscrollFactory = LocalOverscrollFactory.current ?: return null`
    //    ⇒ 只要本层 provide null，**全 app 的滚动容器都拿不到 overscroll**（用户报的没回弹）。
    //  · `Overscroll.kt:390-393` `LocalOverscrollFactory` 的默认值是计算出来的
    //    `compositionLocalWithComputedDefaultOf { defaultOverscrollFactory() }`。
    //  · `AndroidOverscroll.android.kt:93-101` Android 实现：读 `LocalOverscrollConfiguration`，
    //    非空 ⇒ 返回 `AndroidEdgeEffectOverscrollFactory`（:100，:125-133 造
    //    `AndroidEdgeEffectOverscrollEffect`）。
    //  · `OverscrollConfiguration.android.kt:76-77` `LocalOverscrollConfiguration` 的默认值
    //    就是**非空**的 `OverscrollConfiguration()`（本仓没有任何地方 provide 它，已 grep 证实）。
    //  ⇒ 所以「把当前值原样转发下去」就等于本层不 provide，取的正是平台默认工厂。
    val overscrollFactory = LocalOverscrollFactory.current

    // 2026-10-30 口径：全面切 Mochi，不搬旧色板/字阶 ⇒ 只用 Mochi 默认
    // colorScheme / textStyles / shapes，由 MochiTheme 独家管理系统栏图标对比度。
    MochiTheme(systemBarAppearance = !darkTheme) {
        CompositionLocalProvider(
            LocalDarkMode provides darkTheme,
            LocalExtendColors provides extendColors,
            LocalOverscrollFactory provides if (isMiuix) null else overscrollFactory,
            LocalListCardStyle provides listCardStyle,
            LocalOptionSwitchScale provides 1f,
            LocalOptionSwitchExpressiveEnabled provides false,
        ) {
            ProvideMaterial3Theme(dark = darkTheme, amoled = amoledDarkMode) {
                // U4f1：全局 Toast 宿主（Mochi 胶囊）。Toast.show() 的跨页面提示都落在这里，
                // 新的提示替换旧的，不堆叠；host 只吃胶囊区域的触摸。
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    content()
                    heizige.kk.khatkit.uikit.mochi.KkToastHost(
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
