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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SwipeToDismissBox
import heizige.kk.khromia.components.OptionSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeIconButtonVariant
import androidx.compose.foundation.BorderStroke

@Composable
internal fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting
) {
    val dialogState = useEditState<Model> {
        onEdit(it.copy(displayName = it.displayName.trim()))
    }
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
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
                        .fillMaxHeight(0.95f)
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

    SwipeToDismissBox(
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
        // OutlinedCard 在 Miuix 下不画（Miuix 的 Card 没有描边变体），
        // 统一走 KedgeCard + BorderStroke，MD3 侧观感与原来一致。
        KedgeCard(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KedgeSurface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = model.displayName,
                        style = KedgeTextStyles.title(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                }

                // Edit button
                KedgeIconButton(
                    onClick = {
                        dialogState.open(model.copy())
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(build, "Edit")
                }
            }
        }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        availableTools.forEach { (tool, info) ->
            val (title, description) = info
            KedgeCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = KedgeTextStyles.title()
                        )
                        Text(
                            text = description,
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (providerOverride != null) {
            KedgeCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoAIIcon(
                            providerOverride.name,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = KedgeTextStyles.body(),
                            modifier = Modifier.weight(1f)
                        )
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
                }
            }
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
