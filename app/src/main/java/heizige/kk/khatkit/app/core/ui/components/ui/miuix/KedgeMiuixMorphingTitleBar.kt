package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import heizige.kk.kedge.adaptive.miuixScrollBehavior
import kotlinx.coroutines.launch
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 拖动顶栏松手后把折叠位置吸附到展开/收起。
 *
 * Miuix 的 `settleAppBar` 是私有的，这里按同样手感复刻一份：先用 fling 规格惯性
 * 滑行（每帧写回 [TopAppBarState.heightOffset]），再用 snap 规格吸附到 0（展开）
 * 或 [TopAppBarState.heightOffsetLimit]（收起）。吸附目标按当前折叠比例过半判断，
 * 与 Miuix 列表滚动松手时的行为一致。
 */
private suspend fun settleTopAppBar(
    state: top.yukonga.miuix.kmp.basic.TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
) {
    // 已经在展开/收起两端，不用吸附（否则会凭空抖一下）。
    if (state.collapsedFraction < 0.01f || state.collapsedFraction >= 1f) return
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        Animatable(state.heightOffset).animateDecay(velocity, flingAnimationSpec) {
            state.heightOffset = value
        }
    }
    if (snapAnimationSpec != null &&
        state.heightOffset < 0f &&
        state.heightOffset > state.heightOffsetLimit
    ) {
        val target = if (state.collapsedFraction < 0.5f) 0f else state.heightOffsetLimit
        Animatable(state.heightOffset).animateTo(target, snapAnimationSpec) {
            state.heightOffset = value
        }
    }
}

/**
 * Miuix 顶栏标题的「morph 成输入框」组件。
 *
 * 背景：Miuix 原版 [TopAppBar] 的 `title` 是 `String`，无法像 MD3
 * `TopAppBar(title = { ... })` 那样把标题替换成输入框。原版只给了颜色入口
 * （`titleColor` / `largeTitleColor` / `subtitleColor`），所以做法是：
 *
 * - 标题（小标题 + 大标题 + 副标题）颜色随进度淡出；
 * - 输入框**叠在大标题的位置**上淡入 —— 原版 `TopAppBarLayout` 里大标题是
 *   `padding(top = CollapsedHeight) + padding(horizontal = TitlePadding)`，这里照抄
 *   同一组内边距，位置与被替换的标题逐像素对齐，字阶也取同一个 `title1`、颜色取
 *   同一个 `onSurface`，字号字色与标题严格相等；
 * - 搜索态把顶栏**钉在展开态**（`heightOffsetLimit = 0`，与 Miuix `SmallTopAppBar`
 *   的钉法一致）：输入框就叠在大标题那一行，允许折叠的话列表一滚就把那一行连同
 *   输入框裁掉。钉住后 nestedScroll 不再消费滚动，列表照常滚。
 *
 * 进度来源与抽屉一致：以 [expanded] 为准自持 [Animatable]，
 * [dragProgress] 仅在预测返回手势进行中（[dragging]）才接管，保证跟手且不重播动画。
 *
 * @param title 关闭态标题（大标题与小标题同文）。
 * @param subtitle 副标题（Miuix 用 `body2` 样式）。
 * @param expanded 是否处于搜索态。
 * @param keyword 当前输入。
 * @param dragProgress 预测返回手势的实时进度（非拖动时忽略）。
 * @param dragging 手势是否进行中。
 * @param onExitSearch 关闭搜索（输入完成）。
 * @param modifier 传给底层 [TopAppBar]。
 * @param navigationIcon 导航槽内容。**返回箭头由调用方在这一个槽位里与菜单图标
 *   交叉淡化**（Miuix 的导航槽宽度会参与标题定位，组件不再另画一个返回箭头占位）。
 * @param actions 尾部操作。
 * @param scrollBehavior Miuix 折叠行为，用于大标题收缩。
 * @param focusRequester 传给输入框；不传则不自动聚焦。
 */
@Composable
fun KedgeMiuixMorphingTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expanded: Boolean,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    dragProgress: Float = 0f,
    dragging: Boolean = false,
    onExitSearch: () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    /**
     * 是否给顶栏垫状态栏/刘海/导航栏 inset。必须与覆盖层保持一致（覆盖层照抄了
     * 原版同一组 inset padding），所以只暴露一个参数，不要在两处各写一份。
     */
    defaultWindowInsetsPadding: Boolean = true,
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, tween(220))
    }
    LaunchedEffect(dragProgress, dragging) {
        if (dragging) progress.snapTo(dragProgress.coerceIn(0f, 1f))
    }
    val p = progress.value

    // 页面级 backdrop（KedgePageScaffold 在 Miuix 下建立并下发）。
    // 模糊生效时顶栏底色必须透明，否则模糊无从透出。
    val backdrop = heizige.kk.kedge.adaptive.LocalKedgePageBackdrop.current
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    // 大标题折叠必须观察「本屏那个」Miuix 行为：KedgePageScaffold 建了唯一一个实例，
    // 接上 nestedScroll 并下发到这里。页面不用（也不该）自己再 remember 一个。
    val effectiveScroll = scrollBehavior
        ?: heizige.kk.kedge.adaptive.LocalKedgePageScrollBehavior.current
            ?.miuixScrollBehavior
    val barState = effectiveScroll?.state

    // 状态栏/刘海/导航栏 inset 必须在**底层 TopAppBar 组合之前**读成定值。
    //
    // 原因：原版 TopAppBar 自己在 modifier 链上 consume 了这几个 inset，而搜索输入框
    // 是它的兄弟节点（覆盖层），不是它的子节点。等 TopAppBar 组合完再去读
    // WindowInsets，读到的是被 consume 后的 0 —— 实测输入框因此整整上移一个状态栏
    // 高度，直接压在顶栏的返回箭头上（ChatPage 搜索态）。
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val topInset = with(density) { WindowInsets.systemBars.getTop(this) }
    val startInset = with(density) { WindowInsets.displayCutout.getLeft(this, layoutDirection) }
    val endInset = with(density) { WindowInsets.navigationBars.getRight(this, layoutDirection) }

    // 搜索态钉住展开态。原版靠大标题的 onSizeChanged 写 heightOffsetLimit（只在尺寸
    // 变化时写一次），所以退出搜索要自己把自然区间还回去。
    var naturalHeightOffsetLimit by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(expanded, barState) {
        val state = barState ?: return@LaunchedEffect
        if (expanded) {
            naturalHeightOffsetLimit = state.heightOffsetLimit
            if (state.heightOffset != 0f) {
                // 打开搜索时顶栏可能正被滚动收起：先在自然折叠区间里补回展开态，
                // 直接把区间清零会「跳」回去而不是动画过去。
                Animatable(state.heightOffset).animateTo(0f, tween(220))
            }
            state.heightOffsetLimit = 0f
        } else if (naturalHeightOffsetLimit < 0f) {
            state.heightOffsetLimit = naturalHeightOffsetLimit
            state.heightOffset = 0f
        }
    }
    // 尺寸变化（旋转等）会把原版写的折叠区间冲掉，搜索态每次重组再钉一次。
    // 两个字段都不是可观察状态（只有 heightOffset 是，且值相同时不触发重组）。
    SideEffect {
        if (expanded) {
            barState?.heightOffsetLimit = 0f
            barState?.heightOffset = 0f
        }
    }

    // 拖动顶栏本身收缩/展开（MD3 的 TwoRowsTopAppBar 自带这个能力，Miuix 原版没有，
    // 这里补上）。搜索态不给拖：那时顶栏被钉在展开态，拖动只会和钉住的
    // heightOffsetLimit 打架。
    //
    // 修饰符挂在**外层** Box 上（毛玻璃只包顶栏本体）：KedgeBlurredBar 的 modifier
    // 槽会被接上 textureBlur，pointerInput 与毛玻璃混在一根链上没有好处。
    val dragModifier = if (expanded || effectiveScroll == null) {
        Modifier
    } else {
        val scroll = effectiveScroll
        val state = barState!!
        val scope = rememberCoroutineScope()
        Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta -> state.heightOffset += delta },
            onDragStopped = { velocity ->
                scope.launch {
                    settleTopAppBar(
                        state = state,
                        velocity = velocity,
                        flingAnimationSpec = scroll.flingAnimationSpec,
                        snapAnimationSpec = scroll.snapAnimationSpec,
                    )
                }
            },
        )
    }

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().then(dragModifier)) {
            // 底层原版 TopAppBar：负责大标题折叠动画与整体度量。
            // 必须包 KedgeBlurredBar 才有毛玻璃，否则顶栏是死板的纯色。
            heizige.kk.kedge.adaptive.KedgeBlurredBar(backdrop = backdrop) {
            TopAppBar(
                color = barColor,
                title = title,
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 1f - p),
                largeTitle = title,
                largeTitleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 1f - p),
                subtitle = subtitle.orEmpty(),
                subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 1f - p),
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = effectiveScroll,
                defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            )
            }

            // 覆盖层：输入框替换大标题。matchParentSize 不参与父级高度测量，
            // 避免覆盖层把顶栏撑高（父级高度只由底层 TopAppBar 决定）。
            if (p > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        // 内边距照抄原版大标题的几何：状态栏 inset + 折叠行高度，
                        // 水平方向是 inset + TitlePadding。数值在上面提前读好了。
                        .padding(
                            start = with(density) { startInset.toDp() },
                            end = with(density) { endInset.toDp() },
                            top = with(density) { topInset.toDp() } +
                                TopAppBarDefaults.CollapsedHeight,
                        )
                        .padding(horizontal = TopAppBarDefaults.TitlePadding)
                        .graphicsLayer { alpha = p },
                ) {
                    // 与大标题同一个字阶（title1）+ 同一个颜色：输入框才算是「替换」
                    // 标题，而不是另起一个更小的搜索框。
                    if (keyword.isBlank() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = MiuixTheme.textStyles.title1.fontSize,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = keyword,
                        onValueChange = onKeywordChange,
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.title1.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onExitSearch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (focusRequester != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
    }
}
