package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
 * 另有一层「标题点击/长按」的透明覆盖层（[onTitleClick] / [onTitleLongClick]，见下面
 * 覆盖层处的几何说明）：命中区跟着**可见的那个标题**走，展开态命中大标题、折叠态命中
 * 折叠行里的小标题，与 MD3 `titleContent`（收起行与展开行都带 `combinedClickable`）对齐。
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
 * @param onTitleClick 点击标题的回调（MD3 的 `titleContent` 在 Miuix 下没有对应
 *   槽位，原版 [TopAppBar] 的 `title` 是 `String`）。展开态命中大标题、折叠态命中
 *   折叠行里的小标题那一行，两态都可点；搜索展开态不参与。为 null 时不叠加任何
 *   覆盖层，行为与加参数之前完全一致。
 * @param onTitleLongClick 长按标题的回调（命中区同上）；为 null 时该层不处理长按。
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
    onTitleClick: (() -> Unit)? = null,
    onTitleLongClick: (() -> Unit)? = null,
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
    //
    // 横向 inset 必须与 Miuix 原版 TopAppBar 实际 pad 的量一致：它的 modifier 链上
    // 先后挂了两个 windowInsetsPadding（miuix-ui 0.9.3 TopAppBar.kt:769-778）
    //
    //   .windowInsetsPadding(WindowInsets.displayCutout.only(Horizontal))
    //   .windowInsetsPadding(WindowInsets.navigationBars.only(Horizontal))
    //
    // Compose 里每个节点的实际 padding = consumedInsets − ancestorConsumedInsets，而
    // consumedInsets = ancestor.union(insets)（foundation-layout WindowInsetsPadding.kt
    // :475/:493-500），UnionInsets 又是**逐侧 maxOf**（WindowInsets.kt:419-430）。所以两次
    // 叠加的合成结果是**逐侧 max**，而不是两侧相加：
    //   left  = max(displayCutout.left,  navigationBars.left)
    //   right = max(displayCutout.right, navigationBars.right)
    // 直接用 union() 复用同一套语义，避免手写单侧取值（加法则会过度内缩、把命中区挤窄）。
    val barHorizontalInsets = WindowInsets.displayCutout
        .only(WindowInsetsSides.Horizontal)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val topInset = with(density) { WindowInsets.systemBars.getTop(this) }
    // ①（标题点击层）用的横向量：逐侧并集；flag=false 时原版不垫横向（TopAppBar.kt:769-777
    // 的 `if`），这里也跟着清零，保证与顶栏内容区逐像素对齐。
    val startInsetTotal = if (defaultWindowInsetsPadding) {
        with(density) { barHorizontalInsets.getLeft(this, layoutDirection) }
    } else {
        0
    }
    val endInsetTotal = if (defaultWindowInsetsPadding) {
        with(density) { barHorizontalInsets.getRight(this, layoutDirection) }
    } else {
        0
    }
    // ②（大标题区）与搜索输入框覆盖层沿用 t12 之前的既有单侧口径（左 displayCutout.left、
    // 右 navigationBars.right），不在本次返工范围内：② 纵向位于图标槽之下、压不到按钮，
    // 搜索输入框那处是既有约定。
    val startInset = with(density) { WindowInsets.displayCutout.getLeft(this, layoutDirection) }
    val endInset = with(density) { WindowInsets.navigationBars.getRight(this, layoutDirection) }

    // 折叠行里「导航槽 / 尾部操作槽」的**实测**宽度（px）：标题点击覆盖层要用它们
    // 把自己限制在标题所在的那一段（① 的横向边界 = 横向 inset + 16dp 槽内边距 + 这里的
    // 实测内容宽），从而不与两个图标槽重叠——前提是横向 inset 取逐侧并集，见覆盖层注释。
    // 两个槽各包一层 onSizeChanged 写入；尺寸没变时写回同值不触发重组，不会成环。
    var navigationSlotWidthPx by remember { mutableIntStateOf(0) }
    var actionSlotWidthPx by remember { mutableIntStateOf(0) }

    // 折叠态判定：阈值与 Miuix 内部完全一致（TopAppBarLayout 的
    // `smallTitleVisible = collapsedFraction * 3 >= 1`、`largeTitleAlpha = 1 - (frac*3)`），
    // 所以「小标题可见」与「大标题可见」互补，覆盖层不会出现两态都关的空窗。
    // 用 derivedStateOf：只在布尔翻转时重组，滚动过程不会每帧读 heightOffset。
    val smallTitleVisible by remember(barState) {
        derivedStateOf { (barState?.collapsedFraction ?: 0f) * 3f >= 1f }
    }

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
                navigationIcon = {
                    // 只是一层「量宽」包裹：内容原样、尺寸不变，标题定位照旧
                    // （Miuix 的导航槽是 Box(padding(start = NavigationIconPadding)){导航内容}）。
                    Box(Modifier.onSizeChanged { navigationSlotWidthPx = it.width }) {
                        navigationIcon()
                    }
                },
                actions = {
                    // 同上：这层内层 Row 只用于量宽。它是 wrap content，与 Miuix 自己那层
                    // Arrangement.End 的 Row 摆放位置一致，不改变图标位置。
                    Row(
                        modifier = Modifier.onSizeChanged { actionSlotWidthPx = it.width },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions()
                    }
                },
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
                        // 水平方向是「刘海 + 导航栏」两种横向 inset 之和 + TitlePadding。
                        // 数值在上面提前读好了。
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

            // 标题点击覆盖层：原版 TopAppBar 的 `title` 是 String，没有 MD3 那样的
            // `titleContent` 槽位，所以「点标题开模型选择 / 长按改标题」只能叠透明可点层。
            //
            // 命中区必须跟着**可见的那个标题**走，而不是钉死在大标题矩形上：MD3 侧
            // 同一个 titleContent lambda 会被 TwoRowsTopAppBar 的收起行与展开行各渲染
            // 一份、两份都带 combinedClickable，所以两态都能点；Miuix 这里就拆成两段
            // 互补的矩形，「小标题可见」与「大标题可见」的阈值与 Miuix 内部一致：
            //
            // ① 折叠行中间段（小标题那一行）：只在非搜索态存在。
            //    x ∈ [startInsetTotal + NavigationIconPadding + navW,
            //         宽 - endInsetTotal - ActionIconPadding - actW]
            //    y ∈ [topInset, 栏底]
            //    纵向**从 topInset 起算**（不是 topInset + CollapsedHeight），所以顶栏被
            //    列表滚到折叠态时，命中的正是用户看得见的小标题 + 小副标题那一行；
            //    横向边界 = 「顶栏内容区横向 inset」+ 16dp 槽内边距 + 槽的实测内容宽
            //    navW / actW（onSizeChanged 量的），正好落在两个图标槽的可见边缘，槽的
            //    placeable 宽度 = 16dp + 内容宽（见 miuix-ui TopAppBar.kt 的 navigationIcon
            //    / actionIcons 两个 layoutId）。
            //    **条件**：这条边界正确的前提是 startInsetTotal/endInsetTotal 取
            //    「displayCutout 与 navigationBars 的逐侧并集」（= 原版两次链式
            //    windowInsetsPadding 的合成量，推导见上面的取值注释）。t12 用的是单侧
            //    取值（左 displayCutout.left、右 navigationBars.right），只在「刘海在左、
            //    导航栏在右」这一种摆放（竖屏常见）下成立；横屏旋转成「刘海在右 / 导航栏
            //    在左」时左边界会少算 navigationBars.left、右边界会少算 displayCutout.right，
            //    ① 就会盖住图标槽并抢走返回/搜索/新建的点击。改成并集后，两种摆放都不越界。
            // ② 大标题整段（大标题 + 副标题）：x ∈ [startInset, 宽 - endInset]
            //    （沿用 t12 的既有单侧口径）、y ∈ [topInset + CollapsedHeight, 栏底]，
            //    并且只在**大标题可见**（未折叠，`!smallTitleVisible`）时存在。
            //    留这一段是因为①的左边界在 navW 之后：大标题文字从 TitlePadding(26dp) 起排，
            //    比导航槽右沿还靠左，若只保留①，大标题最左边约 30dp（短标题如「新对话」
            //    的首字）会变成点不动的死区；而它纵向在折叠行图标之下（折叠行高 52dp、
            //    图标垂直居中于 26dp、40dp 高 ⇒ 只到 46dp），几何上本来就不冲突。
            //    反向门控（折叠态不渲染②）是为了不留「小标题下方一条看不见却能点」的空带：
            //    栏底由 contentTop 撑到约 64dp，折叠态 ② 只剩小标题下方约 12dp 的空白。
            //
            // 搜索展开态（p > 0.001f）两段都不存在：那时输入框在场，点哪儿都是聚焦输入框。
            //
            // 覆盖层是底层 TopAppBar 的兄弟节点、后画在上，点击由它消费；纵向拖动超过
            // touchSlop 后由父级的 draggable 消费并让 tap 取消，所以「点标题」与
            // 「拖顶栏折叠」仍然共存。
            if (p <= 0.001f && (onTitleClick != null || onTitleLongClick != null)) {
                val navSlotWidth = with(density) { navigationSlotWidthPx.toDp() }
                val actionSlotWidth = with(density) { actionSlotWidthPx.toDp() }

                // ① 折叠行中间段：折叠态唯一可见的标题就在这里（与 MD3 收起行同义）。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(
                            start = with(density) { startInsetTotal.toDp() } +
                                TopAppBarDefaults.NavigationIconPadding + navSlotWidth,
                            end = with(density) { endInsetTotal.toDp() } +
                                TopAppBarDefaults.ActionIconPadding + actionSlotWidth,
                            top = with(density) { topInset.toDp() },
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTitleClick?.invoke() },
                            onLongClick = onTitleLongClick,
                        ),
                )

                // ② 大标题整段：只在大标题可见时存在（见上面的门控理由）。
                if (!smallTitleVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(
                                start = with(density) { startInset.toDp() },
                                end = with(density) { endInset.toDp() },
                                top = with(density) { topInset.toDp() } +
                                    TopAppBarDefaults.CollapsedHeight,
                            )
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTitleClick?.invoke() },
                                onLongClick = onTitleLongClick,
                            ),
                    )
                }
            }
        }
    }
}
