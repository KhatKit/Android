package heizige.kk.khatkit.app.core.ui.components.ai

import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withMatrix
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Kumo/macOS-style Genie deformation from the measured floating toolbar bounds.
 * The horizontal bands let the top of the AI surface open before its lower edge.
 */
@Composable
internal fun Modifier.aiChatGenieEffect(
    progress: Float,
    collapsedBounds: Rect,
    contentLayer: GraphicsLayer,
    maxBandCount: Int = 44,
    collapseFromCenter: Boolean = false,
): Modifier {
    // Reuse the native transform buffers across draw passes. The reveal is driven every
    // frame, so allocating these inside drawWithContent quickly turns into visible GC work.
    val destination = remember { FloatArray(8) }
    val sourcePoints = remember { FloatArray(8) }
    val matrix = remember { Matrix() }
    return drawWithContent {
        val openProgress = progress.coerceIn(0f, 1f)
        if (openProgress >= 0.999f) {
            drawContent()
            return@drawWithContent
        }
        if (
            openProgress <= 0.001f ||
            size.width <= 0f ||
            size.height <= 0f ||
            collapsedBounds.width <= 0f ||
            collapsedBounds.height <= 0f
        ) {
            return@drawWithContent
        }

        val canvas = drawContext.canvas.nativeCanvas
        // Detail peaks in the visibly curved middle; the narrow start and nearly-flat end
        // need fewer samples. This cuts the previous 96 full-layer draws by more than half.
        val curvature = 1f - abs(openProgress * 2f - 1f)
        val bands = aiLerp(28f, maxBandCount.coerceAtLeast(36).toFloat(), curvature)
            .toInt()
        val content = this
        contentLayer.record { content.drawContent() }
        // Overscan in both source and destination coordinates. Clipping only a wider source
        // strip still leaves transformed sub-pixel gaps; evaluating the adjacent boundaries
        // outside each band's nominal range makes their destination quads actually overlap.
        val bandOverscan = (1.5f / size.height).coerceAtMost(0.25f)

        repeat(bands) { index ->
            val start = index.toFloat() / bands
            val end = (index + 1f) / bands
            val sourceStart = (start - bandOverscan).coerceAtLeast(0f)
            val sourceEnd = (end + bandOverscan).coerceAtMost(1f)
            val sourceTop = sourceStart * size.height
            val sourceBottom = sourceEnd * size.height
            val top = aiGenieBoundary(
                sourceStart, openProgress, size.width, size.height, collapsedBounds, collapseFromCenter
            )
            val bottom = aiGenieBoundary(
                sourceEnd, openProgress, size.width, size.height, collapsedBounds, collapseFromCenter
            )

            sourcePoints[0] = 0f
            sourcePoints[1] = sourceTop
            sourcePoints[2] = size.width
            sourcePoints[3] = sourceTop
            sourcePoints[4] = size.width
            sourcePoints[5] = sourceBottom
            sourcePoints[6] = 0f
            sourcePoints[7] = sourceBottom

            destination[0] = top.left
            destination[1] = top.y
            destination[2] = top.right
            destination[3] = top.y
            destination[4] = bottom.right
            destination[5] = bottom.y
            destination[6] = bottom.left
            destination[7] = bottom.y

            if (matrix.setPolyToPoly(sourcePoints, 0, destination, 0, 4)) {
                canvas.withMatrix(matrix) {
                    clipRect(0f, sourceTop, size.width, sourceBottom)
                    drawLayer(contentLayer)
                }
            }
        }
    }
}

private data class AiGenieBoundary(
    val left: Float,
    val right: Float,
    val y: Float,
)

private fun aiGenieBoundary(
    position: Float,
    progress: Float,
    width: Float,
    height: Float,
    collapsedBounds: Rect,
    collapseFromCenter: Boolean = false,
): AiGenieBoundary {
    val roundedPosition = aiSmootherStep(position)
    val exponent = 0.58f + roundedPosition * 1.34f
    val localProgress = aiSmootherStep(progress.pow(exponent))
    val collapsedWidth = if (collapseFromCenter) 0f else collapsedBounds.width
    val boundaryWidth = aiLerp(collapsedWidth, width, localProgress)
    val center = aiLerp(collapsedBounds.center.x, width / 2f, localProgress)
    val collapsedY = if (collapseFromCenter) {
        collapsedBounds.center.y
    } else {
        aiLerp(collapsedBounds.top, collapsedBounds.bottom, position)
    }
    val y = aiLerp(collapsedY, position * height, localProgress)

    return AiGenieBoundary(
        left = center - boundaryWidth / 2f,
        right = center + boundaryWidth / 2f,
        y = y,
    )
}

/**
 * Compensates for the Genie's non-uniform X/Y scaling so the visible destination corner
 * starts as the FLB's CircleShape radius instead of a vertically crushed source radius.
 * [bottomDestinationCornerRadiusPx] defaults to the shared radius, letting callers whose
 * bottom edge must stay tangent to another rounded element (e.g. the category capsule)
 * derive it from measured bounds.
 */
internal fun aiChatGenieWindowShape(
    progress: Float,
    collapsedBounds: Rect,
    destinationCornerRadiusPx: Float,
    bottomDestinationCornerRadiusPx: Float = destinationCornerRadiusPx,
    collapseFromCenter: Boolean = false,
): Shape = AiChatGenieWindowShape(
    progress = progress.coerceIn(0f, 1f),
    collapsedBounds = collapsedBounds,
    destinationCornerRadiusPx = destinationCornerRadiusPx.coerceAtLeast(0f),
    bottomDestinationCornerRadiusPx = bottomDestinationCornerRadiusPx.coerceAtLeast(0f),
    collapseFromCenter = collapseFromCenter
)

private data class AiChatGenieWindowShape(
    val progress: Float,
    val collapsedBounds: Rect,
    val destinationCornerRadiusPx: Float,
    val bottomDestinationCornerRadiusPx: Float,
    val collapseFromCenter: Boolean
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }

        val top = aiGenieBoundary(
            0f, progress, size.width, size.height, collapsedBounds, collapseFromCenter
        )
        val topSamplePosition = 1f / 512f
        val topSample = aiGenieBoundary(
            topSamplePosition, progress, size.width, size.height, collapsedBounds, collapseFromCenter
        )
        val bottom = aiGenieBoundary(
            1f, progress, size.width, size.height, collapsedBounds, collapseFromCenter
        )
        val bottomSamplePosition = 1f - topSamplePosition
        val bottomSample = aiGenieBoundary(
            bottomSamplePosition, progress, size.width, size.height, collapsedBounds, collapseFromCenter
        )

        fun sourceRadii(
            boundaryWidth: Float,
            verticalScale: Float,
            destinationRadiusPx: Float = destinationCornerRadiusPx
        ): Pair<Float, Float> {
            val horizontalScale = (boundaryWidth / size.width).coerceAtLeast(0.0001f)
            return (destinationRadiusPx / horizontalScale).coerceIn(0f, size.width / 2f) to
                (destinationRadiusPx / verticalScale.coerceAtLeast(0.0001f))
                    .coerceIn(0f, size.height / 2f)
        }

        val topVerticalScale =
            (topSample.y - top.y) / (topSamplePosition * size.height)
        val bottomVerticalScale =
            (bottom.y - bottomSample.y) / ((1f - bottomSamplePosition) * size.height)
        val (topRadiusX, topRadiusY) = sourceRadii(top.right - top.left, topVerticalScale)
        val (bottomRadiusX, bottomRadiusY) = sourceRadii(
            bottom.right - bottom.left,
            bottomVerticalScale,
            bottomDestinationCornerRadiusPx
        )
        val kappa = 0.5522848f

        val path = Path().apply {
            moveTo(0f, topRadiusY)
            cubicTo(
                0f,
                topRadiusY * (1f - kappa),
                topRadiusX * (1f - kappa),
                0f,
                topRadiusX,
                0f
            )
            lineTo(size.width - topRadiusX, 0f)
            cubicTo(
                size.width - topRadiusX * (1f - kappa),
                0f,
                size.width,
                topRadiusY * (1f - kappa),
                size.width,
                topRadiusY
            )
            lineTo(size.width, size.height - bottomRadiusY)
            cubicTo(
                size.width,
                size.height - bottomRadiusY * (1f - kappa),
                size.width - bottomRadiusX * (1f - kappa),
                size.height,
                size.width - bottomRadiusX,
                size.height
            )
            lineTo(bottomRadiusX, size.height)
            cubicTo(
                bottomRadiusX * (1f - kappa),
                size.height,
                0f,
                size.height - bottomRadiusY * (1f - kappa),
                0f,
                size.height - bottomRadiusY
            )
            close()
        }
        return Outline.Generic(path)
    }
}

private fun aiSmootherStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * clamped *
        (clamped * (clamped * 6f - 15f) + 10f)
}

private fun aiLerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

/**
 * FLB 落定时长：满行程固定 760ms，按实际距离线性缩放，下限 90ms。
 * 与 KodeHeap 帖子详情页 FLB 同一规格（Post.kt:3181-3193 `aiWindowSettleDurationMillis`）。
 */
internal fun aiWindowSettleDurationMillis(
    start: Float,
    target: Float,
    fullDurationMillis: Int = 760,
    minimumDurationMillis: Int = 90,
): Int {
    val distance = abs(
        target.coerceIn(0f, 1f) - start.coerceIn(0f, 1f)
    ).coerceIn(0f, 1f)
    if (distance <= 0.001f) return 0
    return (fullDurationMillis * distance).roundToInt()
        .coerceIn(minimumDurationMillis, fullDurationMillis)
}

/**
 * 屏幕圆角与 2dp 边框共用的末段曲线：揭示进度过 [morphStartProgress]（默认 0.88）后才开始形变。
 * 与 KodeHeap `aiWindowMorphProgress`（Post.kt:3209-3217）逐式相同。
 */
internal fun aiWindowMorphProgress(
    revealProgress: Float,
    morphStartProgress: Float = 0.88f,
): Float {
    val start = morphStartProgress.coerceIn(0f, 0.999f)
    val linearMorph = ((revealProgress.coerceIn(0f, 1f) - start) / (1f - start))
        .coerceIn(0f, 1f)
    return linearMorph * linearMorph * (3f - 2f * linearMorph)
}

/**
 * 上滑 morph 的淡入/淡出行程：reveal 从 0 走到 [AI_FLB_TOOLBAR_FADE_RANGE] 时，
 * 输入胶囊（含 FAB）淡到 0、编辑器底部工具栏同期淡到 1。
 * 两条曲线严格互补 ⇒ 任一时刻屏幕底部只有一条底栏可见。
 */
internal const val AI_FLB_TOOLBAR_FADE_RANGE = 0.25f

/**
 * Scaffold 里输入胶囊那一行（ActionIconButton + TextInputRow）与右侧 FAB 的 alpha：
 * `1 - smoothstep(reveal / 0.25)`，reveal ≤ 0 时为 1（静止态原样），reveal ≥ 0.25 时为 0。
 */
internal fun aiFlbCapsuleAlpha(revealProgress: Float): Float {
    val t = (revealProgress.coerceIn(0f, 1f) / AI_FLB_TOOLBAR_FADE_RANGE).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

/**
 * 编辑器底部工具栏的 alpha：`smoothstep(reveal / 0.25)`，与 [aiFlbCapsuleAlpha] 互补
 * （两者和为 1，且都 >0.5 的 reveal 区间互不相交）。reveal = 0 时为 0 ⇒ 收起态无工具栏残留。
 */
internal fun aiFlbToolbarAlpha(revealProgress: Float): Float =
    1f - aiFlbCapsuleAlpha(revealProgress)

/**
 * 工具栏 morph 的几何与底色：[collapsedBounds]（输入胶囊 pill）→ [targetBounds]（编辑器最终
 * 工具栏矩形）。位置、宽高、圆角、底色 alpha 全部由同一个 [fraction] 线性插值。
 *
 * [fraction] 是**唯一的插值参数**：随 reveal 单调非降、被 clamp 在 [0, 1]，因此每个分量都随
 * reveal 单调（端点值更大则非降，更小则非增），不会有中段回摆。
 */
internal data class AiFlbToolbarMorph(
    val fraction: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadiusPx: Float,
    val containerAlpha: Float,
)

/**
 * 上滑时编辑器底部工具栏的几何：reveal = 0 精确等于 [collapsedBounds]（胶囊 pill 矩形，
 * 圆角 = pill = 较短边一半），reveal = 1 精确等于 [targetBounds]（改动前的最终工具栏矩形，
 * 圆角 = 18dp 对应 px，底色 alpha = 0.96）。中间段与窗口形变共用同一条末段曲线
 * （[aiWindowMorphProgress]），所以工具栏在窗口铺满前一直贴在窗口底边、不会悬空多出一条底栏。
 *
 * [revealProgress] 在 [0, 1] 外先 clamp；[AiFlbToolbarMorph.fraction] 单调非降。
 */
internal fun aiFlbToolbarMorph(
    revealProgress: Float,
    collapsedBounds: Rect,
    targetBounds: Rect,
    collapsedCornerRadiusPx: Float,
    targetCornerRadiusPx: Float,
    collapsedContainerAlpha: Float = 1f,
    targetContainerAlpha: Float = 0.96f,
): AiFlbToolbarMorph {
    val fraction = aiWindowMorphProgress(revealProgress)
    // 端点直接返回端点值（不做 lerp），保证 t=0/t=1 逐位精确、无浮点残差。
    fun morph(start: Float, end: Float): Float = when {
        fraction <= 0f -> start
        fraction >= 1f -> end
        else -> aiLerp(start, end, fraction)
    }
    return AiFlbToolbarMorph(
        fraction = fraction,
        left = morph(collapsedBounds.left, targetBounds.left),
        top = morph(collapsedBounds.top, targetBounds.top),
        width = morph(collapsedBounds.width, targetBounds.width),
        height = morph(collapsedBounds.height, targetBounds.height),
        cornerRadiusPx = morph(collapsedCornerRadiusPx, targetCornerRadiusPx),
        containerAlpha = morph(collapsedContainerAlpha, targetContainerAlpha),
    )
}

/**
 * 揭示窗口的窗口圆角：起始 = FLB 的 CircleShape 解析半径（较短边一半），
 * 末段 morph 到屏幕物理圆角（KodeHeap `aiWindowCornerRadiusPx`，Post.kt:3196-3206）。
 */
internal fun aiWindowCornerRadiusPx(
    toolbarWidthPx: Float,
    toolbarHeightPx: Float,
    revealProgress: Float,
    screenCornerRadiusPx: Float = 0f,
    morphStartProgress: Float = 0.88f,
): Float {
    val toolbarRadius = minOf(toolbarWidthPx, toolbarHeightPx).coerceAtLeast(0f) / 2f
    val smoothMorph = aiWindowMorphProgress(revealProgress, morphStartProgress)
    return toolbarRadius + (screenCornerRadiusPx.coerceAtLeast(0f) - toolbarRadius) * smoothMorph
}

/**
 * 上滑位移 → AI 页揭示进度。线性到 1 后橡皮筋过冲，最高 [maxOvershoot]。
 * 与 KodeHeap `composerAiRevealProgressFromDrag`（Post.kt:3132-3143）逐式相同。
 */
internal fun composerAiRevealProgressFromDrag(
    rawY: Float,
    commitDistance: Float,
    maxOvershoot: Float = 1.12f,
): Float {
    if (commitDistance <= 0f) return 0f
    val up = (-rawY).coerceAtLeast(0f)
    val linear = up / commitDistance
    if (linear <= 1f) return linear
    val over = linear - 1f
    return (1f + (over / (1f + over * 3f)) * (maxOvershoot - 1f)).coerceAtMost(maxOvershoot)
}

/**
 * 松手是否提交打开编辑器：进度达到阈值（默认 0.35）即播完，否则收回。
 * 过冲（>1）视为已提交（KodeHeap `composerAiRevealCommitOnRelease`，Post.kt:3223-3226）。
 */
internal fun composerAiRevealCommitOnRelease(
    progress: Float,
    commitThreshold: Float = 0.35f,
): Boolean = progress >= commitThreshold
