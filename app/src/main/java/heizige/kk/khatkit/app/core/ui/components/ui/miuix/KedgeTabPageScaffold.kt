package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold

/**
 * 「顶栏 + Tab 条 + Pager」三段式页面的统一骨架（U1b 起单树：MD3）。
 *
 * 顶栏与 Tab 条都走 MD3 形态；pager 内容由 [pageContent] 提供。
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
    rail: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    pageContent: @Composable (Modifier) -> Unit,
) {
    MochiPageScaffold(
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
        rail = rail,
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
