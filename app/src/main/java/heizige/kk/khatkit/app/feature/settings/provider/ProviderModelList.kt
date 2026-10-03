package heizige.kk.khatkit.app.feature.settings.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import heizige.kk.khromia.components.OptionSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.registry.ModelRegistry
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ai.ModelAbilityTag
import heizige.kk.khatkit.app.core.ui.components.ai.ModelModalityTag
import heizige.kk.khatkit.app.core.ui.components.ai.ModelSelector
import heizige.kk.khatkit.app.core.ui.components.ai.ModelTypeTag
import heizige.kk.khatkit.app.core.ui.components.ai.ProviderBalanceText
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.ShareSheet
import heizige.kk.khatkit.app.core.ui.components.ui.SiliconFlowPowerByIcon
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.rememberShareSheetState
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.feature.assistant.detail.CustomBodies
import heizige.kk.khatkit.app.feature.assistant.detail.CustomHeaders
import heizige.kk.khatkit.app.feature.settings.components.ProviderConfigure
import heizige.kk.khatkit.app.feature.settings.components.ProviderConnectionTester
import heizige.kk.khatkit.app.feature.settings.components.SettingProviderBalanceOption
import heizige.kk.khatkit.app.feature.settings.components.isUsingDefaultBaseUrl
import heizige.kk.khatkit.app.feature.settings.components.resetBaseUrlToDefault
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.extendColors
import heizige.kk.khatkit.app.core.util.UiState
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.share
import heizige.kk.khatkit.app.core.ui.icons.sync
import heizige.kk.khromia.components.MultiChoiceSegmentedRow
import heizige.kk.khromia.components.SegmentedItem
import heizige.kk.khromia.components.SingleChoiceSegmentedRow
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeTabRow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixFormMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixRowLabel
import heizige.kk.kedge.containers.KedgeFloatingToolbar
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import androidx.compose.foundation.layout.ColumnScope


@Composable
internal fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val providerManager = rememberAppEntryPoint().providerManager()
    val modelList by produceState(emptyList(), providerSetting) {
        runCatching {
            println("loading models...")
            value = providerManager.getProviderByType(providerSetting)
                .listModels(providerSetting)
                .sortedBy { it.modelId }
                .toList()
        }.onFailure {
            it.printStackTrace()
        }
    }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            // 模型列表
            if (providerSetting.models.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_no_models),
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.setting_provider_page_add_models_hint),
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                items(providerSetting.models, key = { it.id }) { item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.id
                    ) { isDragging ->
                        ModelCard(
                            model = item,
                            onDelete = {
                                onUpdateProvider(providerSetting.delModel(item))
                            },
                            onEdit = { editedModel ->
                                onUpdateProvider(providerSetting.editModel(editedModel))
                            },
                            parentProvider = providerSetting,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    if (isDragging) {
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    } else {
                                        scaleX = 1f
                                        scaleY = 1f
                                    }
                                },
                        )
                    }
                }
            }
        }
        KedgeFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            AddModelButton(
                models = modelList,
                selectedModels = providerSetting.models,
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                onRemoveModel = {
                    onUpdateProvider(providerSetting.delModel(it))
                },
                expanded = expanded,
                parentProvider = providerSetting,
                onUpdateProvider = onUpdateProvider
            )
        }
    }
}

/**
 * 表单分组卡片：一个分组标题 + 一组控件。
 *
 * 之前这张表单是「裸标题 Text + 控件」平铺在 sheet 底色上，彼此之间只有 16dp 间距，
 * 没有任何分组边界看着很散（用户反馈）。这里改回本仓 Miuix 表单的通用形态：一张圆角
 * `surfaceContainer` 卡片装一组，卡片之间靠间距分隔、没有分割线 —— 与设置页
 * （`KedgeFormCard` / `MiuixFormMetrics`）以及 KernelSU 的分组偏好一致。
 *
 * Miuix 下 `KedgeFormCard` 不带内边距，所以自己补一层；MD3 下卡片自带 16dp，不再叠加。
 */
@Composable
internal fun ModelFormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KedgeFormCard(modifier = modifier) {
        val inner = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
            Modifier.padding(MiuixFormMetrics.ItemPadding + 2.dp)
        } else {
            Modifier
        }
        Column(
            modifier = inner,
            verticalArrangement = Arrangement.spacedBy(MiuixFormMetrics.ItemSpacing),
        ) {
            if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                MiuixRowLabel { Text(title) }
            } else {
                Text(title, style = KedgeTextStyles.title())
            }
            content()
        }
    }
}

@Composable
internal fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                // 只在显示名还是「空 / 上一个模型 ID」时才跟着改，否则用户自己填好的
                // 显示名会在继续敲模型 ID 时被反复冲掉。
                displayName = if (model.displayName.isBlank() || model.displayName == model.modelId) {
                    id
                } else {
                    model.displayName
                },
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    Column {
        KedgeTabRow(
            titles = listOf(
                stringResource(R.string.setting_provider_page_basic_settings),
                stringResource(R.string.setting_provider_page_advanced_settings),
                stringResource(R.string.setting_page_built_in_tools),
            ),
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            // 三个标题在 Miuix 的固定宽度 TabRow 里会被截成「基本设…」，交给可滚动形态。
            scrollable = true,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MiuixFormMetrics.GroupSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        ModelFormGroup(stringResource(R.string.setting_provider_page_model_info)) {
                            KedgeOutlinedTextFieldWithSlots(
                                value = model.modelId,
                                onValueChange = {
                                    if (!isEdit) {
                                        // 不要在这里 trim：中文输入法打字期间 value 会带着拼音
                                        // 合成区一起回来，trim 掉尾部空格后回传的值就和 IME
                                        // 里的不一致，光标会被弹到开头。留到确认时再收拾。
                                        setModelId(it)
                                    }
                                },
                                label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    if (!isEdit) {
                                        Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                                    }
                                },
                                enabled = !isEdit,
                                shape = RoundedCornerShape(16.dp)
                            )

                            KedgeOutlinedTextFieldWithSlots(
                                value = model.displayName,
                                onValueChange = {
                                    onModelChange(model.copy(displayName = it))
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            if (isEdit) R.string.setting_provider_page_model_name
                                            else R.string.setting_provider_page_model_display_name
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    if (!isEdit) {
                                        Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        ModelFormGroup(stringResource(R.string.setting_provider_page_model_type)) {
                            ModelTypeSelector(
                                selectedType = model.type,
                                onTypeSelected = {
                                    onModelChange(model.copy(type = it))
                                },
                                fillWidth = false,
                            )
                        }

                        ModelModalitySelector(
                            model = model,
                            inputModalities = model.inputModalities,
                            onUpdateInputModalities = {
                                onModelChange(model.copy(inputModalities = it))
                            },
                            outputModalities = model.outputModalities,
                            onUpdateOutputModalities = {
                                onModelChange(model.copy(outputModalities = it))
                            },
                        )

                        if (model.type == ModelType.CHAT) {
                            ModelFormGroup(stringResource(R.string.setting_provider_page_abilities)) {
                                ModalAbilitySelector(
                                    abilities = model.abilities,
                                    onUpdateAbilities = {
                                        onModelChange(model.copy(abilities = it))
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderOverrideSettings(
                            providerOverride = model.providerOverwrite,
                            onUpdateProviderOverride = { providerOverride ->
                                onModelChange(model.copy(providerOverwrite = providerOverride))
                            },
                            parentProvider = parentProvider
                        )

                        CustomHeaders(
                            headers = model.customHeaders,
                            onUpdate = { headers ->
                                onModelChange(model.copy(customHeaders = headers))
                            }
                        )

                        CustomBodies(
                            customBodies = model.customBodies,
                            onUpdate = { bodies ->
                                onModelChange(model.copy(customBodies = bodies))
                            }
                        )
                    }
                }

                2 -> {
                    // 内置工具页面
                    BuiltInToolsSettings(
                        tools = model.tools,
                        onUpdateTools = { tools ->
                            onModelChange(model.copy(tools = tools))
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddModelButton(
    models: List<Model>,
    selectedModels: List<Model>,
    expanded: Boolean,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val dialogState = useEditState<Model> {
        onAddModel(it.copy(displayName = it.displayName.trim()))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            models = models,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                val inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId)
                val outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId)
                val abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                onAddModel(
                    model.copy(
                        inputModalities = inputModalities,
                        outputModalities = outputModalities,
                        abilities = abilities
                    )
                )
            },
            onModelDeselected = { model ->
                onRemoveModel(model)
            },
            onAllModelSelected = {
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models + it.filter { model ->
                            parentProvider.models.none { existing -> existing.modelId == model.modelId }
                        }.map { model ->
                            model.copy(
                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                            )
                        }
                    )
                )
            },
            onAllModelDeselected = { filteredModels ->
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models.filter { model ->
                            filteredModels.none { filtered -> filtered.modelId == model.modelId }
                        }
                    )
                )
            }
        )

        KedgeButton(
            onClick = {
                dialogState.open(Model())
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    add,
                    contentDescription = stringResource(R.string.setting_provider_page_add_model)
                )
                AnimatedVisibility(expanded) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.setting_provider_page_add_new_model),
                        style = KedgeTextStyles.body()
                    )
                }
            }
        }
    }

    if (dialogState.isEditing) {
        dialogState.currentState?.let { modelState ->
            PrimaryBottomSheet(
                visible = true,
                title = stringResource(R.string.setting_provider_page_add_model),
                imageVector = memory,
                confirmText = stringResource(R.string.setting_provider_page_add),
                onConfirm = {
                    // 首尾空格留到这里再收拾，不在输入过程中改写 value（会打断 IME 合成）。
                    val trimmedId = modelState.modelId.trim()
                    if (trimmedId.isNotBlank() && modelState.displayName.isNotBlank()) {
                        dialogState.currentState =
                            modelState.copy(modelId = trimmedId, displayName = modelState.displayName.trim())
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
                        // 原来按窗口高度的 95% 取，再叠上标题栏和确认按钮行就超出 sheet 的
                        // 可用高度，确认按钮被挤到屏幕外只剩一条边。这里留出标题与按钮行的预算。
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
                            model = modelState,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = false,
                            parentProvider = parentProvider
                        )
                    }

                }
            }
        }
    }
}
