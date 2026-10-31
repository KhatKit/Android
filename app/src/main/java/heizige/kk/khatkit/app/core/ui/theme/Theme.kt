package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import ui.feice.mochi.ios.iosMaterials
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.ThemeController
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberCurrentColorMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberUserSettingsState
import heizige.kk.khatkit.uikit.mochi.LocalKkEffectsEnabled
import heizige.kk.khatkit.uikit.mochi.LocalOptionSwitchExpressiveEnabled
import heizige.kk.khatkit.uikit.mochi.LocalOptionSwitchScale

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()

val LocalDarkMode = compositionLocalOf { false }

/**
 * 本仓 [ColorMode] → mochi 的配色模式：**只换明暗**，不换种子色/取色算法（预设主题运行期不注入）。
 *
 * 三态闭环：
 *  · [ColorMode.SYSTEM] → [ColorSchemeMode.System]：mochi 的 `ThemeController.currentColorScheme()`
 *    读 `isSystemInDarkTheme()`，系统切夜间即跟随（见 Theme.kt 里 `darkTheme` 的同一判定）；
 *  · [ColorMode.LIGHT] / [ColorMode.DARK] → `ColorSchemeMode.Light` / `.Dark`：强覆盖，与系统无关；
 *  · `darkTheme`（LocalDarkMode/扩展色/系统栏图标/代码高亮用）与配色同源于 `colorMode`，
 *    所以不会再出现「配色深、图标浅」的错位。单测见 `ColorModeMappingTest`。
 */
internal fun ColorMode.toMochiColorSchemeMode(): ColorSchemeMode = when (this) {
    ColorMode.SYSTEM -> ColorSchemeMode.System
    ColorMode.LIGHT -> ColorSchemeMode.Light
    ColorMode.DARK -> ColorSchemeMode.Dark
}

/**
 * 三态 → 明暗的**唯一**判定（配色、LocalDarkMode、扩展色、系统栏图标、代码高亮都读它）：
 * SYSTEM 跟随 [systemDark]，LIGHT/DARK 强覆盖系统。单测见 ColorModeMappingTest。
 */
internal fun ColorMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ColorMode.SYSTEM -> systemDark
    ColorMode.LIGHT -> false
    ColorMode.DARK -> true
}

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatKitTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    // 三态判定只此一处：配色、图标、扩展色都从这里拿同一个 boolean
    val darkTheme = colorMode.resolveDark(isSystemInDarkTheme())
    val amoledDarkMode by rememberAmoledDarkMode()

    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // 调色板明暗必须由**本 app 的 ColorMode** 决定，不能交给 MochiTheme 的默认 controller：
    // 默认 controller 是 ColorSchemeMode.System，只看 isSystemInDarkTheme()，
    // 于是「设置里选浅色 + 系统是深色」会得到一套深色配色（而 LocalDarkMode/扩展色/系统栏图标
    // 都按浅色走），这就是浅/深色适配错位的根因。预设主题运行期不注入，只换明暗。
    val mochiController = remember(colorMode) {
        ThemeController(colorSchemeMode = colorMode.toMochiColorSchemeMode())
    }

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

    // FX1（主人拍板）：液态玻璃 / 光效 / 交互动效**默认开**。
    //  · 开：mochi-ios 的 iOS 26 玻璃配方（栏 / 菜单 / 面板 / 弹窗），并用 themeAccent = true
    //    让玻璃里选中的图标/文字用**主题强调色**（本仓调色板不是 iOS 的，不传会用 iOS 蓝）；
    //    scrollEdge 渐进模糊也一起来。
    //  · 关：给 MochiMaterials()（全 null）⇒ 上述位置全部退回纯色，glow/模糊一起消失。
    //  玻璃只作用在 bars / menus / sheets / alerts（MochiMaterials 的语义，§7-4）；
    //  正文按钮是否玻璃由按钮样式决定，这里不碰，也不在页面里写外观分支。
    val effectsEnabled = settings.displaySetting.effectsEnabled
    val mochiMaterials = remember(effectsEnabled, darkTheme) {
        if (effectsEnabled) {
            iosMaterials(isDark = darkTheme, themeAccent = true)
        } else {
            MochiMaterials()
        }
    }

    // 2026-10-30 口径：全面切 Mochi，不搬旧色板/字阶 ⇒ 只用 Mochi 默认
    // colorScheme / textStyles / shapes，由 MochiTheme 独家管理系统栏图标对比度。
    MochiTheme(
        controller = mochiController,
        systemBarAppearance = !darkTheme,
        materials = mochiMaterials,
    ) {
        CompositionLocalProvider(
            LocalDarkMode provides darkTheme,
            // FX1 总开关：Kk 层按压形变 / 触觉 / 高光都读它（见 uikit/mochi/LocalKkEffects.kt）
            LocalKkEffectsEnabled provides effectsEnabled,
            LocalExtendColors provides extendColors,
            // U8：Miuix 已删，回弹一律走 Mochi 的工厂（根 ProvideMochiOverscroll 提供），不再有置空分支。
            LocalOverscrollFactory provides overscrollFactory,
            LocalListCardStyle provides listCardStyle,
            LocalOptionSwitchScale provides 1f,
            LocalOptionSwitchExpressiveEnabled provides false,
        ) {
            ProvideMaterial3Theme(amoled = amoledDarkMode) {
                // U8：把 MD3 的默认水波换成 Mochi 的浅高光（onSurface 10%），
                // 配合 core/ui/modifier/Clickable.kt 的 kkPressState 就是「高光 + 轻形变」一层效果，
                // 不是水波叠缩放。MaterialTheme 在里层 provide LocalIndication，所以这里必须在它内侧覆盖。
                CompositionLocalProvider(
                    LocalIndication provides ripple(color = MochiTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
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
}
