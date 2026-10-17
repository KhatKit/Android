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
import heizige.kk.kedge.components.KedgeMiuixSurface
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

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
    /**
     * 群聊「失败续跑」入口（C1）。非 null 时由 [ChatMessageActionButtons] 渲染续跑按钮；
     * 单聊与「非失败节点」恒为 null。判定由调用方用 `GroupRetryEntry.canResume` 给出，
     * 不在这里另立一套。它同样落到 `ChatManager.regenerateAtMessage`，但只在失败角色的
     * 错误节点上开放，因此能复用同一 `round_id` 且跳过已提交 turn（见 [groupChat] KDoc）。
     */
    onGroupResume: (() -> Unit)? = null,
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
     * 群聊会话。true 时关掉「重新生成 / 删除 / 创建分支」。判定口径由调用方用
     * `isGroupConversation(conversation)` 给出，不在这里另立一套。
     *
     * 前两个（`ChatManager.regenerateAtMessage` / `deleteMessage`）不群聊感知，会让群运行日志
     * 的账面和实际轮次错位：顶掉或删掉一条角色发言后，运行日志里那一轮仍声称该角色已提交，
     * 续跑就会跳过没人再发言的角色。
     *
     * 群聊**不是完全没有重试**：失败角色的错误节点上另有一个收窄的「失败续跑」入口
     * （`onGroupResume`，判定见 `GroupRetryEntry.canResume`）。它触发的仍是同一个
     * `ChatManager.regenerateAtMessage`，但只在「失败角色的错误节点且是全轮最后一个节点」
     * 上开放，因此复用同一 `round_id` 且跳过已提交 turn，不会顶掉已提交角色、账面对得上。
     *
     * 「创建分支」（`forkConversationAtMessage`）的**理由不一样**，别顺着上一句一起理解。
     * fork 对源会话只读——`createForkConversation` 造出新 id 后只 `saveConversation(新 id)`，
     * 一个字都不回写源会话，它**破坏不了群运行日志的账面**。真实问题是静默换会话类型：
     * `createForkConversation` 既不复制 `group_config` 也不复制 `type`，产物是一条把群聊
     * 内容摊平的**单聊**会话（角色名、议长小结、轮次账目全丢），而调用点只拿到新会话 id
     * 就直接 `navigateToChatPage` 跳过去，界面没有任何提示说明「这不是群聊分支」。
     * HTTP 侧那道门禁拒的是同一件事、用同一句口径，见
     * `GroupConversationOperationGuard.ConversationOperation.Fork`。
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

    // 身份行。C1 之前这层完全不存在：群聊里 3 个角色 + 议长 + 投票小结 + 错误节点
    // 复用同一个气泡后长得一模一样。现在只剩「说话者名字 / 性质徽章」这一档
    // （`speakerName` / `speakerBadge`，单聊下均为 null 时不占位）。
    //
    // ⚠️ 气泡旁的头像（用户侧头像列 + 助手侧 AI 图标列）已按需求**整列删除**：
    // AI 头像只在顶栏保留一处，用户头像不出现在消息流里。删除的是渲染路径本身，
    // 不是把它们隐藏 —— 隐藏会留下 28dp 的空列与 `spacedBy(8.dp)` 的空隙，气泡起始
    // 位置会整体缩进。两侧外壳因此都退化成「单列 Column」，气泡贴到页面边距起画。
    //
    // 持久化字段 `displaySetting.showUserAvatar` / `showModelIcon` **保留不动**
    // （旧数据与导入导出不破坏），只是不再有消费方；对应开关行已从双风格设置页删除。

    // 正文 + 尾部（操作栏 / 编辑文件 / 技术统计）。两侧外壳只剩对齐方向不同，内容只写一遍。
    val messageBody: @Composable () -> Unit = {
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
                onUserMessageClick = if (!groupChat && message.role == MessageRole.USER) onEdit else null,
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
                    onGroupResume = onGroupResume,
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

    if (message.role == MessageRole.USER) {
        // 用户侧：只留一列右对齐的正文（原先的「气泡 + 右侧头像列」横排已删）。
        // 不再套 Row，因此没有 `spacedBy(8.dp)` 的列间距、也没有 28dp 头像列，
        // 气泡直接从页面右侧内边距起画（与改动前相比整体右移 36dp = 28 + 8）。
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 昵称行保留：原先它与头像共用一个开关（showUserAvatarLine），开关随「删消息头像」
            // 一并删掉后这里改成无条件显示 —— 与默认开启时逐像素一致，不新增设置项。
            // 字号/字重沿用助手侧名字槽那套（脚注 + 半粗），否则两行名字一眼就能看出大小差。
            Text(
                text = settings.userNickname.ifEmpty { stringResource(R.string.user_default_name) },
                style = KedgeTextStyles.footnoteSmall(),
                fontWeight = FontWeight.SemiBold,
                color = KedgeColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            messageBody()
        }
    } else {
        // 助手侧：只留一列正文（原先「AI 图标占左列 + 正文在右列」的横排已删）。
        // 名字 / 性质徽章仍压在气泡上方，口径见 [assistantMessageLabel]；
        // 头像只在顶栏保留一处，消息流里不再出现，气泡因此左移到页面左边距。
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 名字 + 性质徽章：群聊是角色名，单聊是助手名 / 模型名，口径见此处。
            val label = assistantMessageLabel(
                speakerName = speakerName,
                model = model,
                assistant = assistant,
            )
            if (label != null || speakerBadge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    label?.let { name ->
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
            messageBody()
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

    // 助手气泡底色。**Miuix 下必须用卡片色**：那边 `surfaceContainerHigh` 只比页面底色亮一档
    // （深色实测 #1D202B vs 页面 #10131C，Δ13），真机上肉眼就是「没有气泡」；而同一屏的
    // 思考 / 工具卡片用的是 Miuix 卡片色 `surfaceContainer`（实测 #272A34），所以 Miuix 分支
    // 直接跟卡片同色。MD3 分支维持原来的 `surfaceContainerHigh`：那边页面底色是
    // `surfaceContainer`，`surfaceContainerHigh` 本来就比它亮一档，层次是对的，别一起改。
    val assistantBubbleColor = when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> KedgeMiuixSurface.cardContainer
        KedgeStyle.MD3Exp -> KedgeColors.surfaceContainerHigh
    }

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
                    // 全是圆角底块（推理 / 工具调用）时不要卡片底：连时间线槽与内边距一起去掉，
                    // 让 OptionItem 底块直接铺满消息宽度（见 ChainOfThought.showTimeline）
                    val isOptionItemOnlyBlock = block.steps.fastAll { it.usesOptionItemStyle() }
                    val hasOptionItemStep = block.steps.any { it.usesOptionItemStyle() }
                    ChainOfThought(
                        modifier = Modifier.animateContentSize(),
                        steps = block.steps,
                        showTimeline = !isOptionItemOnlyBlock,
                        cardColors = CardDefaults.cardColors(
                            containerColor = if (hasOptionItemStep) {
                                Color.Transparent
                            } else {
                                KedgeColors.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity)
                            },
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
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
                            } else if (settings.displaySetting.showAssistantBubble) {
                                // 开关**默认开**（`DisplaySetting.showAssistantBubble`）。
                                // 老安装的 JSON 里躺着显式 false（该字段随 encodeDefaults
                                // 一起落盘），由 `PreferenceStoreV6Migration` 一次性翻成 true，
                                // 所以「装完看不见气泡」不会再复发。关掉后退回裸 Markdown 是
                                // 用户主动选择，与用户侧 primaryContainer 气泡左右不对称属预期。
                                KedgeSurface(
                                    modifier = Modifier.animateContentSize(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = assistantBubbleColor.copy(alpha = settings.displaySetting.bubbleOpacity),
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
                                    modifier = Modifier.animateContentSize(),
                                )
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
