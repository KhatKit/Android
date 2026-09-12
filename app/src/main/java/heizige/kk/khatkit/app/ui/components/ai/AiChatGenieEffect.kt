package heizige.kk.khatkit.app.ui.components.ai

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
