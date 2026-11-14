package heizige.kk.khatkit.app.core.ui.components.message

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import heizige.kk.khatkit.app.core.ui.components.ui.KkRichFilterChip
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextField
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import androidx.compose.foundation.layout.PaddingValues
import ui.feice.mochi.basic.MochiButton as MochiRealButton
import ui.feice.mochi.basic.MochiButtonStyle as MochiRealButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import heizige.kk.khatkit.ai.ui.ToolApprovalState
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.message.tools.ToolUIContext
import heizige.kk.khatkit.app.core.ui.components.message.tools.ToolUIRegistry
import heizige.kk.khatkit.app.core.ui.components.richtext.ZoomableAsyncImage
import heizige.kk.khatkit.app.core.ui.components.ui.ChainOfThoughtScope
import heizige.kk.khatkit.app.core.ui.components.ui.DotLoading
import heizige.kk.khatkit.app.core.ui.modifier.shimmer
import heizige.kk.khatkit.app.core.util.JsonInstant
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.questionAnswer
import heizige.kk.khatkit.uikit.mochi.MochiIconButtonVariant

internal const val ASK_USER_TOOL_NAME = "ask_user"

@Composable
fun ChainOfThoughtScope.ChatMessageServerToolStep(tool: UIMessagePart.ServerTool) {
    val loading = !tool.isFinished
    // 选项行（MochiOptionItem）皮：与设置页那种圆角底块一致，见 ChainOfThoughtScope.OptionItemStep
    OptionItemStep(
        icon = {
            if (loading) {
                DotLoading(size = 10.dp)
            } else {
                Icon(
                    imageVector = build,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        },
        label = {
            Text(
                text = stringResource(R.string.chat_message_tool_call_generic, tool.toolName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.shimmer(isLoading = loading),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** P1-1（上游 8bb488e3a）：工具执行耗时；执行中每 100ms 刷新，没有计时（拒绝/问答/旧消息）返回 null。 */
@Composable
private fun rememberToolDuration(tool: UIMessagePart.Tool, loading: Boolean): kotlin.time.Duration? {
    val startedAt = tool.startedAt
    val finishedAt = tool.finishedAt
    val running = loading && startedAt != null && finishedAt == null
    val now by androidx.compose.runtime.produceState(kotlin.time.Clock.System.now(), running) {
        while (running) {
            value = kotlin.time.Clock.System.now()
            kotlinx.coroutines.delay(100)
        }
    }
    return when {
        startedAt == null -> null
        finishedAt != null -> finishedAt - startedAt
        running -> now - startedAt
        else -> null
    }
}

@Composable
fun ChainOfThoughtScope.ChatMessageToolStep(
    tool: UIMessagePart.Tool,
    loading: Boolean = false,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
) {
    // ask_user 是交互式问答流程, 不走注册式渲染框架
    if (tool.toolName == ASK_USER_TOOL_NAME) {
        AskUserToolStep(tool = tool, loading = loading, onToolAnswer = onToolAnswer)
        return
    }

    val renderer = remember(tool.toolName) { ToolUIRegistry.resolve(tool.toolName) }
    val context = remember(tool, loading) {
        ToolUIContext(
            tool = tool,
            arguments = tool.inputAsJson(),
            content = if (tool.isExecuted) {
                runCatching {
                    JsonInstant.parseToJsonElement(
                        tool.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    )
                }.getOrElse { JsonObject(emptyMap()) }
            } else {
                null
            },
            loading = loading,
        )
    }

    var showResult by remember { mutableStateOf(false) }
    var showDenyDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val isPending = tool.isPending
    val isDenied = tool.approvalState is ToolApprovalState.Denied
    val images = tool.output.filterIsInstance<UIMessagePart.Image>()

    // 摘要由注册的渲染器决定; 图片输出与拒绝原因为所有工具通用
    val hasExtraContent = renderer.hasSummary(context) || isDenied || images.isNotEmpty()

    // 「调用工具」这一行用 MochiOptionItem 的皮（圆角底块 + 左图标），
    // 时间线/展开内容/批准按钮全部原样保留。
    ControlledOptionItemStep(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        icon = {
            if (loading) {
                DotLoading(
                    size = 10.dp
                )
            } else {
                Icon(
                    imageVector = renderer.icon(context),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = renderer.title(context),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.shimmer(isLoading = loading).weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // P1-1：执行耗时（执行中实时走字）；不足 100ms 与无计时的不显示
                rememberToolDuration(tool, loading)?.takeIf { it.inWholeMilliseconds >= 100 }?.let { d ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = d.toString(kotlin.time.DurationUnit.SECONDS, 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    )
                }
            }
        },
        extra = if (isPending && onToolApproval != null) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MochiIconButton(
                        variant = MochiIconButtonVariant.Tonal,
                        onClick = { showDenyDialog = true },
                        modifier = Modifier.size(28.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = close,
                            contentDescription = stringResource(R.string.chat_message_tool_deny),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    MochiIconButton(
                        variant = MochiIconButtonVariant.Tonal,
                        onClick = { onToolApproval(tool.toolCallId, true, "") },
                        modifier = Modifier.size(28.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = check,
                            contentDescription = stringResource(R.string.chat_message_tool_approve),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else {
            null
        },
        onClick = { showResult = true },
        content = if (hasExtraContent) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    renderer.Summary(context)
                    if (images.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            items(images) { image ->
                                ZoomableAsyncImage(
                                    model = image.url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(64.dp)
                                        .wrapContentWidth(),
                                )
                            }
                        }
                    }
                    if (isDenied) {
                        val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                        Text(
                            text = stringResource(R.string.chat_message_tool_denied) +
                                if (reason.isNotBlank()) ": $reason" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            null
        },
    )

    if (showDenyDialog && onToolApproval != null) {
        ToolDenyReasonDialog(
            onDismiss = { showDenyDialog = false },
            onConfirm = { reason ->
                showDenyDialog = false
                onToolApproval(tool.toolCallId, false, reason)
            }
        )
    }

        PrimaryBottomSheet(
            visible = showResult,
            title = renderer.title(context),
            imageVector = build,
            onDismiss = { showResult = false },
            scrollable = false,
        ) { dismiss ->
            renderer.Preview(
                context = context,
                onDismissRequest = dismiss,
            )
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainOfThoughtScope.AskUserToolStep(
    tool: UIMessagePart.Tool,
    loading: Boolean,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    val isPending = tool.isPending
    val isAnswered = tool.approvalState is ToolApprovalState.Answered
    val arguments = tool.inputAsJson()

    // Parse questions from arguments
    val questions = remember(arguments) {
        runCatching {
            arguments.jsonObject["questions"]?.jsonArray?.map { q ->
                val obj = q.jsonObject
                AskUserQuestion(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    question = obj["question"]?.jsonPrimitive?.contentOrNull ?: "",
                    options = obj["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    selectionType = obj["selection_type"]?.jsonPrimitive?.contentOrNull ?: "text"
                )
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    // Track answers for text/single questions
    val answers = remember { mutableStateMapOf<String, String>() }
    // Track selected options for multi questions
    val multiAnswers = remember { mutableStateMapOf<String, Set<String>>() }

    val firstQuestion = questions.firstOrNull()?.question ?: "..."

    var expanded by remember { mutableStateOf(true) }

    fun answerPayload(skip: Boolean): String = buildJsonObject {
        if (skip) {
            put("skipped", JsonPrimitive(true))
            put("message", JsonPrimitive("The user chose not to answer these questions. Continue without these answers."))
        }
        put("answers", buildJsonObject {
            questions.forEach { q ->
                val answer = if (skip) {
                    ""
                } else {
                    when (q.selectionType) {
                        "multi" -> (
                            multiAnswers[q.id].orEmpty().toList() +
                                listOfNotNull(answers[q.id]?.takeIf { it.isNotBlank() })
                            ).joinToString(", ")

                        else -> answers[q.id] ?: ""
                    }
                }
                put(q.id, JsonPrimitive(answer))
            }
        })
    }.toString()

    // 和思考、工具调用同一套皮：MochiOptionItem 的圆角底块 + 左图标，
    // 表单内容用 wrapContent 落进同一张卡（推理行同款）
    ControlledOptionItemStep(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        icon = {
            if (loading) {
                DotLoading(size = 10.dp)
            } else {
                Icon(
                    imageVector = questionAnswer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        },
        label = {
            Text(
                text = if (questions.size <= 1) firstQuestion else stringResource(
                    R.string.chat_message_tool_ask_questions,
                    questions.size
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.shimmer(isLoading = loading),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                questions.forEach { q ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = q.question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (isPending && onToolAnswer != null) {
                            if (q.options.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    q.options.forEach { option ->
                                        val selectedOptions = multiAnswers[q.id] ?: emptySet()
                                        KkRichFilterChip(
                                            selected = if (q.selectionType == "multi") {
                                                option in selectedOptions
                                            } else {
                                                answers[q.id] == option
                                            },
                                            onClick = {
                                                if (q.selectionType == "multi") {
                                                    multiAnswers[q.id] = if (option in selectedOptions) {
                                                        selectedOptions - option
                                                    } else {
                                                        selectedOptions + option
                                                    }
                                                } else {
                                                    answers[q.id] = option
                                                }
                                            },
                                            label = { Text(text = option, style = MaterialTheme.typography.labelSmall) },
                                        )
                                    }
                                }
                            }

                            Md3OutlinedTextField(
                                value = answers[q.id] ?: "",
                                onValueChange = { answers[q.id] = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = false,
                                minLines = 1,
                                maxLines = 3,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else if (isAnswered) {
                            // Show the user's answer
                            val answeredState = tool.approvalState as ToolApprovalState.Answered
                            val answerJson = runCatching {
                                JsonInstant.parseToJsonElement(answeredState.answer)
                            }.getOrNull()
                            val answerText = answerJson?.jsonObject?.get("answers")
                                ?.jsonObject?.get(q.id)?.jsonPrimitive?.contentOrNull
                                ?: answeredState.answer
                            Text(
                                text = answerText.ifBlank {
                                    stringResource(R.string.chat_message_tool_not_answered)
                                },
                                style = MochiTheme.textStyles.body,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 提交：通栏胶囊，Mochi 表单的主操作形态
                if (isPending && onToolAnswer != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MochiIconButton(
                            onClick = { onToolAnswer(tool.toolCallId, answerPayload(skip = true)) },
                            variant = MochiIconButtonVariant.Tonal,
                        ) {
                            Icon(
                                imageVector = close,
                                contentDescription = stringResource(R.string.chat_message_tool_skip),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        MochiRealButton(
                            onClick = { onToolAnswer(tool.toolCallId, answerPayload(skip = false)) },
                            enabled = questions.all { q ->
                                when (q.selectionType) {
                                    "multi" -> !multiAnswers[q.id].isNullOrEmpty() || !answers[q.id].isNullOrBlank()
                                    else -> !answers[q.id].isNullOrBlank()
                                }
                            },
                            style = MochiRealButtonStyle.Filled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.chat_message_tool_submit),
                                style = MochiTheme.textStyles.body,
                            )
                        }
                    }
                }
            }
        },
    )
}

private data class AskUserQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val selectionType: String = "text", // "text" | "single" | "multi"
)

@Composable
private fun ToolDenyReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    // DG1：material3 AlertDialog → MochiDialog（拒绝理由输入作为内容，「拒绝」是危险色按钮）
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_message_tool_deny_dialog_title),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(android.R.string.cancel),
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = stringResource(R.string.chat_message_tool_deny),
                role = MochiDialogActionRole.Destructive,
                onClick = { onConfirm(reason) },
            ),
        ),
        dismissOnOutsideTap = true,
    ) {
        Md3OutlinedTextFieldWithSlots(
            value = reason,
            onValueChange = { reason = it },
            label = { Text(stringResource(R.string.chat_message_tool_deny_dialog_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
