package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.adaptive.KedgePageScaffold

/**
 * 「顶栏 + Tab 条 + Pager」三段式页面的统一骨架。
 *
 * 两种风格都走 [KedgePageScaffold]：它在 Miuix 下已经是 Miuix `Scaffold` + 页面级
 * backdrop（顶栏毛玻璃）+ 唯一一份折叠滚动行为（大标题随滚动收起），MD3 下则是原生
 * `Scaffold`。Tab 条由 [KedgeTabRow] 内部按风格分流，因此 pager 内容两边共用。
 *
 * 之前这里为 Miuix 单独手写了一份 `MiuixScaffold`，代价是既没建立 backdrop
 * （顶栏没有毛玻璃）也没下发滚动行为（大标题永远不收缩），所以改成统一入口。
 *
 * @param titles Tab 标题。
 * @param scrollableTab Tab 数量多时用可滚动形态（Miuix 侧会自动走 WithContour）。
 */
@Composable
fun KedgeTabPageScaffold(
    title: String,
    titles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = { BackButton() },
    scrollableTab: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    pageContent: @Composable (Modifier) -> Unit,
) {
    KedgePageScaffold(
        modifier = modifier,
        topBar = {
            heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar(
                title = title,
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        md3ScrollBehavior = scrollBehavior,
        containerColor = CustomColors.pageContainerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            KedgeTabRow(
                titles = titles,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                scrollable = scrollableTab,
                containerColor = CustomColors.pageContainerColor,
            )
            pageContent(Modifier.weight(1f))
        }
    }
}
