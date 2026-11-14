package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.Tooltip
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.barChart
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.folderManaged
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.lightbulb
import heizige.kk.khatkit.app.core.ui.icons.musicNote
import heizige.kk.khatkit.app.core.ui.icons.questionAnswer
import heizige.kk.khatkit.app.core.ui.icons.receiptLong
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.school
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.translate
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.terminal
import heizige.kk.khatkit.app.core.ui.icons.videocam
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.uikit.Render
import kotlinx.coroutines.awaitCancellation
import ui.feice.mochi.effect.content.MochiMorphIcon
import kotlinx.coroutines.delay
import kotlin.random.Random
import heizige.kk.khatkit.uikit.mochi.kkAnimateContentSize
import heizige.kk.khatkit.app.feature.tools.ToolCatalogueWidget

/** An independent overlay: it never changes message-list keys or scroll indices. */
@Composable
internal fun ChatWelcomeHost(
    visible: Boolean,
    sessionKey: String,
    manager: ChatWelcomeManager,
    onSuggestionClick: (String) -> Unit,
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
        ChatWelcome(sessionKey, state, locales, manager, onSuggestionClick)
    }
}

/**
 * 一条「试试这些」。标题/说明/prompt 都是运行时字符串：
 * AI 生成的那批没有资源 id，静态兜底那批在组装时 `stringResource` 取出来。
 *
 * [subtitle] 不给 null：整组行必须是同一个两行高度。AI 那批以前只有标题，
 * 行高比静态那批矮一截，生成前后看着「间隔不一样」。
 */
private data class WelcomeIdea(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector,
)

@Composable
private fun ChatWelcome(
    sessionKey: String,
    state: ChatWelcomeState,
    locales: List<String>,
    manager: ChatWelcomeManager,
    onSuggestionClick: (String) -> Unit,
) {
    var variation by rememberSaveable(sessionKey) { mutableIntStateOf(0) }
    // U7b-2：显隐交给 MochiSheetState（show/hide）。不挂 mochiSheetBackdrop：欢迎页只是聊天页里透明的一块，
    // 那个修饰符在面板弹出过冲时会在它下面铺一层黑底，整页闪一下。
    val widgetSheet = rememberMochiSheetState()
    // 小组件编辑态：描边 + 拖边框改尺寸 + 拖卡片换位 + 移除 / 添加；由标题右侧的编辑按钮切换。
    var editing by rememberSaveable(sessionKey) { mutableStateOf(false) }
    // 静态兜底：AI 还没回来（或快速模型不可用/请求失败）时先摆这几条。
    // 只 remember 打乱后的**下标**，因为文案是 stringResource，必须在组合里取。
    val staticIdeas = listOf(
        WelcomeIdea(stringResource(R.string.chat_idea_web), stringResource(R.string.chat_idea_web_detail), stringResource(R.string.chat_idea_web_prompt), travelExplore),
        WelcomeIdea(stringResource(R.string.chat_idea_image), stringResource(R.string.chat_idea_image_detail), stringResource(R.string.chat_idea_image_prompt), image),
        WelcomeIdea(stringResource(R.string.chat_idea_files), stringResource(R.string.chat_idea_files_detail), stringResource(R.string.chat_idea_files_prompt), folderManaged),
        WelcomeIdea(stringResource(R.string.chat_idea_schedule), stringResource(R.string.chat_idea_schedule_detail), stringResource(R.string.chat_idea_schedule_prompt), schedule),
    )
    val order = remember(sessionKey, variation) {
        (0 until 4).shuffled(Random(sessionKey.hashCode() + variation))
    }
    // 有 AI 生成的就用 AI 的（prompt 就是标题本身，点一下直接发这句）；
    // 否则退回打乱后的静态三条。两批都是「标题 + 说明」两行，行高一致。
    val ideas = if (state.ideas.isEmpty()) {
        order.take(3).map { staticIdeas[it] }
    } else {
        state.ideas.take(3).map { idea ->
            WelcomeIdea(
                title = idea.title,
                subtitle = idea.hint,
                prompt = idea.title,
                icon = ideaIcon(WelcomeIdeas.icon(idea)),
            )
        }
    }
    // 欢迎页保留卡片小组件、可选的插件快捷方式和 AI 提示。
    val widgets = remember(state.widgets) { state.widgets.filter { it.enabled } }
    val hasWidgetOptions = state.widgets.isNotEmpty() || state.terminalAvailable
    val hasEnabledWidgets = widgets.isNotEmpty() || state.terminalEnabled
    // 编辑态里把最后一个也移除了：没东西可编辑，退回「添加小组件」那一行。
    LaunchedEffect(hasEnabledWidgets) { if (!hasEnabledWidgets) editing = false }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
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
        item(key = "ideas_title", contentType = "section") {
            WelcomeSection(
                stringResource(R.string.chat_welcome_ideas), refresh,
                stringResource(R.string.chat_welcome_shuffle),
                // 「换一换」= 让模型重新写三条；variation 仍然自增，AI 不可用时靠它换静态那批。
                onClick = {
                    variation++
                    manager.regenerateIdeas(locales)
                },
                loading = state.ideasLoading,
            )
        }
        item(key = "ideas", contentType = "suggestion") {
            WelcomeIdeaGroup(ideas, 1 + widgets.size, onSuggestionClick)
        }
    }
    PrimaryBottomSheet(
        state = widgetSheet,
        title = stringResource(R.string.chat_widgets_manage),
        imageVector = edit,
        onDismiss = { widgetSheet.hide() },
        scrollable = false,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
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
                        titleContent = { Text("终端快捷方式", style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("在欢迎页添加独立 Termux 终端入口", style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
 * 分区标题行：左边标题，右边一颗 Kedge 图标按钮。
 *
 * 标题字号刻意抬到 OptionItem 的标题档（`title()` + Medium + onSurface）：
 * 以前用小字号的 footnote + onSurfaceVariant，和下面整组选项行不像同一套东西。
 *
 * 按钮统一走 [MochiIconButton] 并传 `shapes`（与侧边栏、设置页同一口径）：
 * 两级回弹/圆角由组件自己叠，不在这里写死尺寸，免得 Miuix / MD3 两套皮肤
 * 里出现一颗「方角」的按钮。[loading] 时换成转圈，避免「换一换」像没反应。
 *
 * 外边距：按钮本身的最小触摸区是 48dp（Miuix / MD3 的 minimumInteractiveComponentSize），
 * 而标题行只有 24dp 高。若让按钮按 48dp 参与测量，这一行会被撑高 —— 标题上下各多出
 * 12dp，看上去「标题」和下面第一条 item 的间距比 item 之间（12dp）大一倍。
 * 这里用 `Modifier.size(24.dp)` 把**布局高度**压回标题行高：内层 48dp 触摸区不缩
 * （用户要求的「不改内边距」就是这层），只是居中溢出到行外 —— 上下各 12dp 正好落进
 * LazyColumn 的 12dp 间隔里，既不裁切也不吃掉相邻 item 的命中区。
 * 因此标题到第一条 item 的距离 = item 间距 = 12dp。
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
    Tooltip(label) {
        MochiIconButton(
            onClick = { if (!loading) onClick() },
            modifier = modifier.semantics { contentDescription = label },
            shapes = IconButtonDefaults.shapes(),
        ) {
            if (loading) {
                KkProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                // 编辑 ↔ 完成 这类换图标走 Mochi 的形变图标，不是硬切。
                MochiMorphIcon(icon, tint = MochiTheme.colorScheme.onSurfaceVariant, size = 20.dp)
            }
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

/**
 * AI 提示行：整行一条 [MochiOptionItem]，与设置页 / 助手列表的选项行同一套皮
 * （图标胶囊 + 标题 + 一句说明 + 尾部箭头，圆角与按压回弹都由组件决定）。
 *
 * 以前这里是两列磁贴网格（图标在上、文案在下、min 132dp），标题长短不一会让两张卡
 * 的视觉重量不齐；换成整行选项后，shuffle 换提示时行高也不再跳动。
 */
@Composable
private fun WelcomeIdeaGroup(
    ideas: List<WelcomeIdea>,
    baseOrder: Int,
    onSuggestionClick: (String) -> Unit,
) {
    // 与设置页 / 卡片市场同一份分组口径（[listCardStyle]）：整组拼成一张卡 ——
    // 首项只圆上边、末项只圆下边、中间四角用小圆角，项间距取 `gap`（默认 2dp，
    // Miuix 是 0）。只在组内这么排，组和「小组件」区之间仍保留列表的 12dp 呼吸位。
    val cards = listCardStyle()
    InlineCardGroup(Modifier.fillMaxWidth()) {
        ideas.forEachIndexed { index, idea ->
            WelcomeIdeaRow(
                idea = idea,
                order = baseOrder + index,
                shape = cards.indexedShape(index, ideas.size),
                onSuggestionClick = onSuggestionClick,
            )
        }
    }
}

/**
 * [WelcomeIdeas.Icon] → 仓里的图标。语义在 [WelcomeIdeas.icon] 里定（关键词表，
 * 纯数据可单测），这里只做一次查表，不参与判断。
 */
private fun ideaIcon(icon: WelcomeIdeas.Icon): ImageVector = when (icon) {
    WelcomeIdeas.Icon.Explore -> travelExplore
    WelcomeIdeas.Icon.Write -> editNote
    WelcomeIdeas.Icon.Files -> folderManaged
    WelcomeIdeas.Icon.Schedule -> schedule
    WelcomeIdeas.Icon.Code -> code
    WelcomeIdeas.Icon.Search -> search
    WelcomeIdeas.Icon.Translate -> translate
    WelcomeIdeas.Icon.Learn -> school
    WelcomeIdeas.Icon.Data -> barChart
    WelcomeIdeas.Icon.Money -> receiptLong
    WelcomeIdeas.Icon.Music -> musicNote
    WelcomeIdeas.Icon.Video -> videocam
    WelcomeIdeas.Icon.Chat -> questionAnswer
    WelcomeIdeas.Icon.Idea -> lightbulb
}

@Composable
private fun WelcomeIdeaRow(
    idea: WelcomeIdea,
    order: Int,
    shape: Shape,
    onSuggestionClick: (String) -> Unit,
) {
    // onClick 是非 @Composable 的 lambda，prompt 要先取出来。
    val prompt = idea.prompt
    // 度量照抄市场卡片行（`ExploreMarketPage` 的 `CompactCard` + `RowIcon`）：
    // 24dp 裸图标取 onSurfaceVariant、标题 title()+Medium、摘要 body()、尾部一行箭头。
    // 两边同一条 [MochiOptionItem] + 同一套 [listCardStyle] 圆角，所以卡片市场里长什么样，
    // 这里就长什么样。
    MochiOptionItem(
        onClick = { onSuggestionClick(prompt) },
        modifier = Modifier.fillMaxWidth().welcomeReveal(order),
        shape = shape,
        leadingContent = {
            Icon(idea.icon, null, Modifier.size(24.dp), tint = MochiTheme.colorScheme.onSurfaceVariant)
        },
        titleContent = {
            Text(
                idea.title,
                style = MochiTheme.textStyles.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                idea.subtitle,
                style = MochiTheme.textStyles.body,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(chevronRight, null, Modifier.size(18.dp), tint = MochiTheme.colorScheme.onSurfaceVariant)
        },
    )
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
