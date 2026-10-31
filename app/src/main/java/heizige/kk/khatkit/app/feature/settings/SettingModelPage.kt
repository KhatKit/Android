package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginRecord
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginStore
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.neurology
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun SettingModelPage(vm: SettingViewModel = hiltViewModel()) {
    
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    // P3a：页面骨架直接换成 Mochi 的 TabScaffold（底部 Tab 栏由 Mochi 自己画），
    // 标题/返回走 MochiLargeTitleScaffold 的标题管线。
    val tabItems = persistentListOf(
        MochiTabItem(
            label = stringResource(R.string.setting_model_page_tab_model),
            icon = { tint -> Icon(neurology, contentDescription = null, tint = tint) },
        ),
        MochiTabItem(
            label = stringResource(R.string.setting_model_page_tab_prompt),
            icon = { tint -> Icon(editNote, contentDescription = null, tint = tint) },
        ),
    )
    MochiTabScaffold(
        items = tabItems,
        state = rememberMochiTabScaffoldState(pagerState.currentPage),
        onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
    ) {
        MochiLargeTitleScaffold(
            title = stringResource(R.string.setting_model_page_title),
            navigationIcon = { heizige.kk.khatkit.app.core.ui.components.nav.BackButton() },
        ) {
            val contentPadding = PaddingValues(0.dp)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> ModelSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
                    1 -> PromptSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(settings: Settings, vm: SettingViewModel, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_chat_model),
                description = stringResource(R.string.setting_model_page_chat_model_desc),
                modelId = settings.chatModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(chatModelId = it.id)) },
            )
        }
        item {
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
        }
        item {
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
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_translate_model),
                modelId = settings.translateModeId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(translateModeId = it.id)) },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_ocr_model),
                description = stringResource(R.string.setting_model_page_ocr_model_desc),
                modelId = settings.ocrModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(ocrModelId = it.id)) },
            )
        }
        item {
            // OCR5：插件入口。未安装时给「去市场安装」提示，点击跳探索市场（OCR 扩展条目在市场页里）。
            OcrPluginSettingItem()
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_compress_model),
                description = stringResource(R.string.setting_model_page_compress_model_desc),
                modelId = settings.compressModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(compressModelId = it.id)) },
            )
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

    Column {
        CardGroup(title = { Text(title) }) {
            item(
                onClick = { state.open() },
                headlineContent = { Text(title) },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = state.currentModel?.displayName
                                ?: emptyText
                                ?: stringResource(R.string.model_list_select_model),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onClear != null && state.currentModel != null) {
                            Icon(
                                imageVector = close,
                                contentDescription = stringResource(R.string.model_list_clear_model),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onClear() },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Icon(
                                arrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
            )
            if (reasoningLevel != null && onUpdateReasoningLevel != null) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                    trailingContent = {
                        ReasoningButton(
                            reasoningLevel = reasoningLevel,
                            onUpdateReasoningLevel = onUpdateReasoningLevel,
                        )
                    },
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }

    ModelListSheet(state = state, onSelect = onSelect)
}

/**
 * OCR5：OCR 插件入口。
 *
 * 状态只读 [OcrPluginStore]（安装/卸载都在探索市场做，这里不重复写盘），点击用
 * [LocalKkNavController] 跳探索市场 —— OCR 原生插件是市场里的扩展条目（kind=dependency）。
 * 文案内联：本阶段只允许改 feature/settings，string 资源不在范围内。
 */
@Composable
private fun OcrPluginSettingItem() {
    val context = LocalContext.current
    val kkNav = LocalKkNavController.current
    // 没有 Mochi 宿主时（预览/测试）退回普通导航，和 SettingPage 的 openMorph 同一套兜底
    val navController = LocalNavController.current
    val record by produceState<OcrPluginRecord?>(initialValue = null) {
        value = runCatching { OcrPluginStore(context).read() }.getOrNull()
    }
    val version = record?.version?.takeIf { it.isNotBlank() }

    Column {
        CardGroup(title = { Text("OCR 插件") }) {
            item(
                onClick = {
                    if (kkNav != null) {
                        kkNav.navigate(Screen.ExploreMarket, source = "setting-ocr-plugin")
                    } else {
                        navController.navigate(Screen.ExploreMarket)
                    }
                },
                headlineContent = {
                    Text(if (version == null) "OCR 插件：未安装" else "OCR 插件")
                },
                supportingContent = {
                    Text(
                        if (version == null) "去市场安装后可用本机 OCR 插件" else "已安装，可在市场里更新或卸载"
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = version?.let { "v$it" } ?: "去市场安装",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            arrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
    }
}
