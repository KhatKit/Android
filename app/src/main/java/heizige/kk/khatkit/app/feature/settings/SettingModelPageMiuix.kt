package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.neurology
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import top.yukonga.miuix.kmp.basic.Icon
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/**
 * 默认模型页的 Miuix 风格版。
 *
 * 原页是 MD3 Scaffold + BottomAppBar + HorizontalPager；这里换成 Miuix 的
 * [NavigationBar] 做两个页签，模型选择项用 [PreferenceArrow]（右侧显示当前模型名 + 箭头）。
 */
@Composable
fun SettingModelPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // 与 MD3 版一致：底栏点击与左右滑动共用同一个 pagerState。
    val pagerState = rememberPagerState { 2 }
    val page by remember { derivedStateOf { pagerState.currentPage } }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_model_page_title),
        navigationIcon = { BackButton() },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = neurology,
                    label = stringResource(R.string.setting_model_page_tab_model),
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = editNote,
                    label = stringResource(R.string.setting_model_page_tab_prompt),
                )
            }
        },
    ) {
        // Pager 只能整体放进**一个** item（它自己是可测量的），所以原来
        // 「miuixGroup 发 5 个 item + 另一分支 1 个 item」的写法要收成一个定高容器：
        // 每页各自竖向滚动，页与页之间横向滑动。
        item {
            Box(modifier = Modifier.fillParentMaxHeight(0.92f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { p ->
                    if (p == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            KedgeCard(
                                modifier = Modifier.fillMaxWidth(),
                                // 与 miuixGroup 一致：卡片不带内边距，
                                // 里面的 PreferenceArrow 自带 InsideMargin。
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Column {
                    ModelSettingRow(
                        title = stringResource(R.string.setting_model_page_chat_model),
                        modelId = settings.chatModelId,
                        providers = settings.providers,
                        onSelect = { vm.updateSettings(settings.copy(chatModelId = it.id)) },
                    )
                    ModelSettingRow(
                        title = stringResource(R.string.setting_model_page_fast_model),
                        modelId = settings.fastModelId,
                        providers = settings.providers,
                        onSelect = { vm.updateSettings(settings.copy(fastModelId = it.id)) },
                reasoningLevel = settings.fastModelReasoningLevel,
                onUpdateReasoningLevel = {
                    vm.updateSettings(settings.copy(fastModelReasoningLevel = it))
                },
                    )
                    ModelSettingRow(
                        title = stringResource(R.string.setting_model_page_translate_model),
                        modelId = settings.translateModeId,
                        providers = settings.providers,
                        onSelect = { vm.updateSettings(settings.copy(translateModeId = it.id)) },
                    )
                    ModelSettingRow(
                        title = stringResource(R.string.setting_model_page_ocr_model),
                        modelId = settings.ocrModelId,
                        providers = settings.providers,
                        onSelect = { vm.updateSettings(settings.copy(ocrModelId = it.id)) },
                    )
                    ModelSettingRow(
                        title = stringResource(R.string.setting_model_page_compress_model),
                        modelId = settings.compressModelId,
                        providers = settings.providers,
                        onSelect = { vm.updateSettings(settings.copy(compressModelId = it.id)) },
                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            PromptSettingsContent(
                                settings = settings,
                                onUpdate = vm::updateSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单个模型选择行：标题 + 描述 + 当前模型名 + 箭头，点击弹出模型列表。 */
@Composable
private fun ModelSettingRow(
    title: String,
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    onSelect: (Model) -> Unit,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = heizige.kk.khatkit.ai.provider.ModelType.CHAT,
    )

    PreferenceArrow(
        title = title,
        summary = state.currentModel?.displayName
            ?: stringResource(R.string.model_list_select_model),
        onClick = { state.open() },
    )

    ModelListSheet(state = state, onSelect = onSelect)
}
