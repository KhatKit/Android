package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningPicker
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.SliderValueEditor
import heizige.kk.khatkit.app.core.ui.components.ui.SliderValueInputDialog
import heizige.kk.khatkit.app.core.ui.components.ui.TagsInput
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarColor
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarShape
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarShapeNames
import heizige.kk.khatkit.app.core.ui.components.ui.sliderValueText
import heizige.kk.khatkit.app.core.ui.hooks.heroAnimation
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.util.toFixed
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.feature.settings.components.parseHexColor
import heizige.kk.khatkit.uikit.mochi.ShapeSelectionGrid
import heizige.kk.khatkit.uikit.mochi.ShapeSelectionOption
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    MochiSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_basic),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantBasicContent(
            // 必须把 MochiSettingsPageScaffold 给的 innerPadding 透传下去。
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm,
        )
    }
}

/**
 * 角色「基本」页：与设置页同一套骨架（LazyColumn，一组一个 item），每组是一张 Mochi 分组卡片。
 *
 * 分组：名称/UID/职责/主管权限 → 头像 → 标签 → 工作区 → 模型 → 采样参数（温度/Top P）
 * → 上下文消息限制 → 输出（流式/推理）→ 最大 Token → 聊天背景（含透明度滑块）。
 * 带开关的行整行可点（点行 = 拨开关）。
 */
@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<heizige.kk.khatkit.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailViewModel
) {
    // U7b-2：头像形状弹层走 MochiSheetState。
    val shapeSheet = rememberMochiSheetState()
    // 头像底色的取色弹窗：与主题页共用 ThemeCustomColorSheet（内含 SquareColorPicker）。
    var showAvatarColorSheet by remember { mutableStateOf(false) }
    // 模型选择：整行可点打开与 ModelSelector 同一个 ModelListSheet。
    val chatModelState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    var showReasoningPicker by remember { mutableStateOf(false) }
    // 背景透明度：点行尾编辑键 = 原 sliderItem 的「点数值直接输入 / 恢复默认」弹窗。
    var showOpacityEditor by remember { mutableStateOf(false) }
    val providerName = providers
        .firstOrNull { provider -> provider.models.any { it.id == assistant.chatModelId } }
        ?.name

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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("avatar") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UIAvatar(
                    value = assistant.avatar,
                    name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    onUpdate = { avatar ->
                        onUpdate(
                            assistant.copy(
                                avatar = avatar
                            )
                        )
                    },
                    avatarShape = assistant.avatarShape,
                    defaultIconName = providerName,
                    avatarColor = assistant.avatarColor,
                    modifier = Modifier
                        .size(80.dp)
                        .heroAnimation("assistant_${assistant.id}")
                )
            }
        }

        item("identity") {
            MochiListSection {
                MochiListTextFieldRow(
                    value = assistant.name,
                    onValueChange = { onUpdate(assistant.copy(name = it)) },
                    label = stringResource(R.string.assistant_page_name),
                    singleLine = false,
                )
                // UID 要能长按选中复制：SelectionContainer 包一层，分隔线起点由它登记给卡片。
                SelectionContainer(Modifier.mochiListDivider(MochiListDefaults.TextRowPadding)) {
                    MochiListRow(
                        title = "角色 UID",
                        subtitle = assistant.id.toString(),
                    )
                }
                MochiListTextFieldRow(
                    value = assistant.description,
                    onValueChange = { onUpdate(assistant.copy(description = it)) },
                    label = "职责描述",
                    singleLine = false,
                )
                MochiListRow(
                    title = "主管权限",
                    subtitle = "允许 AI 创建、修改和删除团队角色；每次变更仍需确认。",
                    onClick = { onUpdate(assistant.copy(canManageRoles = !assistant.canManageRoles)) },
                    trailing = {
                        MochiSwitch(
                            checked = assistant.canManageRoles,
                            onCheckedChange = { onUpdate(assistant.copy(canManageRoles = it)) },
                        )
                    },
                )
            }
        }

        item("avatarStyle") {
            MochiListSection {
                MochiListRow(
                    title = "头像形状",
                    value = assistant.avatarShape,
                    chevron = true,
                    onClick = { shapeSheet.show() },
                )
                // 头像底色：整行可点 = 打开取色弹窗（与主题页共用同一个 ThemeCustomColorSheet，
                // 里面的取色器就是 SquareColorPicker）。选色写回 "#AARRGGBB"，弹窗里的「重置」把字段清回 null。
                // 非法旧值不会崩 —— 预览与初值都走 parseHexColor，解析失败时退回角色名派生色。
                MochiListRow(
                    title = stringResource(R.string.assistant_page_avatar_color),
                    subtitle = assistant.avatarColor?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.assistant_page_avatar_color_default),
                    onClick = { showAvatarColorSheet = true },
                    trailing = {
                        // 当前颜色预览：画的就是头像实际会用的那个色（含未设置时的派生色）。
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(roleAvatarColor(assistant.name, assistant.avatarColor))
                        )
                    },
                )
                MochiListRow(
                    title = stringResource(R.string.assistant_page_use_assistant_avatar),
                    subtitle = stringResource(R.string.assistant_page_use_assistant_avatar_desc),
                    onClick = {
                        onUpdate(
                            assistant.copy(
                                useAssistantAvatar = !assistant.useAssistantAvatar
                            )
                        )
                    },
                    trailing = {
                        MochiSwitch(
                            checked = assistant.useAssistantAvatar,
                            onCheckedChange = {
                                onUpdate(
                                    assistant.copy(
                                        useAssistantAvatar = it
                                    )
                                )
                            },
                        )
                    },
                )
            }
        }

        item("tags") {
            MochiListSection(header = stringResource(R.string.assistant_page_tags)) {
                SectionBlock {
                    TagsInput(
                        value = assistant.tags,
                        tags = tags,
                        onValueChange = { tagIds, tagList ->
                            vm.updateTags(tagIds, tagList)
                        },
                    )
                }
            }
        }

        item("workspace") {
            val noBinding = stringResource(R.string.workspace_no_binding)
            val workspaceOptions = remember(workspaces, noBinding) {
                (listOf(noBinding) + workspaces.map { it.name }).toImmutableList()
            }
            // 0 = 未绑定；找不到已绑定的工作区时也落在「未绑定」，与原 Select 一致。
            val selectedWorkspaceIndex = workspaces
                .indexOfFirst { it.id == assistant.workspaceId?.toString() }
                .let { if (it < 0) 0 else it + 1 }
            MochiListSection(footer = stringResource(R.string.assistant_page_workspace_desc)) {
                MochiListPickerRow(
                    title = stringResource(R.string.assistant_page_workspace),
                    options = workspaceOptions,
                    selectedIndex = selectedWorkspaceIndex,
                    onSelect = { index ->
                        val workspace = if (index == 0) null else workspaces.getOrNull(index - 1)
                        onUpdate(
                            assistant.copy(
                                workspaceId = workspace?.id?.let { Uuid.parse(it) }
                            )
                        )
                    },
                )
            }
        }

        item("chatModel") {
            MochiListSection(footer = stringResource(R.string.assistant_page_chat_model_desc)) {
                MochiListRow(
                    title = stringResource(R.string.assistant_page_chat_model),
                    subtitle = chatModelState.currentModel?.displayName
                        ?: stringResource(R.string.model_list_select_model),
                    chevron = true,
                    onClick = { chatModelState.open() },
                )
            }
        }

        item("sampling") {
            MochiListSection {
                MochiListRow(
                    title = stringResource(R.string.assistant_page_temperature),
                    subtitle = stringResource(R.string.assistant_page_temperature_warning),
                    onClick = {
                        onUpdate(
                            assistant.copy(
                                temperature = if (assistant.temperature == null) 1.0f else null
                            )
                        )
                    },
                    trailing = {
                        MochiSwitch(
                            checked = assistant.temperature != null,
                            onCheckedChange = { enabled ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = if (enabled) 1.0f else null
                                    )
                                )
                            },
                        )
                    },
                )
                if (assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    SectionBlock {
                        MochiTextField(
                            value = temperatureInput,
                            onValueChange = { value ->
                                temperatureInput = value
                                value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                    onUpdate(
                                        assistant.copy(
                                            temperature = temperature
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            error = if (temperatureValue == null || temperatureValue !in 0f..2f) "0 - 2" else null,
                            supportingText = "0 - 2",
                        )
                    }
                }
                MochiListRow(
                    title = stringResource(R.string.assistant_page_top_p),
                    subtitle = stringResource(R.string.assistant_page_top_p_warning),
                    onClick = {
                        onUpdate(
                            assistant.copy(
                                topP = if (assistant.topP == null) 1.0f else null
                            )
                        )
                    },
                    trailing = {
                        MochiSwitch(
                            checked = assistant.topP != null,
                            onCheckedChange = { enabled ->
                                onUpdate(
                                    assistant.copy(
                                        topP = if (enabled) 1.0f else null
                                    )
                                )
                            },
                        )
                    },
                )
                assistant.topP?.let { topP ->
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    SectionBlock {
                        MochiTextField(
                            value = topPInput,
                            onValueChange = { value ->
                                topPInput = value
                                value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                    onUpdate(
                                        assistant.copy(
                                            topP = nextTopP
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            error = if (topPValue == null || topPValue !in 0f..1f) "0 - 1" else null,
                            supportingText = "0 - 1",
                        )
                    }
                }
            }
        }

        item("contextMessageLimit") {
            var contextMessageLimitInput by remember(
                assistant.id,
                assistant.contextMessageLimit
            ) {
                mutableStateOf(assistant.contextMessageLimit.toString())
            }
            var contextMessageLimitFocused by remember(assistant.id) {
                mutableStateOf(false)
            }
            val focusManager = LocalFocusManager.current

            fun commitContextMessageLimit() {
                val value = contextMessageLimitInput.toIntOrNull()
                if (value == null) {
                    contextMessageLimitInput = assistant.contextMessageLimit.toString()
                    return
                }

                contextMessageLimitInput = value.toString()
                if (value != assistant.contextMessageLimit) {
                    onUpdate(assistant.copy(contextMessageLimit = value))
                }
            }

            Column(Modifier.fillMaxWidth()) {
                MochiListSection(
                    header = stringResource(R.string.assistant_page_context_message_limit),
                    footer = stringResource(R.string.assistant_page_context_message_limit_desc),
                ) {
                    SectionBlock {
                        MochiTextField(
                            value = contextMessageLimitInput,
                            onValueChange = { input ->
                                if (input.all(Char::isDigit) &&
                                    (input.isEmpty() || input.toIntOrNull() != null)
                                ) {
                                    contextMessageLimitInput = input
                                    input.toIntOrNull()
                                        ?.takeIf { it != assistant.contextMessageLimit }
                                        ?.let { onUpdate(assistant.copy(contextMessageLimit = it)) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (contextMessageLimitFocused && !focusState.isFocused) {
                                        commitContextMessageLimit()
                                    }
                                    contextMessageLimitFocused = focusState.isFocused
                                },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            singleLine = true,
                            supportingText = stringResource(R.string.assistant_page_context_message_limit_hint),
                        )
                    }
                }
                if (assistant.contextMessageLimit > 0) {
                    MochiListSectionFooter(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        color = MochiTheme.colorScheme.error,
                    )
                }
            }
        }

        item("output") {
            MochiListSection {
                MochiListRow(
                    title = stringResource(R.string.assistant_page_stream_output),
                    subtitle = stringResource(R.string.assistant_page_stream_output_desc),
                    onClick = {
                        onUpdate(
                            assistant.copy(
                                streamOutput = !assistant.streamOutput
                            )
                        )
                    },
                    trailing = {
                        MochiSwitch(
                            checked = assistant.streamOutput,
                            onCheckedChange = {
                                onUpdate(
                                    assistant.copy(
                                        streamOutput = it
                                    )
                                )
                            },
                        )
                    },
                )
                // 整行打开推理强度选择；行尾仍是 ReasoningButton 的图标，显示当前档位。
                MochiListRow(
                    title = "智能 / 推理强度",
                    onClick = { showReasoningPicker = true },
                    trailing = {
                        ReasoningButton(
                            onlyIcon = true,
                            reasoningLevel = assistant.reasoningLevel,
                            onUpdateReasoningLevel = { level ->
                                onUpdate(assistant.copy(reasoningLevel = level))
                            },
                        )
                    },
                )
            }
        }

        item("maxTokens") {
            MochiListSection(
                header = stringResource(R.string.assistant_page_max_tokens),
                footer = stringResource(R.string.assistant_page_max_tokens_desc),
            ) {
                SectionBlock {
                    MochiTextField(
                        value = assistant.maxTokens?.toString() ?: "",
                        onValueChange = { text ->
                            val tokens = if (text.isBlank()) {
                                null
                            } else {
                                text.toIntOrNull()?.takeIf { it > 0 }
                            }
                            onUpdate(
                                assistant.copy(
                                    maxTokens = tokens
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.assistant_page_max_tokens_no_limit),
                        supportingText = if (assistant.maxTokens != null) {
                            stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens)
                        } else {
                            stringResource(R.string.assistant_page_max_tokens_no_token_limit)
                        },
                    )
                }
            }
        }

        item("background") {
            BackgroundPicker(
                background = assistant.background,
                backgroundOpacity = assistant.backgroundOpacity,
                onUpdate = { background ->
                    onUpdate(
                        assistant.copy(
                            background = background
                        )
                    )
                },
                adjustments = {
                    if (assistant.background != null) {
                        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                        MochiListSliderRow(
                            value = backgroundOpacity,
                            onValueChange = { onBackgroundOpacityChange(assistant, onUpdate, it) },
                            valueRange = 0f..1f,
                            steps = 19,
                            title = stringResource(R.string.assistant_page_background_opacity),
                            valueText = sliderValueText(
                                value = backgroundOpacity,
                                displayScale = 100f,
                                suffix = "%",
                            ),
                            supportingText = stringResource(R.string.assistant_page_background_opacity_desc),
                            trailing = {
                                MochiIconButton(
                                    onClick = { showOpacityEditor = true },
                                    style = MochiButtonStyle.Text,
                                    size = 32.dp,
                                ) {
                                    Icon(
                                        imageVector = edit,
                                        contentDescription = stringResource(R.string.assistant_page_background_opacity),
                                        modifier = Modifier.size(18.dp),
                                        tint = MochiTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    ModelListSheet(
        state = chatModelState,
        onSelect = {
            onUpdate(
                assistant.copy(
                    chatModelId = it.id
                )
            )
        },
    )

    if (showReasoningPicker) {
        ReasoningPicker(
            reasoningLevel = assistant.reasoningLevel,
            onDismissRequest = { showReasoningPicker = false },
            onUpdateReasoningLevel = { level ->
                onUpdate(assistant.copy(reasoningLevel = level))
            },
        )
    }

    SliderValueInputDialog(
        visible = showOpacityEditor,
        title = stringResource(R.string.assistant_page_background_opacity),
        editor = SliderValueEditor(
            value = assistant.backgroundOpacity.coerceIn(0f, 1f),
            valueRange = 0f..1f,
            defaultValue = 1.0f,
            onValueChange = { onBackgroundOpacityChange(assistant, onUpdate, it) },
            displayScale = 100f,
            suffix = "%",
            steps = 19,
        ),
        onDismiss = { showOpacityEditor = false },
    )

    PrimaryBottomSheet(
        state = shapeSheet,
        title = "头像形状",
        imageVector = palette,
        onDismiss = { shapeSheet.hide() },
    ) { dismiss ->
        ShapeSelectionGrid(
            options = roleAvatarShapeNames.map { key ->
                ShapeSelectionOption(key = key, shape = roleAvatarShape(key))
            },
            selectedKey = assistant.avatarShape,
            onSelected = { key ->
                onUpdate(assistant.copy(avatarShape = key))
                dismiss()
            },
            previewContent = { option ->
                UIAvatar(
                    name = assistant.name,
                    value = assistant.avatar,
                    avatarShape = option.key,
                    defaultIconName = providerName,
                    avatarColor = assistant.avatarColor,
                    modifier = Modifier.size(56.dp),
                )
            },
        )
    }

    ThemeCustomColorSheet(
        visible = showAvatarColorSheet,
        initialColor = parseHexColor(assistant.avatarColor.orEmpty())
            ?: roleAvatarColor(assistant.name, null),
        history = emptyList(),
        onDismiss = { showAvatarColorSheet = false },
        onClear = {
            onUpdate(assistant.copy(avatarColor = null))
            showAvatarColorSheet = false
        },
        onConfirm = { color ->
            // 只在这里做一次 Color -> 字符串：对话框回调给的是 Color，写回的仍是原来的字符串字段。
            onUpdate(assistant.copy(avatarColor = String.format("#%08X", color.toArgb())))
            showAvatarColorSheet = false
        },
    )
}

/** 背景透明度写回：两位小数、夹在 0..1，与原 sliderItem 的写法一致（滑块和数值输入弹窗共用）。 */
private fun onBackgroundOpacityChange(assistant: Assistant, onUpdate: (Assistant) -> Unit, value: Float) {
    onUpdate(
        assistant.copy(
            backgroundOpacity = value.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
        )
    )
}

/**
 * 分组卡片里的自定义内容（输入框、标签）：左右与行文字对齐（[MochiListDefaults.RowPadding]），
 * 上下同样留白；卡片按它登记的起点画分隔线。
 */
@Composable
private fun SectionBlock(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth()
            .padding(MochiListDefaults.RowPadding),
    ) {
        content()
    }
}
