/*
 * 全屏图片预览（对应 ImageToolbox `MediaImagePager`，Apache-2.0, T8RIN）。
 *
 * 上游用 `net.engawapg.lib.zoomable` + 自研直方图/元数据按钮；这里复用宿主已装的
 * `com.jvziyaoyao.scale`（见 app 的 ImagePreviewDialog），保留同样的交互外形：
 * 上滑关闭、点按隐藏控件、顶栏页码 + 勾选、底栏文件名 + 体积。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.PagerGestureScope
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.kedge.theme.KedgeTextStyles

/** 上滑关闭的阈值（px）。 */
private const val DISMISS_DRAG_THRESHOLD = 240f

@Composable
internal fun MediaImagePager(
    previewUri: String?,
    media: List<Media>,
    selectedMedia: SnapshotStateList<Media>,
    onMediaClick: (Media) -> Unit,
    onDismiss: () -> Unit,
) {
    if (previewUri == null || media.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        MediaImagePagerContent(
            initialUri = previewUri,
            media = media,
            selectedMedia = selectedMedia,
            onMediaClick = onMediaClick,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun MediaImagePagerContent(
    initialUri: String,
    media: List<Media>,
    selectedMedia: SnapshotStateList<Media>,
    onMediaClick: (Media) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialPage = remember(initialUri, media) {
        media.indexOfFirst { it.uri == initialUri }.coerceAtLeast(0)
    }
    val pagerState = rememberZoomablePagerState(initialPage = initialPage) { media.size }

    var hideControls by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // 列表变短时把页码夹回范围内，否则 pager 的 currentPage 会停在越界位置
    LaunchedEffect(media.size) {
        if (pagerState.currentPage >= media.size) {
            pagerState.scrollToPage((media.size - 1).coerceAtLeast(0))
        }
    }

    val currentMedia = media.getOrNull(pagerState.currentPage)
    val currentSelected = currentMedia != null && selectedMedia.contains(currentMedia)

    // 缩放中或翻页中不接管手势，否则会和 pager 的拖拽打架
    val zoomScale by remember(pagerState) {
        derivedStateOf { pagerState.zoomableViewState.value?.scale?.value ?: 1f }
    }
    val canDragDismiss = zoomScale < 1.01f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(canDragDismiss) {
                if (!canDragDismiss) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { _, amount ->
                        dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        if (dragOffset > DISMISS_DRAG_THRESHOLD) onDismiss()
                    },
                )
            },
    ) {
        // 拖拽时整页轻微缩小 + 露出黑底，提示"松手就关"
        val dismissScale = 1f - (dragOffset / DISMISS_DRAG_THRESHOLD).coerceIn(0f, 1f) * 0.25f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = dismissScale
                    scaleY = dismissScale
                },
        ) {
            ImagePager(
                modifier = Modifier.fillMaxSize(),
                pagerState = pagerState,
                detectGesture = PagerGestureScope(onTap = { hideControls = !hideControls }),
                imageLoader = { index ->
                    // media 会随 MediaStore 变更重新 emit，变短时 currentPage 可能已越界
                    val uri = media.getOrNull(index)?.uri.orEmpty()
                    val painter = rememberAsyncImagePainter(uri)
                    painter to painter.intrinsicSize
                },
            )
        }

        val controlsVisible = dragOffset == 0f && !hideControls

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.media_picker_exit),
                    tint = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                        .padding(6.dp),
                )
                if (media.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${media.size}",
                        color = Color.White,
                        style = KedgeTextStyles.bodyLarge(),
                    )
                }
                MediaCheckBox(
                    isChecked = currentSelected,
                    onCheck = { currentMedia?.let(onMediaClick) },
                    uncheckedColor = Color.White,
                    addContainer = currentSelected,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && currentMedia != null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .navigationBarsPadding()
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                currentMedia?.let { item ->
                    Text(
                        text = item.label,
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color.White,
                        style = KedgeTextStyles.body(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.fileSize > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = item.humanFileSize,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = KedgeTextStyles.footnoteSmall(),
                            )
                        }
                    }
                }
            }
        }
    }
}
