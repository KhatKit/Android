package heizige.kk.khatkit.app.feature.splash

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.util.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动图标字形（| < _，来自 KodeHeap）的 Compose 版本。
 *
 * 绘入：开场只有 |，光标 | 从基线下滑上来后，上 2/3 像合页一样折成 <（中点左移成尖、两端甩向右），
 * 下 1/3 顺时针转 90° 落成 _。
 *
 * res/drawable/splash_animated_icon.xml 由 scripts/gen_splash_avd.py 按同一模型生成：AVD 不会转
 * pathData，只能按 [KEYFRAME_STEP] 采样逐段线性插值，这里也照样采样，系统启动页退场时覆盖层从同一
 * 毫秒接着画，交接不跳帧。改这里的常量或模型，必须同步改脚本并重新生成 XML。
 */
internal object SplashGlyph {
    /** AVD 总时长，也是主题里的 windowSplashScreenAnimationDuration。 */
    const val DRAW_IN_MILLIS = 1000

    /** AVD 画布边长。 */
    const val VIEWPORT = 512f

    /** AVD root 组把字形放大的倍数。 */
    const val ICON_SCALE = 1.5f

    /** 字形中心（512 坐标），AVD root 组的 pivot。 */
    private const val CENTER = 252f

    /** 含描边的半宽：(310.57 + 12.97 / 2) - 252。 */
    const val HALF_EXTENT = 65.06f

    private const val STROKE_WIDTH = 12.9705f

    // 开场只有 |；光标 | 从基线下方滑上来（基线以下裁掉，像从地里升起），停一拍后快速折出：
    // < 380→760ms，_ 晚 60ms 落下；都用带回弹的 [snap]。
    private const val SLIDE_START = 80
    private const val SLIDE_MILLIS = 240
    private const val SLIDE_DISTANCE = 131f
    private const val BASELINE = 317.05f
    private val SlideEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private const val FOLD_START = 380
    private const val FOLD_MILLIS = 380
    private const val UNDERSCORE_START = FOLD_START + 60
    private const val UNDERSCORE_MILLIS = 400
    private const val KEYFRAME_STEP = 20
    private const val KEYFRAME_COUNT = (UNDERSCORE_START + UNDERSCORE_MILLIS - FOLD_START) / KEYFRAME_STEP + 1
    private const val SNAP_OVERSHOOT = 1.7f

    // 开场光标：和竖杠同高同宽，上 2/3 是 <，下 1/3 是 _；两段描边重叠 1，中间不留缝。
    private const val CURSOR_X = 280f
    private const val HALF_W = 6.705f
    private const val TOP = 193.43f
    private const val BOTTOM = 310.57f
    private const val CHEVRON_BOTTOM = 267.2f
    private const val HINGE_Y = 230.32f
    private const val L = CURSOR_X - HALF_W
    private const val R = CURSOR_X + HALF_W
    private const val UNDERSCORE_TOP = CHEVRON_BOTTOM + STROKE_WIDTH - 1f

    private val chevronStart = floatArrayOf(
        L, TOP, L, HINGE_Y, L, HINGE_Y, L, CHEVRON_BOTTOM, R, CHEVRON_BOTTOM, R, HINGE_Y, R, TOP,
    )
    private val chevronEnd = floatArrayOf(
        310.57f, 197.14f, 249.78f, 225.95f, 249.78f, 234.28f, 310.57f, 259.76f,
        310.57f, 245.83f, 268.7f, 229.52f, 310.57f, 211.07f,
    )
    private val underscoreStart = floatArrayOf(L, BOTTOM, R, BOTTOM, R, UNDERSCORE_TOP, L, UNDERSCORE_TOP)
    private val underscoreEnd = floatArrayOf(249.87f, 302.44f, 249.87f, 310.48f, 310.48f, 310.48f, 310.48f, 302.44f)

    /** 刚体段：[vertices] 跟着原点从 start 平移、角度从 start 转到 end，段内局部坐标同时插值。 */
    private class Piece(
        val vertices: IntArray,
        val originStart: Offset,
        val degStart: Float,
        val originEnd: Offset,
        val degEnd: Float,
    )

    private val hingeStart = Offset(CURSOR_X, HINGE_Y)
    private val hingeEnd = Offset(254.05f, 229.94f)
    private val upperArm = Piece(intArrayOf(0, 1, 6, 5), hingeStart, -90f, hingeEnd, degrees(204.1f, 310.57f))
    private val lowerArm = Piece(intArrayOf(2, 3, 4, 5), hingeStart, 90f, hingeEnd, degrees(252.8f, 310.57f))
    private val underscorePiece = Piece(
        intArrayOf(0, 1, 2, 3),
        Offset(CURSOR_X, (UNDERSCORE_TOP + BOTTOM) / 2f), -90f,
        Offset(280.175f, 306.46f), 0f,
    )

    private fun degrees(y: Float, x: Float): Float =
        Math.toDegrees(atan2((y - hingeEnd.y).toDouble(), (x - hingeEnd.x).toDouble())).toFloat()

    private val chevronFrames: List<FloatArray> by lazy { keyframes(FOLD_START, FOLD_MILLIS, ::chevronAt) }
    private val underscoreFrames: List<FloatArray> by lazy {
        keyframes(UNDERSCORE_START, UNDERSCORE_MILLIS, ::underscoreAt)
    }

    private val bar = PathParser().parsePathString("M193.43,193.43L193.43,310.57L206.84,310.57L206.84,193.43Z").toPath()
    private val chevronPath = Path()
    private val underscorePath = Path()
    private val stroke = Stroke(width = STROKE_WIDTH)
    private val layerPaint = Paint()

    private fun keyframes(start: Int, duration: Int, shape: (Float) -> FloatArray): List<FloatArray> =
        List(KEYFRAME_COUNT) { k ->
            val t = ((FOLD_START + k * KEYFRAME_STEP - start).toFloat() / duration).coerceIn(0f, 1f)
            shape(snap(t))
        }

    /** easeOutBack：起手最快、冲过终点约 10% 再回落，和脚本里的 snap 相同。 */
    private fun snap(t: Float): Float {
        val u = t - 1f
        return 1f + (SNAP_OVERSHOOT + 1f) * u * u * u + SNAP_OVERSHOOT * u * u
    }

    private fun Piece.place(from: FloatArray, to: FloatArray, i: Int, f: Float): Offset {
        val (along0, across0) = local(from, i, originStart, degStart)
        val (along1, across1) = local(to, i, originEnd, degEnd)
        val along = lerp(along0, along1, f)
        val across = lerp(across0, across1, f)
        val rad = Math.toRadians(lerp(degStart, degEnd, f).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val origin = androidx.compose.ui.geometry.lerp(originStart, originEnd, f)
        return Offset(origin.x + along * c - across * s, origin.y + along * s + across * c)
    }

    private fun local(v: FloatArray, i: Int, origin: Offset, deg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = v[2 * i] - origin.x
        val dy = v[2 * i + 1] - origin.y
        return (dx * c + dy * s) to (-dx * s + dy * c)
    }

    private fun chevronAt(f: Float): FloatArray {
        val out = FloatArray(chevronEnd.size)
        for (arm in arrayOf(upperArm, lowerArm)) {
            for (i in arm.vertices) {
                val p = arm.place(chevronStart, chevronEnd, i, f)
                if (i == 5) {
                    // 尖内侧两臂共用，取平均
                    out[10] += p.x / 2f
                    out[11] += p.y / 2f
                } else {
                    out[2 * i] = p.x
                    out[2 * i + 1] = p.y
                }
            }
        }
        return out
    }

    private fun underscoreAt(f: Float): FloatArray {
        val out = FloatArray(underscoreEnd.size)
        for (i in underscorePiece.vertices) {
            val p = underscorePiece.place(underscoreStart, underscoreEnd, i, f)
            out[2 * i] = p.x
            out[2 * i + 1] = p.y
        }
        return out
    }

    /** 和 AVD 一样：在相邻关键帧之间逐顶点线性插值。 */
    private fun Path.setFrame(frames: List<FloatArray>, millis: Float) {
        val pos = ((millis - FOLD_START) / KEYFRAME_STEP).coerceIn(0f, (KEYFRAME_COUNT - 1).toFloat())
        val k = pos.toInt().coerceAtMost(KEYFRAME_COUNT - 2)
        val f = pos - k
        val a = frames[k]
        val b = frames[k + 1]
        rewind()
        for (i in 0 until a.size / 2) {
            val x = lerp(a[2 * i], b[2 * i], f)
            val y = lerp(a[2 * i + 1], b[2 * i + 1], f)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /**
     * @param center 字形中心（像素）。
     * @param unit 每个 512 坐标单位对应的像素（已含 [ICON_SCALE]）。
     * @param millis 绘入进度，0..[DRAW_IN_MILLIS]。
     * @param cursorAlpha 下划线（光标）的额外透明度，用来做闪烁。
     */
    fun DrawScope.drawSplashGlyph(
        center: Offset,
        unit: Float,
        millis: Float,
        color: Color,
        alpha: Float = 1f,
        cursorAlpha: Float = 1f,
        rotation: Float = 0f,
    ) {
        if (alpha <= 0f || unit <= 0f) return
        val half = HALF_EXTENT * unit * 1.5f
        val needsLayer = alpha < 1f
        if (needsLayer) {
            // 填充和描边叠在一起，直接给半透明会在描边处变深；整体走一层 alpha。
            layerPaint.alpha = alpha
            drawIntoCanvas {
                it.saveLayer(Rect(center.x - half, center.y - half, center.x + half, center.y + half), layerPaint)
            }
        }
        withTransform({
            translate(center.x, center.y)
            rotate(rotation, pivot = Offset.Zero)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-CENTER, -CENTER)
        }) {
            drawPart(bar, color, 1f)
            chevronPath.setFrame(chevronFrames, millis)
            underscorePath.setFrame(underscoreFrames, millis)
            if (millis < FOLD_START) {
                // 滑入期间裁到基线；折叠开始后 _ 转动会扫到基线下面，不再裁。
                val slide = SlideEasing.transform(((millis - SLIDE_START) / SLIDE_MILLIS).coerceIn(0f, 1f))
                clipRect(left = 0f, top = 0f, right = VIEWPORT, bottom = BASELINE) {
                    translate(top = SLIDE_DISTANCE * (1f - slide)) { drawCursor(color, cursorAlpha) }
                }
            } else {
                drawCursor(color, cursorAlpha)
            }
        }
        if (needsLayer) drawIntoCanvas { it.restore() }
    }

    private fun DrawScope.drawCursor(color: Color, cursorAlpha: Float) {
        drawPart(chevronPath, color, 1f)
        if (cursorAlpha > 0f) drawPart(underscorePath, color, cursorAlpha)
    }

    private fun DrawScope.drawPart(path: Path, color: Color, alpha: Float) {
        drawPath(path, color, alpha)
        drawPath(path, color, alpha, style = stroke)
    }
}
