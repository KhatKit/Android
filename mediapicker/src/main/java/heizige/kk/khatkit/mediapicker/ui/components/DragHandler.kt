/*
 * 网格拖拽连选（对应 ImageToolbox `core/ui/widget/modifier/DragHandler.kt`，
 * Apache-2.0, T8RIN）。
 *
 * 行为与 Google Photos 一致：长按一张图进入拖拽态，拖过的格子整段选中/取消；
 * 拖到上下边缘 40dp 内自动滚动。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @param key 用于在数据刷新时重建手势协程
 * @param itemIndex 从 `LazyGridItemInfo` 取业务下标（列表里混着粘性头，要跳过）
 * @param onLongTap 传的是 `key - 1`（扣掉网格最前面那条提示栏占位）
 */
@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.dragHandler(
    key: Any?,
    isVertical: Boolean,
    lazyGridState: LazyGridState,
    selectedItems: MutableState<Set<Int>>,
    onSelectionChange: (Set<Int>) -> Unit = {},
    enabled: Boolean = true,
    onTap: (Int) -> Unit = {},
    onLongTap: (Int) -> Unit = {},
    shouldHandleLongTap: Boolean = true,
    itemIndex: (LazyGridItemInfo) -> Int?,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val currentItemIndex by rememberUpdatedState(itemIndex)
    val isRtl = !isVertical && LocalLayoutDirection.current == LayoutDirection.Rtl
    val autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() }

    Modifier
        .pointerInput(key, enabled, isRtl) {
            if (enabled) {
                detectTapGestures { offset ->
                    val position =
                        if (isRtl) offset.copy(x = size.width - offset.x) else offset
                    lazyGridState.gridItemKeyAtPosition(position, currentItemIndex)?.let { key ->
                        val newItems =
                            if (selectedItems.value.contains(key)) selectedItems.value - key
                            else selectedItems.value + key
                        selectedItems.value = newItems
                        onSelectionChange(newItems)
                        haptics.longPress()
                        onTap(key - 1)
                    }
                }
            }
        }
        .pointerInput(key, shouldHandleLongTap, enabled, isRtl) {
            if (enabled) {
                coroutineScope {
                    var initialKey: Int? = null
                    var currentKey: Int? = null
                    var dragPosition: Offset? = null
                    var autoScrollSpeed = 0f
                    var autoScrollJob: Job? = null

                    fun updateSelection(position: Offset) {
                        val initial = initialKey ?: return
                        lazyGridState.gridItemKeyAtPosition(position, currentItemIndex)?.let { key ->
                            if (currentKey != key) {
                                val newItems = selectedItems.value
                                    .minus(initial..currentKey!!)
                                    .minus(currentKey!!..initial)
                                    .plus(initial..key)
                                    .plus(key..initial)
                                selectedItems.value = newItems
                                onSelectionChange(newItems)
                                currentKey = key
                            }
                        }
                    }

                    fun stopDrag() {
                        initialKey = null
                        currentKey = null
                        dragPosition = null
                        autoScrollSpeed = 0f
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    }

                    detectDragGesturesAfterLongPressInInitialPass(
                        onDragStart = { offset ->
                            val position =
                                if (isRtl) offset.copy(x = size.width - offset.x) else offset
                            lazyGridState.gridItemKeyAtPosition(position, currentItemIndex)?.let { key ->
                                if (!selectedItems.value.contains(key) && shouldHandleLongTap) {
                                    initialKey = key
                                    currentKey = key
                                    dragPosition = position
                                    val newItems = selectedItems.value + key
                                    selectedItems.value = newItems
                                    onSelectionChange(newItems)

                                    autoScrollJob = launch {
                                        lazyGridState.scroll(MutatePriority.PreventUserInput) {
                                            while (isActive) {
                                                val speed = autoScrollSpeed
                                                if (speed != 0f) {
                                                    scrollBy(speed)
                                                    dragPosition?.let(::updateSelection)
                                                }
                                                delay(10)
                                            }
                                        }
                                    }
                                }
                                haptics.longPress()
                                onLongTap(key - 1)
                            }
                        },
                        onDragCancel = ::stopDrag,
                        onDragEnd = ::stopDrag,
                        onDrag = { change, _ ->
                            if (initialKey != null) {
                                val position = if (isRtl) {
                                    change.position.copy(x = size.width - change.position.x)
                                } else {
                                    change.position
                                }
                                dragPosition = position
                                val distFromBottom = if (isVertical) {
                                    lazyGridState.layoutInfo.viewportSize.height - position.y
                                } else {
                                    lazyGridState.layoutInfo.viewportSize.width - position.x
                                }
                                val distFromTop = if (isVertical) position.y else position.x
                                autoScrollSpeed = when {
                                    distFromBottom < autoScrollThreshold ->
                                        autoScrollThreshold - distFromBottom

                                    distFromTop < autoScrollThreshold ->
                                        -(autoScrollThreshold - distFromTop)

                                    else -> 0f
                                }
                                updateSelection(position)
                            }
                        },
                    )
                }
            }
        }
}

private suspend fun PointerInputScope.detectDragGesturesAfterLongPressInInitialPass(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            onDragStart(longPress.position)
            var pointerId = longPress.id
            var isFinished = false

            while (!isFinished) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                var change = event.changes.firstOrNull { it.id == pointerId }

                if (change?.pressed != true) {
                    change = event.changes.firstOrNull { it.pressed }
                    if (change == null) {
                        event.changes
                            .filter(PointerInputChange::changedToUpIgnoreConsumed)
                            .forEach(PointerInputChange::consume)
                        isFinished = true
                    } else {
                        pointerId = change.id
                    }
                }

                if (!isFinished && change != null) {
                    onDrag(change, change.position - change.previousPosition)
                    change.consume()
                }
            }

            onDragEnd()
        } catch (exception: CancellationException) {
            onDragCancel()
            throw exception
        }
    }
}

private fun LazyGridState.gridItemKeyAtPosition(
    hitPoint: Offset,
    itemIndex: (LazyGridItemInfo) -> Int?,
): Int? {
    val found = layoutInfo.visibleItemsInfo.find { itemInfo ->
        itemInfo.size.toIntRect().contains(hitPoint.round() - itemInfo.offset)
    }
    return found?.let(itemIndex)
}
