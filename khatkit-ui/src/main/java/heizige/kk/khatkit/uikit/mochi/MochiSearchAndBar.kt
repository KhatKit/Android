package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.material3.Icon

/**
 * P4a 第二批：Kedge 搜索/浮层骨架/底部导航的 Mochi 版。
 *
 * 参数名沿用旧 Kedge 组件，调用点只改名字；Miuix/LayerBackdrop 专有参数只收不用
 * （毛玻璃由 P6 的 MochiGlassSurface 接手）。
 */

/** 搜索框：Mochi 圆角胶囊底 + material3 TextField（U3：输入类回 MD3）。 */
@Composable
fun Md3SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    active: Boolean = false,
    onActiveChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    expandedContent: @Composable () -> Unit = {},
    cancelLabel: String? = null,
    showResultsWhenEmpty: Boolean = false,
) {
    val scheme = MochiTheme.colorScheme
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // UI-1：高度/圆角对齐 Mochi 度量（40dp 胶囊 = 40/2 的 20dp 圆角）。
                // 之前是 MD3 TextField 的 56dp 最小高 + 上下各 10dp，实际 76dp，比参考高近一倍。
                .height(40.dp)
                .background(scheme.surfaceContainer, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 输入用 BasicTextField：MD3 TextField 有 56dp 最小高，塞不进 40dp 的胶囊
            //（会上下裁字）。外观/占位符仍取 Mochi token，交互（聚焦、清空、取消）不变。
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = scheme.onSurfaceVariant,
                        style = MochiTheme.textStyles.body,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MochiTheme.textStyles.body.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                )
            }
            trailingIcon?.invoke()
            if (cancelLabel != null && (active || value.isNotEmpty())) {
                Text(
                    text = cancelLabel,
                    color = scheme.primary,
                    style = MochiTheme.textStyles.body,
                    modifier = Modifier.clickable { onValueChange(""); onActiveChange?.invoke(false) },
                )
            }
        }
        if (active || value.isNotEmpty() || showResultsWhenEmpty) {
            expandedContent()
        }
    }
}

/** sheet 内的搜索宿主：Mochi 侧目前只需要提供作用域，行为由调用方 content 决定。 */
@Composable
fun MochiSearchSheetHost(content: @Composable () -> Unit) {
    content()
}

/** 叠层骨架：顶/底栏作为图层叠在内容上（BoxScope，调用方自己 align）。 */
@Composable
fun MochiOverlayScaffold(
    modifier: Modifier = Modifier,
    /** 旧 LayerBackdrop：只收不用，毛玻璃由 P6 的 MochiGlassSurface 接手。 */
    backdrop: Any? = null,
    contentInsetTop: Dp = 0.dp,
    contentInsetBottom: Dp = 0.dp,
    topBar: @Composable BoxScope.() -> Unit = {},
    bottomBar: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MochiTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize().padding(top = contentInsetTop, bottom = contentInsetBottom)) {
            content()
        }
        topBar()
        bottomBar()
    }
}

/** 底部导航项（对应旧 Kedge 导航项的字段）。 */
class MochiNavigationItem<T>(
    val key: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/** 底部导航栏：转 Mochi 的 MochiTabBar。 */
@Composable
fun <T> MochiNavigationBar(
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    navigationItems: List<MochiNavigationItem<T>>,
    modifier: Modifier = Modifier,
    onReselect: ((T) -> Unit)? = null,
) {
    if (navigationItems.isEmpty()) return
    val selectedIndex = navigationItems.indexOfFirst { it.key == selectedItem }.coerceAtLeast(0)
    MochiTabBar(
        selectedIndex = selectedIndex,
        onSelect = { index ->
            val item = navigationItems.getOrNull(index) ?: return@MochiTabBar
            if (!item.enabled) return@MochiTabBar
            if (index == selectedIndex) onReselect?.invoke(item.key) else onItemSelected(item.key)
        },
        items = persistentListOf(
            *navigationItems.map { item ->
                MochiTabItem(
                    label = item.label,
                    icon = { tint: Color ->
                        item.icon?.let { Icon(imageVector = it, contentDescription = item.label, tint = tint) }
                    },
                )
            }.toTypedArray(),
        ),
        modifier = modifier,
    )
}
