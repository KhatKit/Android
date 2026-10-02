/*
 * 顶部相册行 + 刷新指示（对应 ImageToolbox `MediaPickerHavePermissions` 里的
 * topBar 部分，Apache-2.0, T8RIN）。
 *
 * 行为：
 *  - 横向滚动的相册 chip，第一个是「全部」
 *  - 右侧一颗按钮切换「展开缩略图」：chip 长出封面图 + 数量徽标，高度固定 100dp
 *  - 重查数据时（已有内容但仍在加载）顶部出现波浪进度条
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.Album
import heizige.kk.khatkit.mediapicker.domain.AlbumState
import heizige.kk.khatkit.mediapicker.domain.MediaState

@Composable
internal fun MediaAlbumRow(
    albumsState: AlbumState,
    mediaState: MediaState,
    selectedAlbumId: Long,
    onAlbumSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAlbums = albumsState.albums.size > 1
    val isRefreshing = mediaState.isLoading && mediaState.media.isNotEmpty()

    // 底色与发丝线由根内容的外层 Column 统一负责，这里只管内容
    Column(modifier = modifier) {
        AnimatedVisibility(visible = hasAlbums, modifier = Modifier.fillMaxWidth()) {
            MediaAlbumChips(
                albums = albumsState.albums,
                selectedAlbumId = selectedAlbumId,
                onAlbumSelected = onAlbumSelected,
            )
        }
        AnimatedVisibility(visible = isRefreshing, modifier = Modifier.fillMaxWidth()) {
            WavyPickerProgress(modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun MediaAlbumChips(
    albums: List<Album>,
    selectedAlbumId: Long,
    onAlbumSelected: (Long) -> Unit,
) {
    var showAlbumThumbnail by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

    Row(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = WindowInsets.displayCutout.asPaddingValues()
                    .calculateStartPadding(layoutDirection) + 8.dp,
                end = WindowInsets.displayCutout.asPaddingValues()
                    .calculateEndPadding(layoutDirection) + 8.dp,
            ),
            state = listState,
        ) {
            items(items = albums, key = { it.id }) { album ->
                MediaAlbumChip(
                    album = album,
                    selected = selectedAlbumId == album.id,
                    showThumbnail = showAlbumThumbnail,
                    onClick = { onAlbumSelected(album.id) },
                )
            }
        }
        KedgeIconButton(onClick = { showAlbumThumbnail = !showAlbumThumbnail }) {
            val rotation by animateFloatAsState(if (showAlbumThumbnail) 180f else 0f)
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.media_picker_expand_albums),
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun MediaAlbumChip(
    album: Album,
    selected: Boolean,
    showThumbnail: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImageVisible = showThumbnail && album.uri.isNotEmpty()
    val horizontalPadding by animateDpAsState(if (isImageVisible) 8.dp else 12.dp, label = "albumChipH")
    val verticalPadding by animateDpAsState(if (isImageVisible) 8.dp else 0.dp, label = "albumChipV")
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(1.dp) }
    val title = if (album.id == ALL_ALBUM_ID) {
        stringResource(R.string.media_picker_all)
    } else {
        album.label
    }

    // 上游用的是自研 EnhancedChip（16dp 圆角 / 32dp 最小高度 / labelLarge+SemiBold /
    // 未选中带 outlineVariant 描边），不是 MD3 的 FilterChip —— FilterChip 自带描边
    // 和更大的内边距，视觉上明显不是一个东西。这里用 Surface 复刻它的形态。
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "albumChipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "albumChipContent",
    )
    // 上游 `ColorScheme.outlineVariant()` = onSecondaryContainer @30% 叠在 surfaceContainer 上。
    // 直接用配色里的 outlineVariant 不可靠：有些主题（含 Miuix 桥接）给的是接近透明的
    // 值，chip 就完全看不出边界 —— 这里按上游的算法自己算。
    val borderColor = MaterialTheme.colorScheme.onSecondaryContainer
        .copy(alpha = 0.3f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainer)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        modifier = modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
    ) {
        // 展开缩略图会改变 chip 高度：没有动画的话整行会「啪」地一下撑开。
        // 「全部」没有封面图，用一个固定高度占位，让它在展开时和其它 chip 保持等高。
    val reservedHeight by animateDpAsState(
        targetValue = if (showThumbnail && album.uri.isEmpty()) 140.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "albumChipReservedHeight",
    )
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .heightIn(min = reservedHeight)
                .animateContentSize(animationSpec = tween(durationMillis = 250)),
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = KedgeTextStyles.footnote().copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.onSizeChanged {
                    textWidth = with(density) { it.width.toDp().coerceAtLeast(100.dp) }
                },
            )
            BoxAnimatedVisibility(
                visible = isImageVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box {
                    BoxAnimatedVisibility(
                        visible = textWidth > 1.dp,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        AsyncImage(
                            model = album.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(100.dp)
                                .width(textWidth)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(100.dp)
                            .width(textWidth)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = album.count.toString(),
                            style = KedgeTextStyles.title().copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}
