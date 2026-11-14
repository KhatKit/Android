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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.mediapicker.R
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import ui.feice.mochi.basic.MochiRefreshIndicator
import ui.feice.mochi.basic.mochiRefreshHold
import ui.feice.mochi.basic.rememberMochiRefreshState
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.mochiContainedScroll
import ui.feice.mochi.effect.overscroll.rememberMochiOverscrollEffect
import ui.feice.mochi.effect.glass.MochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.icons.MochiIonicon
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
import androidx.compose.ui.unit.Dp
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
    onTakePicture: (() -> Unit)? = null,
    onScrolled: (Boolean) -> Unit = {},
    /** 栏底渐显进度 0..1（滚进栏下面多少）。 */
    onScrollProgress: (Float) -> Unit = {},
    onRefresh: (suspend () -> Unit)? = null,
    /** 玻璃取样源：录网格自己，给栏 / 底部按钮取样。 */
    backdrop: MochiBackdrop? = null,
    /** 网格顶部留白（= 栏高 + 状态栏）；内容可以从它下面滚进栏里。 */
    topContentPadding: Dp = Dp.Unspecified,
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

    // 网格条目：媒体项（含日期头）+ 第一格「拍照」。拍照放在「今天」这一组里面，
    // 也就是第一个日期头之后第一格；没有日期头时退回最前面。
    val entries = remember(state.mappedMedia, onTakePicture) {
        if (onTakePicture == null) {
            state.mappedMedia.map { GridEntry.Real(it) }
        } else {
            buildList<GridEntry> {
                var inserted = false
                state.mappedMedia.forEach { item ->
                    add(GridEntry.Real(item))
                    if (!inserted && item is MediaItem.Header) {
                        add(GridEntry.Camera)
                        inserted = true
                    }
                }
                if (!inserted) add(0, GridEntry.Camera)
            }
        }
    }
    // 不用 LazyGridItemInfo.index 做换算：权限提示、拍照格和日期头都会改变布局下标，
    // 而 itemsIndexed 的 index 只代表 entries 自身下标，混用两套下标会让首行点选落到下一行。
    // 直接按稳定 key 映射到 mappedMedia，拖拽连选与普通点选共用同一套业务索引。
    val mediaIndexByEntryKey = remember(entries, state.mappedMedia) {
        state.mappedMedia.withIndex().associate { (index, item) -> item.key to index }
    }

    // 滚离顶部 → 收起相册芯片行（工具台同一套判断：第一项不是 0 或已滚过一点）。
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }.distinctUntilChanged().collect(onScrolled)
    }
    // 栏底渐显：滚到顶透明，滚过一小段就完全不透明（同 Mochi 滚动边缘的节奏）。
    LaunchedEffect(gridState) {
        snapshotFlow {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / 48f).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect(onScrollProgress)
    }

    // Mochi 下拉刷新：跟页面同一套 overscroll / 指示器。
    val overscroll = rememberMochiOverscrollEffect()
    val refresh = rememberMochiRefreshState(overscroll, onRefresh = onRefresh)

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (backdrop != null) {
                    Modifier.mochiBackdropSource(backdrop)
                } else {
                    Modifier
                },
            ),
    ) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = cutout.calculateStartPadding(layoutDirection),
                end = cutout.calculateEndPadding(layoutDirection),
            )
            .mochiRefreshHold(refresh)
            .mochiContainedScroll(overscroll)
            .dragHandler(
                key = state.mappedMedia,
                enabled = isSelectionOfAll && allowMultiple &&
                    (!isGalleryMode || selectedMedia.isNotEmpty()),
                itemIndex = { item ->
                    mediaIndexByEntryKey[item.key]
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
                    if (selectedMedia.isEmpty()) {
                        onPreview(
                            (state.mappedMedia.getOrNull(it) as? MediaItem.MediaViewItem)
                                ?.media?.uri,
                        )
                    }
                },
                shouldHandleLongTap = selectedMedia.isNotEmpty(),
            ),
        overscrollEffect = overscroll,
        flingBehavior = MochiScrollDefaults.flingBehavior(),
        columns = GridCells.Adaptive(100.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = remember(
            navBar,
            isButtonVisible,
            selectedMedia.isNotEmpty(),
            topContentPadding,
        ) {
            PaddingValues(
                top = if (topContentPadding == Dp.Unspecified) 0.dp else topContentPadding,
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
            items = entries,
            key = { _, entry -> entry.key },
            contentType = { _, entry -> entry.key.toString().startsWith("media_") },
            span = { _, entry -> GridItemSpan(if (entry.spanFull) maxLineSpan else 1) },
        ) { _, entry ->
            when (entry) {
                is GridEntry.Camera -> {
                    if (onTakePicture != null) CameraTile(onClick = onTakePicture)
                }

                is GridEntry.Real -> when (val item = entry.item) {
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
        MochiRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
    }
}

/** 网格里的一项：媒体项（含日期头）或「拍照」格。 */
private sealed interface GridEntry {
    val key: Any
    val spanFull: Boolean

    data class Real(val item: MediaItem) : GridEntry {
        override val key: Any get() = item.key
        override val spanFull: Boolean get() = item.key.isHeaderKey
    }

    data object Camera : GridEntry {
        override val key: Any get() = "camera"
        override val spanFull: Boolean get() = false
    }
}

/** 网格第一格的「拍照」：和照片同尺寸的方块，图标用 mochi-icons。 */
@Composable
private fun CameraTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MochiIonicon(
                "camera",
                size = 28.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.media_picker_take_picture),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
