package heizige.kk.khatkit.app.core.ui.components.ui.mochi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiSegmentedControl
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 标签页切换条：Mochi 分段控件或横向滚动芯片。
 *
 * 调用方只提供标题列表与选中项；滚动形态由 [scrollable]（或标题数 > 3）决定。
 */
@Composable
fun MochiTabRow(
    titles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    containerColor: Color = Color.Transparent,
    edgePadding: Dp = 0.dp,
) {
    if (titles.isEmpty()) return
    if (scrollable || titles.size > 3) {
        MochiChipGroup(
            modifier = modifier.background(containerColor),
            contentPadding = PaddingValues(horizontal = edgePadding),
        ) {
            titles.forEachIndexed { index, title ->
                MochiChip(title, index == selectedTabIndex, { onTabSelected(index) })
            }
        }
    } else {
        MochiSegmentedControl(
            selectedIndex = selectedTabIndex, onSelect = onTabSelected,
            items = titles.toImmutableList(), modifier = modifier.fillMaxWidth().background(containerColor),
        )
    }
}
