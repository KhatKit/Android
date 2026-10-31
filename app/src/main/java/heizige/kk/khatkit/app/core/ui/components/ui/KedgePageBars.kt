package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiButtonDefaults
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import ui.feice.mochi.effect.glass.MochiBackdrop
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.roundToInt

/**
 * 顶栏底色与滚动的联动：静止态 = 本页底色，折叠/滚动后才渐变到 topBarColors 的
 * scrolledContainerColor（surfaceContainer）。
 *
 * M3 自己就会按 `collapsedFraction` 在 `containerColor` 与 `scrolledContainerColor`
 * 之间插值（`AppBar.kt` 的 `TopAppBarColors.containerColor(fraction)`，缓动
 * `FastOutLinearInEasing`），但 [CustomColors.topBarColors] 此前把两色都设成同一支
 * surfaceContainer，插值等于空转 —— 表现就是「顶栏永远一个颜色，跟下面的滚动无关」。
 * 这里只把**静止态**换成页面底色（MD3 下即 surface，与 Scaffold 的 containerColor
 * 同色），滚动语义交给 M3，顶栏因此静止时与内容连成一片、内容被顶起才浮出色块。
 *
 * [scrollBehavior] 为 null 的页面（压根不折叠、`collapsedFraction` 恒为 0）原样返回：
 * 那里不存在「滚动后」这个状态，顶栏必须始终自带颜色，否则会与页面同色而「消失」。
 */
@Composable
internal fun scrollLinkedTopBarColors(
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?,
): TopAppBarColors =
    if (scrollBehavior != null) {
        colors.copy(containerColor = CustomColors.pageContainerColor)
    } else {
        colors
    }

/**
 * 页面大标题栏要挂的滚动连接：**只在 MD3Exp 下挂**，Miuix 下返回空 Modifier。
 *
 * MD3 的 Scaffold 自己不接滚动行为，页面历来手写
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`；但 Miuix 下
 * [heizige.kk.khatkit.uikit.mochi.MochiPageScaffold] 已经接上了 Miuix 的折叠行为，
 * 两个连接会同时挂在同一棵树上。
 *
 * 更糟的是 MD3 那个行为的 `heightOffsetLimit` 只由 MD3 顶栏写入，Miuix 下 MD3 顶栏
 * 根本没组合，limit 一直停在 `-Float.MAX_VALUE`，于是它把滚动增量**全部吃掉** ——
 * 表现是整页滑不动（大标题也不会折叠）。
 *
 * 所以凡是 Miuix 下可达、又自己手写了这个连接的页面（探索市场、助手列表）都必须
 * 走这里，不要直接写 `Modifier.nestedScroll(...)`。
 */
@Composable
fun activeNestedScroll(scrollBehavior: TopAppBarScrollBehavior): Modifier =
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)

/** 0..1 之间的平滑过渡（与 Mochi 大标题页同一条曲线）。 */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * 页面大标题栏，照 Mochi 大标题页（MochiLargeTitleScaffold）的样子：
 * 上面一条 [MochiTitleBar]（返回 / 操作 + 居中小标题），下面一块左对齐的大标题。
 *
 * Mochi 的大标题长在正文里跟着滚；这里的页面都是「Scaffold 顶栏 + MD3 折叠行为」结构，
 * 所以改成大标题区跟着 [scrollBehavior] 的 heightOffset 收起：往上滚时大标题区被
 * 裁掉、淡出，收到一半后栏里的小标题模糊淡入，收完栏底色才浮出来（与 Mochi 同一顺序）。
 * heightOffsetLimit 由这里按大标题区实测高度写入（原先是 M3 顶栏写的），否则
 * exitUntilCollapsed 会把滚动全吃掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageLargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    @Suppress("UNUSED_PARAMETER") colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    /** 标题整体透明度。0=标题隐藏（仅留返回按钮），随滚动淡入即 KSU 的 About 页行为。 */
    titleAlpha: Float = 1f,
    /** 栏里玻璃控件（返回键等）要取样的背景来源；null 时材质按 fallback 画纯色，没有模糊。 */
    backdrop: MochiBackdrop? = null,
) {
    val alpha = titleAlpha.coerceIn(0f, 1f)
    // 没显式传时用页面骨架下发的来源：这样所有走 MochiPageScaffold 的页面都拿到同一份栏材质
    val effectiveBackdrop = backdrop ?: LocalKkBarBackdrop.current
    val state = scrollBehavior?.state
    val background = CustomColors.pageContainerColor
    val collapsed = { state?.collapsedFraction ?: 0f }
    Column(modifier = modifier.fillMaxWidth().background(background)) {
        MochiTitleBar(
            title = title,
            navigationIcon = { KkBarIconScope { navigationIcon() } },
            actions = { KkBarIconScope { actions() } },
            background = background,
            titleProgress = { smoothstep(0.5f, 1f, collapsed()) * alpha },
            backgroundProgress = { smoothstep(0.9f, 1f, collapsed()) },
            backdrop = effectiveBackdrop,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    val full = placeable.height
                    if (state != null && state.heightOffsetLimit != -full.toFloat()) {
                        state.heightOffsetLimit = -full.toFloat()
                    }
                    val shown = (full + (state?.heightOffset ?: 0f)).roundToInt().coerceIn(0, full)
                    layout(placeable.width, shown) { placeable.place(0, shown - full) }
                }
                .graphicsLayer { this.alpha = (1f - smoothstep(0f, 0.7f, collapsed())) * alpha }
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            if (titleContent != null) {
                ProvideTextStyle(MochiTheme.textStyles.largeTitle) { titleContent() }
            } else {
                Text(
                    text = title,
                    style = MochiTheme.textStyles.largeTitle,
                    color = MochiTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MochiTheme.textStyles.subhead,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 页面中等标题栏：Mochi 只有大标题 / 紧凑两种，中等标题并到大标题。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageMediumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.mediumTopAppBarColors(),
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    backdrop: MochiBackdrop? = null,
) {
    KedgePageLargeTopBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
        titleContent = titleContent,
        subtitle = subtitle,
        backdrop = backdrop,
    )
}

/**
 * 页面小标题栏：Mochi 紧凑标题栏（[MochiTitleBar]，居中小标题），内容滚进栏下面后底色才浮出来。
 * 折叠行为的 heightOffsetLimit 写 0（这条栏不收起），否则 exitUntilCollapsed 会吃掉滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    @Suppress("UNUSED_PARAMETER") colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    backdrop: MochiBackdrop? = null,
) {
    val state = scrollBehavior?.state
    val effectiveBackdrop = backdrop ?: LocalKkBarBackdrop.current
    SideEffect {
        if (state != null && state.heightOffsetLimit != 0f) state.heightOffsetLimit = 0f
    }
    val background = CustomColors.pageContainerColor
    if (titleContent != null) {
        // 富标题（图标 + 文字等）：MochiTitleBar 只收字符串，这里沿用它的度量自己摆
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(background)
                .statusBarsPadding()
                .height(MochiTitleBarDefaults.Height)
                .padding(horizontal = MochiButtonDefaults.BarHorizontalPadding),
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) { navigationIcon() }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 64.dp)) {
                ProvideTextStyle(MochiTheme.textStyles.navTitle) { titleContent() }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    } else {
        MochiTitleBar(
            title = title,
            modifier = modifier.background(background),
            subtitle = subtitle,
            navigationIcon = { KkBarIconScope { navigationIcon() } },
            actions = { KkBarIconScope { actions() } },
            background = background,
            backgroundProgress = { state?.overlappedFraction ?: 0f },
            backdrop = effectiveBackdrop,
        )
    }
}
