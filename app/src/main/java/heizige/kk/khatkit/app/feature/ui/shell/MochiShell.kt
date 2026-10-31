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
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues.Zero)
            }
            bottomBar()
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
        Box(modifier = Modifier.align(Alignment.BottomEnd)) { floatingActionButton() }
    }
}
