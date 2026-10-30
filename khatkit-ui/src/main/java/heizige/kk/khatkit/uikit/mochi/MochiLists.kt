package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.material3.Icon

/**
 * P4c：Kedge 列表行 / 弹层 / 菜单 / 反馈族的 Mochi 版。
 *
 * 参数名沿用旧 Kedge 组件，调用点只改名字。弹层用 androidx.compose.ui.window.Popup
 * （不是 material3 Dialog/ModalBottomSheet，也不是 Kedge 的 Miuix sheet）：
 *  - [MochiModalBottomSheet] / [MochiDialog]：Popup + Mochi 表面色 + 圆角，点外部关闭；
 *  - [MochiDropdownMenuSlots] / [MochiDropdownItemSlot]：Popup 内的菜单列表；
 *  - [MochiSnackbarHost]：底部 Mochi 胶囊条。
 * 弹层里的滚动容器一律由调用方给（BottomSheetScrollSourceGuardTest 仍按原约束校验）。
 */

/** 列表行。 */
@Composable
fun MochiListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: Any? = null,
    tonalElevation: Dp? = null,
    shape: Shape? = null,
    /** 旧 Kedge 未使用，保留以兼容调用点。 */
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            overlineContent?.invoke()
            headlineContent()
            supportingContent?.invoke()
        }
        trailingContent?.invoke()
    }
}

/** 底部弹层（Mochi 胶囊圆角 + 标题槽）。 */
@Composable
fun MochiModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    miuixInsideMarginWidth: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(MochiTheme.colorScheme.surfaceContainer, MochiTheme.shapes.extraLarge)
                .padding(horizontal = miuixInsideMarginWidth, vertical = 16.dp),
        ) {
            title?.let {
                Text(text = it, color = MochiTheme.colorScheme.onSurface, style = MochiTheme.textStyles.titleLarge)
            }
            content()
        }
    }
}

/** 对话框（Popup + Mochi 表面）。 */
@Composable
fun MochiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = modifier
                .background(MochiTheme.colorScheme.surfaceContainer, MochiTheme.shapes.extraLarge)
                .padding(20.dp),
        ) {
            title?.let {
                Text(text = it, color = MochiTheme.colorScheme.onSurface, style = MochiTheme.textStyles.titleLarge)
            }
            summary?.let {
                Text(text = it, color = MochiTheme.colorScheme.onSurfaceVariant, style = MochiTheme.textStyles.body)
            }
            content()
        }
    }
}

/** 菜单项数据。 */
data class MochiMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun MenuList(items: List<MochiMenuItem>, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .background(MochiTheme.colorScheme.surfaceContainerHigh, MochiTheme.shapes.large)
            .padding(vertical = 6.dp),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.enabled) {
                        onDismiss()
                        item.onClick()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = when {
                    !item.enabled -> MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    item.destructive -> MochiTheme.colorScheme.error
                    else -> MochiTheme.colorScheme.onSurface
                }
                item.icon?.let { Icon(imageVector = it, contentDescription = null, tint = tint) }
                Text(text = item.label, color = tint, style = MochiTheme.textStyles.body, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
fun MochiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<MochiMenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
) {
    if (!expanded) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Box(modifier = modifier) { MenuList(items, onDismissRequest) }
    }
}

@Composable
fun MochiListPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    items: List<MochiMenuItem>,
) {
    if (!show) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column {
            Text(text = title, color = MochiTheme.colorScheme.onSurfaceVariant, style = MochiTheme.textStyles.footnote)
            MenuList(items, onDismissRequest)
        }
    }
}

/** 菜单容器（槽位版）：Popup + 自定义内容。 */
@Composable
fun MochiDropdownMenuSlots(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** 不要求 ColumnScope 接收者：旧 Kedge 调用点有传普通 lambda 的写法。 */
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = modifier
                .background(MochiTheme.colorScheme.surfaceContainerHigh, MochiTheme.shapes.large)
                .padding(vertical = 6.dp),
        ) { content() }
    }
}

/** 菜单项（槽位版）。 */
@Composable
fun MochiDropdownItemSlot(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        Box(modifier = Modifier.padding(horizontal = 8.dp)) { text() }
        trailingIcon?.invoke()
    }
}

/** 文本气泡提示：Mochi 侧退化为「内容 + 长按时的文本气泡」；P6 接 MochiPopover。 */
@Composable
fun MochiTextTooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(modifier = Modifier.clickable(onClick = { visible = !visible })) { content() }
        if (visible) {
            Popup(onDismissRequest = { visible = false }, properties = PopupProperties(focusable = false)) {
                Text(
                    text = text,
                    color = MochiTheme.colorScheme.onSurface,
                    style = MochiTheme.textStyles.footnote,
                    modifier = Modifier
                        .background(MochiTheme.colorScheme.surfaceContainerHighest, MochiTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 下拉刷新：Mochi 侧先做语义占位（内容和刷新回调都透传），真实拉伸效果由
 * ProvideMochiOverscroll（P3a 已接根）提供；MochiRefresh 的 suspend onRefresh
 * 在 P6 接入后替换本实现。
 */
@Composable
fun MochiPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) { content() }
}

// ---- Snackbar / Toast 族 ----

enum class MochiSnackbarResult { Dismissed, ActionPerformed }

enum class MochiSnackbarDuration { Short, Long, Indefinite }

class MochiSnackbarHostState {
    internal var message by mutableStateOf<String?>(null)
    internal var actionLabel by mutableStateOf<String?>(null)
    internal var actionTaken by mutableStateOf(false)

    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: MochiSnackbarDuration = MochiSnackbarDuration.Short,
    ): MochiSnackbarResult {
        this.message = message
        this.actionLabel = actionLabel
        this.actionTaken = false
        return if (actionTaken) MochiSnackbarResult.ActionPerformed else MochiSnackbarResult.Dismissed
    }

    fun dismiss() {
        message = null
    }
}

val LocalMochiSnackbarHostState = staticCompositionLocalOf<MochiSnackbarHostState?> { null }

@Composable
fun rememberMochiSnackbarHostState(): MochiSnackbarHostState = remember { MochiSnackbarHostState() }

@Composable
fun MochiSnackbarHost(state: MochiSnackbarHostState, modifier: Modifier = Modifier) {
    val message = state.message ?: return
    Row(
        modifier = modifier
            .padding(16.dp)
            .background(MochiTheme.colorScheme.inverseSurface, MochiTheme.shapes.full)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = message, color = MochiTheme.colorScheme.inverseOnSurface, style = MochiTheme.textStyles.body)
        state.actionLabel?.let { label ->
            Text(
                text = label,
                color = MochiTheme.colorScheme.primary,
                style = MochiTheme.textStyles.body,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        state.actionTaken = true
                        state.message = null
                    },
            )
        }
    }
}
