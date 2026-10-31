package heizige.kk.khatkit.app.feature.splash

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.theme.GoogleSansFlex
import heizige.kk.khatkit.app.feature.splash.SplashGlyph.drawSplashGlyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** 系统启动页退场时交给覆盖层的东西：图标在窗口里的位置，以及 AVD 已经播到第几毫秒。 */
internal class SplashHandoff(val iconBounds: Rect?, val drawInMillis: Float)

/**
 * 冷启动品牌动画的状态。
 *
 * API 31+：系统 SplashScreen 播字形绘入（splash_animated_icon.xml），首帧就绪时退场，
 * 位置和进度交接给 [SplashIntroHost] 的覆盖层，覆盖层画好同一帧后才移除系统启动页，交接无跳帧。
 * API 31 以下没有系统启动页，覆盖层从头自己画。
 */
@Stable
class SplashIntroState(active: Boolean) {
    var active by mutableStateOf(active)
        private set

    internal var handoff by mutableStateOf<SplashHandoff?>(null)
        private set

    private var waitingForHandoff = active
    private var removeSplashView: (() -> Unit)? = null

    /** 在 Activity.onCreate 里调用；不需要动画时什么都不做，系统启动页走默认退场。 */
    fun attach(activity: Activity) {
        if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreenApi31.attach(activity, this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun onSplashExit(view: SplashScreenView) {
        if (!waitingForHandoff) {
            view.remove()
            return
        }
        // iconView 是圆形遮罩（无图标底色时 192dp），图标 drawable 按 1.5 倍（288dp）居中绘制，
        // 遮罩只裁掉 drawable 外圈 —— 真机量过：字形像素宽度 = iconView 宽 × 1.5 换算的结果。
        val bounds = view.iconView?.let { icon ->
            val location = IntArray(2)
            icon.getLocationInWindow(location)
            val center = Offset(location[0] + icon.width / 2f, location[1] + icon.height / 2f)
            Rect(center, icon.width * ICON_DRAWABLE_TO_MASK / 2f)
        }
        val elapsed = view.iconAnimationStart?.toEpochMilli()?.let(::elapsedSinceIconStart)
            ?: SplashGlyph.DRAW_IN_MILLIS.toLong()
        val drawIn = elapsed.toFloat().coerceIn(0f, SplashGlyph.DRAW_IN_MILLIS.toFloat())
        handoff = SplashHandoff(bounds, drawIn)
        if (drawIn >= SplashGlyph.DRAW_IN_MILLIS) {
            // 字形已画完：覆盖层一开始就垫着完整字形，画面一致，立即移除。部分 ROM（ColorOS 实测）
            // 回调时已撤掉图标 surface，再拖两帧会露出一帧只有底色的启动页。
            view.remove()
        } else {
            removeSplashView = { view.remove() }
        }
    }

    /** 覆盖层已经接住（或不再等交接）：移除系统启动页，之后再来的退场回调直接移除。 */
    internal fun stopWaitingForHandoff() {
        waitingForHandoff = false
        removeSplashView?.invoke()
        removeSplashView = null
    }

    internal fun finish() {
        stopWaitingForHandoff()
        active = false
    }
}

/**
 * AOSP 往 Instant 里塞的是 uptimeMillis（androidx core-splashscreen 也这么读），
 * ColorOS 实测是墙钟时间。哪个时钟算出来的差值落在合理区间就用哪个。
 */
private fun elapsedSinceIconStart(startMillis: Long): Long? =
    listOf(SystemClock.uptimeMillis(), System.currentTimeMillis())
        .map { it - startMillis }
        .firstOrNull { it in 0..60_000 }

@RequiresApi(Build.VERSION_CODES.S)
private object SplashScreenApi31 {
    fun attach(activity: Activity, state: SplashIntroState) {
        activity.splashScreen.setOnExitAnimationListener { view -> state.onSplashExit(view) }
    }
}

// 首帧画出来系统才会回调退场；重页面冷启动时首帧可能要 2s 以上，等久一点。
private const val HANDOFF_TIMEOUT_MILLIS = 5000L
/** 启动图标 drawable 边长与圆形遮罩直径之比（288dp / 192dp，带底色时 240dp / 160dp）。 */
private const val ICON_DRAWABLE_TO_MASK = 1.5f
private const val FALLBACK_ICON_SIZE_DP = 288
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val RevealEasing = CubicBezierEasing(0.6f, 0f, 0.25f, 1f)

/**
 * 包住 App 内容；[SplashIntroState.active] 期间在上面盖一层启动动画：
 * 字形绘入 → 字形上移、名称与版本淡入 → 光标闪一下 → 字形收成圆环 → 圆环化成水波形状向外扩散揭开内容。
 */
@Composable
fun SplashIntroHost(state: SplashIntroState, content: @Composable () -> Unit) {
    val reveal = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.active) {
                        Modifier.graphicsLayer {
                            val s = lerp(0.94f, 1f, reveal.value)
                            scaleX = s
                            scaleY = s
                        }
                    } else {
                        Modifier
                    }
                ),
            propagateMinConstraints = true,
        ) {
            content()
        }
        if (state.active) {
            SplashIntroOverlay(state, reveal)
        }
    }
}

@Composable
private fun SplashIntroOverlay(state: SplashIntroState, reveal: Animatable<Float, *>) {
    val density = LocalDensity.current
    val background = colorResource(R.color.splash_background)
    val foreground = colorResource(R.color.splash_foreground)
    val contentColor = colorResource(R.color.splash_content)

    // API 31+ 交接前覆盖层被系统启动页盖着，先垫一个完整字形：部分 ROM（ColorOS 实测）在退场回调时
    // 先撤掉图标 surface，覆盖层若是空的会闪一帧底色。31 以下没有系统启动页，从头画。
    val drawIn = remember {
        Animatable(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SplashGlyph.DRAW_IN_MILLIS.toFloat() else 0f)
    }
    val brand = remember { Animatable(0f) }
    val cursor = remember { Animatable(1f) }
    val morph = remember { Animatable(0f) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    val maskPath = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    val ringPath = remember { Path() }

    LaunchedEffect(state) {
        val handoff = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            withTimeoutOrNull(HANDOFF_TIMEOUT_MILLIS) {
                snapshotFlow { state.handoff }.filterNotNull().first()
            }
        } else {
            null
        }
        // 没等到交接就保持初始值（31+ 完整字形 / 31 以下从头画）。
        handoff?.let { drawIn.snapTo(it.drawInMillis) }
        // 等覆盖层按交接位置画出一帧，再移除系统启动页。
        withFrameNanos {}
        withFrameNanos {}
        state.stopWaitingForHandoff()

        val remaining = SplashGlyph.DRAW_IN_MILLIS - drawIn.value
        if (remaining > 0f) {
            drawIn.animateTo(
                SplashGlyph.DRAW_IN_MILLIS.toFloat(),
                tween(remaining.roundToInt(), easing = LinearEasing),
            )
        }
        brand.animateTo(1f, tween(520, easing = EmphasizedDecelerate))
        cursor.animateTo(0.1f, tween(120))
        cursor.animateTo(1f, tween(120))
        delay(100)
        morph.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        reveal.animateTo(1f, tween(680, easing = RevealEasing))
        state.finish()
    }

    // 下面这些都在 draw / layout 阶段读动画值，动画过程不触发重组。
    fun iconBounds(): Rect {
        state.handoff?.iconBounds?.let { return it.translate(-windowOffset) }
        val size = with(density) { FALLBACK_ICON_SIZE_DP.dp.toPx() }
        return Rect(Offset(overlaySize.width / 2f, overlaySize.height / 2f) - Offset(size / 2f, size / 2f), Size(size, size))
    }

    fun baseUnit(): Float = iconBounds().width / SplashGlyph.VIEWPORT * SplashGlyph.ICON_SCALE

    fun glyphScale(): Float = lerp(1f, 0.72f, brand.value)

    fun glyphCenter(): Offset {
        val lift = with(density) { 56.dp.toPx() } * brand.value
        return iconBounds().center - Offset(0f, lift)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                windowOffset = it.positionInWindow()
                overlaySize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
            .pointerInput(Unit) {
                // 动画期间吞掉触摸，避免点穿到下面还没露出来的页面。
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = glyphCenter()
            val unit = baseUnit() * glyphScale()
            val glyphRadius = SplashGlyph.HALF_EXTENT * unit
            val m = morph.value
            val r = reveal.value

            val ringRestRadius = glyphRadius * 1.3f
            val holeRadius = lerp(0f, farthestCorner(center, size) * 1.15f, r)
            val wobble = 0.1f * sin(PI.toFloat() * r) + 0.03f * sin(PI.toFloat() * m)
            val phase = (m + r) * PI.toFloat() * 1.5f

            if (holeRadius < 1f) {
                drawRect(background)
            } else {
                maskPath.rewind()
                maskPath.addRect(Rect(Offset.Zero, size))
                maskPath.appendBlob(center, holeRadius, wobble, phase)
                drawPath(maskPath, background)
            }

            // 字形背后一圈很淡的光晕，随品牌阶段亮起、揭幕时散掉。
            val glowAlpha = 0.14f * brand.value * (1f - r)
            if (glowAlpha > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(foreground.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = glyphRadius * 3.2f,
                    ),
                    radius = glyphRadius * 3.2f,
                    center = center,
                )
            }

            drawSplashGlyph(
                center = center,
                unit = unit * lerp(1f, 0.5f, m),
                millis = drawIn.value,
                color = foreground,
                alpha = 1f - m,
                cursorAlpha = cursor.value,
                rotation = -40f * m,
            )

            val ringAlpha = m * (1f - ((r - 0.5f) / 0.5f).coerceIn(0f, 1f))
            if (ringAlpha > 0f) {
                val ringRadius = max(ringRestRadius * lerp(0.35f, 1f, m), holeRadius)
                ringPath.rewind()
                ringPath.appendBlob(center, ringRadius, wobble, phase)
                drawPath(
                    path = ringPath,
                    color = foreground,
                    alpha = ringAlpha,
                    style = Stroke(width = lerp(2.5f, 1f, r) * density.density),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val top = glyphCenter().y + SplashGlyph.HALF_EXTENT * baseUnit() * glyphScale() + 28.dp.toPx()
                    IntOffset(0, top.roundToInt())
                }
                .graphicsLayer {
                    alpha = brand.value * (1f - morph.value)
                    translationY = (1f - brand.value) * 16.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = foreground,
                fontSize = 30.sp,
                fontFamily = GoogleSansFlex.Display.Emphasized.Small,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.splash_tagline),
                color = contentColor,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
        }

        Text(
            text = stringResource(R.string.splash_version, BuildConfig.VERSION_NAME),
            color = contentColor,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .graphicsLayer { alpha = 0.7f * brand.value * (1f - morph.value) },
        )
    }
}

private fun farthestCorner(center: Offset, size: Size): Float = max(
    max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
    max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
)

/**
 * 往 [this] 里追加一个闭合的"水波圆"：半径按两组正弦起伏（[amplitude] 为相对半径的幅度），
 * 点之间用二次贝塞尔过中点连接，边缘是光滑的。
 */
private fun Path.appendBlob(center: Offset, radius: Float, amplitude: Float, phase: Float) {
    val count = 72
    val points = Array(count) { i ->
        val theta = 2f * PI.toFloat() * i / count
        val wave = 0.6f * sin(3f * theta + phase) + 0.4f * sin(5f * theta - 1.7f * phase)
        val rr = radius * (1f + amplitude * wave)
        Offset(center.x + rr * cos(theta), center.y + rr * sin(theta))
    }
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val start = mid(points[count - 1], points[0])
    moveTo(start.x, start.y)
    for (i in 0 until count) {
        val control = points[i]
        val end = mid(control, points[(i + 1) % count])
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    close()
}
