package heizige.kk.khatkit.app.feature.chat

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
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.Greeting
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.Tooltip
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.barChart
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
import heizige.kk.khatkit.app.core.ui.icons.videocam
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.Render
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.random.Random

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
    var manageWidgets by rememberSaveable { mutableStateOf(false) }
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
    // 只留「卡片的小组件」和 AI 提示两种内容：用户自己装的卡片（原「我的卡片」区）不再出现在欢迎页。
    val widgets = remember(state.widgets) { state.widgets.filter { it.enabled } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "greeting", contentType = "header") {
            WelcomeHero(Modifier.welcomeReveal(0))
        }
        // 这一区常驻：标题右侧的管理入口（edit）一直显示，方便随时进 sheet 打开新组件；
        // 全部关掉时列表位置换成那颗全宽的「添加小组件」按钮。
        if (state.widgets.isNotEmpty()) {
            item(key = "widgets_title", contentType = "section") {
                WelcomeSection(
                    stringResource(R.string.chat_widgets),
                    edit, stringResource(R.string.chat_widgets_manage),
                    onClick = { manageWidgets = true },
                )
            }
            if (widgets.isEmpty()) {
                item(key = "widgets_add", contentType = "widget") {
                    AddWidgetRow(Modifier.welcomeReveal(1)) { manageWidgets = true }
                }
            } else {
                // 整组拼成一张卡：圆角与项间距全交给 [listCardStyle]（软件口径），脚本只提供内容。
                // 组内顺序 = 添加顺序（manager 已按 CardCache.installOrder 排过），不在这里再排。
                item(key = "widgets", contentType = "widget") {
                    val cards = listCardStyle()
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(cards.gap)) {
                        widgets.forEachIndexed { index, widget ->
                            key(widget.card.manifest.name) {
                                WelcomeWidgetView(
                                    widget = widget,
                                    locales = locales,
                                    manager = manager,
                                    shape = cards.indexedShape(index, widgets.size),
                                    modifier = Modifier.welcomeReveal(1 + index),
                                )
                            }
                        }
                    }
                }
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
        visible = manageWidgets,
        title = stringResource(R.string.chat_widgets_manage),
        imageVector = edit,
        onDismiss = { manageWidgets = false },
        scrollable = false,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            // 行照抄欢迎页那批选项行（[KedgeOptionItem] + [listCardStyle] 的整组圆角）：
            // 24dp 裸图标、title 字阶的标题、body 的说明、尾部一颗 KedgeSwitch。
            // 外面不再套 InlineCardGroup —— 圆角要按 index/count 自己算成「一张卡」。
            val cards = listCardStyle()
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(cards.gap),
            ) {
                state.widgets.forEachIndexed { index, widget ->
                    val strings = remember(widget.card.manifest, locales) { CardStrings(widget.card.manifest, locales) }
                    KedgeOptionItem(
                        onClick = { manager.setEnabled(widget.card.manifest.name, !widget.enabled) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cards.indexedShape(index, state.widgets.size),
                        leadingContent = {
                            Icon(dashboard, null, Modifier.size(24.dp), tint = KedgeColors.onSurfaceVariant)
                        },
                        titleContent = {
                            Text(
                                strings.widgetTitle,
                                style = KedgeTextStyles.title(),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = strings.description.takeIf { it.isNotBlank() }?.let { description ->
                            {
                                Text(
                                    description,
                                    style = KedgeTextStyles.body(),
                                    color = KedgeColors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailingContent = {
                            KedgeSwitch(
                                checked = widget.enabled,
                                onCheckedChange = { manager.setEnabled(widget.card.manifest.name, it) },
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
@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Greeting(style = KedgeTextStyles.displayTitle())
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chat_welcome_question),
            style = KedgeTextStyles.bodyLarge(),
            color = KedgeColors.onSurfaceVariant,
        )
    }
}

/**
 * 分区标题行：左边标题，右边一颗 Kedge 图标按钮。
 *
 * 标题字号刻意抬到 OptionItem 的标题档（`title()` + Medium + onSurface）：
 * 以前用小字号的 footnote + onSurfaceVariant，和下面整组选项行不像同一套东西。
 *
 * 按钮统一走 [KedgeIconButton] 并传 `shapes`（与侧边栏、设置页同一口径）：
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
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = KedgeTextStyles.title().copy(fontWeight = FontWeight.Medium),
            color = KedgeColors.onSurface,
        )
        WelcomeRefreshButton(action, icon, loading, onClick, Modifier.size(24.dp))
    }
}

/** Retain the button's center and touch target while replacing only its icon. */
@Composable
private fun WelcomeRefreshButton(
    label: String,
    icon: ImageVector,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tooltip(label) {
        KedgeIconButton(
            onClick = { if (!loading) onClick() },
            modifier = modifier.semantics { contentDescription = label },
            shapes = IconButtonDefaults.shapes(),
        ) {
            if (loading) {
                KedgeProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(icon, null, Modifier.size(20.dp), tint = KedgeColors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 小组件全部关掉时的占位入口：一整行「添加小组件」，点开管理 sheet。
 *
 * 不用实心按钮，直接复用下面提示行那套 [KedgeOptionItem]：24dp 裸图标 + title 字阶标题
 * + 尾部箭头。整页只有一种行，空态不会突然冒出一颗白胶囊把视觉重量抢走。
 */
@Composable
private fun AddWidgetRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = stringResource(R.string.chat_widgets_add)
    val cards = listCardStyle()
    KedgeOptionItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cards.indexedShape(0, 1),
        leadingContent = { Icon(add, null, Modifier.size(24.dp), tint = KedgeColors.onSurfaceVariant) },
        titleContent = {
            Text(
                label,
                style = KedgeTextStyles.title(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(chevronRight, null, Modifier.size(18.dp), tint = KedgeColors.onSurfaceVariant) },
    )
}

/**
 * AI 提示行：整行一条 [KedgeOptionItem]，与设置页 / 助手列表的选项行同一套皮
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(cards.gap)) {
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
    // 两边同一条 [KedgeOptionItem] + 同一套 [listCardStyle] 圆角，所以卡片市场里长什么样，
    // 这里就长什么样。
    KedgeOptionItem(
        onClick = { onSuggestionClick(prompt) },
        modifier = Modifier.fillMaxWidth().welcomeReveal(order),
        shape = shape,
        leadingContent = {
            Icon(idea.icon, null, Modifier.size(24.dp), tint = KedgeColors.onSurfaceVariant)
        },
        titleContent = {
            Text(
                idea.title,
                style = KedgeTextStyles.title(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                idea.subtitle,
                style = KedgeTextStyles.body(),
                color = KedgeColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(chevronRight, null, Modifier.size(18.dp), tint = KedgeColors.onSurfaceVariant)
        },
    )
}

@Composable
private fun WelcomeWidgetView(
    widget: WelcomeWidget,
    locales: List<String>,
    manager: ChatWelcomeManager,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val strings = remember(widget.card.manifest, locales) { CardStrings(widget.card.manifest, locales) }
    val root = widget.content
    val values = remember(root) { mutableStateMapOf<String, Any?>().apply { root?.initialValues()?.let(::putAll) } }
    val errors = remember(root) { mutableStateMapOf<String, String>() }
    WelcomeWidgetFrame(
        title = strings.widgetTitle,
        description = strings.description,
        loading = widget.loading,
        shape = shape,
        modifier = modifier,
        onRefresh = {
            errors.clear()
            root?.errors(values)?.let(errors::putAll)
            if (errors.isEmpty()) manager.refreshFromHeader(widget.card.manifest.name, root?.collect(values).orEmpty())
        },
    ) {
        if (widget.error != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.chat_widget_failed), style = KedgeTextStyles.footnote(), color = KedgeColors.error)
                Text(widget.error, style = KedgeTextStyles.footnoteSmall(), color = KedgeColors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        AnimatedVisibility(
            visible = root != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(150)),
        ) {
            if (root != null) {
                KhatKitTheme(style = manager.provider.uiStyle, darkTheme = rememberIsDarkTheme()) {
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                        root.Render(
                            values = values, errors = errors, pickerHost = null, readOnly = widget.loading,
                            onAction = { action ->
                                errors.clear()
                                errors.putAll(root.errors(values))
                                if (errors.isEmpty()) manager.refresh(widget.card.manifest.name, action, root.collect(values))
                            },
                            markdown = { text, modifier -> Box(modifier) { MarkdownBlock(text) } },
                            image = { src, description, modifier ->
                                AsyncImage(
                                    model = if (src.startsWith("content://")) android.net.Uri.parse(src) else java.io.File(src),
                                    contentDescription = description, modifier = modifier,
                                )
                            },
                        )
                    }
                }
            }
        }
        if (root == null && !widget.loading && widget.error == null) {
            Text(
                stringResource(R.string.chat_widget_empty),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                style = KedgeTextStyles.footnote(),
                color = KedgeColors.onSurfaceVariant,
            )
        }
    }
}

/** Native settings row above script content; the parent animates all height changes. */
@Composable
internal fun WelcomeWidgetFrame(
    title: String,
    description: String,
    loading: Boolean,
    shape: Shape,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    KedgeSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = KedgeColors.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(260))) {
            KedgeOptionItem(
                onClick = null,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                backgroundColor = Color.Transparent,
                leadingContent = {
                    Icon(dashboard, null, Modifier.size(24.dp), tint = KedgeColors.onSurfaceVariant)
                },
                titleContent = {
                    Text(
                        title,
                        style = KedgeTextStyles.title(),
                        fontWeight = FontWeight.Medium,
                        color = KedgeColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = description.takeIf { it.isNotBlank() }?.let {
                    {
                        Text(
                            description,
                            style = KedgeTextStyles.body(),
                            color = KedgeColors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingContent = {
                    WelcomeRefreshButton(stringResource(R.string.chat_widget_refresh), refresh, loading, onRefresh)
                },
            )
            content()
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
