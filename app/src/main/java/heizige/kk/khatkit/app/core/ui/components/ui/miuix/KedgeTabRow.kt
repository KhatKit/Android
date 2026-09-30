package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.basic.TabRow as MiuixTabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour as MiuixTabRowWithContour

/**
 * 标签页切换条的统一入口，按当前风格渲染：
 *
 * - Miuix：原版 [MiuixTabRow]（固定 Tab，数量少时用）/ [MiuixTabRowWithContour]
 *   （带轮廓，选中项有描边，KernelSU 系观感）
 * - MD3Exp：`SecondaryTabRow` / `SecondaryScrollableTabRow`，行为与原来完全一致
 *
 * 业务侧只需要提供标题列表和选中项，切换逻辑与动画由调用方持有（通常配 PagerState）。
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
    when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> {
            // Miuix 的 TabRow 尺寸固定（TabRowMaxWidth），标题多时用 WithContour 形态，
            // 它内部自带 itemSpacing 与自适应宽度，更接近 MD3 的可滚动条。
            val row = @Composable {
                if (scrollable || titles.size > 3) {
                    MiuixTabRowWithContour(
                        tabs = titles,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = onTabSelected,
                        modifier = modifier,
                    )
                } else {
                    MiuixTabRow(
                        tabs = titles,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = onTabSelected,
                        modifier = modifier,
                    )
                }
            }
            row()
        }

        KedgeStyle.MD3Exp -> {
            @Composable
            fun Tabs() {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(title) },
                    )
                }
            }
            if (scrollable) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = containerColor,
                    edgePadding = edgePadding,
                    modifier = modifier,
                ) { Tabs() }
            } else {
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = containerColor,
                    modifier = modifier,
                ) { Tabs() }
            }
        }
    }
}
