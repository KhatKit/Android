package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import heizige.kk.kedge.adaptive.KedgeBlurSurface
import heizige.kk.kedge.adaptive.KedgeBlurredBar
import heizige.kk.kedge.adaptive.rememberKedgeBlurBackdrop
import heizige.kk.kedge.adaptive.LocalKedgeEnableBlur
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KSU 首页同款大标题栏。
 *
 * 目前全仓无调用方（设置页用的是 `MiuixSettingsPage` 内联的 Scaffold+TopAppBar）。
 * 保留它是因为它是「KSU 大标题栏 + 毛玻璃」的标准样板：后续若要做首页或把
 * `MiuixSettingsPage` 的顶栏抽出来，直接用它即可，别再手写一份。
 *
 * 照搬 KernelSU `ui/screen/home/HomeMiuix.kt` 的 `TopBar`：直接用 Miuix 原版
 * `TopAppBar`，**不传 `largeTitle`** —— 默认 `largeTitle = title`，因此得到
 * 真正的大标题栏（`title1` 字号），滚动时大标题折叠为小标题。
 *
 * 结构与 KSU 一致：backdrop 只录内容区，顶栏作为兄弟节点采样
 * （KSU：`Box(Modifier.layerBackdrop(backdrop)) { LazyColumn }` + `BlurredBar`）。
 *
 * @param title 折叠后的小标题，同时也是大标题内容。
 * @param subtitle 大标题下方的副标题（Miuix 用 `body2` 样式）。
 * @param backdrop 由页面（MiuixSettingsPage / 页面 Scaffold）建立并下发，
 *   这里只消费；为 null 时退化为不透明底色。
 */
@Composable
fun KedgeMiuixHomeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    // 顶栏底色：模糊生效时必须透明，否则模糊无从透出（KSU 同逻辑）
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    KedgeBlurredBar(backdrop = backdrop) {
        TopAppBar(
            color = barColor,
            title = title,
            subtitle = subtitle.orEmpty(),
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            bottomContent = bottomContent,
        )
    }
}

/**
 * 供页面使用的 backdrop 版本：Miuix 下按开关建立 backdrop，
 * 页面把内容区用 [KedgeBlurSurface] 包住即可获得 KSU 式毛玻璃。
 */
@Composable
fun rememberMiuixHomeBackdrop(): top.yukonga.miuix.kmp.blur.LayerBackdrop? =
    rememberKedgeBlurBackdrop(LocalKedgeEnableBlur.current)

/** 与 KSU 首页一致的横向 insets（状态栏/刘海由 topBar 自己消费）。 */
internal val MiuixHomeContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars
        .add(WindowInsets.displayCutout)
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal)
