package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 标签页切换条（U1b 起单树：MD3 的 SecondaryTabRow / SecondaryScrollableTabRow）。
 *
 * 调用方只提供标题列表与选中项；滚动形态由 [scrollable]（或标题数 > 3）决定。
 */
@Composable
fun KedgeTabRow(
    titles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    containerColor: Color = Color.Transparent,
    edgePadding: Dp = 0.dp,
) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(title) },
            )
        }
    }
    if (scrollable || titles.size > 3) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = containerColor,
            edgePadding = edgePadding,
            modifier = modifier,
        ) { tabs() }
    } else {
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = containerColor,
            modifier = modifier,
        ) { tabs() }
    }
}
