package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import androidx.compose.material3.TopAppBarScrollBehavior

/**
 * 页面骨架的统一入口：按当前风格在 MD3 / Miuix 之间分流。
 *
 * 绝大多数设置与助手子页的结构都是「顶栏 + 一块自己管滚动的内容」，原本每页都要
 * 写一遍 `if (Miuix) ... else KedgePageScaffold(...)`。这里收口成一个入口，
 * 页面只提供 [title] 与 [content]。
 *
 * Miuix 下仍走 [KedgePageScaffold]：它内部已经是 Miuix `Scaffold` + 页面级 backdrop
 * + Miuix 折叠行为，顶栏由 [content] 之外的 `topBar` 槽提供，控件形态与毛玻璃都
 * 是 Miuix 的，而**内容布局完全交给页面自己**。
 *
 * ## 为什么不能把 content 塞进 `MiuixSettingsPage { item { ... } }`
 *
 * 那样做等于把页面内容塞进外层 `LazyColumn` 的一个 item，于是：
 * - 页面自己再套一层 `LazyColumn`（本骨架的 19 个调用方里有 16 个都是）就变成
 *   「LazyColumn 套 LazyColumn」，内层拿到 maxHeight = Infinity，直接崩；
 * - 内层的滚动也拿不到 Scaffold 的 contentPadding。
 * MD3 分支一直是把 content 直接交出去的，这里保持一致。
 *
 * @param title 顶栏标题。
 * @param content 内容；两种风格下都是 [PaddingValues] -> Unit，由页面自己放滚动容器。
 * @param navigationIcon 导航槽，默认返回按钮（[BackButton] 自身已双风格）。
 * @param actions 顶栏右侧操作（Miuix 下渲染为 Miuix IconButton）。
 * @param bottomBar 底部栏（如工作区详情页的导航栏）。两种风格都透传给 Scaffold。
 * @param largeTitle Miuix 下用大标题栏（默认）还是小标题栏（如 WebView 这类工具页）。
 * @param scrollBehavior MD3 的折叠滚动行为。仅 MD3 分支使用：Miuix 下改用
 *   KedgePageScaffold 建立的 Miuix 行为，否则两套行为会互相抢滚动增量。
 */
@Composable
fun KedgeSettingsPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = { BackButton() },
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: androidx.compose.material3.FabPosition =
        androidx.compose.material3.FabPosition.End,
    /** Miuix 下用大标题栏（默认）还是小标题栏（如 WebView 这类工具页）。 */
    largeTitle: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // MD3 的 Scaffold 不会自己接滚动行为，页面原本都是手动写
    // `modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`。
    // 这里统一补上，但只在 MD3 下补：Miuix 下 KedgePageScaffold 已经接了 Miuix 行为，
    // 再挂一个 MD3 连接会抢走增量，导致大标题不折叠。
    val md3NestedScroll = if (scrollBehavior != null &&
        LocalKedgeStyle.current == KedgeStyle.MD3Exp
    ) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }

    KedgePageScaffold(
        modifier = modifier.then(md3NestedScroll),
        topBar = {
            if (largeTitle) {
                heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors,
                )
            } else {
                heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors,
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        md3ScrollBehavior = scrollBehavior,
        containerColor = CustomColors.pageContainerColor,
    ) { innerPadding ->
        content(innerPadding)
    }
}
