/*
 * 网格之上的浮层：选择确认条、加载态、空态、搜索/筛选入口
 * （对应 ImageToolbox `MediaPickerGridWithOverlays`，Apache-2.0, T8RIN）。
 */

package heizige.kk.khatkit.mediapicker.ui.components

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import heizige.kk.kedge.components.KedgeBadge
import heizige.kk.kedge.components.KedgeButtonDefaults
import heizige.kk.kedge.components.KedgeBadgedBox
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeIconButtonVariant
import heizige.kk.kedge.components.KedgeFloatingActionButton
import heizige.kk.kedge.components.KedgeTextFieldWithSlots
import heizige.kk.kedge.overlays.KedgeProgressIndicator
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
            val containerColor by animateColorAsState(
                targetValue = if (canPick) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                label = "pickFabContainer",
            )
            val contentColor by animateColorAsState(
                targetValue = if (canPick) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "pickFabContent",
            )
            Column(horizontalAlignment = Alignment.End) {
                // 取消选择：40dp 小 FAB（MD3Exp 下原本是 SmallFloatingActionButton，
                // Miuix 没有小 FAB，用 KedgeFloatingActionButton + size 压到同尺寸）
                AnimatedVisibility(visible = canPick) {
                    KedgeFloatingActionButton(
                        onClick = { selectedMedia.clear() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Deselect,
                            contentDescription = clearSelectionLabel,
                        )
                    }
                }
                KedgeBadgedBox(
                    badge = {
                        BoxAnimatedVisibility(
                            visible = selectedMedia.isNotEmpty() && allowMultiple,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            KedgeBadge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(text = selectedMedia.size.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                ) {
                    // 确认按钮：56dp 标准 FAB（上游 EnhancedFloatingActionButtonType.Primary），
                    // 不是 40dp 的 Small —— 里面还要塞图标 + 文案
                    KedgeFloatingActionButton(
                        onClick = { if (canPick) onPicked() },
                        containerColor = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.semantics { contentDescription = pickLabel },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Icon(imageVector = Icons.Filled.Verified, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pickLabel)
                        }
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
                KedgeProgressIndicator()
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
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(108.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (albumsState.error + "\n" + mediaState.error).trim()
                        .ifEmpty { stringResource(R.string.media_picker_no_data) },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                KedgeButton(onClick = onRetry) { Text(stringResource(R.string.media_picker_try_again)) }
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
                        KedgeTextFieldWithSlots(
                            value = searchKeyword,
                            onValueChange = {
                                searchKeyword = it
                                onKeywordChange(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.media_picker_search_here)) },
                            leadingIcon = {
                                KedgeIconButton(
                                    onClick = {
                                        searchKeyword = ""
                                        onKeywordChange("")
                                        onSearchingChange(false)
                                    },
                                    modifier = Modifier.padding(start = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = stringResource(R.string.media_picker_exit),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            trailingIcon = {
                                BoxAnimatedVisibility(
                                    visible = searchKeyword.isNotEmpty(),
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut(),
                                ) {
                                    KedgeIconButton(
                                        onClick = {
                                            searchKeyword = ""
                                            onKeywordChange("")
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.media_picker_close),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Search,
                                autoCorrectEnabled = null,
                            ),
                            shape = CircleShape,
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

        BackHandler(selectedMedia.isNotEmpty() && !isSearching) {
            selectedMedia.clear()
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
        KedgeIconButton(
            onClick = onSearch,
            variant = KedgeIconButtonVariant.Filled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
            shapes = KedgeButtonDefaults.md3IconButtonShapes(groupedShape(index = 0, size = 2, vertical = false)),
            modifier = Modifier.size(width = 38.dp, height = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.media_picker_search),
            )
        }
        KedgeIconButton(
            onClick = onFilter,
            variant = KedgeIconButtonVariant.Filled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            shapes = KedgeButtonDefaults.md3IconButtonShapes(groupedShape(index = 1, size = 2, vertical = false)),
            modifier = Modifier.size(width = 38.dp, height = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterAlt,
                contentDescription = stringResource(R.string.media_picker_filter),
            )
        }
    }
}
