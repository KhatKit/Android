package heizige.kk.khatkit.app.feature.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.navigation.MochiCompactTitleScaffold
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.theme.MochiDefaults
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
    MochiTabBar(
        selectedIndex = selected,
        onSelect = { index -> items.getOrNull(index)?.onClick?.invoke() },
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
    floatingActionButtonPosition: Any? = null,
    md3ScrollBehavior: Any? = null,
    /** 非空时走 Mochi 真页面骨架（大标题/紧凑标题），content 仍拿 PaddingValues.Zero。 */
    title: String? = null,
    subtitle: String? = null,
    /** true = 大标题页（可折叠）；false = 紧凑标题页（正文自带分组小标题时用）。 */
    largeTitle: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (title != null) {
        val body: @Composable ColumnScope.() -> Unit = { content(PaddingValues.Zero) }
        if (largeTitle) {
            MochiLargeTitleScaffold(
                title = title,
                modifier = modifier,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions,
                background = MochiDefaults.groupedBackground(),
                content = body,
            )
        } else {
            MochiCompactTitleScaffold(
                title = title,
                modifier = modifier,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions,
                background = MochiDefaults.groupedBackground(),
                content = body,
            )
        }
        return
    }
    Box(modifier = modifier.fillMaxSize().background(containerColor)) {
        val windowWidth = currentWindowDpSize().width
        val railOnSide = rail != null && windowWidth >= 600.dp && windowWidth < 840.dp
        if (railOnSide) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(96.dp).fillMaxHeight()) { rail?.invoke() }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    topBar()
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues.Zero)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                Box(modifier = Modifier.weight(1f)) {
                    content(PaddingValues.Zero)
                }
                bottomBar()
                rail?.invoke()
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
        Box(modifier = Modifier.align(Alignment.BottomEnd)) { floatingActionButton() }
    }
}
