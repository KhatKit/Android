package heizige.kk.khatkit.uikit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.adaptive.KedgeLargeTopAppBar
import heizige.kk.kedge.adaptive.KedgeNavigationBar
import heizige.kk.kedge.adaptive.KedgeNavigationItem
import heizige.kk.kedge.adaptive.rememberKedgeExitUntilCollapsedScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.khromia.components.LocalBottomSheetDismiss
import heizige.kk.khromia.layout.FullscreenPopup
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.KedgeColors
import kotlinx.coroutines.flow.distinctUntilChanged

/** 自绘弹层的默认高度：与 Khromia `PrimaryBottomSheet` 的 0.9 屏上限对齐。 */
private const val DEFAULT_HEIGHT_FRACTION = 0.9f

/**
 * 卡片专用的 BottomSheet（`ui.form` / `ui.screen` / `ui.show` / `ui.sheet` / `web.openLogin` 共用）。
 *
 * 为什么单独开一个而不是继续走 Khromia `PrimaryBottomSheet`：那个组件的顶栏（图标 + 标题）
 * 和底栏（取消 / 确认按钮行）是**写死**在 `BasicBottomSheet` 的 `dragHandle` / `bottomBar`
 * 槽位里的，也没有全屏与锁定关闭的口子 —— 它自己内部把高度钳在 0.9 屏、
 * `dismissible` 的 Kedge（Miuix）分支还被忽略。卡片需要按次控制这五件事，
 * 所以在这里按 `PrimaryBottomSheet` 的**同一套视觉**（同样的图标 + 标题行、同样的
 * 圆形图标 + 标题 + 按钮底栏、同样的 20dp 圆角与 0.6 遮罩）重画一层，
 * 遮罩 / 顶栏 / 底栏 / 手势 / 返回键全部由本文件说了算。
 *
 * 支持的开关（全部来自 [UiSheetOptions]，脚本侧字段名见 `docs/lua-card-development.md` §4.2）：
 *
 * | 开关 | 效果 |
 * |---|---|
 * | `fullscreen` | 铺满整个窗口、去圆角，仍然可以下滑 / 返回关闭（除非锁定） |
 * | `height` | 高度占屏比例 0.1–1.0，默认 0.9 |
 * | `hide_topbar` | 不画顶栏（拖柄条与标题行都不画），内容顶到屏幕上边缘 |
 * | `hide_bottombar` | 不画底栏；**锁定态下被忽略**（底栏是用户唯一出口） |
 * | `dismissable=false` | 遮罩点击 / 返回键 / 下滑都不能关闭，只能点弹层里的按钮 |
 *
 * 方向（`orientation`）不在这里处理：那是宿主级开关，由 `KhatKitUiHost` 改
 * `Activity.requestedOrientation`，默认 BottomSheet 与自绘弹层都能用。
 */
@Composable
fun KhatKitBottomSheet(
    title: String,
    imageVector: ImageVector,
    options: UiSheetOptions,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    headerActions: List<KhatKitSheetAction> = emptyList(),
    overflowActions: List<KhatKitSheetAction> = emptyList(),
    overflowIcon: ImageVector? = null,
    onAction: (KhatKitSheetAction) -> Unit = {},
    onDismiss: () -> Unit,
    /** 点底部导航条目时回传它的事件名（见 [UiSheetOptions.navigation]）。 */
    onNavigate: (String) -> Unit = {},
    /** 内容变化时换一个值即可把滚动位置归零（卡片换屏用；默认不重置）。 */
    scrollKey: Any? = null,
    /** 每次请求的内容标识（换页/加载更多后换值）：用于重置触底加载守卫。 */
    contentKey: Any? = null,
    lazyListState: LazyListState? = null,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val offsetY = remember { Animatable(0f) }
    val backProgress = remember { Animatable(0f) }
    val scrollState = remember(scrollKey) { ScrollState(0) }
    // Commit selection and content in the same frame. Optimistic selection left
    // the previous page visible under a newly selected tab while Lua was busy.
    var navigationPending by remember(contentKey) { mutableStateOf(false) }
    val navSelected = options.navigation?.selected
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 96.dp.toPx() }
    val hasTopBar = headerActions.isNotEmpty() ||
        (overflowIcon != null && overflowActions.isNotEmpty())
    val dismissable = options.dismissable
    val navigation = options.navigation
    val navVisible = navigation != null && navigation.items.isNotEmpty()
    // 导航底栏就是底栏：有它时操作底栏让位；锁定态例外，出口不能被导航挤掉。
    val bottomBarVisible = if (navVisible) !dismissable else (!options.hideBottomBar || !dismissable)
    val lockedCloseText = dismissText ?: stringResource(R.string.khatkit_close)
    val heightFraction = when {
        options.fullscreen -> 1f
        else -> options.height ?: DEFAULT_HEIGHT_FRACTION
    }
    // 大标题栏用 Kedge 的折叠行为：顶栏观察的是喂给内容滚动的这个实例。
    val largeTopBar = !options.hideTopBar && options.largeTopBar
    val topBarBehavior = if (largeTopBar) rememberKedgeExitUntilCollapsedScrollBehavior() else null

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val target = (offsetY.value + delta).coerceAtLeast(0f)
            offsetY.snapTo(target)
        }
    }

    // 锁定时给一个**非空**的 no-op：FullscreenPopup 只在 onDismiss != null 时吃掉返回键，
    // 传 null 会让 BACK 穿透到 Activity（退到聊天页 / 退出应用），等于绕过锁定。
    val popupDismiss: () -> Unit = if (dismissable) onDismiss else ({})

    // 预测性返回：手势进度直接推到弹层位移上，松手提交才关闭，取消弹回。
    if (dismissable) {
        PredictiveBackHandler { backEvents ->
            try {
                backEvents.collect { event ->
                    backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                onDismiss()
            } catch (error: CancellationException) {
                backProgress.animateTo(0f, tween(durationMillis = 180))
                throw error
            }
        }
    }

    // 触底加载：距离底部 64dp 内回传一次 load_more；新内容到达后守卫复位。
    val loadMoreEvent = options.loadMoreEvent
    var loadMoreArmed by remember(contentKey) { mutableStateOf(true) }
    LaunchedEffect(loadMoreEvent, scrollState.value, scrollState.maxValue) {
        if (lazyListState != null || loadMoreEvent == null || !loadMoreArmed) return@LaunchedEffect
        val thresholdPx = with(density) { 64.dp.toPx() }
        if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - thresholdPx) {
            loadMoreArmed = false
            onNavigate(loadMoreEvent)
        }
    }
    LaunchedEffect(lazyListState, contentKey, loadMoreEvent) {
        val list = lazyListState ?: return@LaunchedEffect
        val event = loadMoreEvent ?: return@LaunchedEffect
        var armed = true
        snapshotFlow {
            val layout = list.layoutInfo
            layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 2
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && armed) {
                armed = false
                onNavigate(event)
            }
        }
    }

    FullscreenPopup(onDismiss = popupDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 遮罩是独立兄弟层：点空白关闭，但点击弹层表面不会穿透到这里。锁定时遮罩不可点。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = dismissable,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (options.fullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.height(screenHeight * heightFraction)
                        },
                    )
                    .offset {
                        IntOffset(
                            0,
                            offsetY.value.roundToInt() +
                                (backProgress.value * screenHeight.toPx()).roundToInt(),
                        )
                    }
                    .then(
                        if (dismissable) {
                            Modifier.draggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    if (offsetY.value > dismissThreshold || velocity > 800f) {
                                        onDismiss()
                                    } else {
                                        scope.launch { offsetY.animateTo(0f) }
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(
                        if (options.fullscreen) RoundedCornerShape(0.dp)
                        else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ),
                // 大标题栏用的是 Miuix/M3 的 surface；弹层如果还是 surfaceContainerLow，
                // 状态栏区域（= 弹层底色）会比顶栏亮一档，看起来像两截。统一到 surface。
                color = KedgeColors.surface,
                shadowElevation = 8.dp,
            ) {
                // 大标题栏自己会消费状态栏 inset 并把底色画到状态栏后面（和聊天抽屉同一套做法：
                // 弹层不垫顶部 inset，栏自己画）。所以全屏 + 大顶栏时只垫底部导航栏；
                // 其余路径（拖柄顶栏 / 隐藏顶栏）保持整块 safeDrawing 内缩。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (largeTopBar && options.fullscreen) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            },
                        ),
                ) {
                    if (largeTopBar) {
                        KedgeLargeTopAppBar(
                            title = title,
                            scrollBehavior = topBarBehavior,
                            // 不做毛玻璃：弹层是全屏实心页面，透明顶栏会透出下面的聊天页，
                            // 状态栏与顶栏也会因此串色。
                            backdrop = null as LayerBackdrop?,
                            actions = {
                                options.topActions.forEach { item ->
                                    KedgeIconButton(
                                        onClick = { onNavigate(item.event) },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            imageVector = item.icon?.let { UiIconVectors.vector(it) }
                                                ?: Icons.Filled.Circle,
                                            contentDescription = item.label,
                                        )
                                    }
                                }
                            },
                        )
                    } else if (!options.hideTopBar) {
                        if (hasTopBar) {
                            SheetTopBar(
                                title = title,
                                headerActions = headerActions,
                                overflowActions = overflowActions,
                                overflowIcon = overflowIcon,
                                onAction = onAction,
                            )
                        } else {
                            SheetDragHandle()
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (topBarBehavior != null) {
                                    Modifier.nestedScroll(topBarBehavior.nestedScrollConnection)
                                } else {
                                    Modifier
                                },
                            )
                            .then(if (lazyListState == null) Modifier.verticalScroll(scrollState) else Modifier),
                    ) {
                        content(onDismiss)
                    }
                    if (navVisible) {
                        KedgeNavigationBar(
                            selectedItem = navSelected ?: navigation.items.first().event,
                            onItemSelected = { event ->
                                if (!navigationPending && event != navSelected &&
                                    navigation.items.any { it.event == event && it.enabled }) {
                                    navigationPending = true
                                    onNavigate(event)
                                }
                            },
                            navigationItems = navigation.items.map { item ->
                                KedgeNavigationItem(
                                    key = item.event,
                                    label = item.label,
                                    icon = item.icon?.let { UiIconVectors.vector(it) } ?: Icons.Filled.Circle,
                                    enabled = item.enabled,
                                )
                            },
                        )
                    }
                    if (bottomBarVisible) {
                        if (hasTopBar) {
                            SheetCloseBar(dismissText, onDismiss)
                        } else {
                            SheetBottomBar(
                                title = title,
                                imageVector = imageVector,
                                dismissText = if (dismissable) dismissText else lockedCloseText,
                                confirmText = confirmText,
                                onConfirm = onConfirm,
                                onDismiss = onDismiss,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 弹层顶部：拖动把手 + 标题行（标题在左，溢出菜单与图标按钮在右上角）。
 *
 * 图标按钮的无障碍描述直接用 action 的 label，点击后由调用方回传 event。
 */
@Composable
internal fun SheetTopBar(
    title: String,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    overflowIcon: ImageVector?,
    onAction: (KhatKitSheetAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KedgeColors.surfaceVariant.copy(alpha = 0.54f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    SheetGrabber()
                }
                if (overflowIcon != null && overflowActions.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    KedgeIconButton(
                        onClick = { expanded = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = overflowIcon,
                            contentDescription = stringResource(R.string.khatkit_sheet_more_actions),
                        )
                    }
                    KedgeDropdownMenuSlots(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        overflowActions.forEach { action ->
                            KedgeDropdownItemSlot(
                                text = { Text(action.label) },
                                leadingIcon = { Icon(action.imageVector, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onAction(action)
                                },
                            )
                        }
                    }
                }
                headerActions.forEach { action ->
                    KedgeIconButton(
                        onClick = { onAction(action) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(action.imageVector, contentDescription = action.label)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = KedgeTextStyles.body(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun SheetDragHandle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KedgeColors.surfaceVariant.copy(alpha = 0.54f))
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetGrabber()
    }
}

/** 那根 64×4 的圆角小横条（拖柄视觉，不承担手势）。 */
@Composable
private fun SheetGrabber() {
    Box(
        modifier = Modifier
            .height(4.dp)
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KedgeColors.onSurfaceVariant.copy(alpha = 0.87f)),
    )
}

/**
 * 顶栏布局下的底部操作栏：只留一个关闭按钮（标题已经在顶栏）。
 *
 * [onDismiss] 为空时退回 [LocalBottomSheetDismiss]（只有 Khromia `BasicBottomSheet` 内部才有值，
 * 自绘弹层里必须显式传入，否则按钮点了没反应）。
 */
@Composable
internal fun SheetCloseBar(dismissText: String?, onDismiss: (() -> Unit)? = null) {
    val actualDismissText = dismissText ?: stringResource(R.string.khatkit_close)
    val sheetDismiss = LocalBottomSheetDismiss.current
    val dismiss = onDismiss ?: sheetDismiss
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KedgeColors.surfaceVariant.copy(alpha = 0.26f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        KedgeButton(
            onClick = { dismiss() },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(actualDismissText)
        }
    }
}

/** 默认底栏：圆形图标 + 标题 + 右侧确认 / 关闭按钮（沿用 `PrimaryBottomSheet` 的样式）。 */
@Composable
private fun SheetBottomBar(
    title: String,
    imageVector: ImageVector,
    dismissText: String?,
    confirmText: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val actualDismissText = dismissText ?: stringResource(heizige.kk.khromia.R.string.bottom_sheet_dismiss)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KedgeColors.surfaceVariant.copy(alpha = 0.26f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .background(KedgeColors.surfaceVariant.copy(alpha = 0.54f), CircleShape)
                .padding(8.dp),
            tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(text = title, style = KedgeTextStyles.body())

        Spacer(modifier = Modifier.weight(1f))

        KedgeButton(
            onClick = { onConfirm?.invoke() ?: onDismiss() },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(confirmText ?: actualDismissText)
        }
    }
}
