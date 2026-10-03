package heizige.kk.khatkit.app.feature.settings.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeBadge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.registry.ModelRegistry
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ai.ModelAbilityTag
import heizige.kk.khatkit.app.core.ui.components.ai.ModelModalityTag
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khromia.components.SegmentedItem
import heizige.kk.kedge.components.KedgeSingleChoiceSegmentedRow
import heizige.kk.kedge.components.KedgeMultiChoiceSegmentedRow
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeBadgedBox


@Composable
internal fun ModelPicker(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    if (showModal) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.setting_provider_page_avaliable_models),
            imageVector = memory,
            onDismiss = { showModal = false },
            scrollable = false,
        ) { _ ->
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = models.fastFilter {
                if (filterKeywords.isEmpty()) {
                    true
                } else {
                    filterKeywords.all { keyword ->
                        it.modelId.contains(keyword, ignoreCase = true) ||
                            it.displayName.contains(keyword, ignoreCase = true)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val unselectedCount = filteredModels.count { model ->
                        selectedModels.none { it.modelId == model.modelId }
                    }

                    KedgeTextButton(
                        onClick = {
                            if (unselectedCount > 0) {
                                onAllModelSelected(filteredModels)
                            } else {
                                onAllModelDeselected(filteredModels)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(
                            if (unselectedCount > 0) stringResource(
                                R.string.setting_provider_page_select_all,
                                unselectedCount
                            ) else stringResource(R.string.setting_provider_page_deselect_models)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(filteredModels) {
                        KedgeCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            ) {
                                AutoAIIcon(
                                    it.modelId,
                                    Modifier.size(32.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(
                                        4.dp
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = it.modelId,
                                        style = KedgeTextStyles.title(),
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val modelMeta = remember(it) {
                                            it.copy(
                                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(it.modelId),
                                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(it.modelId),
                                                abilities = ModelRegistry.MODEL_ABILITIES.getData(it.modelId),
                                            )
                                        }
                                        ModelModalityTag(
                                            model = modelMeta,
                                        )
                                        ModelAbilityTag(
                                            model = modelMeta,
                                        )
                                    }
                                }
                                KedgeIconButton(
                                    onClick = {
                                        if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                            // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                            onModelDeselected(selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                                ?: it)
                                        } else {
                                            onModelSelected(it)
                                        }
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                        Icon(close, null)
                                    } else {
                                        Icon(add, null)
                                    }
                                }
                            }
                        }
                    }
                }
                KedgeOutlinedTextFieldWithSlots(
                    value = filterText,
                    onValueChange = {
                        filterText = it
                    },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.setting_provider_page_filter_example))
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
    KedgeBadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                KedgeBadge {
                    Text(models.size.toString())
                }
            }
        }
    ) {
        KedgeIconButton(
            onClick = {
                showModal = true
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(package2, null)
        }
    }
}

/**
 * 模型类型（聊天/图像/嵌入）单选。
 *
 * 标题由外层分组卡片给出，这里只出胶囊行；[fillWidth] 传 `false` 让它和下面几个多选组
 * 一样按文案宽度排布，同一张表单里不会出现「一排等分大药丸 + 一排窄药丸」的错位。
 */
@Composable
internal fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit,
    fillWidth: Boolean = true,
) {
    KedgeSingleChoiceSegmentedRow(
        items = ModelType.entries.map { type ->
            SegmentedItem(
                label = stringResource(
                    when (type) {
                        ModelType.CHAT -> R.string.setting_provider_page_chat_model
                        ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                        ModelType.IMAGE -> R.string.setting_provider_page_image_model
                    }
                ),
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
            )
        },
        fillWidth = fillWidth,
    )
}

/**
 * 输入/输出模态（文本/图片）多选。
 *
 * 两组各有自己的标题与卡片，所以这里自己出两个 [ModelFormGroup]；调用方只管传数据。
 * 只有聊天模型才显示 —— 图像/嵌入模型的模态由类型本身决定。
 */
@Composable
internal fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit,
) {
    if (model.type == ModelType.CHAT) {
        ModelFormGroup(stringResource(R.string.setting_provider_page_input_modality)) {
            KedgeMultiChoiceSegmentedRow(
                items = Modality.entries.map { modality ->
                    SegmentedItem(
                        label = stringResource(
                            when (modality) {
                                Modality.TEXT -> R.string.setting_provider_page_text
                                Modality.IMAGE -> R.string.setting_provider_page_image
                            }
                        ),
                        selected = modality in inputModalities,
                        onClick = {
                            onUpdateInputModalities(
                                if (modality in inputModalities) {
                                    inputModalities - modality
                                } else {
                                    inputModalities + modality
                                }
                            )
                        },
                    )
                },
            )
        }

        ModelFormGroup(stringResource(R.string.setting_provider_page_output_modality)) {
            KedgeMultiChoiceSegmentedRow(
                items = Modality.entries.map { modality ->
                    SegmentedItem(
                        label = stringResource(
                            when (modality) {
                                Modality.TEXT -> R.string.setting_provider_page_text
                                Modality.IMAGE -> R.string.setting_provider_page_image
                            }
                        ),
                        selected = modality in outputModalities,
                        onClick = {
                            onUpdateOutputModalities(
                                if (modality in outputModalities) {
                                    outputModalities - modality
                                } else {
                                    outputModalities + modality
                                }
                            )
                        },
                    )
                },
            )
        }
    }
}

/** 能力（工具/推理）多选。标题同样交给外层分组卡片。 */
@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit,
) {
    KedgeMultiChoiceSegmentedRow(
        items = ModelAbility.entries.map { ability ->
            SegmentedItem(
                label = stringResource(
                    when (ability) {
                        ModelAbility.TOOL -> R.string.setting_provider_page_tool
                        ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                    }
                ),
                selected = ability in abilities,
                onClick = {
                    onUpdateAbilities(
                        if (ability in abilities) abilities - ability else abilities + ability
                    )
                },
            )
        },
    )
}
