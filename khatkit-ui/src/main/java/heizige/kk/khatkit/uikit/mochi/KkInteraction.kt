package heizige.kk.khatkit.uikit.mochi

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import ui.feice.mochi.anim.MochiMotions
import ui.feice.mochi.utils.MochiHapticProfile
import ui.feice.mochi.utils.MochiHaptics
import ui.feice.mochi.utils.rememberMochiHaptics

/**
 * U8：交互手感的唯一入口（按压 / 触觉 / 动效强弱），对应 Mochi 的
 * `docs/guide/haptics.md`、`effects.md` 与示例 ElasticPage / GlowPage。
 *
 * 规则和 Mochi 一致：**只在状态真的变了、或者松手会改变结果时**震一下，不用 `LocalHapticFeedback`，
 * 避免和组件自带的 Mochi 触觉双发（`LocalHapticFeedback` 走的是 View 的 performHapticFeedback，
 * 与 Mochi 的 composition 原语是两条路，同时用会听到两声）。
 */
@Stable
class KkHaptics internal constructor(
    private val mochi: MochiHaptics,
    /** FX1 总开关（[LocalKkEffectsEnabled]）：关掉后所有方法静默，一次都不震。 */
    private val enabled: Boolean = true,
) {
    private fun emit(block: () -> Unit) {
        if (enabled) block()
    }

    /** 点开一个东西（菜单、弹层、页面 push）。 */
    fun open() = emit { mochi.tapOpen() }

    /** 长按开始（拖拽排序、长按菜单）。 */
    fun longPress() = emit { mochi.longPress() }

    /** 经过新的一项 / 一步 / 中点（滑杆步进、换档吸附、列表滑过新项）。 */
    fun tick() = emit { mochi.tick() }

    /** 确认（开关、选中、松手完成）。 */
    fun confirm() = emit { mochi.confirm() }

    /** 越过「松手即提交」阈值（滑动删除、阈值型手势），来回越都震。 */
    fun threshold() = emit { mochi.threshold() }

    /** 拖拽排序开始 = 长按开拖。 */
    fun dragStarted() = emit { mochi.longPress() }

    /** 拖拽排序结束 = 松手落位。 */
    fun dragStopped() = emit { mochi.confirm() }
}

/** 子树的统一触觉入口；跟随 [LocalKkEffectsEnabled]（FX1 总开关关掉时静默）。 */
@Composable
fun rememberKkHaptics(): KkHaptics {
    val mochi = rememberMochiHaptics(MochiHapticProfile.IosLike)
    val effectsEnabled = LocalKkEffectsEnabled.current
    return remember(mochi, effectsEnabled) { KkHaptics(mochi, effectsEnabled) }
}

/**
 * 系统「减弱动效」是否打开（开发者选项的动画时长缩放 = 0，或无障碍里关掉动画）。
 * 与 Mochi 的 MochiPopover.kt:212 同一判定；读 ANIMATOR_DURATION_SCALE 而不是
 * systemAnimatorDurationScale 的 Flow，避免每个组合都起协程。
 */
@Composable
fun rememberKkReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * MD3 可点区（Card / ListItem / TextField 尾部图标 / Surface(onClick)）的按压形变：
 * 按下时缩到 [pressedScale]，与 Mochi 的 `Modifier.mochiPressDeform` 观感一致（只缩放、不画第二层水波）。
 * 减弱动效打开时直接切到终态（snap），不跑弹簧；点击功能不依赖动画完成，所以不影响可用性。
 *
 * 用法（配 Mochi 默认的 0.97 缩放、不覆盖形状）：
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * Card(modifier = Modifier.kkPressState(interaction), onClick = ..., interactionSource = interaction)
 * ```
 */
fun Modifier.kkPressState(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val reduceMotion = rememberKkReduceMotion()
    // FX1：总开关关掉时不做按压形变（交互仍然可用，只是没有缩放）
    val effectsEnabled = LocalKkEffectsEnabled.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && effectsEnabled) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else MochiMotions.responseSpring(0.2f, 1f),
        label = "kk_press_scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 转场参数（与 MochiNavHost 的默认值逐项对齐，见 MochiNavHost.kt:80-104 的 MochiNavSpec）：
 * 前进/返回用 300ms smooth（不回弹，尾巴不拖），取消用 responseSpring(0.35, 1f)，
 * 侧推 push 用 responseSpring(0.42, 1f)。U7a 接 MochiNavHost 时直接取这里的值，
 * 页面里不要再各写一套 spring。
 */
object KkMotionSpecs {
    const val OpenDurationMs: Int = 300
    const val CloseDurationMs: Int = 300
    const val CancelResponse: Float = 0.35f
    const val PushResponse: Float = 0.42f
}
