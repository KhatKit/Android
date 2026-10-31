package heizige.kk.khatkit.app.feature.settings.provider

import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.runtime.LaunchedEffect
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.KkCard
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import heizige.kk.khatkit.uikit.mochi.KkOptionSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar
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
import heizige.kk.khatkit.uikit.mochi.Toast
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
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
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
import heizige.kk.khatkit.uikit.mochi.KkMultiChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeTabRow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.FormMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.FormRowLabel
import heizige.kk.khatkit.uikit.mochi.KkFloatingToolbar
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
            // 模型行是同一组（圆角按 index/count 算），项间距取全局列表卡片样式
            verticalArrangement = Arrangement.spacedBy(listCardStyle().gap),
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.setting_provider_page_add_models_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // 用 itemsIndexed 而不只是 items：`ModelCard` 的圆角不再有默认值，
                // 得按「组里第几项 / 共几项」从 listCardStyle() 算出来传下去
                // （与提供商页、工具页同一套口径：首项只圆上边、末项只圆下边）。
                itemsIndexed(
                    items = providerSetting.models,
                    key = { _, item -> item.id },
                ) { index, item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.id
                    ) { isDragging ->
                        KkGroupedColumn(
                            modifier = Modifier.graphicsLayer {
                                if (isDragging) {
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                } else {
                                    scaleX = 1f
                                    scaleY = 1f
                                }
                            },
                            shape = listCardStyle().indexedShape(index, providerSetting.models.size),
                            containerColor = MochiListDefaults.colors().container,
                            topDivider = index > 0 && listCardStyle().gap == 0.dp,
                        ) {
                            ModelCard(
                                model = item,
                                shape = listCardStyle().indexedShape(index, providerSetting.models.size),
                                onDelete = {
                                    onUpdateProvider(providerSetting.delModel(item))
                                },
                                onEdit = { editedModel ->
                                    onUpdateProvider(providerSetting.editModel(editedModel))
                                },
                                parentProvider = providerSetting,
                                modifier = Modifier.longPressDraggableHandle(),
                            )
                        }
                    }
                }
            }
        }
        KkFloatingToolbar(
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
 * （`KedgeFormCard` / `FormMetrics`）以及 KernelSU 的分组偏好一致。
 *
 * 卡内边距由**这里**给、两种风格都要给：`KedgeFormCard` 只是分段容器 ——
 * `InlineCardGroup` 自己画底色 + 收角，但**不加内边距**（组内是 `KedgeFormRow` 时内边距
 * 由行自己带，见 `FormItem` 的 `modifier` 注释）。本组装的是「标题 + 任意控件」，没有行
 * 组件帮忙撑内边距，所以自己补一层；MD3 下也一样（改造前 MD3 那 16dp 来自旧 `KkCard`
 * 的默认 `contentPadding`，现在容器不再提供）。
 */
@Composable
internal fun ModelFormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KedgeFormCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(FormMetrics.ItemPadding + 2.dp),
            verticalArrangement = Arrangement.spacedBy(FormMetrics.ItemSpacing),
        ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
        
            content()
        }
    }
}

@Composable
internal fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tabLabels = listOf(
        stringResource(R.string.setting_provider_page_basic_settings),
        stringResource(R.string.setting_provider_page_advanced_settings),
        stringResource(R.string.setting_page_built_in_tools),
    ).toImmutableList()

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

    Column(modifier.fillMaxSize()) {
        // 页签用 Mochi 分段控件（不是 MD3 下划线 TabRow），左右滑动由下面的 Pager 承担
        MochiSegmentedControl(
            selectedIndex = pagerState.currentPage,
            onSelect = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            items = tabLabels,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            behavior = SheetTabBehavior,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicModelCard(
                        model = model,
                        onModelChange = onModelChange,
                        isEdit = isEdit,
                        setModelId = ::setModelId,
                    )
                }

                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

                2 -> BuiltInToolsSettings(
                    tools = model.tools,
                    onUpdateTools = { tools ->
                        onModelChange(model.copy(tools = tools))
                    }
                )
            }
        }
    }
}

/** 面板页签：和 Mochi 列表里的分段控件同一尺寸。 */
private val SheetTabBehavior = MochiSegmentedDefaults.behavior(height = 32.dp, segmentPadding = 12.dp)

/**
 * 基本设置：**一张**分组卡片，信息 / 类型 / 输入 / 输出 / 能力用分隔线分段。
 * 不再像以前那样一个分组一张卡（用户反馈：同一份表单里属于一部分的 option item 应该合在一起）。
 */
@Composable
private fun BasicModelCard(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    setModelId: (String) -> Unit,
) {
    KedgeFormCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FormMetrics.ItemPadding + 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelFormSectionTitle(stringResource(R.string.setting_provider_page_model_info))
            Md3OutlinedTextFieldWithSlots(
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

            Md3OutlinedTextFieldWithSlots(
                value = model.displayName,
                onValueChange = { onModelChange(model.copy(displayName = it)) },
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

            ModelFormDivider()

            ModelFormSectionTitle(stringResource(R.string.setting_provider_page_model_type))
            ModelTypeSelector(
                selectedType = model.type,
                onTypeSelected = { onModelChange(model.copy(type = it)) },
                fillWidth = false,
            )

            if (model.type == ModelType.CHAT) {
                ModelFormDivider()
                ModelFormSectionTitle(stringResource(R.string.setting_provider_page_input_modality))
                ModelInputModalitySegmented(
                    inputModalities = model.inputModalities,
                    onUpdate = { onModelChange(model.copy(inputModalities = it)) },
                )

                ModelFormDivider()
                ModelFormSectionTitle(stringResource(R.string.setting_provider_page_output_modality))
                ModelOutputModalitySegmented(
                    outputModalities = model.outputModalities,
                    onUpdate = { onModelChange(model.copy(outputModalities = it)) },
                )

                ModelFormDivider()
                ModelFormSectionTitle(stringResource(R.string.setting_provider_page_abilities))
                ModalAbilitySelector(
                    abilities = model.abilities,
                    onUpdateAbilities = { onModelChange(model.copy(abilities = it)) },
                )
            }
        }
    }
}

@Composable
private fun ModelFormSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModelFormDivider() {
    MochiHorizontalDivider(modifier = Modifier.fillMaxWidth())
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

        MochiButton(
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // U7b-2e：最后一份 currentState 快照 + LaunchedEffect 驱动；退出动画期间读快照不 NPE。
    // 表单直接开到大档：半屏放不下三页签内容，打开即所见
    val addModelSheet = rememberMochiSheetState(detents = listOf(MochiSheetDetent.Large))
    var lastEditingModel by remember { mutableStateOf(dialogState.currentState) }
    LaunchedEffect(dialogState.isEditing) {
        if (dialogState.isEditing) {
            lastEditingModel = dialogState.currentState
            addModelSheet.show()
        } else {
            addModelSheet.hide()
        }
    }
    lastEditingModel?.let { modelState ->
            PrimaryBottomSheet(
                state = addModelSheet,
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
                // 顶栏浮在正文上：标题/添加不再占一行把内容挤下去，正文铺满面板
                floatingHeader = true,
            ) { _ ->
                ModelSettingsForm(
                    model = modelState,
                    onModelChange = { dialogState.currentState = it },
                    isEdit = false,
                    parentProvider = parentProvider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
}
