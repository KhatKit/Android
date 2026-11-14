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

import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.Album
import heizige.kk.khatkit.mediapicker.domain.AlbumState
import heizige.kk.khatkit.mediapicker.domain.MediaState
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup

@Composable
internal fun MediaAlbumRow(
    albumsState: AlbumState,
    mediaState: MediaState,
    selectedAlbumId: Long,
    onAlbumSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** 网格往下滚时收起芯片行（工具台同款），回到顶部再展开。 */
    collapsed: Boolean = false,
) {
    val hasAlbums = albumsState.albums.size > 1
    val isRefreshing = mediaState.isLoading && mediaState.media.isNotEmpty()

    // 底色与发丝线由根内容的外层 Column 统一负责，这里只管内容
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = hasAlbums && !collapsed,
            modifier = Modifier.fillMaxWidth(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
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
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    // Mochi 的芯片组：单行横向滚动、芯片按下有 Mochi 的形变，手指按在芯片上拖动时
    // 拖动交给滚动容器（不会误触选中）；左右留白跟挖孔屏对齐。
    MochiChipGroup(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(
            start = cutout.calculateStartPadding(layoutDirection) + 8.dp,
            end = cutout.calculateEndPadding(layoutDirection) + 8.dp,
        ),
    ) {
        albums.forEach { album ->
            val title = if (album.id == ALL_ALBUM_ID) {
                stringResource(R.string.media_picker_all)
            } else {
                album.label
            }
            MochiChip(
                label = title,
                selected = selectedAlbumId == album.id,
                onClick = { onAlbumSelected(album.id) },
            )
        }
    }
}

/**
 * 与 app 的 KkContentSizeSpec（Mochi 的 mochiAnimateHeight 默认弹簧）同参数：阻尼 1、刚度 380。
 * mediapicker 只依赖 material3 与自身，不引 khatkit-ui，所以在这里自带同一份曲线。
 */
private val MochiContentSizeSpring = spring<IntSize>(
    dampingRatio = 1f,
    stiffness = 380f,
    visibilityThreshold = IntSize(1, 1),
)