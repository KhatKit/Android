package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.ColumnScope
import heizige.kk.kedge.components.KedgeCard
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.kedge.adaptive.KedgeBlurSurface
import heizige.kk.kedge.adaptive.KedgeBlurredBar
import heizige.kk.kedge.adaptive.LocalKedgeEnableBlur
import heizige.kk.kedge.adaptive.rememberKedgeBlurBackdrop

/**
 * Miuix 风格的设置页骨架，照搬 KernelSU `SettingsMiuix.kt` 的结构：
 * [MiuixScrollBehavior] 驱动的顶栏、可选毛玻璃、12dp 水平边距、12dp 分组间距、
 * 关闭 overscrollEffect（改用 Miuix 的 overScrollVertical）。
 *
 * @param bottomInnerPadding 列表底部额外留白（避开底部输入栏等）。
 * @param content 列表内容，按 KernelSU 的写法在 `item {}` 里放一整页。
 */
@Composable
fun MiuixSettingsPage(
    title: String,
    modifier: Modifier = Modifier,
    bottomInnerPadding: Dp = 0.dp,
    enableBlur: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    /** Scaffold 的底色。传 [Color.Transparent] 可让下层背景（如关于页的流光）透出来。 */
    containerColor: Color = MiuixTheme.colorScheme.surface,
    /** 标题透明度。对应 KernelSU `AboutMiuix.kt` 的 `titleColor` 随滚动淡入：0=标题隐藏。 */
    titleAlpha: Float = 1f,
    /**
     * 用小标题栏（无折叠大标题）。对应 KernelSU `AboutMiuix.kt` 的 `SmallTopAppBar`：
     * Miuix 的 [TopAppBar] 默认 `largeTitle = title`，同一行标题会被画两次，
     * 关于页需要只保留一个位置，故走 SmallTopAppBar。
     */
    smallTitleBar: Boolean = false,
    /**
     * 顶栏底色透明度。对应 lyricon `AboutScreen.kt:118` 的
     * `color = colorScheme.surface.copy(alpha = scrollProgress)`：起始顶栏完全透明
     * （只见返回按钮），随滚动渐显。不为 null 时会覆盖 [enableBlur] 的底色决策。
     */
    barColorAlpha: Float? = null,
    /** 外部持有的列表状态：需要驱动视差/折叠动画的页面传入（如关于页的大 logo 头图）。 */
    lazyListState: LazyListState? = null,
    content: LazyListScope.() -> Unit,
) {
    // 0.9.3 里 MiuixScrollBehavior 是 @Composable 工厂函数（默认参数全走 remember），不是类。
    val scrollBehavior = MiuixScrollBehavior()
    // backdrop 每屏一个，只录制下面的列表内容；顶栏是它的兄弟节点（KSU 原版结构）。
    // 绝不能把顶栏也包进 KedgeBlurSurface，那样顶栏会采样到含自己的图层而崩。
    val backdrop = rememberKedgeBlurBackdrop(enableBlur && LocalKedgeEnableBlur.current)

    Scaffold(
        topBar = {
            // barColorAlpha != null 时（关于页）不套毛玻璃：页面底层本身就是流光，
            // 再叠一层 surface 0.87 的模糊会把底色压成近似纯色。
            // 做法对齐 lyricon `AboutScreen.kt`：顶栏直接用带 alpha 的纯色。
            val bar: @Composable () -> Unit = {
                val barColor = if (barColorAlpha != null) {
                    MiuixTheme.colorScheme.surface.copy(alpha = barColorAlpha.coerceIn(0f, 1f))
                } else if (backdrop != null) {
                    Color.Transparent
                } else {
                    MiuixTheme.colorScheme.surface
                }
                val titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha)
                if (smallTitleBar) {
                    SmallTopAppBar(
                        title = title,
                        titleColor = titleColor,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon ?: {},
                        actions = actions,
                    )
                } else TopAppBar(
                    color = barColor,
                    title = title,
                    // 标题随滚动淡入：起始完全透明，只留返回按钮（KSU 行为）
                    titleColor = titleColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon ?: {},
                    actions = actions,
                )
            }
            if (barColorAlpha != null) bar() else KedgeBlurredBar(backdrop = backdrop) { bar() }
        },
        bottomBar = { bottomBar?.invoke() },
        containerColor = containerColor,
        // 必须保留 Miuix 默认的 MiuixPopupHost()：OverlayDropdownPreference /
        // Spinner 等弹层通过 LocalPopupStates 注册，再由这个 host 实际渲染。
        // 传空 lambda 会让下拉、选择器全部点不动（KernelSU 能传空是因为它的
        // Scaffold 版本自带 host，Miuix 0.9.3 这里必须显式保留默认值）。
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        // Box 必须 fillMaxSize：否则 Miuix Scaffold 给的是松散约束，
        // 里面的 LazyColumn 拿不到确定高度，滚不动。
        KedgeBlurSurface(backdrop = backdrop, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState ?: rememberLazyListState(),
                modifier = Modifier
                    // fillMaxSize 而不是 fillMaxHeight：Miuix Scaffold 的 content 槽
                    // 给的是松散高度约束，只 fillMaxHeight 时 LazyColumn 高度为 0，
                    // 列表无法滚动。必须同时 fillMaxWidth + 撑满可用高度。
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = MiuixPageMetrics.HorizontalPadding),
                contentPadding = innerPadding.plus(
                    PaddingValues(bottom = bottomInnerPadding)
                ),
                overscrollEffect = null,
                content = content,
            )
        }
    }
}

/**
 * Miuix 分组卡片 + 12dp 上间距，写法与 KernelSU 的 `SettingsMiuix.kt` 一致。
 *
 * 每个分组是列表里的一个 item，卡片内部直接放 miuix-preference 的各项
 * （`SwitchPreference` / `ArrowPreference` / `OverlayDropdownPreference` …）。
 */
fun LazyListScope.miuixGroup(
    modifier: Modifier = Modifier,
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        KedgeCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = MiuixPageMetrics.GroupSpacing),
            // 分组卡片本身**不再加内边距**：里面的 ArrowPreference / SwitchPreference
            // 等组件自己就带 BasicComponentDefaults.InsideMargin(16dp)。这里再给
            // KedgeCard 的默认 16dp 的话，单项内边距会叠成 32dp，看着比 KernelSU 松。
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}

/** Miuix 设置页的间距刻度，与 KernelSU 保持一致。 */
object MiuixPageMetrics {
    /** 列表水平边距。 */
    val HorizontalPadding = 12.dp

    /** 分组卡片之间的上间距。 */
    val GroupSpacing = 12.dp
}

/**
 * 给「裸」列表项（自己画卡片、没有 [miuixGroup] 包裹的那种）补上 Miuix 的组间距。
 *
 * [miuixGroup] 自带 12dp 上间距，但 MCP / 文件 / 搜索这几页的卡片是 item 自己画的
 * （`McpServerItem` / `FileItem` / `SearchProviderCard` 内部直接 `KedgeCard`），
 * 不走分组包裹，于是列表里项与项之间**完全贴在一起** —— Miuix 的观感要求每项都是
 * 独立圆角卡片，之间留 12dp。
 *
 * 用法：`Modifier.miuixItemSpacing()`，给每个 item 的 modifier 加上即可。
 */
fun Modifier.miuixItemSpacing(): Modifier =
    this.padding(top = MiuixPageMetrics.GroupSpacing)
