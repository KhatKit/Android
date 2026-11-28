package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavBottomBar
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavTab
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavPages
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavPageReselectHandler
import heizige.kk.khatkit.app.feature.ui.shell.rememberMochiNavPagesState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginRecord
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginStore
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.neurology
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSliderRow

@Composable
fun SettingModelPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navPages = rememberMochiNavPagesState(2)
    val modelList = rememberLazyListState()
    val promptList = rememberLazyListState()
    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_model_page_title),
        rail = {
            MochiNavBottomBar(listOf(
                MochiNavTab(neurology, stringResource(R.string.setting_model_page_tab_model), navPages.currentPage == 0) {
                    navPages.select(0)
                },
                MochiNavTab(editNote, stringResource(R.string.setting_model_page_tab_prompt), navPages.currentPage == 1) {
                    navPages.select(1)
                },
            ))
        },
    ) { padding ->
        MochiNavPages(state = navPages, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ModelSettingsPage(settings, vm, padding, modelList)
                1 -> {
                    MochiNavPageReselectHandler { promptList.animateScrollToItem(0) }
                    PromptSettingsPage(settings, vm, padding, promptList)
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(settings: Settings, vm: SettingViewModel, contentPadding: PaddingValues, listState: LazyListState) {
    MochiNavPageReselectHandler { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "model-assignments") {
            MochiListSection(header = stringResource(R.string.setting_model_page_tab_model)) {
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_chat_model),
                    description = stringResource(R.string.setting_model_page_chat_model_desc),
                    modelId = settings.chatModelId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(chatModelId = it.id)) },
                )
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_fast_model),
                    description = stringResource(R.string.setting_model_page_fast_model_desc),
                    modelId = settings.fastModelId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(fastModelId = it.id)) },
                    reasoningLevel = settings.fastModelReasoningLevel,
                    onUpdateReasoningLevel = {
                        vm.updateSettings(settings.copy(fastModelReasoningLevel = it))
                    },
                )
                // 记忆整理模型：没设置时跟快速模型（上游 72d419171）
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_memory_model),
                    description = stringResource(R.string.setting_model_page_memory_model_desc),
                    modelId = settings.memoryModelId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(memoryModelId = it.id)) },
                    emptyText = stringResource(R.string.setting_model_page_memory_model_follow_fast),
                    onClear = { vm.updateSettings(settings.copy(memoryModelId = null)) },
                    // 没设置时推理强度也跟着快速模型，所以只有选了模型才显示这一行
                    reasoningLevel = settings.memoryModelReasoningLevel.takeIf { settings.memoryModelId != null },
                    onUpdateReasoningLevel = {
                        vm.updateSettings(settings.copy(memoryModelReasoningLevel = it))
                    },
                )
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_translate_model),
                    modelId = settings.translateModeId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(translateModeId = it.id)) },
                )
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_ocr_model),
                    description = stringResource(R.string.setting_model_page_ocr_model_desc),
                    modelId = settings.ocrModelId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(ocrModelId = it.id)) },
                )
                ModelSettingItem(
                    title = stringResource(R.string.setting_model_page_compress_model),
                    description = stringResource(R.string.setting_model_page_compress_model_desc),
                    modelId = settings.compressModelId,
                    providers = settings.providers,
                    onSelect = { vm.updateSettings(settings.copy(compressModelId = it.id)) },
                )
            }
        }
        item(key = "ocr-plugin") {
            // OCR5：插件入口。未安装时给「去市场安装」提示，点击跳探索市场（OCR 扩展条目在市场页里）。
            OcrPluginSettingItem()
        }
        item(key = "auto-compress") {
            MochiListSection {
                MochiListRow(
                    title = stringResource(R.string.setting_model_page_auto_compress_title),
                    subtitle = stringResource(R.string.setting_model_page_auto_compress_desc),
                    trailing = {
                        KkSwitch(
                            checked = settings.enableAutoCompress,
                            onCheckedChange = {
                                vm.updateSettings(settings.copy(enableAutoCompress = it))
                            },
                        )
                    },
                )
                MochiListSliderRow(
                    value = settings.autoCompressThreshold,
                    onValueChange = {
                        vm.updateSettings(
                            settings.copy(autoCompressThreshold = it.coerceIn(0.5f..0.95f))
                        )
                    },
                    valueRange = 0.5f..0.95f,
                    steps = 8,
                    enabled = settings.enableAutoCompress,
                    title = stringResource(R.string.setting_model_page_auto_compress_threshold),
                    valueText = "${(settings.autoCompressThreshold * 100).toInt()}%",
                    supportingText = stringResource(R.string.setting_model_page_auto_compress_threshold_desc),
                )
            }
        }
    }
}

@Composable
private fun ModelSettingItem(
    title: String,
    description: String? = null,
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    onSelect: (Model) -> Unit,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
    /** 没选模型时显示的文字（例如「跟随快速模型」） */
    emptyText: String? = null,
    /** 给了清除入口时，选了模型后行尾显示清除图标；用于「不设置 = 跟随别的模型」的槽位 */
    onClear: (() -> Unit)? = null,
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = ModelType.CHAT,
    )

    MochiListRow(
        title = title,
        subtitle = description,
        value = state.currentModel?.displayName
            ?: emptyText
            ?: stringResource(R.string.model_list_select_model),
        onClick = { state.open() },
        trailing = if (onClear != null && state.currentModel != null) {
            {
                MochiIconButton(onClick = onClear) {
                    Icon(
                        imageVector = close,
                        contentDescription = stringResource(R.string.model_list_clear_model),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            null
        },
    )
    if (reasoningLevel != null && onUpdateReasoningLevel != null) {
        MochiListRow(
            title = stringResource(R.string.assistant_page_thinking_budget),
            trailing = {
                ReasoningButton(
                    reasoningLevel = reasoningLevel,
                    onUpdateReasoningLevel = onUpdateReasoningLevel,
                )
            },
        )
    }

    ModelListSheet(state = state, onSelect = onSelect)
}

/**
 * OCR5：OCR 插件入口。
 *
 * 状态只读 [OcrPluginStore]（安装/卸载都在探索市场做，这里不重复写盘），点击普通压栈跳探索市场 ——
 * OCR 原生插件是市场里的扩展条目（kind=dependency）。
 * 文案内联：本阶段只允许改 feature/settings，string 资源不在范围内。
 */
@Composable
private fun OcrPluginSettingItem() {
    val context = LocalContext.current
    // 设置里的子页一律普通压栈，不做共享元素形变（和 SettingPage 的列表入口同一套）
    val navController = LocalNavController.current
    val record by produceState<OcrPluginRecord?>(initialValue = null) {
        value = runCatching { OcrPluginStore(context).read() }.getOrNull()
    }
    val version = record?.version?.takeIf { it.isNotBlank() }

    MochiListSection(header = "OCR 插件") {
        MochiListRow(
            title = if (version == null) "未安装" else "已安装",
            subtitle = if (version == null) "去市场安装后可用本机 OCR 插件" else "已安装，可在市场里更新或卸载",
            value = version?.let { "v$it" } ?: "去市场安装",
            onClick = { navController.navigate(Screen.ExploreMarket) },
            chevron = true,
        )
    }
}
