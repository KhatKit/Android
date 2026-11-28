package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.uikit.mochi.MochiTooltipIconButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import heizige.kk.khatkit.app.core.ui.icons.utility.check
import heizige.kk.khatkit.app.core.ui.icons.navigation.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.utility.dashboard
import heizige.kk.khatkit.app.core.ui.icons.utility.edit
import heizige.kk.khatkit.app.core.ui.icons.navigation.refresh
import heizige.kk.khatkit.app.core.ui.icons.utility.terminal
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.uikit.Render
import kotlinx.coroutines.awaitCancellation
import ui.feice.mochi.effect.content.MochiMorphIcon
import kotlinx.coroutines.delay
import heizige.kk.khatkit.uikit.mochi.kkAnimateContentSize
import heizige.kk.khatkit.app.feature.tools.ToolCatalogueWidget
import heizige.kk.khatkit.app.feature.tools.ToolCatalogueViewModel
import heizige.kk.khatkit.app.feature.tools.ToolEntryIcon
import heizige.kk.khatkit.app.core.util.toolCategory
import heizige.kk.khatkit.app.core.util.toolDescription
import heizige.kk.khatkit.app.core.util.toolTitle
import heizige.kk.khatkit.app.core.util.recommendedTools
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.app.feature.chat.helper.ChatWelcomeManager
import heizige.kk.khatkit.app.feature.chat.data.ChatWelcomeState



/** An independent overlay: it never changes message-list keys or scroll indices. */
@Composable
internal fun ChatWelcomeHost(
    visible: Boolean,
    sessionKey: String,
    manager: ChatWelcomeManager,
    inputBarHeight: Dp,
    topInset: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val revision by manager.provider.cardCatalogueRevision.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val locales = remember(configuration) {
        (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(visible, revision, locales, lifecycle) {
        if (visible) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                manager.prepare(locales, revision)
                awaitCancellation()
            } finally {
                manager.pause()
            }
        }
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        ChatWelcome(sessionKey, state, locales, manager, inputBarHeight, topInset, listState)
    }
}

@Composable
private fun ChatWelcome(
    sessionKey: String,
    state: ChatWelcomeState,
    locales: List<String>,
    manager: ChatWelcomeManager,
    inputBarHeight: Dp,
    topInset: Dp,
    listState: LazyListState,
) {
    var variation by rememberSaveable(sessionKey) { mutableIntStateOf(0) }
    val catalogue: ToolCatalogueViewModel = hiltViewModel(key = "welcome-tool-catalogue")
    val tools by catalogue.state.collectAsStateWithLifecycle()
    val headerIcons by catalogue.headerIcons.collectAsStateWithLifecycle()
    val recommendations = remember(tools.cards, sessionKey, variation) {
        recommendedTools(tools.cards, sessionKey.hashCode(), variation)
    }
    val nav = LocalKkNavController.current
    // U7b-2：显隐交给 MochiSheetState（show/hide）。不挂 mochiSheetBackdrop：欢迎页只是聊天页里透明的一块，
    // 那个修饰符在面板弹出过冲时会在它下面铺一层黑底，整页闪一下。
    val widgetSheet = rememberMochiSheetState()
    // 小组件编辑态：描边 + 拖边框改尺寸 + 拖卡片换位 + 移除 / 添加；由标题右侧的编辑按钮切换。
    var editing by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val widgets = remember(state.widgets) { state.widgets.filter { it.enabled } }
    val hasWidgetOptions = state.widgets.isNotEmpty() || state.terminalAvailable
    val hasEnabledWidgets = widgets.isNotEmpty() || state.terminalEnabled
    // 编辑态里把最后一个也移除了：没东西可编辑，退回「添加小组件」那一行。
    LaunchedEffect(hasEnabledWidgets) { if (!hasEnabledWidgets) editing = false }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 20.dp, bottom = inputBarHeight + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 这一区常驻：没有开着的组件时，标题右侧是管理入口、列表里是全宽「添加小组件」；
        // 有组件时标题按钮切编辑态，编辑态的网格末尾多一格「添加小组件」打开管理 sheet。
        if (hasWidgetOptions) {
            item(key = "widgets_title", contentType = "section") {
                val manage = !hasEnabledWidgets
                WelcomeSection(
                    stringResource(R.string.chat_widgets),
                    if (!manage && editing) check else edit,
                    stringResource(
                        when {
                            manage -> R.string.chat_widgets_manage
                            editing -> R.string.chat_widgets_done
                            else -> R.string.chat_widgets_edit
                        },
                    ),
                    onClick = { if (manage) widgetSheet.show() else editing = !editing },
                )
            }
            if (!hasEnabledWidgets) {
                item(key = "widgets_add", contentType = "widget") {
                    AddWidgetRow(Modifier.welcomeReveal(1)) { widgetSheet.show() }
                }
            } else {
                // 格子网格：尺寸、顺序都由用户在编辑态拖出来（manager 落盘），内容由卡片按格数给。
                item(key = "widgets", contentType = "widget") {
                    WelcomeWidgetBoard(
                        widgets = widgets,
                        locales = locales,
                        manager = manager,
                        terminalEnabled = state.terminalEnabled,
                        editing = editing,
                        onAdd = { widgetSheet.show() },
                        modifier = Modifier.welcomeReveal(1),
                    )
                }
            }
        }
        if (!hasEnabledWidgets) {
            item(key = "all_tools", contentType = "widget") {
                ToolCatalogueWidget()
            }
        }
        item(key = "recommendations_title", contentType = "section") {
            WelcomeSection(
                stringResource(R.string.chat_welcome_ideas), refresh,
                stringResource(R.string.chat_welcome_shuffle),
                onClick = {
                    when {
                        tools.cards.size > recommendations.size -> variation++
                        tools.cards.isNotEmpty() && !tools.complete -> catalogue.loadMore()
                        else -> catalogue.refresh()
                    }
                },
                loading = tools.loading && recommendations.isEmpty(),
            )
        }
        item(key = "recommendations", contentType = "recommendation") {
            if (recommendations.isNotEmpty()) {
                WelcomeRecommendedTools(recommendations, locales, tools.opening, headerIcons, 1 + widgets.size) { entry ->
                    val sourcePage = nav?.current as? Screen.Chat ?: return@WelcomeRecommendedTools
                    catalogue.open(entry) {
                        if (nav.current == sourcePage) {
                            nav.navigate(Screen.CardWidget(entry.name, entry.toolTitle(locales)))
                        }
                    }
                }
            } else if (!tools.loading && tools.error == null) {
                Text(stringResource(R.string.chat_welcome_no_recommended_cards), Modifier.padding(horizontal = 16.dp), color = MochiTheme.colorScheme.onSurfaceVariant)
            }
            tools.error?.let { error ->
                Text(error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MochiTheme.colorScheme.error)
            }
        }
    }
    PrimaryBottomSheet(
        state = widgetSheet,
        title = stringResource(R.string.chat_widgets_manage),
        imageVector = edit,
        onDismiss = { widgetSheet.hide() },
        scrollable = false,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // 行照抄欢迎页那批选项行（[MochiOptionItem] + [listCardStyle] 的整组圆角）：
            // 24dp 裸图标、title 字阶的标题、body 的说明、尾部一颗 KkSwitch。
            // 外面套 InlineCardGroup：gap 为 0 时整组是一张 Mochi 分组卡片（行间分隔线）。
            val cards = listCardStyle()
            InlineCardGroup(Modifier.fillMaxWidth()) {
                state.widgets.forEachIndexed { index, widget ->
                    val strings = remember(widget.card.manifest, locales) { CardStrings(widget.card.manifest, locales) }
                    MochiOptionItem(
                        onClick = { manager.setEnabled(widget.card.manifest.name, !widget.enabled) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cards.indexedShape(index, state.widgets.size),
                        leadingContent = {
                            Icon(dashboard, null, Modifier.size(24.dp), tint = MochiTheme.colorScheme.onSurfaceVariant)
                        },
                        titleContent = {
                            Text(
                                strings.widgetTitle,
                                style = MochiTheme.textStyles.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = strings.description.takeIf { it.isNotBlank() }?.let { description ->
                            {
                                Text(
                                    description,
                                    style = MochiTheme.textStyles.body,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailingContent = {
                            KkSwitch(
                                checked = widget.enabled,
                                onCheckedChange = { manager.setEnabled(widget.card.manifest.name, it) },
                            )
                        },
                    )
                }
                if (state.terminalAvailable) {
                    val terminalIndex = state.widgets.size
                    MochiOptionItem(
                        onClick = { manager.setTerminalEnabled(!state.terminalEnabled) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cards.indexedShape(terminalIndex, state.widgets.size + 1),
                        leadingContent = {
                            Icon(terminal, null, Modifier.size(24.dp), tint = MochiTheme.colorScheme.onSurfaceVariant)
                        },
                        titleContent = { Text(stringResource(R.string.chat_welcome_terminal_shortcut), style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(stringResource(R.string.chat_welcome_terminal_shortcut_desc), style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            KkSwitch(
                                checked = state.terminalEnabled,
                                onCheckedChange = manager::setTerminalEnabled,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 顶部问候区：大号问候语 + 一句引导。
 *
 * 原先那根渐变饰条已删 — 全屏唯一的强调色块会和下方的中性卡片抢视线，
 * 语气层级现在只靠字号和留白拉开。
 */
/**
 * 分区标题行：左边标题，右边一颗长按显示提示的 Mochi 图标按钮。
 *
 * 标题字号刻意抬到 OptionItem 的标题档（`title()` + Medium + onSurface）：
 * 以前用小字号的 footnote + onSurfaceVariant，和下面整组选项行不像同一套东西。
 *
 * 保留 24dp 标题布局高度；[loading] 只替换图标，不移动锚点。
 */
@Composable
private fun WelcomeSection(
    title: String,
    icon: ImageVector,
    action: String,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    // 和 Mochi 的分组小标题对齐：标题缩进到卡片内文字起点（16dp 页面边距 + 16dp 行内边距）
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MochiTheme.textStyles.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MochiTheme.colorScheme.onSurface,
        )
        WelcomeRefreshButton(action, icon, loading, onClick, Modifier.size(24.dp))
    }
}

/** Retain the button's center and touch target while replacing only its icon. */
@Composable
internal fun WelcomeRefreshButton(
    label: String,
    icon: ImageVector,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MochiTooltipIconButton(label, onClick = onClick, modifier = modifier, enabled = !loading) {
        if (loading) {
            KkProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            MochiMorphIcon(icon, tint = MochiTheme.colorScheme.onSurfaceVariant, size = 20.dp)
        }
    }
}

/**
 * 小组件全部关掉时的占位入口：一整行「添加小组件」，点开管理 sheet。
 *
 * 不用实心按钮，直接复用下面提示行那套 [MochiOptionItem]：24dp 裸图标 + title 字阶标题
 * + 尾部箭头。整页只有一种行，空态不会突然冒出一颗白胶囊把视觉重量抢走。
 */
@Composable
private fun AddWidgetRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = stringResource(R.string.chat_widgets_add)
    val cards = listCardStyle()
    MochiOptionItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cards.indexedShape(0, 1),
        leadingContent = { Icon(add, null, Modifier.size(24.dp), tint = MochiTheme.colorScheme.onSurfaceVariant) },
        titleContent = {
            Text(
                label,
                style = MochiTheme.textStyles.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(chevronRight, null, Modifier.size(18.dp), tint = MochiTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun WelcomeRecommendedTools(
    entries: List<CardIndexEntry>,
    locales: List<String>,
    opening: String?,
    headerIcons: Map<String, String>,
    baseOrder: Int,
    onOpen: (CardIndexEntry) -> Unit,
) {
    val cards = listCardStyle()
    InlineCardGroup(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            MochiOptionItem(
                onClick = { if (opening == null) onOpen(entry) },
                modifier = Modifier.fillMaxWidth().welcomeReveal(baseOrder + index),
                shape = cards.indexedShape(index, entries.size),
                leadingContent = {
                    ToolEntryIcon(entry, headerIcons[entry.name], 24.dp, MochiTheme.colorScheme.onSurfaceVariant)
                },
                titleContent = {
                    Text(entry.toolTitle(locales), style = MochiTheme.textStyles.titleMedium,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(entry.toolDescription(locales).ifBlank { entry.toolCategory().title },
                        style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    if (opening == entry.name) KkProgressIndicator(Modifier.size(18.dp))
                    else Icon(chevronRight, null, Modifier.size(18.dp), tint = MochiTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }
}

/**
 * 入场：整层按 40ms 阶梯淡入并上浮 14dp。
 *
 * 只动 `alpha` / `translationY`（都是合成层属性），不参与布局，所以不会让
 * LazyColumn 的测量结果在动画期间抖动；`order` 由调用点固定，不进 key。
 */
@Composable
private fun Modifier.welcomeReveal(order: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40L * order)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "welcomeReveal",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 14.dp.toPx()
    }
}
