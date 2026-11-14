/*
 * 网格之上的浮层：选择确认条、加载态、空态、搜索/筛选入口
 * （对应 ImageToolbox `MediaPickerGridWithOverlays`，Apache-2.0, T8RIN）。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiActivityIndicator
import androidx.compose.runtime.CompositionLocalProvider
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.icons.MochiIonicon

import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.AlbumState
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.domain.MediaState

@Composable
internal fun MediaPickerGridWithOverlays(
    mediaState: MediaState,
    filteredMediaState: MediaState,
    albumsState: AlbumState,
    selectedMedia: SnapshotStateList<Media>,
    selectedAlbumId: Long,
    isSearching: Boolean,
    allowMultiple: Boolean,
    isManagePermissionAllowed: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    onPicked: () -> Unit,
    onRetry: () -> Unit,
    onRequestManagePermission: () -> Unit,
    onRequestFilter: () -> Unit,
    onPreview: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** 第一格「拍照」；null 时不出这一格。 */
    onTakePicture: (() -> Unit)? = null,
    /** 网格滚离顶部时回调 true（收起相册芯片行用）。 */
    onScrolled: (Boolean) -> Unit = {},
    /** 栏底渐显进度 0..1。 */
    onScrollProgress: (Float) -> Unit = {},
    /** Mochi 下拉刷新：重新扫一遍 MediaStore。 */
    onRefresh: (suspend () -> Unit)? = null,
    /** 玻璃取样源：只录网格本身，录完给栏 / 按钮取样。 */
    backdrop: ui.feice.mochi.effect.glass.MochiBackdrop? = null,
    /** 网格顶部的留白（= 栏高 + 状态栏），让内容能从栏底下面滚过去。 */
    topContentPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
) {
    var searchKeyword by rememberSaveable(isSearching) { mutableStateOf("") }

    LaunchedEffect(isSearching) {
        if (!isSearching) {
            searchKeyword = ""
            onKeywordChange("")
        }
    }

    val isButtonVisible = (!allowMultiple || selectedMedia.isNotEmpty()) && !isSearching
    val canPick = selectedMedia.isNotEmpty()
    val clearSelectionLabel = stringResource(R.string.media_picker_clear_selection)
    val pickLabel = stringResource(R.string.media_picker_pick)

    // 底部浮着的按钮（搜索 / 筛选 / 取消选择 / 选取）在网格录制层外面，
    // 这里把网格那份背景来源下发给它们，玻璃样式才有东西可取样。
    CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
    Box(modifier = modifier.fillMaxSize()) {
        MediaPickerGrid(
            state = filteredMediaState,
            isSelectionOfAll = selectedAlbumId == ALL_ALBUM_ID,
            selectedMedia = selectedMedia,
            allowMultiple = allowMultiple,
            isGalleryMode = false,
            isButtonVisible = isButtonVisible,
            isManagePermissionAllowed = isManagePermissionAllowed,
            onRequestManagePermission = onRequestManagePermission,
            onPreview = onPreview,
            onTakePicture = onTakePicture,
            onScrolled = onScrolled,
            onScrollProgress = onScrollProgress,
            onRefresh = onRefresh,
            backdrop = backdrop,
            topContentPadding = topContentPadding,
        )

        // ── 选择确认条：Deselect 小 FAB + Pick 扩展 FAB，角标显示已选数量
        BoxAnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .safeDrawingPadding(),
            visible = isButtonVisible,
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
        ) {
            Column(horizontalAlignment = Alignment.End) {
                // 取消选择：使用 Mochi 图标按钮，避免在选择器里混用 Material FAB。
                AnimatedVisibility(visible = canPick) {
                    MochiIconButton(
                        onClick = { selectedMedia.clear() },
                        style = MochiButtonStyle.ClearGlass,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Deselect,
                            contentDescription = clearSelectionLabel,
                        )
                    }
                }
                MochiButton(
                    onClick = { if (canPick) onPicked() },
                    enabled = canPick,
                    // 玻璃样式：跟设置中心的「玻璃按钮」开关联动（关掉退回实心）。
                    style = MochiButtonStyle.ClearGlass,
                    modifier = Modifier.semantics { contentDescription = pickLabel },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.Verified, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (allowMultiple && selectedMedia.isNotEmpty()) "$pickLabel (${selectedMedia.size})" else pickLabel)
                    }
                }
            }
        }

        // ── 首次加载 / 搜索重算时的遮罩
        val isHaveNoData = mediaState.media.isEmpty() && !mediaState.isLoading
        val showLoading = (mediaState.isLoading && mediaState.media.isEmpty() || filteredMediaState.isLoading) &&
            !isHaveNoData
        val scrimColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.scrim.copy(
                if (showLoading && filteredMediaState.media.isNotEmpty()) 0.5f else 0f,
            ),
            label = "pickerScrim",
        )
        BoxAnimatedVisibility(
            visible = showLoading,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(scrimColor),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MochiActivityIndicator()
            }
        }

        // ── 搜索无结果
        BoxAnimatedVisibility(
            visible = filteredMediaState.media.isEmpty() &&
                !filteredMediaState.isLoading && isSearching,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.media_picker_nothing_found_by_search),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(2f)
                        .sizeIn(maxHeight = 140.dp, maxWidth = 140.dp)
                        .fillMaxSize(),
                )
                Spacer(Modifier.weight(1f))
            }
        }

        // ── 没有任何媒体（空相册 / 查询失败）
        BoxAnimatedVisibility(
            visible = isHaveNoData,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.weight(1f))
                MochiEmptyState(
                    title = (albumsState.error + "\n" + mediaState.error).trim()
                        .ifEmpty { stringResource(R.string.media_picker_no_data) },
                    icon = { MochiIonicon("images", size = 36.dp) },
                    action = {
                        MochiButton(
                            onClick = onRetry,
                            style = MochiButtonStyle.ClearGlass,
                        ) { Text(stringResource(R.string.media_picker_try_again)) }
                    },
                )
                Spacer(Modifier.weight(1f))
            }
        }

        // ── 底部：搜索框 / 搜索 + 筛选 两颗按钮
        BoxAnimatedVisibility(
            visible = !mediaState.isLoading && !isHaveNoData,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .safeDrawingPadding(),
                targetState = isSearching,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pickerSearchMode",
            ) { searchMode ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (searchMode) {
                        // Mochi 的搜索栏：胶囊输入框 + 清空按钮，聚焦时右侧滑出取消键。
                        MochiSearchBar(
                            query = searchKeyword,
                            onQueryChange = {
                                searchKeyword = it
                                onKeywordChange(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            placeholder = stringResource(R.string.media_picker_search_here),
                            cancelText = stringResource(R.string.media_picker_exit),
                            onCancel = {
                                searchKeyword = ""
                                onKeywordChange("")
                                onSearchingChange(false)
                            },
                        )
                    } else {
                        MediaSearchAndFilterButtons(
                            onSearch = { onSearchingChange(true) },
                            onFilter = onRequestFilter,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
        }

        // 清空选择也用预测返回：手势期间先看窗口退后预览，松手提交才清空。
        PredictiveBackHandler(selectedMedia.isNotEmpty() && !isSearching) { events ->
            try {
                events.collect { }
                selectedMedia.clear()
            } catch (_: CancellationException) {
            }
        }
    }
    }
}

/** 底部左下角的「搜索 / 筛选」双按钮，两颗贴合成分组控件。 */
@Composable
internal fun MediaSearchAndFilterButtons(
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MochiIconButton(
            onClick = onSearch,
            style = MochiButtonStyle.ClearGlass,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.media_picker_search),
            )
        }
        MochiIconButton(
            onClick = onFilter,
            style = MochiButtonStyle.ClearGlass,
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterAlt,
                contentDescription = stringResource(R.string.media_picker_filter),
            )
        }
    }
}
