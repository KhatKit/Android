package heizige.kk.khatkit.uikit

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.khromia.components.BasicBottomSheet
import heizige.kk.khromia.components.LocalBottomSheetDismiss
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.layout.FullscreenPopup
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * BottomSheet 拖柄顶行右侧的一个动作（`ui.sheet` / `web.openLogin` 的 action）。
 *
 * 脚本只声明 `icon` 名与 `event`，宿主把它解析成 [imageVector] 后传进来；
 * [label] 用作无障碍描述与 tooltip，[event] 是点击后回传卡片的事件名。
 */
data class KhatKitSheetAction(
    val event: String,
    val label: String,
    val imageVector: ImageVector,
)

/**
 * 卡片弹层统一入口（`ui.form` / `ui.show` / `ui.sheet` 消费 [UiSheetOptions]）。
 *
 * - 默认（无 fullscreen / height）：与旧版一致，走 Khromia [PrimaryBottomSheet]；
 * - `fullscreen` / `height`：铺满窗口的大弹层，高度取满屏或屏幕比例，宽度铺满，
 *   保留拖动把手、下滑关闭、返回键取消，内容区滚动、底部操作栏固定；
 * - [headerActions] / [overflowActions] 非空时改为「拖柄顶行 + 标题」布局：
 *   拖柄所在顶行右侧是图标按钮与溢出菜单（卡片网页登录的 Cookie 保存按钮就走这里），
 *   底部只留关闭按钮。
 */
@Composable
fun KhatKitSheet(
    title: String,
    imageVector: ImageVector,
    options: UiSheetOptions,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    headerActions: List<KhatKitSheetAction> = emptyList(),
    overflowActions: List<KhatKitSheetAction> = emptyList(),
    overflowIcon: ImageVector? = null,
    onAction: (KhatKitSheetAction) -> Unit = {},
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val hasTopBar = headerActions.isNotEmpty() ||
        (overflowIcon != null && overflowActions.isNotEmpty())

    if (!options.usesLargeSheet) {
        if (!hasTopBar) {
            PrimaryBottomSheet(
                visible = true,
                title = title,
                imageVector = imageVector,
                dismissText = dismissText,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                scrollable = true,
            ) { dismiss -> content(dismiss) }
            return
        }
        TopBarSheet(
            title = title,
            headerActions = headerActions,
            overflowActions = overflowActions,
            overflowIcon = overflowIcon,
            dismissText = dismissText,
            onAction = onAction,
            onDismiss = onDismiss,
            content = content,
        )
        return
    }

    LargeSheet(
        title = title,
        imageVector = imageVector,
        fullscreen = options.fullscreen,
        heightFraction = options.height ?: 1f,
        dismissText = dismissText,
        confirmText = confirmText,
        onConfirm = onConfirm,
        headerActions = headerActions,
        overflowActions = overflowActions,
        overflowIcon = overflowIcon,
        onAction = onAction,
        onDismiss = onDismiss,
        content = content,
    )
}

/** 拖柄顶行 + 标题的弹层：动作位于拖柄右侧，底部只留关闭按钮。 */
@Composable
private fun TopBarSheet(
    title: String,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    overflowIcon: ImageVector?,
    dismissText: String?,
    onAction: (KhatKitSheetAction) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    BasicBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        enablePredictiveBack = true,
        scrollable = true,
        dragHandle = {
            SheetTopBar(
                title = title,
                headerActions = headerActions,
                overflowActions = overflowActions,
                overflowIcon = overflowIcon,
                onAction = onAction,
            )
        },
        bottomBar = { SheetCloseBar(dismissText) },
        content = { content(LocalBottomSheetDismiss.current) },
    )
}

@Composable
private fun LargeSheet(
    title: String,
    imageVector: ImageVector,
    fullscreen: Boolean,
    heightFraction: Float,
    dismissText: String?,
    confirmText: String?,
    onConfirm: (() -> Unit)?,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    overflowIcon: ImageVector?,
    onAction: (KhatKitSheetAction) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 96.dp.toPx() }
    val hasTopBar = headerActions.isNotEmpty() ||
        (overflowIcon != null && overflowActions.isNotEmpty())

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val target = (offsetY.value + delta).coerceAtLeast(0f)
            offsetY.snapTo(target)
        }
    }

    FullscreenPopup(onDismiss = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 遮罩是独立兄弟层：点空白关闭，但点击弹层表面不会穿透到这里。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(screenHeight * heightFraction))
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (offsetY.value > dismissThreshold || velocity > 800f) {
                                onDismiss()
                            } else {
                                scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                    )
                    .clip(
                        if (fullscreen) RoundedCornerShape(0.dp)
                        else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    if (hasTopBar) {
                        SheetTopBar(
                            title = title,
                            headerActions = headerActions,
                            overflowActions = overflowActions,
                            overflowIcon = overflowIcon,
                            onAction = onAction,
                        )
                    } else {
                        SheetDragHandle()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content(onDismiss)
                    }
                    if (hasTopBar) {
                        SheetCloseBar(dismissText)
                    } else {
                        SheetBottomBar(
                            title = title,
                            imageVector = imageVector,
                            dismissText = dismissText,
                            confirmText = confirmText,
                            onConfirm = onConfirm,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 弹层顶部：拖动把手 + 标题行（标题在左，溢出菜单与图标按钮在右上角）。
 *
 * 图标按钮的无障碍描述直接用 action 的 label，点击后由调用方回传 event。
 */
@Composable
private fun SheetTopBar(
    title: String,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    overflowIcon: ImageVector?,
    onAction: (KhatKitSheetAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f)),
                    )
                }
                if (overflowIcon != null && overflowActions.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    KedgeIconButton(
                        onClick = { expanded = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = overflowIcon,
                            contentDescription = stringResource(R.string.khatkit_sheet_more_actions),
                        )
                    }
                    KedgeDropdownMenuSlots(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        overflowActions.forEach { action ->
                            KedgeDropdownItemSlot(
                                text = { Text(action.label) },
                                leadingIcon = { Icon(action.imageVector, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onAction(action)
                                },
                            )
                        }
                    }
                }
                headerActions.forEach { action ->
                    KedgeIconButton(
                        onClick = { onAction(action) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(action.imageVector, contentDescription = action.label)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SheetDragHandle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f))
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(4.dp)
                .width(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f)),
        )
    }
}

/** 顶栏布局下的底部操作栏：只留一个关闭按钮（标题已经在顶栏）。 */
@Composable
private fun SheetCloseBar(dismissText: String?) {
    val actualDismissText = dismissText ?: stringResource(R.string.khatkit_close)
    val dismiss = LocalBottomSheetDismiss.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = { dismiss() },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(actualDismissText)
        }
    }
}

@Composable
private fun SheetBottomBar(
    title: String,
    imageVector: ImageVector,
    dismissText: String?,
    confirmText: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val actualDismissText = dismissText ?: stringResource(heizige.kk.khromia.R.string.bottom_sheet_dismiss)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f), CircleShape)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(text = title, style = MaterialTheme.typography.labelLarge)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onConfirm?.invoke() ?: onDismiss() },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(confirmText ?: actualDismissText)
        }
    }
}
