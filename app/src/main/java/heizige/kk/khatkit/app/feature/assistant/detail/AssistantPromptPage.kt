package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.transformers.DefaultPlaceholderProvider
import heizige.kk.khatkit.app.core.data.ai.transformers.TransformerContext
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.AssistantAffectScope
import heizige.kk.khatkit.app.core.data.model.AssistantRegex
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.message.ChatMessage
import heizige.kk.khatkit.app.core.ui.components.ui.TextArea
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.icons.dragHandle
import heizige.kk.khatkit.app.core.ui.icons.sync
import heizige.kk.khatkit.app.core.ui.theme.ChatFontProvider
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.UiState
import heizige.kk.khatkit.app.core.util.insertAtCursor
import heizige.kk.khatkit.app.core.util.onError
import heizige.kk.khatkit.app.core.util.onSuccess
import heizige.kk.khatkit.app.core.util.plus
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableColumn
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListDisclosure
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.theme.MochiTheme
import kotlin.uuid.Uuid

/** LazyColumn 的项间距；[mochiListSection] 的各行要跨过它连成一张卡片，经 [LocalMochiListItemSpacing] 告诉它。 */
private val SectionSpacing = 16.dp

/** 预设消息可选的两种角色（下拉里的顺序） */
private val PresetRoles = listOf(MessageRole.USER, MessageRole.ASSISTANT)

/** 现有字符串里带的「：」「:」结尾，放进分组小标题时去掉 */
private fun String.withoutTrailingColon(): String = trim().trimEnd(':', '：').trim()

@Composable
fun AssistantPromptPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_prompt),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantPromptContent(
            // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
private fun AssistantPromptContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val templateTransformer = rememberAppEntryPoint().templateTransformer()

    // mochiListSection 的小标题 / 说明在非 Composable 的 LazyListScope 里用，先取出来
    val presetMessagesHeader = stringResource(R.string.assistant_page_preset_messages)
    val presetMessagesFooter = stringResource(R.string.assistant_page_preset_messages_desc)
    val roleOptions = remember { PresetRoles.map { it.toString() }.toImmutableList() }

    CompositionLocalProvider(LocalMochiListItemSpacing provides SectionSpacing) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = PageMetrics.TopContentPadding,
                end = 16.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            // 系统提示词：标题头由 TextArea 自己画（同排还有全屏/导入按钮），下面一段是可插入的变量
            item("systemPrompt") {
                val systemPromptValue = rememberTextFieldState(
                    initialText = assistant.systemPrompt,
                )
                LaunchedEffect(Unit) {
                    snapshotFlow { systemPromptValue.text }.collect {
                        onUpdate(
                            assistant.copy(
                                systemPrompt = it.toString()
                            )
                        )
                    }
                }

                MochiListSection {
                    Box(
                        Modifier
                            .mochiListDivider(MochiListDefaults.RowPadding)
                            .padding(horizontal = MochiListDefaults.RowPadding, vertical = 8.dp)
                    ) {
                        TextArea(
                            state = systemPromptValue,
                            label = stringResource(R.string.assistant_page_system_prompt),
                            minLines = 5,
                            maxLines = 10
                        )
                    }
                    Column(
                        modifier = Modifier
                            .mochiListDivider(MochiListDefaults.RowPadding)
                            .padding(MochiListDefaults.RowPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_page_available_variables).withoutTrailingColon(),
                            style = MochiTheme.textStyles.subhead,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                        MochiChipGroup(wrap = true, contentPadding = PaddingValues(0.dp)) {
                            DefaultPlaceholderProvider.placeholders.forEach { (k, info) ->
                                PlaceholderChip(
                                    placeholderKey = k,
                                    displayName = info.displayName,
                                    onClick = { systemPromptValue.insertAtCursor("{{$k}}") },
                                )
                            }
                        }
                    }
                }
            }

            item("conversationOverrides") {
                MochiListSection {
                    MochiListRow(
                        title = stringResource(R.string.assistant_page_allow_conversation_system_prompt),
                        subtitle = stringResource(R.string.assistant_page_allow_conversation_system_prompt_desc),
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    allowConversationSystemPrompt = !assistant.allowConversationSystemPrompt
                                )
                            )
                        },
                        trailing = {
                            MochiSwitch(
                                checked = assistant.allowConversationSystemPrompt,
                                onCheckedChange = { value ->
                                    onUpdate(assistant.copy(allowConversationSystemPrompt = value))
                                },
                            )
                        },
                    )
                    MochiListRow(
                        title = stringResource(R.string.assistant_page_allow_conversation_prompt_injection),
                        subtitle = stringResource(R.string.assistant_page_allow_conversation_prompt_injection_desc),
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    allowConversationPromptInjection = !assistant.allowConversationPromptInjection
                                )
                            )
                        },
                        trailing = {
                            MochiSwitch(
                                checked = assistant.allowConversationPromptInjection,
                                onCheckedChange = { value ->
                                    onUpdate(assistant.copy(allowConversationPromptInjection = value))
                                },
                            )
                        },
                    )
                }
            }

            item("messageTemplate") {
                val missingMessage = "{{ message }}" !in assistant.messageTemplate
                val footer = stringResource(R.string.assistant_page_message_template_desc) + "\n" +
                    stringResource(R.string.assistant_page_template_variables_label) + " " +
                    stringResource(R.string.assistant_page_template_variable_role) + ": {{ role }}, " +
                    stringResource(R.string.assistant_page_template_variable_message) + ": {{ message }}, " +
                    stringResource(R.string.assistant_page_template_variable_time) + ": {{ time }}, " +
                    stringResource(R.string.assistant_page_template_variable_date) + ": {{ date }}"
                MochiListSection(
                    header = stringResource(R.string.assistant_page_message_template),
                    footer = footer,
                ) {
                    Box(
                        Modifier
                            .mochiListDivider(MochiListDefaults.RowPadding)
                            .padding(MochiListDefaults.RowPadding)
                    ) {
                        MochiTextField(
                            value = assistant.messageTemplate,
                            onValueChange = {
                                onUpdate(
                                    assistant.copy(
                                        messageTemplate = it
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 15,
                            error = if (missingMessage) {
                                stringResource(R.string.assistant_page_message_template_missing_message)
                            } else {
                                null
                            },
                            clearButton = false,
                            textStyle = MochiTheme.textStyles.body.copy(
                                fontSize = 12.sp,
                                fontFamily = JetbrainsMono,
                                lineHeight = 16.sp
                            ),
                        )
                    }
                    MochiListRow(
                        title = stringResource(R.string.setting_model_page_reset_to_default),
                        onClick = {
                            onUpdate(assistant.copy(messageTemplate = "{{ message }}"))
                        },
                        enabled = assistant.messageTemplate != "{{ message }}",
                        leading = {
                            Icon(
                                imageVector = sync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MochiTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }

            item("templatePreview") {
                val rawMessages = listOf(
                    UIMessage.user("你好啊"),
                    UIMessage.assistant("你好，有什么我可以帮你的吗？"),
                )
                val preview by produceState<UiState<List<UIMessage>>>(
                    UiState.Success(rawMessages),
                    assistant
                ) {
                    value = runCatching {
                        UiState.Success(
                            templateTransformer.transform(
                                ctx = TransformerContext(
                                    context = context,
                                    model = Model(modelId = "gpt-4o", displayName = "GPT-4o"),
                                    assistant = assistant,
                                    settings = settings
                                ),
                                messages = rawMessages
                            )
                        )
                    }.getOrElse {
                        UiState.Error(it)
                    }
                }
                MochiListSection(
                    header = stringResource(R.string.assistant_page_template_preview).withoutTrailingColon(),
                ) {
                    Column(
                        Modifier
                            .mochiListDivider(MochiListDefaults.RowPadding)
                            .padding(8.dp)
                    ) {
                        preview.onError {
                            Text(
                                text = it.message ?: it.javaClass.name,
                                style = MochiTheme.textStyles.subhead,
                                color = MochiTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        preview.onSuccess {
                            ChatFontProvider(displaySetting = settings.displaySetting) {
                                it.fastForEach { message ->
                                    ChatMessage(
                                        node = message.toMessageNode(),
                                        onFork = {},
                                        onRegenerate = {},
                                        onEdit = {},
                                        onShare = {},
                                        onDelete = {},
                                        onUpdate = {},
                                        lastMessage = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 预设消息：一条消息一项（角色 + 内容 + 删除），末尾一行「添加」，整组是同一张卡片
            val presetMessages = assistant.presetMessages
            mochiListSection(
                key = "presetMessages",
                header = presetMessagesHeader,
                footer = presetMessagesFooter,
            ) {
                rows(count = presetMessages.size) { index ->
                    val presetMessage = presetMessages[index]
                    MochiListPickerRow(
                        title = stringResource(R.string.assistant_page_template_variable_role),
                        options = roleOptions,
                        selectedIndex = PresetRoles.indexOf(presetMessage.role),
                        onSelect = { selected ->
                            val role = PresetRoles[selected]
                            onUpdate(
                                assistant.copy(
                                    presetMessages = assistant.presetMessages.mapIndexed { i, msg ->
                                        if (i == index) {
                                            msg.copy(role = role)
                                        } else {
                                            msg
                                        }
                                    }
                                )
                            )
                        },
                    )
                    MochiListTextFieldRow(
                        value = presetMessage.toText(),
                        onValueChange = { text ->
                            onUpdate(
                                assistant.copy(
                                    presetMessages = assistant.presetMessages.mapIndexed { i, msg ->
                                        if (i == index) {
                                            msg.copy(parts = listOf(UIMessagePart.Text(text)))
                                        } else {
                                            msg
                                        }
                                    }
                                )
                            )
                        },
                        singleLine = false,
                        maxLines = 6,
                    )
                    MochiListButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    presetMessages = assistant.presetMessages.filterIndexed { i, _ ->
                                        i != index
                                    }
                                )
                            )
                        },
                        destructive = true,
                    )
                }
                row(key = "add") {
                    MochiListButton(
                        text = stringResource(R.string.add),
                        onClick = {
                            val lastRole = assistant.presetMessages.lastOrNull()?.role ?: MessageRole.ASSISTANT
                            val nextRole = when (lastRole) {
                                MessageRole.USER -> MessageRole.ASSISTANT
                                MessageRole.ASSISTANT -> MessageRole.USER
                                else -> MessageRole.USER
                            }
                            onUpdate(
                                assistant.copy(
                                    presetMessages = assistant.presetMessages + UIMessage(
                                        role = nextRole,
                                        parts = listOf(UIMessagePart.Text(""))
                                    )
                                )
                            )
                        },
                    )
                }
            }

            // 正则：每条一张卡片（可长按把手拖动排序），最后一张卡片是「添加」+ 说明
            item("regexes") {
                val haptic = LocalHapticFeedback.current
                Column(Modifier.fillMaxWidth()) {
                    MochiListSectionHeader(stringResource(R.string.assistant_page_regex_title))
                    ReorderableColumn(
                        list = assistant.regexes,
                        onSettle = { fromIndex, toIndex ->
                            val regexes = assistant.regexes.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                            onUpdate(assistant.copy(regexes = regexes))
                        },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { index, regex, isDragging ->
                        key(regex.id) {
                            ReorderableItem(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AssistantRegexCard(
                                    regex = regex,
                                    onUpdate = onUpdate,
                                    assistant = assistant,
                                    index = index,
                                    modifier = Modifier.scale(if (isDragging) 0.95f else 1f),
                                    dragHandleModifier = Modifier
                                        .fillMaxSize()
                                        .longPressDraggableHandle(
                                            enabled = assistant.regexes.size > 1,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.GestureThresholdActivate
                                                )
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    if (assistant.regexes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                    }
                    MochiListSection(
                        footer = stringResource(R.string.assistant_page_regex_desc),
                    ) {
                        MochiListButton(
                            text = stringResource(R.string.add),
                            onClick = {
                                onUpdate(
                                    assistant.copy(
                                        regexes = assistant.regexes + AssistantRegex(
                                            id = Uuid.random()
                                        )
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 可插入变量的芯片：显示名由 [DefaultPlaceholderProvider] 给的 Composable 画（它用的是 Material3 Text），
 * 所以把芯片的字号和颜色同时提供给 Material3 的 LocalTextStyle / LocalContentColor。
 */
@Composable
private fun PlaceholderChip(
    placeholderKey: String,
    displayName: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val chipStyle = MochiTheme.textStyles.subhead.copy(fontWeight = FontWeight.Medium)
    MochiChip(
        label = "{{$placeholderKey}}",
        selected = false,
        onClick = onClick,
        labelContent = {
            CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides chipStyle,
                androidx.compose.material3.LocalContentColor provides ui.feice.mochi.theme.LocalContentColor.current,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    displayName()
                    Text(": {{$placeholderKey}}", style = chipStyle, maxLines = 1)
                }
            }
        },
    )
}

@Composable
private fun AssistantRegexCard(
    regex: AssistantRegex,
    onUpdate: (Assistant) -> Unit,
    assistant: Assistant,
    index: Int,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    val updateRegex: ((AssistantRegex) -> AssistantRegex) -> Unit = { transform ->
        onUpdate(
            assistant.copy(
                regexes = assistant.regexes.mapIndexed { i, reg ->
                    if (i == index) transform(reg) else reg
                }
            )
        )
    }

    MochiListSection(modifier = modifier) {
        // 首行：拖拽把手 + 名称 + 开关，点整行切换开关
        MochiListRow(
            title = regex.name,
            onClick = { updateRegex { it.copy(enabled = !regex.enabled) } },
            leading = {
                Box(
                    modifier = dragHandleModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = dragHandle,
                        contentDescription = null,
                    )
                }
            },
            trailing = {
                MochiSwitch(
                    checked = regex.enabled,
                    onCheckedChange = { enabled -> updateRegex { it.copy(enabled = enabled) } },
                )
            },
        )

        // 展开后：名称 / 查找 / 替换、影响范围、仅视觉、删除
        MochiListDisclosure(
            title = stringResource(R.string.edit),
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Column(
                modifier = Modifier
                    .mochiListDivider(MochiListDefaults.RowPadding)
                    .padding(MochiListDefaults.RowPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MochiTextField(
                    value = regex.name,
                    onValueChange = { name -> updateRegex { it.copy(name = name) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.assistant_page_regex_name),
                )
                MochiTextField(
                    value = regex.findRegex,
                    onValueChange = { findRegex -> updateRegex { it.copy(findRegex = findRegex.trim()) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.assistant_page_regex_find_regex),
                    supportingText = "e.g., \\b\\w+@\\w+\\.\\w+\\b",
                )
                MochiTextField(
                    value = regex.replaceString,
                    onValueChange = { replaceString -> updateRegex { it.copy(replaceString = replaceString) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.assistant_page_regex_replace_string),
                    supportingText = "e.g., [EMAIL]",
                )
            }

            Column(
                modifier = Modifier
                    .mochiListDivider(MochiListDefaults.RowPadding)
                    .padding(MochiListDefaults.RowPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_regex_affecting_scopes).withoutTrailingColon(),
                    style = MochiTheme.textStyles.subhead,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
                MochiChipGroup(wrap = true, contentPadding = PaddingValues(0.dp)) {
                    AssistantAffectScope.entries.forEach { scope ->
                        val checked = scope in regex.affectingScope
                        MochiChip(
                            label = scope.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = checked,
                            onClick = {
                                val newScopes = if (!checked) {
                                    regex.affectingScope + scope
                                } else {
                                    regex.affectingScope - scope
                                }
                                updateRegex { it.copy(affectingScope = newScopes) }
                            },
                            showCheck = true,
                        )
                    }
                }
            }

            MochiListRow(
                title = stringResource(R.string.assistant_page_regex_visual_only),
                onClick = { updateRegex { it.copy(visualOnly = !regex.visualOnly) } },
                trailing = {
                    MochiSwitch(
                        checked = regex.visualOnly,
                        onCheckedChange = { visualOnly -> updateRegex { it.copy(visualOnly = visualOnly) } },
                    )
                },
            )

            MochiListButton(
                text = stringResource(R.string.delete),
                onClick = {
                    onUpdate(
                        assistant.copy(
                            regexes = assistant.regexes.filterIndexed { i, _ ->
                                i != index
                            }
                        )
                    )
                },
                destructive = true,
            )
        }
    }
}
