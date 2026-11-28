package heizige.kk.khatkit.app.feature.ui.shell

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import ui.feice.mochi.basic.MochiTabBarScrollBehavior
import ui.feice.mochi.basic.rememberMochiTabBarScrollBehavior
import ui.feice.mochi.navigation.LocalMochiBottomBarInset
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults
import heizige.kk.khatkit.uikit.mochi.NavigationBarScrim
import heizige.kk.khatkit.uikit.mochi.rememberNavigationBarMaterial
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.SubcomposeLayout
import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.persistentListOf
import heizige.kk.khatkit.uikit.mochi.LocalKkNavBarStyle
import heizige.kk.khatkit.uikit.mochi.NavBarLabelStyle
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabBarDefaults
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.effect.glass.MochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.effect.glass.rememberMochiBarBackdrop
import ui.feice.mochi.effect.glass.rememberMochiBackdrop
import ui.feice.mochi.effect.mask.mochiScrollEdgeBlur
import ui.feice.mochi.theme.MochiDefaults
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.feice.mochi.navigation.MochiSidebarItem
import androidx.compose.material3.TopAppBarScrollBehavior
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.uikit.mochi.LocalMochiTitleScroll
import heizige.kk.khatkit.uikit.mochi.MochiPageTitleBar
import heizige.kk.khatkit.uikit.mochi.mochiTitleScroll
import heizige.kk.khatkit.uikit.mochi.rememberMochiTitleScroll

/**
 * P3a 外壳层：把原来走 Kedge 的「页面骨架 + 底部导航栏」换成 Mochi 实现。
 *
 * 这里刻意保留原来 Kedge 的**槽位形状**（topBar / bottomBar / content(PaddingValues)），
 * 这样 10 个页面只改调用名、不动页面结构，P4 再把各页的 Kedge 顶栏换成
 * `MochiLargeTitleScaffold` 自己的标题管线。真正的 Mochi 骨架
 * （`MochiTabScaffold` / `MochiTabBar`）在 [MochiNavBottomBar] 与 SettingModelPage 里。
 */

/** 底部导航项（对应原 MochiNavTab 的字段）。 */
class MochiNavTab(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

internal enum class TabPlacement(val sideWidth: Dp) { Bottom(0.dp), Rail(96.dp), Sidebar(216.dp) }

internal fun tabPlacement(available: Dp) = when {
    available >= 840.dp -> TabPlacement.Sidebar
    available >= 600.dp -> TabPlacement.Rail
    else -> TabPlacement.Bottom
}

private val LocalTabPlacement = staticCompositionLocalOf { TabPlacement.Bottom }

/**
 * 底部导航栏：Mochi 的 `MochiTabBar`（胶囊 / 毛玻璃条），样式全部来自 MochiTheme。
 *
 * 标签样式与滚动收起读设置（外观 → 导航栏）：[LocalKkNavBarStyle]，默认「图标 + 文字、滚动收起」
 * （老行为）。样式只影响布局/行为，玻璃仍由主题材质决定，和「液态玻璃」总开关联动。
 */
@Composable
fun MochiNavBottomBar(
    items: List<MochiNavTab>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val navStyle = LocalKkNavBarStyle.current
    val behavior = when (navStyle.labelStyle) {
        NavBarLabelStyle.LABELS -> MochiTabBarDefaults.behavior()
        NavBarLabelStyle.SELECTED -> MochiTabBarDefaults.iconOnlyBehavior(selectedLabelOnly = true)
        NavBarLabelStyle.ICONS -> MochiTabBarDefaults.iconOnlyBehavior()
    }
    val placement = LocalTabPlacement.current
    if (placement != TabPlacement.Bottom) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, tab ->
                val active = index == selected
                val showLabel = navStyle.labelStyle == NavBarLabelStyle.LABELS ||
                    (navStyle.labelStyle == NavBarLabelStyle.SELECTED && active)
                if (placement == TabPlacement.Sidebar && showLabel) {
                    MochiSidebarItem(
                        title = tab.label,
                        selected = active,
                        onClick = tab.onClick,
                        leading = { Icon(tab.icon, null, Modifier.size(24.dp)) },
                    )
                    return@forEachIndexed
                }
                val tint = if (active) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = active, role = Role.Tab, onClick = tab.onClick)
                        .semantics { if (!showLabel) contentDescription = tab.label }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                    )
                    // 和底部标签栏同一套标签样式：仅图标 / 仅选中项显示文字 / 都显示
                    if (showLabel) {
                        Text(
                            text = tab.label,
                            style = MochiTheme.textStyles.labelSmall,
                            color = tint,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        return
    }
    // 设置里关掉「滚动收起」就不接滚动行为：底栏始终展开
    val scrollBehavior = if (navStyle.collapseOnScroll) LocalKkNavBarScrollBehavior.current else null
    MochiTabBar(
        selectedIndex = selected,
        onSelect = { index ->
            scrollBehavior?.expand()
            items.getOrNull(index)?.onClick?.invoke()
        },
        scrollBehavior = scrollBehavior,
        colors = MochiTabBarDefaults.colors(shadow = Color.Transparent),
        material = rememberNavigationBarMaterial(),
        // 标签样式（图标+文字 / 仅选中项文字 / 仅图标）走 Mochi 的 behavior
        behavior = behavior,
        // 玻璃标签栏取样的是骨架录下的正文（栏浮在正文上，透出的就是它）
        backdrop = LocalKkBarBackdrop.current,
        items = persistentListOf(
            *items.map { tab ->
                MochiTabItem(
                    label = tab.label,
                    icon = { tint: Color ->
                        Icon(imageVector = tab.icon, contentDescription = tab.label, tint = tint)
                    },
                )
            }.toTypedArray(),
        ),
        modifier = modifier,
    )
}

/**
 * 页面骨架（Mochi 底色 + 顶栏/底栏槽位）。
 *
 * 默认顶栏参与流式布局。选择 scrollUnderTopBar 的页面使用实际栏高作为列表的顶部 contentPadding。
 *
 * @param md3ScrollBehavior 仅为兼容旧调用点保留，Mochi 侧不使用（P4 删）。
 *
 * TOP1：新增 title 模式 —— 传了 [title] 就交给 Mochi 真页面骨架
 * （[MochiLargeTitleScaffold] / [MochiCompactTitleScaffold]：大标题折叠、滚动边缘渐隐、下拉刷新、
 * 安全区都由库内处理），topBar 槽不再需要；不传 title 时保持旧槽位骨架，11 处存量调用点零改动。
 */
/**
 * 当前页面骨架给顶栏的玻璃取样源（材质开了玻璃才非空）。
 *
 * 栏里的返回键、动作按钮都是玻璃：只有拿到取样源时才会真正模糊背后的内容，
 * 否则按材质 fallback 画纯色。页面骨架统一在这里录一份正文、下发本地的来源，
 * 页面的顶栏（Mochi 的 [MochiTitleBar] / Kedge 的顶栏）读它即可 ——
 * 不要再让每个页面各自 remember 一份。
 */
internal val LocalKkBarBackdrop = staticCompositionLocalOf<MochiBackdrop?> { null }

/**
 * Compact Mochi settings page shell used by extension/workspace pages.
 *
 * This lives next to the actual Mochi shell so feature pages do not depend on the legacy
 * Kedge/Miuix compatibility package.
 */
@Composable
fun MochiSettingsPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = { BackButton() },
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    rail: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: Any? = null,
    @Suppress("UNUSED_PARAMETER") largeTitle: Boolean = true,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior? = null,
    edgeFade: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    MochiPageScaffold(
        modifier = modifier,
        topBar = {
            MochiPageTitleBar(
                title = title,
                navigationIcon = { KkBarIconScope { navigationIcon() } },
                actions = { KkBarIconScope { actions() } },
                background = MochiTheme.colorScheme.background,
                backdrop = LocalKkBarBackdrop.current,
                collapsible = largeTitle,
            )
        },
        bottomBar = bottomBar,
        rail = rail,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = MochiDefaults.groupedBackground(),
        edgeFade = edgeFade,
    ) { innerPadding -> content(innerPadding) }
}

/** 浮动底栏的滚动收起（往下滚收成小圆、往上滚展开）；只在 [MochiPageScaffold] 的底部 rail 里有。 */
private val LocalKkNavBarScrollBehavior = staticCompositionLocalOf<MochiTabBarScrollBehavior?> { null }

/** 量一下栏的高度（空槽是 0），按原样摆放。 */
@Composable
private fun MeasuredBar(onHeight: (Int) -> Unit, bar: @Composable () -> Unit) {
    Column(modifier = Modifier.onSizeChanged { onHeight(it.height) }) { bar() }
}

/** Fixed-header pages keep their layout; scroll-under pages measure the bar before laying out the list. */
@Composable
private fun PageTopBarLayout(
    topBar: @Composable () -> Unit,
    backdrop: MochiBackdrop?,
    scrollUnderTopBar: Boolean,
    scrollEdgeProgress: () -> Float,
    edgeFade: Boolean,
    bottomEdge: Boolean,
    bottomInset: Dp = 0.dp,
    bodyModifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!scrollUnderTopBar) {
        Column(Modifier.fillMaxSize()) {
            topBar()
            val fade = if (edgeFade && bottomEdge) Modifier.kkScrollEdgeFade(top = 0.dp) else Modifier
            Box(Modifier.fillMaxSize().then(bodyModifier).mochiBackdropSource(backdrop).then(fade)) {
                content(PaddingValues(bottom = bottomInset))
            }
        }
        return
    }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bar = subcompose("bar") { Column { topBar() } }.single().measure(loose)
        val topInset = bar.height.toDp()
        val body = subcompose("body") {
            // 栏下渐进模糊（顶栏样式的模糊参数生效的前提；栏本身不铺不透明底色）
            val blur = Modifier.mochiScrollEdgeBlur(topInset, scrollEdgeProgress)
            val fade = if (edgeFade && bottomEdge) Modifier.kkScrollEdgeFade(top = 0.dp) else Modifier
            Box(Modifier.fillMaxSize().then(bodyModifier).mochiBackdropSource(backdrop).then(blur).then(fade)) {
                content(PaddingValues(top = topInset, bottom = bottomInset))
            }
        }.single().measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.place(0, 0)
            bar.place(0, 0)
        }
    }
}

@Composable
fun MochiPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    /**
     * 自适应导航槽：窄布局用底栏，中宽用侧轨，展开时用带标签的 Mochi 侧栏。
     * 传了 rail 就别再传 bottomBar。
     */
    rail: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MochiTheme.colorScheme.background,
    /**
     * 栏玻璃取样源（bar backdrop）底下垫的底色，默认跟 [containerColor]。
     *
     * 页面自己的底色是透明（正文里铺助手背景图/渐层）时必须单独给一个**不透明**的页面底色：
     * 取样源录的是「底色 + 正文」，底色透明时录出来的是一张半透明的图层，玻璃在它上面取样算出来的面色
     * 发灰（浅色下栏按钮是一块 #A0A0A0 的圆盘，而不是白胶囊）。给成页面实际底色就正常了。
     */
    barBackdropColor: Color = containerColor,
    floatingActionButtonPosition: Any? = null,
    md3ScrollBehavior: Any? = null,
    /** 非空时由骨架提供标题和适合普通 Column 内容的滚动容器。 */
    title: String? = null,
    subtitle: String? = null,
    /** true = 大标题页（可折叠）；false = 紧凑标题页（正文自带分组小标题时用）。 */
    largeTitle: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /**
     * 正文贴着底栏的一段是否渐隐。
     */
    edgeFade: Boolean = true,
    /** Opt in only if the scroll container consumes top padding as contentPadding, not outer padding. */
    scrollUnderTopBar: Boolean = false,
    /** 栏下渐进模糊的进度（0 = 不模糊，1 = 最强）；不传时跟随大标题收起进度。 */
    scrollEdgeProgress: (() -> Float)? = null,
    /** Other floating surfaces on this page can sample the body even with solid bar buttons. */
    captureBodyBackdrop: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (title != null) {
        MochiPageScaffold(
            modifier = modifier,
            containerColor = containerColor,
            topBar = {
                MochiPageTitleBar(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigationIcon?.let { icon -> { KkBarIconScope { icon() } } },
                    actions = actions?.let { acts -> { KkBarIconScope { acts() } } },
                    background = containerColor,
                    backdrop = LocalKkBarBackdrop.current,
                    collapsible = largeTitle,
                )
            },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = MochiTabScaffoldDefaults.contentBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(ui.feice.mochi.list.MochiListDefaults.SectionSpacing),
            ) { content(PaddingValues.Zero) }
        }
        return
    }
    // 栏玻璃的取样源（材质开玻璃才非空）：正文录一层，顶栏从 CompositionLocal 里读。
    // 所有走本骨架的页面（设置、列表、工具页……）因此共用同一套栏材质，不再有的页有模糊、有的没有。
    val barBackdrop = if (captureBodyBackdrop) {
        rememberMochiBackdrop(background = barBackdropColor)
    } else {
        rememberMochiBarBackdrop(background = barBackdropColor)
    }
    val titleScroll = rememberMochiTitleScroll()
    // 栏下渐进模糊的进度：页面没传就跟着大标题收起走
    val edgeProgress: () -> Float = scrollEdgeProgress ?: { titleScroll.progress }
    val pageTopBar: @Composable () -> Unit = {
        CompositionLocalProvider(LocalMochiTitleScroll provides titleScroll, content = topBar)
    }
    val titleGestures = Modifier.mochiTitleScroll(titleScroll)
    // 栏量出来高度为 0 = 这一页没有那条栏，那一边就没有交界、不遮
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalKkBarBackdrop provides barBackdrop) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(containerColor)) {
            val placement = tabPlacement(maxWidth)
            if (rail != null && placement != TabPlacement.Bottom) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                    val sideWidth = if (LocalKkNavBarStyle.current.labelStyle == NavBarLabelStyle.ICONS) TabPlacement.Rail.sideWidth else placement.sideWidth
                    Box(modifier = Modifier.width(sideWidth).fillMaxHeight()) {
                        CompositionLocalProvider(LocalTabPlacement provides placement, content = rail)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PageTopBarLayout(pageTopBar, barBackdrop, scrollUnderTopBar, edgeProgress, edgeFade, bottomEdge = false,
                            bodyModifier = titleGestures, content = content)
                    }
                }
            } else if (rail != null) {
                FloatingRailLayout(
                    barBackdrop = barBackdrop,
                    background = containerColor,
                    topBar = pageTopBar,
                    scrollUnderTopBar = scrollUnderTopBar,
                    scrollEdgeProgress = edgeProgress,
                    edgeFade = edgeFade,
                    bodyModifier = titleGestures,
                    rail = rail,
                    content = content,
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        PageTopBarLayout(pageTopBar, barBackdrop, scrollUnderTopBar, edgeProgress, edgeFade, bottomEdge = bottomBarHeight > 0,
                            bodyModifier = titleGestures, content = content)
                    }
                    MeasuredBar(onHeight = { bottomBarHeight = it }, bar = bottomBar)
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
            Box(modifier = Modifier.align(Alignment.BottomEnd)) { floatingActionButton() }
        }
    }
}

/**
 * 底部 rail（[MochiNavBottomBar]）浮在正文上，和 Mochi 的 [ui.feice.mochi.navigation.MochiTabScaffold] 同一套：
 * - 正文铺到屏幕底，玻璃栏取样正文，手势区用页面底色向上渐隐
 * - 栏连同留白的整块高度经 [LocalMochiBottomBarInset] 下发，content 拿到的 PaddingValues.bottom 也是它：
 *   页面把它当列表的 contentPadding（或用 [MochiTabScaffoldDefaults.contentBottomPadding]），
 *   最后一项停在栏上面，滚动时内容从栏底下穿过
 * - 正文往下滚栏收成小圆、往上滚展开
 * 内容从悬浮底栏下面透出来。
 */
@Composable
private fun FloatingRailLayout(
    barBackdrop: MochiBackdrop?,
    background: Color,
    topBar: @Composable () -> Unit,
    scrollUnderTopBar: Boolean,
    scrollEdgeProgress: () -> Float,
    edgeFade: Boolean,
    bodyModifier: Modifier,
    rail: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val scrollBehavior = rememberMochiTabBarScrollBehavior()
    var railPx by remember { mutableIntStateOf(0) }
    val railInset = with(density) { railPx.toDp() }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMochiBottomBarInset provides railInset) {
            PageTopBarLayout(topBar, barBackdrop, scrollUnderTopBar, scrollEdgeProgress, edgeFade, bottomEdge = false,
                bottomInset = railInset,
                bodyModifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).then(bodyModifier),
                content = content)
        }
        NavigationBarScrim(background, Modifier.align(Alignment.BottomCenter))
        CompositionLocalProvider(LocalKkNavBarScrollBehavior provides scrollBehavior) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 量的是含留白的整块高度；收起时的位移不影响它，正文底部不跟着跳
                    .onSizeChanged { railPx = it.height }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) { rail() }
        }
    }
}
