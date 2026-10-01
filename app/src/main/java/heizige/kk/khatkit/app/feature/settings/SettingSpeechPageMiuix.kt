package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.icons.mic
import heizige.kk.khatkit.app.feature.settings.components.ASRProviderConfigure
import heizige.kk.khatkit.app.feature.settings.components.TTSProviderConfigure
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/**
 * 语音设置页的 Miuix 风格版（TTS / ASR 双页签）。
 *
 * provider 卡片、拖拽排序（sh.calvin.reorderable）本就与风格无关；配置表单里
 * 67 个输入框已换成 Kedge 的 KedgeTextField，因此这里只换外壳与页签栏。
 */
@Composable
fun SettingSpeechPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pageState = remember { mutableIntStateOf(0) }
    val page = pageState.intValue
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }

    MiuixSettingsPage(
        title = stringResource(R.string.speech_page_title),
        navigationIcon = { BackButton() },
        actions = {
            if (page == 0) {
                AddTTSProviderButton { picked ->
                    vm.updateSettings(
                        settings.copy(ttsProviders = listOf(picked) + settings.ttsProviders)
                    )
                }
            } else {
                AddASRProviderButton { picked ->
                    vm.updateSettings(
                        settings.copy(asrProviders = listOf(picked) + settings.asrProviders)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { pageState.intValue = 0 },
                    icon = volumeUp,
                    label = stringResource(R.string.speech_tab_tts),
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { pageState.intValue = 1 },
                    icon = mic,
                    label = stringResource(R.string.speech_tab_asr),
                )
            }
        },
    ) {
        item {
            Box(modifier = Modifier.fillParentMaxHeight(0.92f)) {
                if (page == 0) {
                    TTSProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingTTSProvider = it },
                    )
                } else {
                    ASRProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingASRProvider = it },
                    )
                }
            }
        }
    }

    editingTTSProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.setting_tts_page_edit_provider),
            imageVector = volumeUp,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = {
                vm.updateSettings(
                    settings.copy(
                        ttsProviders = settings.ttsProviders.map {
                            if (it.id == provider.id) currentProvider else it
                        }
                    )
                )
                editingTTSProvider = null
            },
            onDismiss = { editingTTSProvider = null },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { currentProvider = it },
                )
            }
        }
    }

    editingASRProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.setting_asr_page_edit_provider),
            imageVector = mic,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = {
                vm.updateSettings(
                    settings.copy(
                        asrProviders = settings.asrProviders.map {
                            if (it.id == provider.id) currentProvider else it
                        }
                    )
                )
                editingASRProvider = null
            },
            onDismiss = { editingASRProvider = null },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ASRProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { currentProvider = it },
                )
            }
        }
    }
}
