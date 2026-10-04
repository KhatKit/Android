package heizige.kk.khatkit.app.core.ui.components.message

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import heizige.kk.khatkit.app.core.ui.components.charts.ChartCard
import heizige.kk.khatkit.app.core.ui.components.charts.ChartSpec
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessageAnnotation
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.isEmptyUIMessage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.AssistantAffectScope
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.replaceRegexes
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.richtext.ZoomableAsyncImage
import heizige.kk.khatkit.app.core.ui.components.richtext.buildMarkdownPreviewHtml
import heizige.kk.khatkit.app.core.ui.components.webview.WebViewContentCache
import heizige.kk.khatkit.app.core.ui.components.ui.ChainOfThought
import heizige.kk.khatkit.app.core.ui.components.ui.Favicon
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.modifier.shimmer
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.theme.LocalChatFontFamily
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import heizige.kk.khatkit.app.core.ui.theme.extendColors
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.core.util.urlDecode
import heizige.kk.kedge.theme.KedgeColors
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.musicNote
import heizige.kk.khatkit.app.core.ui.icons.videocam
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface

private val EMPTY_BASE64_IMAGE_REGEX = Regex("^data:image/[^;]*;base64,\\s*$")

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    /**
     * 说话者名字（群聊里是角色名，由 `GroupSpeakerResolver` 算出）。null = 不显示，
     * 单聊路径保持原样：两个既有调用点（`ChatList` / `AssistantPromptPage`）不传就是旧行为。
     */
    speakerName: String? = null,
    /** 性质徽章文本（议长 / 投票小结 / 错误）。null = 不显示。 */
    speakerBadge: String? = null,
    /** [speakerBadge] 是否是错误节点——错误徽章染成 error 色，一眼能挑出来。 */
    speakerBadgeIsError: Boolean = false,
    /**
     * 群聊会话。true 时关掉「重新生成 / 删除 / 创建分支」：这三个回调打到
     * `ChatManager.regenerateAtMessage` / `deleteMessage` / `forkConversationAtMessage`，
     * 都不是群聊感知的，会让群运行日志的账面和实际轮次错位。判定口径由调用方用
     * `isGroupConversation(conversation)` 给出，不在这里另立一套。
     */
    groupChat: Boolean = false,
) {
    val message = node.messages[node.selectIndex]
    val settings = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    // 气泡上方的身份行。C1 之前这层完全不存在：群聊里 3 个角色 + 议长 + 投票小结 + 错误节点
    // 复用同一个气泡后长得一模一样。渲染条件只有一条——**真有东西可显示**才占位：
    // 单聊路径下 speakerName / speakerBadge 都是 null，这一行要么不渲染、要么只渲染原本
    // 就该显示却一直没被接上的头像（`showUserAvatar` / `showModelIcon` / `showModelName`
    // 三个开关因此重新生效），不会出现凭空多出来的空隙。
    val showUserAvatarLine = shouldShowUserAvatar(message, settings.showUserAvatar)
    val showAssistantAvatarLine = shouldShowAssistantAvatar(message, model, assistant)
    val showSpeakerLine = speakerName != null || speakerBadge != null ||
            showUserAvatarLine || showAssistantAvatarLine
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showSpeakerLine) {
            if (message.role == MessageRole.USER) {
                // 用户侧组件自带「名字 + 头像」右对齐的排版，直接用。
                ChatMessageUserAvatar(
                    message = message,
                    avatar = settings.userAvatar,
                    nickname = settings.userNickname,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChatMessageAssistantAvatar(
                        message = message,
                        loading = loading,
                        model = model,
                        assistant = assistant,
                        // 群聊已经有角色名了，别再并排挂一个助手名。
                        showName = speakerName == null,
                        // fill = false：头像行按内容宽占位，剩下的宽度留给徽章，
                        // 名字再长也不会把徽章挤出屏幕。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    speakerName?.let { name ->
                        Text(
                            text = name,
                            style = KedgeTextStyles.footnoteSmall(),
                            fontWeight = FontWeight.SemiBold,
                            color = KedgeColors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    speakerBadge?.let { badge ->
                        ChatMessageSpeakerBadge(text = badge, isError = speakerBadgeIsError)
                    }
                }
            }
        }

        ProvideTextStyle(textStyle) {
            MessagePartsBlock(
                assistant = assistant,
                role = message.role,
                parts = message.parts,
                annotations = message.annotations,
                loading = loading,
                model = model,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
            )

            message.translation?.let { translation ->
                CollapsibleTranslationText(
                    content = translation,
                    onClickCitation = {}
                )
            }
        }

        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }

        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation,
                    groupChat = groupChat,
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
        )

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(message = message)
        }

    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            groupChat = groupChat,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val contentColor = KedgeColors.onSurface.copy(alpha = 0.65f)

    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    groupedParts.fastForEach { block ->
        when (block) {
            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    val isReasoningOnlyBlock = block.steps.fastAll { it is ThinkingStep.ReasoningStep }
                    ChainOfThought(
                        modifier = Modifier.animateContentSize(),
                        steps = block.steps,
                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                        cardColors = CardDefaults.cardColors(
                            containerColor = KedgeColors.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        val textContent = @Composable {
                            if (role == MessageRole.USER) {
                                KedgeSurface(
                                    modifier = Modifier.animateContentSize(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = KedgeColors.primaryContainer.copy(alpha = settings.displaySetting.bubbleOpacity),
                                    onClick = { onUserMessageClick?.invoke() },
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        MarkdownBlock(
                                            content = part.text.replaceRegexes(
                                                assistant = assistant,
                                                scope = AssistantAffectScope.USER,
                                                visual = true,
                                            ),
                                            onClickCitation = handleClickCitation
                                        )
                                    }
                                }
                            } else {
                                if (settings.displaySetting.showAssistantBubble) {
                                    KedgeSurface(
                                        modifier = Modifier.animateContentSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = KedgeColors.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            MarkdownBlock(
                                                content = part.text.replaceRegexes(
                                                    assistant = assistant,
                                                    scope = AssistantAffectScope.ASSISTANT,
                                                    visual = true,
                                                ),
                                                onClickCitation = handleClickCitation,
                                            )
                                        }
                                    }
                                } else {
                                    MarkdownBlock(
                                        content = part.text.replaceRegexes(
                                            assistant = assistant,
                                            scope = AssistantAffectScope.ASSISTANT,
                                            visual = true,
                                        ),
                                        onClickCitation = handleClickCitation,
                                        modifier = Modifier
                                            .animateContentSize()
                                    )
                                }
                            }
                        }

                        // 流式生成期间不启用 SelectionContainer：Markdown 在不断重渲染，
                        // 内部可选择的 Text 会频繁注册/注销，与 Compose 选择工具栏在绘制阶段
                        // 对 selectable 列表的排序产生并发修改，导致 ConcurrentModificationException。
                        // 生成结束后内容稳定，再启用文本选择。
                        if (loading) {
                            textContent()
                        } else {
                            SelectionContainer {
                                textContent()
                            }
                        }
                    }

                    is UIMessagePart.Video -> {
                        KedgeSurface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(videocam, null)
                            }
                        }
                    }

                    is UIMessagePart.Audio -> {
                        KedgeSurface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = KedgeColors.secondaryContainer
                        ) {
                            ProvideTextStyle(KedgeTextStyles.footnoteSmall()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = musicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(EMPTY_BASE64_IMAGE_REGEX)
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(KedgeColors.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        KedgeSurface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = KedgeColors.tertiaryContainer
                        ) {
                            ProvideTextStyle(KedgeTextStyles.footnoteSmall()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (part.mime) {
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.docx),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        "application/pdf" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.pdf),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        else -> {
                                            Icon(
                                                imageVector = insertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = part.fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            var expand by remember { mutableStateOf(false) }
            if (expand) {
                ProvideTextStyle(
                    KedgeTextStyles.body().copy(
                        color = MaterialTheme.extendColors.gray8.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    color = contentColor.copy(alpha = 0.2f),
                                    size = Size(width = 10f, height = size.height),
                                )
                            }
                            .padding(start = 16.dp)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            KedgeTextButton(
                onClick = {
                    expand = !expand
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.citations_count, annotations.size))
            }
        }
    }
}

/**
 * 身份行右侧那枚小徽章：议长裁决 / 投票小结 / 错误。
 *
 * 刻意做得比正文更轻——脚注字号、小圆角、低饱和底色——一眼能看出这条消息是什么性质，
 * 但不能喧宾夺主。错误用 error 色，因为它是唯一需要人立刻处理的性质。
 */
@Composable
private fun ChatMessageSpeakerBadge(text: String, isError: Boolean) {
    KedgeSurface(
        shape = RoundedCornerShape(6.dp),
        color = if (isError) {
            KedgeColors.errorContainer.copy(alpha = 0.7f)
        } else {
            KedgeColors.surfaceContainerHigh
        },
    ) {
        Text(
            text = text,
            style = KedgeTextStyles.footnoteSmall(),
            color = if (isError) KedgeColors.onErrorContainer else KedgeColors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
