package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold

/**
 * 页面骨架的统一入口（U1b 起单树：MD3 骨架，旧双风格 分支已删除）。
 *
 * 页面只提供 [title] 与 [content]；内容布局完全交给页面自己（不要把 content 塞进外层
 * LazyColumn 的 item，否则页面再套 LazyColumn 会拿到 maxHeight = Infinity）。
 *
 * @param largeTitle 用大标题栏（默认）还是小标题栏（如 WebView 这类工具页）。
 * @param scrollBehavior MD3 折叠滚动行为；非空时由本层挂 nestedScroll 连接。
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
    largeTitle: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val nested = if (scrollBehavior != null) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }
    MochiPageScaffold(
        modifier = modifier.then(nested),
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
