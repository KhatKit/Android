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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.components.KedgeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ai.ModelSelector
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.components.ui.TagsInput
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarColor
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarShape
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarShapeNames
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.feature.settings.components.parseHexColor
import heizige.kk.khromia.components.ShapeSelectionGrid
import heizige.kk.khromia.components.ShapeSelectionOption
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.hooks.heroAnimation
import heizige.kk.khatkit.app.core.util.toFixed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.data.model.Tag as DataTag
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeRowSupport
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixFormMetrics
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_basic),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantBasicContent(
            // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
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
    var showShapeSheet by remember { mutableStateOf(false) }
    // 头像底色的取色弹窗：与主题页共用 ThemeCustomColorSheet（内含 SquareColorPicker）。
    var showAvatarColorSheet by remember { mutableStateOf(false) }
    val providerName = providers
        .firstOrNull { provider -> provider.models.any { it.id == assistant.chatModelId } }
        ?.name
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(MiuixFormMetrics.GroupSpacing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MiuixFormMetrics.GroupSpacing),
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

        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_name))
                },
                supportingContent = {
                    KedgeOutlinedTextField(
                        value = assistant.name,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    name = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                },
            )

            item(
                headlineContent = { Text("角色 UID") },
                supportingContent = {
                    SelectionContainer {
                        Text(assistant.id.toString(), style = KedgeTextStyles.footnoteSmall())
                    }
                },
            )
            item(
                headlineContent = { Text("职责描述") },
                supportingContent = {
                    KedgeOutlinedTextField(
                        value = assistant.description,
                        onValueChange = { onUpdate(assistant.copy(description = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                },
            )
            item(
                headlineContent = { Text("主管权限") },
                supportingContent = { Text("允许 AI 创建、修改和删除团队角色；每次变更仍需确认。") },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.canManageRoles,
                        onCheckedChange = { onUpdate(assistant.copy(canManageRoles = it)) },
                    )
                },
            )
            item(
                onClick = { showShapeSheet = true },
                headlineContent = { Text("头像形状") },
                supportingContent = { Text(assistant.avatarShape) },
                trailingContent = { Icon(arrowForward, contentDescription = null) },
            )
            // 头像底色：整行可点 = 打开取色弹窗（与主题页共用同一个 ThemeCustomColorSheet，
            // 里面的取色器就是 SquareColorPicker）。之前是裸文本框，要手打 #RRGGBB；
            // 现在不再手输：选色写回 "#AARRGGBB"，弹窗里的「重置」把字段清回 null。
            // 非法旧值不会崩 —— 预览与初值都走 parseHexColor，解析失败时退回角色名派生色。
            item(
                onClick = { showAvatarColorSheet = true },
                headlineContent = { Text(stringResource(R.string.assistant_page_avatar_color)) },
                supportingContent = {
                    Text(
                        assistant.avatarColor?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.assistant_page_avatar_color_default)
                    )
                },
                trailingContent = {
                    // 当前颜色预览：画的就是头像实际会用的那个色（含未设置时的派生色）。
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(roleAvatarColor(assistant.name, assistant.avatarColor))
                    )
                },
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_tags))
                },
                supportingContent = {
                    TagsInput(
                        value = assistant.tags,
                        tags = tags,
                        onValueChange = { tagIds, tagList ->
                            vm.updateTags(tagIds, tagList)
                        },
                    )
                },
            )

            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_workspace))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(stringResource(R.string.assistant_page_workspace_desc))
                        },
                        content = {
                            val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                            Select(
                                options = listOf<WorkspaceEntity?>(null) + workspaces,
                                selectedOption = selectedWorkspace,
                                onOptionSelected = { workspace ->
                                    onUpdate(
                                        assistant.copy(
                                            workspaceId = workspace?.id?.let { Uuid.parse(it) }
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                optionToString = { workspace ->
                                    workspace?.name ?: stringResource(R.string.workspace_no_binding)
                                },
                            )
                        },
                    )
                },
            )
        }

        CardGroup {
            item(
                onClick = {
                    onUpdate(
                        assistant.copy(
                            useAssistantAvatar = !assistant.useAssistantAvatar
                        )
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.useAssistantAvatar,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useAssistantAvatar = it
                                )
                            )
                        }
                    )
                },
            )
        }

        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_chat_model))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(stringResource(R.string.assistant_page_chat_model_desc))
                        },
                        content = {
                            ModelSelector(
                                modelId = assistant.chatModelId,
                                providers = providers,
                                type = ModelType.CHAT,
                                onSelect = {
                                    onUpdate(
                                        assistant.copy(
                                            chatModelId = it.id
                                        )
                                    )
                                },
                            )
                        },
                    )
                },
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_temperature))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(
                                text = buildAnnotatedString {
                                    append(stringResource(R.string.assistant_page_temperature_warning))
                                }
                            )
                        },
                        content = {
                            if (assistant.temperature != null) {
                                var temperatureInput by remember(assistant.id) {
                                    mutableStateOf(assistant.temperature.toString())
                                }
                                val temperatureValue = temperatureInput.toFloatOrNull()
                                KedgeOutlinedTextFieldWithSlots(
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
                                    isError = temperatureValue == null || temperatureValue !in 0f..2f,
                                    supportingText = {
                                        Text("0 - 2")
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        },
                    )
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                },
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(
                                text = buildAnnotatedString {
                                    append(stringResource(R.string.assistant_page_top_p_warning))
                                }
                            )
                        },
                        content = {
                            assistant.topP?.let { topP ->
                                var topPInput by remember(assistant.id) {
                                    mutableStateOf(topP.toString())
                                }
                                val topPValue = topPInput.toFloatOrNull()
                                KedgeOutlinedTextFieldWithSlots(
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
                                    isError = topPValue == null || topPValue !in 0f..1f,
                                    supportingText = {
                                        Text("0 - 1")
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        },
                    )
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                },
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_context_message_limit))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(
                                text = stringResource(R.string.assistant_page_context_message_limit_desc),
                            )
                        },
                        content = {
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

                            KedgeOutlinedTextFieldWithSlots(
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
                                supportingText = {
                                    Text(
                                        stringResource(R.string.assistant_page_context_message_limit_hint)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )

                            if (assistant.contextMessageLimit > 0) {
                                Text(
                                    text = stringResource(R.string.assistant_page_context_message_limit_warning),
                                    style = KedgeTextStyles.footnoteSmall(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                },
            )
        }

        CardGroup {
            item(
                onClick = {
                    onUpdate(
                        assistant.copy(
                            streamOutput = !assistant.streamOutput
                        )
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_stream_output))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_stream_output_desc))
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.streamOutput,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    streamOutput = it
                                )
                            )
                        }
                    )
                },
            )
        }

        CardGroup {
            item(
                headlineContent = {
                    Text("智能 / 推理强度")
                },
                supportingContent = {
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { level ->
                            onUpdate(assistant.copy(reasoningLevel = level))
                        }
                    )
                },
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_max_tokens))
                },
                supportingContent = {
                    KedgeRowSupport(
                        description = {
                            Text(stringResource(R.string.assistant_page_max_tokens_desc))
                        },
                        content = {
                            KedgeOutlinedTextFieldWithSlots(
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
                                placeholder = {
                                    Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                                },
                                supportingText = {
                                    if (assistant.maxTokens != null) {
                                        Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                                    } else {
                                        Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        },
                    )
                },
            )
        }

        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_gradient_background))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_gradient_background_desc))
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.useGradientBackground,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useGradientBackground = it
                                )
                            )
                        }
                    )
                },
                // 开关行整行可点（与 KedgeSwitchFormRow 一致）。
                onClick = {
                    onUpdate(
                        assistant.copy(
                            useGradientBackground = !assistant.useGradientBackground
                        )
                    )
                },
            )
        }

        if (!assistant.useGradientBackground) {
            BackgroundPicker(
                background = assistant.background,
                backgroundOpacity = assistant.backgroundOpacity,
                onUpdate = { background ->
                    onUpdate(
                        assistant.copy(
                            background = background
                        )
                    )
                }
            )
        }

        if (!assistant.useGradientBackground && assistant.background != null) {
            val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
            CardGroup {
                item(
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_background_opacity))
                    },
                    supportingContent = {
                        KedgeRowSupport(
                            description = {
                                Text(stringResource(R.string.assistant_page_background_opacity_desc))
                            },
                            content = {
                                KedgeSlider(
                                    value = backgroundOpacity,
                                    onValueChange = {
                                        onUpdate(
                                            assistant.copy(
                                                backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                            )
                                        )
                                    },
                                    valueRange = 0f..1f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(
                                        R.string.assistant_page_background_opacity_value,
                                        (backgroundOpacity * 100).roundToInt()
                                    ),
                                    style = KedgeTextStyles.footnoteSmall(),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                )
                            },
                        )
                    },
                )
            }
        }
    }

    PrimaryBottomSheet(
        visible = showShapeSheet,
        title = "头像形状",
        imageVector = palette,
        onDismiss = { showShapeSheet = false },
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
