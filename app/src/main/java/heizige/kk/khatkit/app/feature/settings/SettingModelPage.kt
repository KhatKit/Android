package heizige.kk.khatkit.app.feature.settings

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
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
                                ?: stringResource(R.string.model_list_select_model),
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
