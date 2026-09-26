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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.layout.FullscreenPopup
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 卡片弹层统一入口（`ui.form` / `ui.show` 消费 [UiSheetOptions]）。
 *
 * - 默认（无 fullscreen / height）：与旧版一致，走 Khromia [PrimaryBottomSheet]；
 * - `fullscreen` / `height`：铺满窗口的大弹层，高度取满屏或屏幕比例，宽度铺满，
 *   保留拖动把手、下滑关闭、返回键取消，内容区滚动、底部操作栏固定。
 */
@Composable
fun KhatKitSheet(
    title: String,
    imageVector: ImageVector,
    options: UiSheetOptions,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    if (!options.usesLargeSheet) {
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

    LargeSheet(
        title = title,
        imageVector = imageVector,
        fullscreen = options.fullscreen,
        heightFraction = options.height ?: 1f,
        dismissText = dismissText,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = content,
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
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 96.dp.toPx() }

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
                    SheetDragHandle()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content(onDismiss)
                    }
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
