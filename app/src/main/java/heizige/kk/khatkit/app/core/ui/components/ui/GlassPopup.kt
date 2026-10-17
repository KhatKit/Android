package heizige.kk.khatkit.app.core.ui.components.ui

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 进场时长：与头像编辑弹层同一档，卡片缩放 + 淡入。 */
private const val PopupEnterMillis = 220

/** 退场略快（退场不需要被看清），[GlassPopup] 的 dismiss 延迟按这一档算。 */
private const val PopupExitMillis = 160

/**
 * 玻璃弹窗（popup）的公共宿主：居中浮起的一张半透明卡片 + 压暗的遮罩。
 *
 * 与底部抽屉的分工：抽屉负责「一行一行往下读」的长表单，这里负责「一眼看完再决定」
 * 的详情 —— 从列表里点开一张卡片/一个合集，视线不移到屏幕底部，弹窗就在原地浮起。
 *
 * 三层结构（自下而上）：
 * 1. **页面本体**：由调用方自己挂 [popupBackdropBlur]。模糊必须作用在**页面**上，
 *    而不是弹窗上 —— 弹窗是盖在模糊层之上的兄弟节点，自己保持清晰，才像隔着毛玻璃看页面。
 * 2. **遮罩**：压暗 + 吃掉点击（点它能关）。
 * 3. **卡片**：半透明页面底色 + 1dp 高光描边 + 大圆角投影。因为垫着的页面已经被模糊，
 *    半透明卡片透出来的就是模糊后的颜色，这正是「毛玻璃」的观感来源。
 *
 * 退场：所有关闭路径（点遮罩 / 返回键 / 卡片自己的关闭按钮）都走 [dismiss]。
 * [dismiss] 先播完退场动画再回调 [onDismiss]，所以调用方可以照旧「一点就 `state = null`」
 * 地把弹窗从组合里摘掉，不会看到硬切。
 *
 * 返回手势是**预测性**的：拖动过程中卡片跟着手缩小、变淡，松手提交才走 [dismiss]，
 * 中途松手（取消）弹回原样。这里只注册一条 [PredictiveBackHandler]：再叠一个 BackHandler
 * 会在同一个 dispatcher 上抢返回事件，手势进度就在半路断掉。
 */
@Composable
fun GlassPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 卡面最宽：手机上等于屏宽减两侧 20dp，平板/横屏时收成一列窄卡而不是铺满。 */
    maxWidth: Dp = 400.dp,
    /** 卡面最高占可用高度的比例：留出上下余量，让「这是个浮层」一眼可见。 */
    maxHeightFraction: Float = 0.86f,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 先以 visible=false 组合一帧，下一帧再翻成 true：首帧即目标态会让进场动画直接跳过。
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            visible = false
            scope.launch {
                delay(PopupExitMillis.toLong())
                onDismiss()
            }
        }
    }
    // 返回手势与系统手势：交给弹窗自己收，不用页面各处再挂 BackHandler。
    //
    // 预测性返回：手势拖到哪，卡片就缩/淡到哪；松手提交才走 [dismiss]，取消则弹回。
    // 只留这一条 handler（原来的 BackHandler 已并入）：两者注册在同一个
    // OnBackPressedDispatcher 上，同时存在会互相抢占，手势进度就在半路断掉。
    val backProgress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = !closing) { backEvents ->
        try {
            backEvents.collect { event ->
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            // 手势提交：卡片此刻已经淡到只剩影子，余下的交给 [dismiss] 的退场动画收尾。
            dismiss()
        } catch (e: CancellationException) {
            // 手势取消：弹回完全打开态（与进场同一档时长，手感对称）。
            backProgress.animateTo(0f, tween(durationMillis = PopupEnterMillis))
            throw e
        }
    }

    val scrim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) PopupEnterMillis else PopupExitMillis),
        label = "glass_popup_scrim",
    )
    val dark = rememberIsDarkTheme()
    val cardColor = CustomColors.pageContainerColor
    // 半透明度取 0.82：再透就压不住背后的碎细节（当系统不支持 RenderEffect 时尤其明显），
    // 再实就看不出底下是模糊的页面，跟普通对话框没差别。
    val glassColor = cardColor.copy(alpha = 0.82f)
    val hairline = if (dark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val scrimColor = Color.Black.copy(alpha = if (dark) 0.42f else 0.28f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cardMaxHeight = maxHeight * maxHeightFraction
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩自己吃掉所有点击：既关弹窗，也保证下层页面收不到手。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                )
                .background(scrimColor.copy(alpha = scrimColor.alpha * scrim))
                // 手势进度只走绘制层（graphicsLayer 的 lambda 是延迟读，不触发重组）：
                // 拖到哪遮罩就淡到哪，拖回原处也逐帧跟着回来。
                .graphicsLayer { alpha = 1f - backProgress.value },
        )
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(PopupEnterMillis)) +
                scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)),
            exit = fadeOut(tween(PopupExitMillis)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(PopupExitMillis)),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
                    .heightIn(max = cardMaxHeight)
                    // 跟手缩到 0.9、淡到 15%：拖满时卡片只剩一层影子，
                    // 提交后由退场动画抹掉它 —— 不会出现「松手先弹回满屏、再播退场」的跳变。
                    .graphicsLayer {
                        val progress = backProgress.value
                        alpha = 1f - 0.85f * progress
                        val scale = 1f - 0.10f * progress
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(elevation = 28.dp, shape = shape, clip = false)
                    .clip(shape)
                    .background(glassColor)
                    .border(width = 1.dp, color = hairline, shape = shape),
            ) {
                content(dismiss)
            }
        }
    }
}

/**
 * 页面本体的「弹窗打开时高斯模糊」。挂在页面根节点（Scaffold）上，弹窗作为它的兄弟节点浮在上面。
 *
 * 为什么用 [Modifier.blur]（RenderEffect）而不是把页面录进 backdrop 再采样：
 * - 页面是一整块可滚动的 LazyColumn，平时每帧录制成本不低；`blur` 只在开关时切换一次特效。
 * - 不需要弹窗与页面共享同一套 backdrop，弹窗留在页面组合里就能用。
 *
 * Android 12 以下没有 RenderEffect（[Modifier.blur] 在那些设备上是空操作），
 * 调用方可以据此把遮罩压深一点来补足「下层被压住」的观感。
 */
@Composable
fun Modifier.popupBackdropBlur(open: Boolean, radius: Dp = 20.dp): Modifier {
    if (Build.VERSION.SDK_INT < 31) return this
    val animatedRadius by animateDpAsState(
        targetValue = if (open) radius else 0.dp,
        animationSpec = tween(durationMillis = PopupEnterMillis),
        label = "glass_popup_backdrop_blur",
    )
    if (animatedRadius <= 0.1.dp) return this
    // 默认的 Rectangle 边缘处理：整屏一张层，边缘不需要外扩（Unbounded 反而更贵）。
    return blur(radius = animatedRadius)
}
