package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableListItemScope

private const val DraggingScale = 0.95f

/**
 * 统一的列表拖拽排序手势：长按整个 item 开始拖拽，带触感反馈和拖拽中的缩放效果。
 *
 * 在 `ReorderableItem { isDragging -> }` 内使用，作为 item 的 modifier：
 * ```
 * ReorderableItem(state, key) { isDragging ->
 *     MyItem(modifier = longPressReorder(isDragging))
 * }
 * ```
 */
@Composable
fun ReorderableCollectionItemScope.longPressReorder(
    isDragging: Boolean,
    enabled: Boolean = true,
): Modifier {
    val haptics = rememberKkHaptics()
    return Modifier
        .draggingScale(isDragging)
        .longPressDraggableHandle(
            enabled = enabled,
            onDragStarted = { haptics.longPress() },
            onDragStopped = { haptics.confirm() },
        )
}

/**
 * [longPressReorder] 的 `ReorderableColumn` / `ReorderableRow` 版本。
 */
@Composable
fun ReorderableListItemScope.longPressReorder(
    isDragging: Boolean,
    enabled: Boolean = true,
): Modifier {
    val haptics = rememberKkHaptics()
    return Modifier
        .draggingScale(isDragging)
        .longPressDraggableHandle(
            enabled = enabled,
            onDragStarted = { haptics.longPress() },
            onDragStopped = { haptics.confirm() },
        )
}

@Composable
private fun Modifier.draggingScale(isDragging: Boolean): Modifier {
    val scale by animateFloatAsState(if (isDragging) DraggingScale else 1f, label = "reorder_scale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
