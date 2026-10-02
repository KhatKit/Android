/*
 * 选择器网格（对应 ImageToolbox `MediaPickerGrid`，Apache-2.0, T8RIN）。
 *
 * 规格：
 *  - `GridCells.Adaptive(100.dp)`、格间距 1dp、背景 surface
 *  - 相册切换或搜索结果变化时滚回顶部
 *  - 长按 = 预览大图；多选时长按 = 开始拖拽连选
 *  - 底部内容留白给「选择」确认条与搜索栏
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.domain.MediaItem
import heizige.kk.khatkit.mediapicker.domain.MediaState
import heizige.kk.khatkit.mediapicker.domain.isHeaderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MediaPickerGrid(
    state: MediaState,
    isSelectionOfAll: Boolean,
    selectedMedia: SnapshotStateList<Media>,
    allowMultiple: Boolean,
    isGalleryMode: Boolean,
    isButtonVisible: Boolean,
    isManagePermissionAllowed: Boolean,
    onRequestManagePermission: () -> Unit,
    onPreview: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val hapticFeedback = LocalHapticFeedback.current

    // 只在「换相册 / 数据真的变了」时回顶。直接拿 mappedMedia 当 key 会在每次
    // MediaStore 变更（哪怕只是新拍了一张别的图）和每次改排序选项时把用户拽回第 0 项。
    val contentFingerprint = remember(state.mappedMedia) {
        state.mappedMedia.size to state.mappedMedia.firstOrNull()?.key
    }
    LaunchedEffect(contentFingerprint) {
        gridState.requestScrollToItem(0)
    }

    val onMediaClick: (Media) -> Unit = {
        if (allowMultiple) {
            if (selectedMedia.contains(it)) selectedMedia.remove(it) else selectedMedia.add(it)
        } else {
            if (selectedMedia.contains(it)) {
                selectedMedia.remove(it)
            } else {
                if (selectedMedia.isNotEmpty()) selectedMedia[0] = it else selectedMedia.add(it)
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val selection = selectedMedia.toList()
    val selectionIndices = remember(selection) {
        buildMap {
            selection.forEachIndexed { index, media -> putIfAbsent(media, index) }
        }
    }
    val privateSelection = remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(state.mappedMedia, isSelectionOfAll, selectedMedia.size) {
        if (isSelectionOfAll) {
            privateSelection.value = withContext(Dispatchers.Default) {
                state.mappedMedia.mapIndexedNotNull { index, item ->
                    if (item is MediaItem.MediaViewItem && item.media in selectionIndices) index else null
                }.toSet()
            }
        }
    }

    LaunchedEffect(selectedMedia.size) {
        if (selectedMedia.isEmpty() && isSelectionOfAll) {
            privateSelection.value = emptySet()
        }
    }

    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val navBar = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = cutout.calculateStartPadding(layoutDirection),
                end = cutout.calculateEndPadding(layoutDirection),
            )
            .dragHandler(
                key = state.mappedMedia,
                enabled = isSelectionOfAll && allowMultiple &&
                    (!isGalleryMode || selectedMedia.isNotEmpty()),
                itemIndex = { item ->
                    (item.index - if (isManagePermissionAllowed) 0 else 1).takeIf { it >= 0 }
                },
                lazyGridState = gridState,
                isVertical = true,
                selectedItems = privateSelection,
                onSelectionChange = { indices ->
                    val media = indices.mapNotNull {
                        (state.mappedMedia.getOrNull(it) as? MediaItem.MediaViewItem)?.media
                    }
                    selectedMedia.clear()
                    selectedMedia.addAll(media)
                },
                onLongTap = {
                    // 粘性头也是一条 item，长按它会走到这里；它不是 MediaViewItem，
                    // 必须传 null 而不是空串 —— 空串会让预览器 open 到第 0 张不相干的图。
                    if (selectedMedia.isEmpty()) {
                        onPreview(
                            (state.mappedMedia.getOrNull(it + 1) as? MediaItem.MediaViewItem)
                                ?.media?.uri
                        )
                    }
                },
                shouldHandleLongTap = selectedMedia.isNotEmpty(),
            ),
        columns = GridCells.Adaptive(100.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = remember(navBar, isButtonVisible, selectedMedia.isNotEmpty()) {
            PaddingValues(
                bottom = navBar.calculateBottomPadding() +
                    (if (isButtonVisible) 80.dp else 0.dp) +
                    (if (selectedMedia.isNotEmpty()) 52.dp else 0.dp),
            )
        },
    ) {
        if (!isManagePermissionAllowed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ManageExternalStorageWarning(onRequestManagePermission)
            }
        }
        itemsIndexed(
            items = state.mappedMedia,
            key = { _, item -> item.key },
            contentType = { _, item -> item.key.startsWith("media_") },
            span = { _, item -> GridItemSpan(if (item.key.isHeaderKey) maxLineSpan else 1) },
        ) { _, item ->
            when (item) {
                is MediaItem.Header -> {
                    // 部分选中时整个头不亮：只有整组全选才打勾
                    val isChecked = remember { mutableStateOf(false) }
                    LaunchedEffect(selectedMedia.size, item.data) {
                        isChecked.value = item.data.isNotEmpty() &&
                            selectionIndices.keys.containsAll(item.data)
                    }
                    MediaStickyHeader(
                        date = item.text,
                        isChecked = isChecked.value,
                        onChecked = if (allowMultiple && (!isGalleryMode || selectedMedia.isNotEmpty())) {
                            {
                                hapticFeedback.longPress()
                                scope.launch {
                                    isChecked.value = !isChecked.value
                                    if (isChecked.value) {
                                        selectedMedia.addAll(
                                            item.data.filter { it !in selectionIndices }
                                        )
                                    } else {
                                        selectedMedia.removeAll(item.data.toSet())
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }

                is MediaItem.MediaViewItem -> {
                    val selectionIndex = selectionIndices[item.media] ?: -1
                    MediaImage(
                        media = item.media,
                        isInSelection = !isGalleryMode || selectionIndex >= 0,
                        canClick = if (isGalleryMode) {
                            selectedMedia.isEmpty() || !isSelectionOfAll || !allowMultiple
                        } else {
                            !isSelectionOfAll || !allowMultiple
                        },
                        onItemClick = {
                            if (isGalleryMode && selectedMedia.isEmpty()) {
                                onPreview(it.uri)
                            } else {
                                hapticFeedback.longPress()
                                onMediaClick(it)
                            }
                        },
                        onItemLongClick = {
                            if (isGalleryMode) {
                                onMediaClick(it)
                            } else {
                                onPreview(it.uri)
                            }
                        },
                        selectionIndex = if (selectedMedia.size > 1) selectionIndex else -1,
                        isSelected = selectionIndex >= 0,
                    )
                }
            }
        }
    }
}
