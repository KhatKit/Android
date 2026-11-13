package heizige.kk.khatkit.uikit.mochi

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 底部导航栏（MochiTabBar）的标签样式，来自设置「外观 → 导航栏 → 导航栏样式」。
 *
 * 对应 Mochi 的 `MochiTabBarBehavior`：
 * - [LABELS]：图标 + 文字（默认，64dp 高、每项 92dp）
 * - [SELECTED]：只有图标，选中项的标签横排在图标右边（52dp 高、每项 60dp，Mochi demo 首页同款）
 * - [ICONS]：只有图标（52dp 高、每项 60dp）
 *
 * 放在 uikit 而不是 app：带 UI 的卡片（khatkit-ui 的 KhatKitPage / MochiNavigationBar
 * 渲染）和 app 的底部导航栏要读同一套设置。
 */
@Serializable
enum class NavBarLabelStyle {
    @SerialName("labels")
    LABELS,

    @SerialName("selected")
    SELECTED,

    @SerialName("icons")
    ICONS,
}

/**
 * 导航栏外观设置（由 app 的 `KhatKitTheme` 按 Settings provide）。
 *
 * 页面 / 外壳 / 卡片 UI 只读 [LocalKkNavBarStyle]，不要自己再读 Settings：
 * - [labelStyle] 底部导航栏标签样式
 * - [collapseOnScroll] 内容往下滚时底部导航栏是否收起成只剩当前图标的小胶囊
 *   （Mochi 的 iOS 26 行为；只在浮在正文上的底部导轨里生效）
 */
@Immutable
data class KkNavBarStyle(
    val labelStyle: NavBarLabelStyle = NavBarLabelStyle.LABELS,
    val collapseOnScroll: Boolean = true,
)

/** 当前导航栏外观；默认值 = 老行为（图标 + 文字、滚动收起）。 */
val LocalKkNavBarStyle = staticCompositionLocalOf { KkNavBarStyle() }
