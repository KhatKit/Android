package heizige.kk.khatkit.app.feature.ui.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import ui.feice.mochi.basic.MochiTabBarScrollBehavior
import ui.feice.mochi.basic.rememberMochiTabBarScrollBehavior
import ui.feice.mochi.navigation.LocalMochiBottomBarInset
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults
import ui.feice.mochi.navigation.mochiScrollEdgeFade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import heizige.kk.khatkit.app.core.ui.components.ui.KkScrollEdgeExtent
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
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.navigation.MochiCompactTitleScaffold
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.effect.glass.MochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.effect.glass.rememberMochiBarBackdrop
import ui.feice.mochi.theme.MochiDefaults
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import ui.feice.mochi.navigation.MochiTitleBar

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

/**
 * 底部导航栏：Mochi 的 `MochiTabBar`（胶囊 / 毛玻璃条），样式全部来自 MochiTheme。
 */
@Composable
fun MochiNavBottomBar(
    items: List<MochiNavTab>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val windowWidth = currentWindowDpSize().width
    if (windowWidth >= 600.dp && windowWidth < 840.dp) {
        // C1：600-840dp 用左侧导航轨代替底部 TabBar（只换形状，选中/点击语义不变）
        Column(
            modifier = modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, tab ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tab.onClick() }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        return
    }
    val scrollBehavior = LocalKkNavBarScrollBehavior.current
    MochiTabBar(
        selectedIndex = selected,
        onSelect = { index ->
            scrollBehavior?.expand()
            items.getOrNull(index)?.onClick?.invoke()
        },
        scrollBehavior = scrollBehavior,
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
 * 槽位形状与旧 MochiPageScaffold 对齐：内容拿到的是 [PaddingValues.Zero]，因为顶/底栏
 * 现在参与流式布局（不再叠加在内容上），页面里的 `padding(innerPadding)` 自然变成 0 成本。
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
            MochiTitleBar(
                title = title,
                navigationIcon = { KkBarIconScope { navigationIcon() } },
                actions = { KkBarIconScope { actions() } },
                background = MochiTheme.colorScheme.background,
                backdrop = LocalKkBarBackdrop.current,
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

/** 正文录制一层玻璃取样源；[backdrop] 为 null（材质没开玻璃）时是空操作。 */
@Composable
private fun BackdropContent(backdrop: MochiBackdrop?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().mochiBackdropSource(backdrop)) { content() }
}

/** 量一下栏的高度（空槽是 0），按原样摆放。 */
@Composable
private fun MeasuredBar(onHeight: (Int) -> Unit, bar: @Composable () -> Unit) {
    Column(modifier = Modifier.onSizeChanged { onHeight(it.height) }) { bar() }
}

/** 正文：有栏的那一边贴着栏渐隐 + 渐进模糊（见 kkScrollEdgeFade），[enabled] 为 false 时原样。 */
@Composable
private fun EdgeFadedContent(
    enabled: Boolean,
    top: Boolean,
    bottom: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!enabled || (!top && !bottom)) {
        content(PaddingValues.Zero)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .kkScrollEdgeFade(
                top = if (top) KkScrollEdgeExtent else 0.dp,
                bottom = if (bottom) KkScrollEdgeExtent else 0.dp,
            ),
    ) { content(PaddingValues.Zero) }
}

@Composable
fun MochiPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    /**
     * C1：导航条槽。宽度 600-840dp 时放到左侧当导航轨，其它宽度仍放底部（页面不用关心方向）。
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
    /** 非空时走 Mochi 真页面骨架（大标题/紧凑标题），content 仍拿 PaddingValues.Zero。 */
    title: String? = null,
    subtitle: String? = null,
    /** true = 大标题页（可折叠）；false = 紧凑标题页（正文自带分组小标题时用）。 */
    largeTitle: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /**
     * 正文贴着顶栏 / 底栏的那一段做 Mochi 滚动边缘过渡（渐隐 + FX 开时渐进模糊，见 kkScrollEdgeFade）。
     * 正文顶部或底部有固定不滚的东西（搜索框、Tab 条、底部按钮），或正文里画了背景图时传 false，
     * 由页面把 kkScrollEdgeFade 加在真正滚动的那个容器上。
     */
    edgeFade: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (title != null) {
        val body: @Composable ColumnScope.() -> Unit = { content(PaddingValues.Zero) }
        if (largeTitle) {
            MochiLargeTitleScaffold(
                title = title,
                modifier = modifier,
                subtitle = subtitle,
                navigationIcon = navigationIcon?.let { icon -> { KkBarIconScope { icon() } } },
                actions = actions?.let { acts -> { KkBarIconScope { acts() } } },
                background = MochiDefaults.groupedBackground(),
                content = body,
            )
        } else {
            MochiCompactTitleScaffold(
                title = title,
                modifier = modifier,
                subtitle = subtitle,
                navigationIcon = navigationIcon?.let { icon -> { KkBarIconScope { icon() } } },
                actions = actions?.let { acts -> { KkBarIconScope { acts() } } },
                background = MochiDefaults.groupedBackground(),
                content = body,
            )
        }
        return
    }
    // 栏玻璃的取样源（材质开玻璃才非空）：正文录一层，顶栏从 CompositionLocal 里读。
    // 所有走本骨架的页面（设置、列表、工具页……）因此共用同一套栏材质，不再有的页有模糊、有的没有。
    val barBackdrop = rememberMochiBarBackdrop(background = barBackdropColor)
    // 栏量出来高度为 0 = 这一页没有那条栏，那一边就没有交界、不遮
    var topBarHeight by remember { mutableIntStateOf(0) }
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalKkBarBackdrop provides barBackdrop) {
        Box(modifier = modifier.fillMaxSize().background(containerColor)) {
            val windowWidth = currentWindowDpSize().width
            val railOnSide = rail != null && windowWidth >= 600.dp && windowWidth < 840.dp
            if (railOnSide) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(96.dp).fillMaxHeight()) { rail?.invoke() }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MeasuredBar(onHeight = { topBarHeight = it }, bar = topBar)
                        Box(modifier = Modifier.weight(1f)) {
                            BackdropContent(barBackdrop) { EdgeFadedContent(edgeFade, topBarHeight > 0, false, content) }
                        }
                    }
                }
            } else if (rail != null) {
                FloatingRailLayout(
                    barBackdrop = barBackdrop,
                    background = containerColor,
                    topBar = topBar,
                    onTopBarHeight = { topBarHeight = it },
                    edgeFade = edgeFade && topBarHeight > 0,
                    rail = rail,
                    content = content,
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    MeasuredBar(onHeight = { topBarHeight = it }, bar = topBar)
                    Box(modifier = Modifier.weight(1f)) {
                        BackdropContent(barBackdrop) {
                            EdgeFadedContent(edgeFade, topBarHeight > 0, bottomBarHeight > 0, content)
                        }
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
 * - 正文铺到屏幕底，栏下面透出的就是正文（玻璃栏取样它；纯色栏后面垫一层朝上渐隐的底色）
 * - 栏连同留白的整块高度经 [LocalMochiBottomBarInset] 下发，content 拿到的 PaddingValues.bottom 也是它：
 *   页面把它当列表的 contentPadding（或用 [MochiTabScaffoldDefaults.contentBottomPadding]），
 *   最后一项停在栏上面，滚动时内容从栏底下穿过
 * - 正文往下滚栏收成小圆、往上滚展开
 * 顶边交界照旧按 [edgeFade] 渐隐；底边不遮（栏是浮着的，内容本来就要从它底下透出来）。
 */
@Composable
private fun FloatingRailLayout(
    barBackdrop: MochiBackdrop?,
    background: Color,
    topBar: @Composable () -> Unit,
    onTopBarHeight: (Int) -> Unit,
    edgeFade: Boolean,
    rail: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val scrollBehavior = rememberMochiTabBarScrollBehavior()
    var railPx by remember { mutableIntStateOf(0) }
    val railInset = with(density) { railPx.toDp() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MeasuredBar(onHeight = onTopBarHeight, bar = topBar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                CompositionLocalProvider(LocalMochiBottomBarInset provides railInset) {
                    BackdropContent(barBackdrop) {
                        EdgeFadedContent(edgeFade, top = true, bottom = false) {
                            content(PaddingValues(bottom = railInset))
                        }
                    }
                }
            }
        }
        if (MochiTheme.materials.bar == null) {
            Spacer(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 36.dp)
                    .mochiScrollEdgeFade(background, bottom = true, extent = 56.dp),
            )
        }
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
