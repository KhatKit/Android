package heizige.kk.khatkit.app.core.ui.components.ai

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import heizige.kk.khatkit.app.core.util.ImageHelper
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiButtonVariant
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * SZ-E：纯 Compose 裁剪对话框，替代原第三方裁剪库（省掉其原生 so ≈1.1MB 与 Activity/主题依赖）。
 *
 * 能力对齐原第三方裁剪库用法：拖动/双指缩放图片、自由比例下拖四个角调整裁剪框、90° 旋转、重置、
 * 比例可选（自由 / 1:1 / 4:3 / 3:4 / 16:9）、头像场景由调用方传 aspectRatio = 1f 锁定 1:1。
 * 解码走 [ImageHelper.loadOptimizedBitmap]（带采样与长边限制）+ [ImageHelper.correctImageOrientation]（EXIF 摆正），
 * HEIF 由调用方先转 JPEG 或由 BitmapFactory 的 HEIF 支持兜底；失败时 Toast 提示并回调 onDismiss。
 *
 * 几何约定：图片按 fit 尺寸居中摆放，图层再做 scale/translation；裁剪框在**视图坐标**里，
 * 输出时把裁剪框四角反算回源图像素（除以 fitScale * scale，并减去位移）。
 */
@Composable
fun ImageCropDialog(
    image: Uri,
    aspectRatio: Float? = null,
    freeStyleCropEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var source by remember(image) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val loaded = ImageHelper.loadOptimizedBitmap(context, image, maxSize = 4096)
                    ?: return@runCatching null
                ImageHelper.correctImageOrientation(context, image, loaded)
            }.getOrNull()
        }
        if (decoded == null) {
            Toast.show("Failed to load image for cropping", isError = true)
            onDismiss()
        } else {
            source = decoded
        }
    }

    val loadedBitmap = source ?: return
    // 旋转直接换一张摆正后的源图（不叠图层旋转，避免导出时再补矩阵）
    var bitmap by remember(loadedBitmap) { mutableStateOf(loadedBitmap) }

    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var freeRect by remember(bitmap) { mutableStateOf<Rect?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MochiTextButton(onClick = onDismiss) { Text("取消") }
                Text("裁剪", color = Color.White, style = MochiTheme.textStyles.navTitle)
                MochiTextButton(onClick = {
                    bitmap = rotateBitmap(bitmap, 90)
                    scale = 1f
                    offset = Offset.Zero
                    freeRect = null
                }) { Text("旋转") }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()
                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                val fitScale = min(viewW / bw, viewH / bh)
                val shownW = bw * fitScale
                val shownH = bh * fitScale

                val effectiveAspect = aspectRatio
                val crop = remember(viewW, viewH, effectiveAspect, freeStyleCropEnabled, freeRect) {
                    freeRect ?: cropRectFor(viewW, viewH, effectiveAspect)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, crop) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset += pan
                            }
                        },
                ) {
                    Image(
                        bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dim = Color.Black.copy(alpha = 0.55f)
                        drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, crop.top))
                        drawRect(
                            dim,
                            topLeft = Offset(0f, crop.bottom),
                            size = Size(size.width, size.height - crop.bottom),
                        )
                        drawRect(dim, topLeft = Offset(0f, crop.top), size = Size(crop.left, crop.height))
                        drawRect(
                            dim,
                            topLeft = Offset(crop.right, crop.top),
                            size = Size(size.width - crop.right, crop.height),
                        )
                        // 裁剪框 + 三分构图辅助线
                        val stroke = 2.dp.toPx()
                        drawRect(
                            color = Color.White,
                            topLeft = crop.topLeft,
                            size = crop.size,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                        )
                        val guide = Color.White.copy(alpha = 0.5f)
                        for (i in 1..2) {
                            val x = crop.left + crop.width * i / 3f
                            val y = crop.top + crop.height * i / 3f
                            drawLine(guide, Offset(x, crop.top), Offset(x, crop.bottom), strokeWidth = 1f)
                            drawLine(guide, Offset(crop.left, y), Offset(crop.right, y), strokeWidth = 1f)
                        }
                    }

                    if (freeStyleCropEnabled && effectiveAspect == null) {
                        CornerHandle(crop.topLeft) { freeRect = crop.resizeFrom(FreeCorner.TopLeft, it, viewW, viewH) }
                        CornerHandle(Offset(crop.right, crop.top)) { freeRect = crop.resizeFrom(FreeCorner.TopRight, it, viewW, viewH) }
                        CornerHandle(Offset(crop.left, crop.bottom)) { freeRect = crop.resizeFrom(FreeCorner.BottomLeft, it, viewW, viewH) }
                        CornerHandle(Offset(crop.right, crop.bottom)) { freeRect = crop.resizeFrom(FreeCorner.BottomRight, it, viewW, viewH) }
                    }
                }

                // 底部：比例 + 重置 + 完成
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectChoice("自由", selected = effectiveAspect == null) {
                            freeRect = null
                        }
                        listOf(1f, 4f / 3f, 3f / 4f, 16f / 9f).forEach { a ->
                            AspectChoice(aspectLabel(a), selected = effectiveAspect == a) {
                                freeRect = null
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MochiTextButton(onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            freeRect = null
                        }) { Text("重置") }
                        MochiButton(
                            onClick = {
                                val out = cropSourceRect(
                                    crop = crop,
                                    bitmap = bitmap,
                                    viewW = viewW,
                                    viewH = viewH,
                                    fitScale = fitScale,
                                    scale = scale,
                                    offset = offset,
                                )
                                if (out.width >= 1 && out.height >= 1) {
                                    runCatching {
                                        Bitmap.createBitmap(
                                            bitmap,
                                            out.left.roundToInt().coerceIn(0, bitmap.width - 1),
                                            out.top.roundToInt().coerceIn(0, bitmap.height - 1),
                                            out.width.roundToInt().coerceIn(1, bitmap.width),
                                            out.height.roundToInt().coerceIn(1, bitmap.height),
                                        )
                                    }.onSuccess(onConfirm).onFailure {
                                        Toast.show("Failed to crop image", isError = true)
                                    }
                                }
                            },
                            variant = MochiButtonVariant.Primary,
                        ) { Text("完成") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AspectChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    MochiButton(
        onClick = onClick,
        variant = if (selected) MochiButtonVariant.Primary else MochiButtonVariant.Secondary,
    ) { Text(label) }
}

@Composable
private fun CornerHandle(position: Offset, onDrag: (Offset) -> Unit) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                translationX = position.x - 22.dp.toPx()
                translationY = position.y - 22.dp.toPx()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        drag = down.position
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val cur = change.position
                            onDrag(cur - last)
                            last = cur
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape),
        )
    }
}

private fun cropRectFor(viewW: Float, viewH: Float, aspect: Float?): Rect {
    val maxW = viewW * 0.86f
    val maxH = viewH * 0.86f
    val w: Float
    val h: Float
    if (aspect == null) {
        w = maxW
        h = maxH
    } else if (maxW / maxH > aspect) {
        h = maxH
        w = h * aspect
    } else {
        w = maxW
        h = w / aspect
    }
    val left = (viewW - w) / 2f
    val top = (viewH - h) / 2f
    return Rect(left, top, left + w, top + h)
}

private enum class FreeCorner { TopLeft, TopRight, BottomLeft, BottomRight }

private fun Rect.resizeFrom(corner: FreeCorner, delta: Offset, viewW: Float, viewH: Float): Rect {
    val minSize = 48f
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (corner) {
        FreeCorner.TopLeft -> { l = (l + delta.x).coerceIn(0f, r - minSize); t = (t + delta.y).coerceIn(0f, b - minSize) }
        FreeCorner.TopRight -> { r = (r + delta.x).coerceIn(l + minSize, viewW); t = (t + delta.y).coerceIn(0f, b - minSize) }
        FreeCorner.BottomLeft -> { l = (l + delta.x).coerceIn(0f, r - minSize); b = (b + delta.y).coerceIn(t + minSize, viewH) }
        FreeCorner.BottomRight -> { r = (r + delta.x).coerceIn(l + minSize, viewW); b = (b + delta.y).coerceIn(t + minSize, viewH) }
    }
    return Rect(l, t, r, b)
}

/** 把视图坐标的裁剪框反算成源图像素矩形（考虑 fit 缩放、用户缩放与位移）。 */
private fun cropSourceRect(
    crop: Rect,
    bitmap: Bitmap,
    viewW: Float,
    viewH: Float,
    fitScale: Float,
    scale: Float,
    offset: Offset,
): Rect {
    val cx = viewW / 2f
    val cy = viewH / 2f
    fun mapX(x: Float) = ((x - cx - offset.x) / (fitScale * scale) + bitmap.width / 2f)
    fun mapY(y: Float) = ((y - cy - offset.y) / (fitScale * scale) + bitmap.height / 2f)
    val l = mapX(crop.left).coerceIn(0f, bitmap.width - 1f)
    val t = mapY(crop.top).coerceIn(0f, bitmap.height - 1f)
    val r = mapX(crop.right).coerceIn(l + 1f, bitmap.width.toFloat())
    val b = mapY(crop.bottom).coerceIn(t + 1f, bitmap.height.toFloat())
    return Rect(l, t, r, b)
}

private fun aspectLabel(value: Float): String = when {
    value == 1f -> "1:1"
    value == 4f / 3f -> "4:3"
    value == 3f / 4f -> "3:4"
    else -> "16:9"
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
