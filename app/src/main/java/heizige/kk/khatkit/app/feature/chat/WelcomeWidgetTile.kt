package heizige.kk.khatkit.app.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.ui.CardWidgetLayout
import heizige.kk.khatkit.ui.WidgetFrame
import heizige.kk.khatkit.uikit.Render
import heizige.kk.khatkit.uikit.mochi.KkHaptics
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.kkAnimateContentSize
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurVisibility
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.shape.MochiConcentricContainer
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.shape.mochiBorder
import ui.feice.mochi.theme.MochiTheme

/** 外框内边距，和设置行（[MochiOptionItem]）的水平内边距一致；PLAIN / HEADER 的内容按它取同心圆角。 */
private val TilePadding: Dp = 16.dp

/** 编辑态「添加小组件」那一格占的格数。 */
private val AddTileCells = WidgetCells(2, 1)

/**
 * 欢迎页的小组件网格。
 *
 * - 平时：每格和设置里的选项卡片同一张皮（列表底色 + [listCardStyle] 的圆角）；
 *   能被用户运行的卡片点一下，从这一格长成卡片整页（[CardWidgetPage]）。
 * - 编辑态（[editing]，由标题右侧的编辑按钮切换）：每格出现描边和右上角的移除按钮，网格末尾多一格「添加小组件」，
 *   - 拖右/下/右下三个把手改尺寸：跟手，越过一格吸附并震一下，内容按新格数现挑版本
 *     （[heizige.kk.khatkit.ui.CardWidgetContent.pick]），松手落盘；
 *   - 按住卡片本身拖动换位置：卡片浮起跟手，原位留一块淡色占位；中心压到别的格子就和它换序，
 *     其它格子弹簧让位，松手吸附进占位并落盘。
 */
@Composable
internal fun WelcomeWidgetBoard(
    widgets: List<WelcomeWidget>,
    locales: List<String>,
    manager: ChatWelcomeManager,
    editing: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberKkHaptics()
    val reduceMotion = rememberKkReduceMotion()
    val nav = LocalKkNavController.current
    val gap = with(density) { WidgetGridGeometry.Gap.roundToPx() }
    val maxCell = with(density) { WidgetGridGeometry.MaxCell.roundToPx() }
    var width by remember { mutableIntStateOf(0) }
    val geometry = remember(width, gap, maxCell) { WidgetGridGeometry.of(width, gap, maxCell) }
    val names = widgets.map { it.card.manifest.name }
    // 「添加」那格排在最后，只占位置、不参与换序。
    val sizes = widgets.map { it.size } + listOfNotNull(AddTileCells.takeIf { editing })
    val rects = remember(sizes, geometry) { widgetRects(sizes, geometry) }
    val latestNames by rememberUpdatedState(names)
    val latestRects by rememberUpdatedState(rects)
    // 状态本身是快照状态；这张表只是按卡片名找回同一个对象，不参与重组。
    val states = remember { HashMap<String, WidgetResizeState>() }
    val move = remember { WidgetMoveState(scope) }
    // 被拖/拖尺寸的那格会盖到邻居和列表其它项上：整块网格抬高，子项里再把它抬到邻居上面。
    val active = move.name != null || move.settling != null || names.any { states[it]?.frame != null }

    fun slot(name: String): Offset = latestRects.getOrNull(latestNames.indexOf(name))?.topLeft ?: Offset.Zero

    // 被拖那格的中心压到别的组件上就换过去；离开刚换过的那格之前不再换，大小不一的格子不会来回抖。
    fun dragged(name: String, delta: Offset) {
        val topLeft = move.drag(delta)
        val rect = latestRects.getOrNull(latestNames.indexOf(name)) ?: return
        val center = topLeft + Offset(rect.width / 2, rect.height / 2)
        val hit = latestRects.take(latestNames.size).indexOfFirst { center in it }.takeIf { it >= 0 }?.let(latestNames::get)
        if (hit != move.hovered) move.hovered = null
        if (hit != null && hit != name && move.hovered == null) {
            move.hovered = hit
            manager.move(name, latestNames.indexOf(hit))
            haptics.tick()
        }
    }

    WidgetGrid(
        sizes = sizes,
        geometry = { WidgetGridGeometry.of(it, gap, maxCell) },
        modifier = modifier.fillMaxWidth().onSizeChanged { width = it.width }.zIndex(if (active) 1f else 0f),
    ) {
        widgets.forEachIndexed { index, widget ->
            val name = widget.card.manifest.name
            key(name) {
                val state = remember { states.getOrPut(name) { WidgetResizeState(scope) } }
                val strings = remember(widget.card.manifest, locales) { CardStrings(widget.card.manifest, locales) }
                val title = strings.widgetTitle
                val lifted = move.lifted(name)
                val reorder = listOfNotNull(
                    (index - 1).takeIf { it >= 0 }?.let { stringResource(R.string.chat_widget_move_earlier) to it },
                    (index + 1).takeIf { it < widgets.size }?.let { stringResource(R.string.chat_widget_move_later) to it },
                ).map { (label, target) ->
                    CustomAccessibilityAction(label) { manager.move(name, target); manager.persistOrder(); true }
                }
                WelcomeWidgetTile(
                    widget = widget,
                    title = title,
                    description = strings.description,
                    editing = editing,
                    geometry = geometry,
                    state = state,
                    haptics = haptics,
                    reduceMotion = reduceMotion,
                    manager = manager,
                    moving = move.name == name,
                    shift = { move.translation(name, slot(name)) },
                    reorderActions = reorder,
                    onOpen = if (nav != null && widget.card.manifest.supportsUser()) {
                        { nav.navigate(Screen.CardWidget(name, title), source = widgetSourceKey(name)) }
                    } else {
                        null
                    },
                    onRemove = {
                        haptics.confirm()
                        manager.setEnabled(name, false)
                    },
                    onMoveStart = {
                        move.start(name, slot(name))
                        haptics.dragStarted()
                    },
                    onMove = { dragged(name, it) },
                    onMoveEnd = {
                        haptics.dragStopped()
                        move.drop(slot(name), reduceMotion)
                        manager.persistOrder()
                    },
                    modifier = Modifier
                        .animateGridPlacement(animate = !reduceMotion && !lifted)
                        .zIndex(if (lifted || state.frame != null) 1f else 0f),
                )
            }
        }
        if (editing) {
            key("widgets_add_tile") {
                AddWidgetTile(onAdd, Modifier.animateGridPlacement(animate = !reduceMotion))
            }
        }
    }
}

/** 设置选项卡片的外形：一张独立卡片，圆角跟 设置 → 界面 → 列表卡片样式 走。 */
@Composable
private fun tileCorner(): Dp = listCardStyle().largeCorner

@Composable
private fun WelcomeWidgetTile(
    widget: WelcomeWidget,
    title: String,
    description: String,
    editing: Boolean,
    geometry: WidgetGridGeometry,
    state: WidgetResizeState,
    haptics: KkHaptics,
    reduceMotion: Boolean,
    manager: ChatWelcomeManager,
    moving: Boolean,
    shift: () -> Offset,
    reorderActions: List<CustomAccessibilityAction>,
    onOpen: (() -> Unit)?,
    onRemove: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = widget.card.manifest.name
    val corner = tileCorner()
    val shape = MochiRoundedRectangle(corner)
    val color = MochiListDefaults.colors().container
    val accent = MochiTheme.colorScheme.primary
    val dragging = state.edge != null
    val resize = { cells: WidgetCells -> manager.setSize(name, cells, persist = false) }
    val persist = { manager.setSize(name, widget.size, persist = true) }
    val sizeDescription = stringResource(R.string.chat_widget_size, title, widget.size.width, widget.size.height)
    val labels = listOf(
        stringResource(R.string.chat_widget_wider),
        stringResource(R.string.chat_widget_narrower),
        stringResource(R.string.chat_widget_taller),
        stringResource(R.string.chat_widget_shorter),
    )
    val removeLabel = stringResource(R.string.chat_widget_remove, title)
    val start by rememberUpdatedState(onMoveStart)
    val follow by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onMoveEnd)
    // 拿起来的那格放大一点、带阴影，像从网格里浮起来。
    val lift by animateFloatAsState(
        if (moving) 1f else 0f,
        if (reduceMotion) snap() else MochiSprings.snappy(300),
        label = "widget_lift",
    )
    // 进出编辑态：描边从外面收进来、把手淡入、叉号从小弹到原大；退出时反着走完再拆掉。
    val edit by animateFloatAsState(
        if (editing) 1f else 0f,
        if (reduceMotion) snap() else MochiSprings.snappy(350),
        label = "widget_edit",
    )
    Box(
        modifier
            .resizeFrame(state)
            .then(
                if (editing) {
                    Modifier.resizeSemantics(
                        sizeDescription, widget.size, widget.limits, if (widget.limits.resizable) labels else emptyList(),
                        resize, persist,
                        reorderActions + CustomAccessibilityAction(removeLabel) { onRemove(); true },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // 拖着走的时候原位留一块淡色占位：松手就吸附到这里。
        if (lift > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = lift }
                    .background(accent.copy(alpha = 0.10f), shape)
                    .mochiBorder(1.5.dp, accent.copy(alpha = 0.35f), shape),
            )
        }
        // 卡片本体和它的编辑装饰一起跟手平移；占位留在格子里不动。
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val offset = shift()
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val scale = 1f + 0.04f * lift
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = 16.dp.toPx() * lift
                        this.shape = shape
                    }
                    // 来源登记记录整张卡片：页面从这里长出来，关闭时缩回这里。
                    .then(if (onOpen != null) Modifier.mochiNavSource(widgetSourceKey(name), corner, color) else Modifier)
                    .then(if (onOpen != null && !editing) Modifier.mochiPressDeform() else Modifier)
                    .background(color, shape)
                    .clip(shape)
                    .then(
                        when {
                            // 编辑态按住卡片直接拖，越过触摸阈值就拿起来；把手和移除按钮盖在上面，先接到手势。
                            editing -> Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start() },
                                    onDragEnd = { end() },
                                    onDragCancel = { end() },
                                ) { change, amount ->
                                    change.consume()
                                    follow(amount)
                                }
                            }
                            onOpen != null -> Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = stringResource(R.string.chat_widget_open),
                                onClick = onOpen,
                            )
                            else -> Modifier
                        },
                    ),
            ) {
                WidgetTileContent(widget, title, description, corner, readOnly = editing || widget.loading, manager)
            }
            if (edit > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = edit.coerceIn(0f, 1f)
                            val scale = 1.03f - 0.03f * edit
                            scaleX = scale
                            scaleY = scale
                        }
                        .mochiBorder(
                            2.dp,
                            if (dragging || moving) accent else accent.copy(alpha = 0.55f),
                            shape,
                        ),
                )
                // 把手只在编辑态里组合（退出时直接拆，免得淡出途中还接得到拖动）。
                if (editing && widget.limits.resizable && !moving) {
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = edit.coerceIn(0f, 1f) }) {
                        WidgetResizeHandles(widget.size, widget.limits, geometry, state, haptics, reduceMotion, resize, persist)
                    }
                }
                if (!dragging && !moving) RemoveBadge(removeLabel, { edit }) { if (editing) onRemove() }
                if (dragging) WidgetSizeReadout(widget.size)
            }
        }
    }
}

/**
 * 编辑态右上角的移除按钮：26dp 的圆形叉号骑在卡片右上角，触摸区 44dp。
 * 只是关掉这个组件（[ChatWelcomeManager.setEnabled]），随时能从「添加小组件」再开回来，所以不二次确认。
 */
@Composable
private fun BoxScope.RemoveBadge(label: String, progress: () -> Float, onRemove: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = 14.dp, y = (-14).dp)
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onRemove,
            )
            .mochiPressDeform(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    val t = progress()
                    alpha = t.coerceIn(0f, 1f)
                    val scale = 0.4f + 0.6f * t
                    scaleX = scale
                    scaleY = scale
                }
                .background(MochiTheme.colorScheme.background, MochiCapsule)
                .padding(2.dp)
                .background(MochiTheme.colorScheme.onSurfaceVariant, MochiCapsule),
            contentAlignment = Alignment.Center,
        ) {
            Icon(close, label, Modifier.size(14.dp), tint = MochiTheme.colorScheme.surface)
        }
    }
}

/** 编辑态网格末尾的「添加小组件」格：虚一点的同款卡片，点开管理 sheet。 */
@Composable
private fun AddWidgetTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.chat_widgets_add)
    val shape = MochiRoundedRectangle(tileCorner())
    // 只在进入编辑态时才组合，进场靠这一拍从 false 翻到 true。
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(modifier) {
        MochiBlurVisibility(shown, Modifier.matchParentSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .mochiPressDeform()
                    .background(MochiListDefaults.colors().container.copy(alpha = 0.6f), shape)
                    .mochiBorder(1.5.dp, MochiTheme.colorScheme.outlineVariant, shape)
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(horizontal = TilePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(add, null, Modifier.size(20.dp), tint = MochiTheme.colorScheme.primary)
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MochiTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 格子里的内容：按当前格数挑一版，换版本（或脚本推了新内容）时做 Mochi 的模糊替换。
 * 各版本共用一份字段值，从小拖到大、再拖回来，输入不会丢。
 */
@Composable
private fun WidgetTileContent(
    widget: WelcomeWidget,
    title: String,
    description: String,
    corner: Dp,
    readOnly: Boolean,
    manager: ChatWelcomeManager,
) {
    val name = widget.card.manifest.name
    val content = widget.content
    val values = remember(content) {
        mutableStateMapOf<String, Any?>().apply {
            content?.let { c ->
                (listOf(c.root) + c.layouts.map { it.root }).forEach { root ->
                    root.initialValues().forEach { (key, value) -> if (key !in this) this[key] = value }
                }
            }
        }
    }
    val errors = remember(content) { mutableStateMapOf<String, String>() }
    val picked = content?.pick(widget.size.width, widget.size.height)
    MochiBlurCrossfade(
        targetState = picked,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) { layout ->
        val body: @Composable () -> Unit = { WidgetBody(widget, layout, values, errors, readOnly, manager) }
        when (layout?.frame ?: WidgetFrame.HEADER) {
            WidgetFrame.HEADER -> WelcomeWidgetFrame(
                title = title,
                description = description,
                corner = corner,
                loading = widget.loading,
                onRefresh = {
                    val root = layout?.root
                    errors.clear()
                    root?.errors(values)?.let(errors::putAll)
                    if (errors.isEmpty()) manager.refreshFromHeader(name, root?.collect(values).orEmpty())
                },
                modifier = Modifier.fillMaxSize(),
                content = body,
            )
            WidgetFrame.PLAIN -> Box(Modifier.fillMaxSize().padding(TilePadding)) {
                MochiConcentricContainer(corner, TilePadding, body)
            }
            WidgetFrame.FULL -> Box(Modifier.fillMaxSize()) {
                MochiConcentricContainer(corner, 0.dp, body)
            }
        }
    }
}

@Composable
private fun WidgetBody(
    widget: WelcomeWidget,
    layout: CardWidgetLayout?,
    values: MutableMap<String, Any?>,
    errors: MutableMap<String, String>,
    readOnly: Boolean,
    manager: ChatWelcomeManager,
) {
    val root = layout?.root
    if (root == null) {
        // 还没内容：失败给原因，空给一句说明；加载中只有标题行的转圈。
        if (widget.loading) return
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (widget.error != null) R.string.chat_widget_failed else R.string.chat_widget_empty),
                style = MochiTheme.textStyles.footnote,
                color = if (widget.error != null) MochiTheme.colorScheme.error else MochiTheme.colorScheme.onSurfaceVariant,
            )
            widget.error?.let {
                Text(
                    it,
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    root.Render(
        values = values,
        errors = errors,
        pickerHost = null,
        readOnly = readOnly,
        onAction = { action ->
            if (!readOnly) {
                errors.clear()
                errors.putAll(root.errors(values))
                if (errors.isEmpty()) manager.refresh(widget.card.manifest.name, action, root.collect(values))
            }
        },
        markdown = { text, modifier -> Box(modifier) { MarkdownBlock(text) } },
        image = { src, description, modifier ->
            AsyncImage(
                model = when {
                    src.startsWith("content://") -> android.net.Uri.parse(src)
                    src.startsWith("https://") -> src
                    else -> java.io.File(src)
                },
                contentDescription = description,
                modifier = modifier,
            )
        },
    )
}

/**
 * 默认外框（`frame = "header"`）：顶上一行就是设置里的选项行（[MochiOptionItem]：图标 + 标题 + 说明，
 * 行尾刷新），下面接卡片内容。底色和圆角归格子，这里只排版。
 */
@Composable
internal fun WelcomeWidgetFrame(
    title: String,
    description: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = listCardStyle().largeCorner,
    content: @Composable () -> Unit,
) {
    // 格子里尺寸是定死的，这里的尺寸弹簧只在独立使用（内容自己撑高）时起作用。
    Column(modifier.kkAnimateContentSize()) {
        MochiOptionItem(
            onClick = null,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            backgroundColor = Color.Transparent,
            leadingContent = {
                Icon(dashboard, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            titleContent = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = description.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = {
                WelcomeRefreshButton(stringResource(R.string.chat_widget_refresh), refresh, loading, onRefresh)
            },
        )
        Box(Modifier.fillMaxWidth().padding(start = TilePadding, end = TilePadding, top = 4.dp, bottom = TilePadding)) {
            MochiConcentricContainer(corner, TilePadding, content)
        }
    }
}
