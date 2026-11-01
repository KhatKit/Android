package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.easteregg.EmojiBurstSpec
import heizige.kk.khatkit.app.core.ui.components.easteregg.EmojiBurstState
import heizige.kk.khatkit.uikit.mochi.LocalKkEffectsEnabled
import heizige.kk.khatkit.uikit.mochi.MochiBadgedBox
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.uikit.mochi.kkPressState
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.effect.content.mochiBounce
import ui.feice.mochi.shape.MochiSuperellipse
import ui.feice.mochi.theme.MochiTheme

/**
 * 关于页头图 + 图标彩蛋的全部状态。
 *
 * 状态只活在本页：切走再回来重新收集，不落 DataStore（不想为一个彩蛋动设置版本号）。
 * 连击窗口 1.6s，窗口内继续点会累加；满 [ComboGoal] 次、或长按蓄力成功一次，就算「解锁」。
 */
internal class AboutEggState {
    var combo by mutableIntStateOf(0)
        private set
    var bestCombo by mutableIntStateOf(0)
        private set
    var comboUnlocked by mutableStateOf(false)
        private set
    var chargeUnlocked by mutableStateOf(false)
        private set

    /** 每次交互自增：图标、角标的弹簧都拿它当 trigger。 */
    var tick by mutableIntStateOf(0)
        private set

    /** 长按蓄力进度（0..1），画成一个包住图标的环。 */
    val charge = Animatable(0f)

    /** 正在扩散的冲击波，画完自己从列表里摘掉。 */
    val waves = mutableStateListOf<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()

    /** 图标自己那层的坐标：粒子按它定位，换布局不会跑偏。 */
    var iconCoords: LayoutCoordinates? = null

    val unlocked: Boolean get() = comboUnlocked || chargeUnlocked

    fun tap(): Int {
        combo += 1
        bestCombo = maxOf(bestCombo, combo)
        tick += 1
        if (combo >= ComboGoal) comboUnlocked = true
        return combo
    }

    fun charged() {
        chargeUnlocked = true
        combo = 0
        tick += 1
    }

    fun resetCombo() {
        combo = 0
    }

    companion object {
        /** 连击到这个数解锁「彩蛋猎人」。 */
        const val ComboGoal = 10
    }
}

@Composable
internal fun rememberAboutEggState(): AboutEggState = remember { AboutEggState() }

/**
 * 页头：应用图标（彩蛋入口）+ 名称 + 一句话说明 + 版本胶囊。
 *
 * 图标交互三档，跟 Mochi 的触觉规则一致（只在状态真的变了才震）：
 * 1. 轻点 —— 图标弹簧压一下、两圈冲击波、emoji 炸开；连击角标从 ×2 起出现，
 *    每次点数用 MochiCounterText 滚上去。
 * 2. 连击满 [AboutEggState.ComboGoal] 次 —— 三圈冲击波 + 更密的一把，触觉切到确认档，弹一条 Toast。
 * 3. 长按 —— 蓄力环在 520ms 内填满，松手/满环时从页面顶端下 emoji 雨 + 中心大爆发。
 *
 * 减弱动效或关掉「光效」时只留触觉，粒子与冲击波全部跳过（Mochi：系统动画关掉则弹性关掉）。
 */
@Composable
internal fun AboutHero(
    state: AboutEggState,
    burst: EmojiBurstState,
    /** 彩蛋的落点：连击满之后进隐藏终端。 */
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val haptics = rememberKkHaptics()
    val reduceMotion = rememberKkReduceMotion()
    val effectsEnabled = LocalKkEffectsEnabled.current
    val motion = effectsEnabled && !reduceMotion
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var chargeJob by remember { mutableStateOf<Job?>(null) }

    // 连击越久，图标外圈的光越亮（0.20 → 0.38），弹簧收放
    val glowAlpha by animateFloatAsState(
        targetValue = 0.20f + (state.combo.coerceAtMost(AboutEggState.ComboGoal) / AboutEggState.ComboGoal.toFloat()) * 0.18f,
        animationSpec = MochiSprings.smooth(620),
        label = "aboutEggGlow",
    )

    fun fireTap() {
        val combo = state.tap()
        val reachedGoal = combo == AboutEggState.ComboGoal
        if (motion) {
            launchWave(state, scope)
            scope.launch {
                delay(90)
                launchWave(state, scope)
            }
            if (reachedGoal) {
                scope.launch {
                    repeat(2) { index ->
                        delay(140L * (index + 1))
                        launchWave(state, scope)
                    }
                }
            }
            burst.burst(
                anchor = state.iconCoords,
                spec = EmojiBurstSpec(
                    count = (8 + combo * 2).coerceAtMost(26),
                    speedMin = 300f + combo * 18f,
                    speedMax = 580f + combo * 26f,
                    sizeMinSp = 18f,
                    sizeMaxSp = 30f,
                ),
            )
        }
        if (reachedGoal || combo % 5 == 0) haptics.confirm() else haptics.open()
        if (reachedGoal) {
            Toast.show("彩蛋解锁：正在打开隐藏终端", isError = false)
            // 先让这一发炸完再切页，不然涟漪和 emoji 都被导航动画吃掉
            scope.launch {
                delay(360)
                onOpenTerminal()
            }
        }
    }

    fun fireCharged() {
        state.charged()
        if (motion) {
            launchWave(state, scope)
            scope.launch {
                delay(70)
                launchWave(state, scope)
            }
            scope.launch {
                delay(150)
                launchWave(state, scope)
            }
            burst.rain(
                EmojiBurstSpec(
                    count = 20,
                    speedMin = 180f,
                    speedMax = 380f,
                    sizeMinSp = 18f,
                    sizeMaxSp = 30f,
                    lifetimeSeconds = 4.4f,
                )
            )
            burst.burst(
                anchor = state.iconCoords,
                spec = EmojiBurstSpec(
                    count = 18,
                    speedMin = 520f,
                    speedMax = 940f,
                    sizeMinSp = 20f,
                    sizeMaxSp = 34f,
                ),
            )
        }
        haptics.confirm()
        Toast.show("长按彩蛋：蓄力满，撒了一屏 🎊", isError = false)
    }

    // 手势里用到的是「最新一次组合」的这两个动作：图标坐标、动效开关变了也照样取到新值
    val latestTap by rememberUpdatedState(newValue = { fireTap() })
    val latestCharge by rememberUpdatedState(newValue = { fireCharged() })

    // 连击窗口：停手 1.6s 后归零，角标随之淡出
    LaunchedEffect(state.combo) {
        if (state.combo <= 0) return@LaunchedEffect
        delay(1600)
        state.resetCombo()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(AboutIconStage),
            contentAlignment = Alignment.Center,
        ) {
            // 主色光晕：把图标从分组页底色上托起来；连击时变亮，等于给「蓄力」一个视觉读数
            Box(
                modifier = Modifier
                    .size(AboutIconStage)
                    .drawBehind {
                        val radius = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    cs.primary.copy(alpha = glowAlpha),
                                    cs.primary.copy(alpha = glowAlpha * 0.32f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    },
            )
            // 蓄力环 + 冲击波
            Canvas(modifier = Modifier.size(AboutIconStage)) {
                val ringRadius = 62.dp.toPx()
                val charge = state.charge.value
                if (charge > 0.01f) {
                    drawCircle(
                        color = cs.primary.copy(alpha = 0.14f),
                        radius = ringRadius,
                        style = Stroke(3.dp.toPx()),
                    )
                    drawArc(
                        color = cs.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * charge.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                state.waves.forEach { wave ->
                    val progress = wave.value
                    if (progress <= 0f || progress >= 1f) return@forEach
                    // 最多扩到图标外 30dp 左右：再大就压到下面的名称上了
                    val radius = ringRadius * (0.94f + progress * 0.55f)
                    drawCircle(
                        color = cs.primary.copy(alpha = (1f - progress) * 0.32f),
                        radius = radius,
                        style = Stroke((1.2f + (1f - progress) * 2f).dp.toPx()),
                    )
                }
            }
            MochiBadgedBox(
                badge = { ComboBadge(state) },
            ) {
                AsyncImage(
                    model = R.mipmap.ic_launcher,
                    contentDescription = stringResource(R.string.app_name) + " 图标",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(AboutIconSize)
                        .clip(MochiSuperellipse(5f))
                        .onGloballyPositioned { coordinates -> state.iconCoords = coordinates }
                        .kkPressState(interaction, pressedScale = 0.94f)
                        .mochiBounce(state.tick, depth = 0.12f)
                        .semantics {
                            onClick(label = "彩蛋") {
                                latestTap()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    // 手动喂按压事件：kkPressState 靠它做 0.94 的缩，手势归自己管
                                    val press = PressInteraction.Press(offset)
                                    interaction.emit(press)
                                    chargeJob?.cancel()
                                    state.charge.snapTo(0f)
                                    if (motion) {
                                        chargeJob = scope.launch {
                                            state.charge.animateTo(1f, tween(520, easing = LinearEasing))
                                        }
                                    }
                                    val released = tryAwaitRelease()
                                    interaction.emit(
                                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                                    )
                                    chargeJob?.cancel()
                                    chargeJob = null
                                    if (released) {
                                        state.charge.animateTo(0f, tween(140))
                                    } else {
                                        state.charge.snapTo(0f)
                                    }
                                },
                                onTap = { latestTap() },
                                onLongPress = { latestCharge() },
                            )
                        },
                )
            }
        }

        Text(
            text = stringResource(R.string.app_name),
            style = MochiTheme.textStyles.largeTitle,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "多服务商接入，会话与配置都存在本机",
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AboutPill {
                Text(
                    text = stringResource(
                        R.string.about_page_version_format,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MochiTheme.textStyles.footnote,
                    color = cs.onSurfaceVariant,
                )
            }
            if (BuildConfig.DEBUG) {
                AboutPill(container = cs.tertiary.copy(alpha = 0.16f)) {
                    Text(
                        text = "DEBUG",
                        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.tertiary,
                    )
                }
            }
        }
        Text(
            text = "连点十下有惊喜 · 长按可以蓄力",
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** 版本胶囊：分组页里的一枚小圆底标签。 */
@Composable
private fun AboutPill(
    container: Color = ui.feice.mochi.theme.MochiDefaults.fill(),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(MochiTheme.shapes.full)
            .background(container)
            .padding(horizontal = 11.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** 连击角标：从 ×2 起出现，数字每次滚上去，整枚跟着弹簧弹一下。 */
@Composable
private fun ComboBadge(state: AboutEggState) {
    val cs = MochiTheme.colorScheme
    AnimatedVisibility(
        visible = state.combo >= 2,
        enter = scaleIn(animationSpec = MochiSprings.bouncy(380)) + fadeIn(tween(120)),
        exit = scaleOut(animationSpec = tween(120)) + fadeOut(tween(120)),
    ) {
        Row(
            modifier = Modifier
                .mochiBounce(state.tick, depth = 0.3f)
                .background(cs.primary, MochiTheme.shapes.full)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "×",
                style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onPrimary,
            )
            MochiCounterText(
                value = state.combo.toLong(),
                color = cs.onPrimary,
                style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** 冲击波：一圈从图标里扩散出去的主色细环，画完把自己从列表里摘掉。 */
private fun launchWave(state: AboutEggState, scope: CoroutineScope) {
    val wave = Animatable(0f)
    state.waves.add(wave)
    scope.launch {
        wave.animateTo(1f, tween(620, easing = LinearOutSlowInEasing))
        state.waves.remove(wave)
    }
}

private val AboutIconSize = 108.dp
private val AboutIconStage = 176.dp
