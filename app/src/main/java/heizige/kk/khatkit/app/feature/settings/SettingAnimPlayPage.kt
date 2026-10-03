package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.hypot

/** 揭幕总时长。原视频整段 0.42s。 */
private const val RevealDurationMillis = 420

/** 中央标记的边长。 */
private val MarkSize = 96.dp

/**
 * 孔沿羽化带宽 ÷ 孔半径。
 *
 * 原视频的孔边是糊开的：靠外那一圈是半透明渐变，而不是硬边。跟着半径等比缩放，所以
 * 动画从 0 放到最大，过渡带的观感保持一致（固定像素宽度的话，开场孔小、过渡带按固定
 * 宽度铺开会先糊一大片，结尾又变得很锐）。
 */
private const val FeatherRatio = 0.18f

/**
 * 圆形揭幕（iris reveal）动画的试验页，点一下「播放一次」播一遍。
 *
 * 做法是一个「带圆孔的遮罩」：上层铺满纯色封面，下层是真实内容，一个圆形孔从中心长大
 * 把封面吃掉。用 [PathFillType.EvenOdd] 在整屏矩形上叠一个圆，靠半径动画驱动 ——
 * 不需要裁剪容器，也不用真的挖洞。
 *
 * 终点半径不是屏幕宽高的一半，而是**中心到最远角**的距离，否则四角会露不干净。
 */
@Composable
fun SettingAnimPlayPage() {
    var playToken by remember { mutableIntStateOf(0) }
    val radius = remember { Animatable(0f) }
    var maxRadius by remember { mutableFloatStateOf(0f) }

    // playToken 初始为 0 不播；每点一次 +1 播一遍，播完停在完全揭开的状态。
    LaunchedEffect(playToken) {
        if (playToken == 0) return@LaunchedEffect
        radius.snapTo(0f)
        radius.animateTo(
            targetValue = maxRadius,
            animationSpec = tween(RevealDurationMillis, easing = FastOutSlowInEasing),
        )
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_anim_play_page),
        navigationIcon = { BackButton() },
        largeTitle = false,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MiuixTheme.colorScheme.background),
        ) {
            // 下层：被揭示的内容。圆心放了个小圆点，孔扩张时它始终留在孔心，
            // 这样「孔往哪扩、扩到哪」一眼能看出来。
            DemoContent()

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KedgeButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { playToken++ },
                ) {
                    Text(
                        text = stringResource(R.string.setting_anim_play_page_replay),
                        style = KedgeTextStyles.body(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 上层封面。盖在按钮上面，所以按钮要点得等动画播完（封面 radius=0 时是全屏的）。
            RevealCover(radius = radius.value, onMaxRadius = { maxRadius = it })
        }
    }
}

/**
 * 封面层：整屏纯色 + 中央标记 + 圆形孔。
 *
 * [onMaxRadius] 回吐「孔盖满整屏所需的半径」，也就是中心到最远角的距离。
 */
@Composable
private fun RevealCover(radius: Float, onMaxRadius: (Float) -> Unit) {
    val base = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        Color(0xFF3F3D6B)
    } else {
        Color(0xFF2B2C4F)
    }
    val markColor = lerp(base, Color.White, 0.45f)

    // 标记随孔扩张淡出（原视频里孔刚撑开时中心那枚标记是变淡消失的，不是被裁掉）。
    // Density 只在这一处用来把 MarkSize 换成 px，好让淡出区间跟实际像素对齐。
    val markPx = with(LocalDensity.current) { MarkSize.toPx() }
    val markAlpha = (1f - radius / (markPx * 1.6f)).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // 动画终点半径。注意不能只取「中心到最远角」：羽化带意味着「完全被揭开」的
            // 范围只有 radius × (1 - FeatherRatio)，直接拿最远角当终点的话，播完时四角
            // 刚好落在遮罩全不透明的一侧，边缘还剩一圈揭不开的渐变。要再乘一个
            // (1 + FeatherRatio)，让最远角落在羽化带的内侧，才是真的整屏露完。
            val farthestCorner = maxOf(
                hypot(center.x, center.y),
                hypot(size.width - center.x, center.y),
                hypot(center.x, size.height - center.y),
                hypot(size.width - center.x, size.height - center.y),
            )
            onMaxRadius(farthestCorner * (1f + FeatherRatio))

            if (radius <= 0f) {
                drawRect(color = base)
                return@Canvas
            }

            // 关键：孔沿不是硬边，而是有一段羽化过渡带。原视频里圆形孔的边界是糊开的
            // ——中间一段半透明渐变，从完全透明过渡到完全不透明，硬边会显得很假。
            // 用径向渐变实现：外半径 = radius + 过渡带宽，其中
            //   [0, innerStop]        全透明（孔内）
            //   [innerStop, midStop]  过渡带（羽化）
            //   [midStop, 1]          全不透明（封面）
            val outer = radius * (1f + FeatherRatio)
            val innerStop = ((radius - radius * FeatherRatio) / outer).coerceIn(0f, 1f)
            val midStop = (radius / outer).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        innerStop to Color.Transparent,
                        midStop to base,
                        1f to base,
                    ),
                    center = center,
                    radius = outer,
                ),
            )
        }

        Icon(
            painter = rememberVectorPainter(bolt),
            contentDescription = null,
            tint = markColor,
            modifier = Modifier
                .align(Alignment.Center)
                .size(MarkSize)
                .graphicsLayer { alpha = markAlpha },
        )
    }
}

/** 演示内容：仿首页的三段结构，只为让圆孔的变化清楚可见。 */
@Composable
private fun DemoContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        MiuixTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(50),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(16.dp)
                .background(Color(0xFF2C62D9), CircleShape),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 96.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = 6) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .background(
                            MiuixTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(18.dp),
                        ),
                )
            }
        }
    }
}