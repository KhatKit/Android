package heizige.kk.khatkit.app.feature.settings.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.OptionSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.overlays.KedgeSwipeToDismissBox
import heizige.kk.kedge.overlays.rememberKedgeSwipeToDismissState
import heizige.kk.kedge.theme.KedgeColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ai.ModelAbilityTag
import heizige.kk.khatkit.app.core.ui.components.ai.ModelModalityTag
import heizige.kk.khatkit.app.core.ui.components.ai.ModelTypeTag
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.feature.settings.components.ProviderConfigure
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeIconButtonVariant

@Composable
internal fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    // 不给默认值：圆角必须由调用点按 `listCardStyle()` 的 index/count 算好传进来。
    // 留个 20dp 默认就等于留个「假默认」——调用点忘传也能编译，行却退回独立卡片，
    // 与同页其它组的圆角口径不一致（全仓 KedgeOptionItem 的 shape 一律来自 listCardStyle）。
    shape: Shape,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting
) {
    val dialogState = useEditState<Model> {
        onEdit(it.copy(displayName = it.displayName.trim()))
    }
    val swipeToDismissBoxState = rememberKedgeSwipeToDismissState()
    val scope = rememberCoroutineScope()

    if (dialogState.isEditing) {
        dialogState.currentState?.let { editingModel ->
            PrimaryBottomSheet(
                visible = true,
                title = stringResource(R.string.setting_provider_page_edit_model),
                imageVector = memory,
                confirmText = stringResource(R.string.confirm),
                onConfirm = {
                    if (editingModel.displayName.isNotBlank()) {
                        dialogState.confirm()
                    }
                },
                onDismiss = {
                    dialogState.dismiss()
                },
                scrollable = false,
            ) { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 同 AddModelButton：给标题栏与确认按钮行留出预算，别把按钮挤出屏幕。
                        .fillMaxHeight(0.72f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = editingModel,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = true,
                            parentProvider = parentProvider
                        )
                    }

                }
            }
        }
    }

    KedgeSwipeToDismissBox(
        state = swipeToDismissBoxState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KedgeIconButton(
                    onClick = {
                        scope.launch {
                            swipeToDismissBoxState.reset()
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(close, null)
                }
                KedgeIconButton(
                    variant = KedgeIconButtonVariant.Filled,
                    onClick = {
                        scope.launch {
                            onDelete()
                            swipeToDismissBoxState.reset()
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        delete,
                        contentDescription = stringResource(R.string.chat_page_delete)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = true,
        modifier = modifier
    ) {
        // 模型行 = 图标 / 名称 / 标签 / 编辑按钮，正好是 `KedgeOptionItem` 的四个槽。
        // 以前是 `KedgeCard` + `BorderStroke` 手搓一行（Miuix 的 Card 没有描边变体，
        // MD3 侧才像 OutlinedCard），现在与提供商行、工具开关行走同一套行渲染：
        // 底色/圆角/内边距/字阶与双风格分流全交给组件，调用点只摆槽位。
        // onClick 传 null：整行本来就不响应点击（只有右侧编辑按钮进弹层），
        // 这里不改交互，只换皮。
        KedgeOptionItem(
            onClick = null,
            modifier = modifier,
            shape = shape,
            leadingContent = {
                KedgeSurface(
                    color = KedgeColors.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier.size(36.dp),
                    )
                }
            },
            titleContent = {
                Text(
                    text = model.displayName,
                    style = KedgeTextStyles.title(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (model.providerOverwrite != null) {
                        Tag(type = TagType.INFO) {
                            Text(
                                model.providerOverwrite?.javaClass?.simpleName ?: model.providerOverwrite?.name
                                ?: "ProviderOverwrite"
                            )
                        }
                    }
                    ModelTypeTag(model = model)
                    ModelModalityTag(model = model)
                    ModelAbilityTag(model = model)
                }
            },
            // Edit button
            trailingContent = {
                KedgeIconButton(
                    onClick = {
                        dialogState.open(model.copy())
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(build, "Edit")
                }
            },
        )
    }
}

@Composable
internal fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_page_built_in_tools),
            style = KedgeTextStyles.title()
        )

        Text(
            text = stringResource(R.string.setting_page_built_in_tools_desc),
            style = KedgeTextStyles.body(),
            color = KedgeColors.onSurfaceVariant
        )

        val availableTools = listOf(
            BuiltInTools.Search to stringResource(R.string.setting_page_built_in_tools_search),
            BuiltInTools.UrlContext to stringResource(R.string.setting_page_built_in_tools_url_context),
            BuiltInTools.ImageGeneration to stringResource(R.string.setting_page_built_in_tools_image_generation),
        )

        // 三个「标题 + 开关」行：不再各自一张 KedgeCard + 手搓 Row，而是同一组的
        // `KedgeOptionItem`（圆角按 index/count、项距取 `listCardStyle().gap`，
        // Miuix 下并成一整块）。整行可点 = 翻转开关，与设置分组里的开关行同款。
        val cards = listCardStyle()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            availableTools.forEachIndexed { index, (tool, title) ->
                KedgeOptionItem(
                    onClick = {
                        if (tool in tools) onUpdateTools(tools - tool)
                        else onUpdateTools(tools + tool)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = cards.indexedShape(index, availableTools.size),
                    titleContent = {
                        Text(
                            text = title,
                            style = KedgeTextStyles.title(),
                        )
                    },
                    trailingContent = {
                        OptionSwitch(
                            checked = tool in tools,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onUpdateTools(tools + tool)
                                } else {
                                    onUpdateTools(tools - tool)
                                }
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_provider_override),
            style = KedgeTextStyles.title()
        )

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            style = KedgeTextStyles.body(),
            color = KedgeColors.onSurfaceVariant
        )

        if (providerOverride != null) {
            // 单行（组里只有这一项）：圆角按 `indexedShape(0, 1)` 取，四角都是大圆角。
            // 图标 / 名称 / 编辑 / 移除 四个位置正好对上 KedgeOptionItem 的槽。
            KedgeOptionItem(
                onClick = null,
                modifier = Modifier.fillMaxWidth(),
                shape = listCardStyle().indexedShape(0, 1),
                leadingContent = {
                    AutoAIIcon(
                        providerOverride.name,
                        modifier = Modifier.size(24.dp)
                    )
                },
                titleContent = {
                    Text(
                        text = "${providerOverride.name} (Override)",
                        style = KedgeTextStyles.title(),
                    )
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KedgeIconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(build, contentDescription = "Edit override")
                        }
                        KedgeIconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(close, contentDescription = "Remove override")
                        }
                    }
                },
            )
        } else {
            KedgeButton(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                        description = {},
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        // Provider configuration modal
        if (showProviderConfig && editingProvider != null) {
            var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }
            PrimaryBottomSheet(
                visible = true,
                title = stringResource(R.string.setting_provider_page_configure_provider_override),
                imageVector = dns,
                confirmText = stringResource(R.string.setting_provider_page_save),
                onConfirm = {
                    onUpdateProviderOverride(internalProvider.copyProvider(name = internalProvider.name.trim()))
                    showProviderConfig = false
                    editingProvider = null
                },
                onDismiss = {
                    showProviderConfig = false
                    editingProvider = null
                },
                scrollable = false,
            ) { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            onEdit = { internalProvider = it }
                        )
                    }
                }
            }
        }
    }
}
