/*
 * 网格里的单个图片格（对应 ImageToolbox `MediaImage`，Apache-2.0, T8RIN）。
 *
 * 视觉要点（与上游一致）：
 *  - 未选中：4dp 圆角铺满格子
 *  - 选中：四周内缩 12dp、16dp 圆角、2dp 描边（primaryContainer）
 *  - 右上格式角标 / 左下体积角标在选中时缩到 0.5 倍
 *  - 左上勾选标记，选中且多张时显示顺序序号
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import heizige.kk.khatkit.mediapicker.domain.Media

@Composable
fun MediaImage(
    modifier: Modifier = Modifier,
    media: Media,
    isInSelection: Boolean = true,
    isSelected: Boolean,
    selectionIndex: Int,
    canClick: Boolean,
    onItemClick: (Media) -> Unit,
    onItemLongClick: (Media) -> Unit,
) {
    val transition = updateTransition(isSelected, label = "mediaImageSelection")

    // 显式 tween：默认 spring 允许过冲，选中时缩放会「弹一下」再停住
    val selectedSize = transition.animateDp(label = "mediaImageSelectedSize") {
        if (it) 12.dp else 0.dp
    }
    val overlayScale = transition.animateFloat(label = "mediaImageOverlayScale") {
        if (it) 0.5f else 1f
    }

    var isImageError by remember(media.uri) { mutableStateOf(false) }

    val strokeColor = if (isSelected) {
        if (isImageError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (canClick) {
                    Modifier.hapticsCombinedClickable(
                        onClick = { onItemClick(media) },
                        onLongClick = { onItemLongClick(media) },
                    )
                } else {
                    Modifier
                }
            )
            .aspectRatio(1f),
    ) {
        val shape = RoundedCornerShape(if (isSelected) 16.dp else 4.dp)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                // fillMaxSize 而不是再来一次 aspectRatio：格子本身已经是正方形
                // （外层 aspectRatio(1f) 保证的），再叠一层 aspectRatio 会让选中态
                // 的定位依赖两次比例计算，出现偏移
                .fillMaxSize()
                .padding(selectedSize.value)
                .clip(shape)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    shape = shape,
                    color = strokeColor,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
        ) {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = remember(media.uri) {
                    ImageRequest.Builder(context)
                        .data(media.uri)
                        .size(384)
                        .precision(Precision.INEXACT)
                        .memoryCacheKey(media.uri)
                        .diskCacheKey(media.uri)
                        .allowHardware(true)
                        .build()
                },
                contentDescription = media.label,
                contentScale = ContentScale.Crop,
                onSuccess = { isImageError = false },
                onError = { isImageError = true },
                success = { SubcomposeAsyncImageContent() },
                error = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSystemInDarkTheme()) {
                                    MaterialTheme.colorScheme.errorContainer.copy(0.25f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.5f),
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f),
                        )
                    }
                },
                filterQuality = FilterQuality.High,
            )
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            MediaExtensionHeader(
                modifier = Modifier
                    .padding(selectedSize.value / 2)
                    .graphicsLayer {
                        scaleX = overlayScale.value
                        scaleY = overlayScale.value
                    },
                media = media,
            )
        }

        if (media.fileSize > 0) {
            MediaSizeFooter(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(selectedSize.value / 2)
                    .graphicsLayer {
                        scaleX = overlayScale.value
                        scaleY = overlayScale.value
                        transformOrigin = TransformOrigin(0.3f, 0.5f)
                    },
                media = media,
            )
        }

        if (isInSelection) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {
                MediaCheckBox(
                    isChecked = isSelected,
                    uncheckedColor = OnMediaOverlay.copy(0.8f),
                    checkedColor = if (isImageError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    checkedIcon = if (isImageError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                    selectionIndex = selectionIndex,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            transition.animateColor(label = "mediaImageCheckboxBg") {
                                if (it) {
                                    MaterialTheme.colorScheme.surfaceContainer
                                } else {
                                    Color.Transparent
                                }
                            }.value
                        ),
                )
            }
        }
    }
}
