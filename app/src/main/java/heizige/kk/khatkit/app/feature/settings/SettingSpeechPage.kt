package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_SYSTEM_TTS_ID
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalTTSState
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.mic
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.stopCircle
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.feature.settings.components.ASRProviderConfigure
import heizige.kk.khatkit.app.feature.settings.components.TTSProviderConfigure
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavBottomBar
import heizige.kk.khatkit.app.feature.ui.shell.MochiNavTab
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import heizige.kk.khatkit.uikit.mochi.MochiIconButton as KkIconButton
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.list.mochiReorderable
import ui.feice.mochi.list.rememberMochiReorderState
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults
import ui.feice.mochi.theme.MochiTheme

@Composable
fun SettingSpeechPage(vm: SettingViewModel = hiltViewModel()) {
    
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    // 底部导航 + 横向 Pager：点底栏与左右滑动共用同一个 pagerState，
    // 之前是 selectedPage 整数 + when() 直接换内容，手指划不动。
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val selectedPage by remember { derivedStateOf { pagerState.currentPage } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    MochiSettingsPageScaffold(
        // 两个 Pager 页各自给列表加顶边渐隐，骨架级的交界渐隐会把左右滑动的两页糊在一起
        edgeFade = false,
        title = stringResource(R.string.speech_page_title),
        scrollBehavior = scrollBehavior,
        actions = {
            if (selectedPage == 0) {
                AddTTSProviderButton {
                    vm.updateSettings(
                        settings.copy(
                            ttsProviders = listOf(it) + settings.ttsProviders
                        )
                    )
                }
            } else {
                AddASRProviderButton {
                    vm.updateSettings(
                        settings.copy(
                            asrProviders = listOf(it) + settings.asrProviders,
                            selectedASRProviderId = settings.selectedASRProviderId ?: it.id
                        )
                    )
                }
            }
        },
        rail = {
            MochiNavBottomBar(
                items = listOf(
                    MochiNavTab(
                        selected = selectedPage == 0,
                        icon = volumeUp,
                        label = stringResource(R.string.speech_tab_tts),
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    ),
                    MochiNavTab(
                        selected = selectedPage == 1,
                        icon = mic,
                        label = stringResource(R.string.speech_tab_asr),
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    ),
                ),
            )
        },
    ) { _ ->
        // 底栏浮在正文上：不给 Pager 加 innerPadding，各页列表自己按 contentBottomPadding() 在底部留白
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> TTSProviderList(
                    settings = settings,
                    onUpdateSettings = vm::updateSettings,
                    onEdit = { editingTTSProvider = it },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> ASRProviderList(
                    settings = settings,
                    onUpdateSettings = vm::updateSettings,
                    onEdit = { editingASRProvider = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Edit TTS Provider Bottom Sheet
    // U7b-2e：保留最后一份 provider 快照 + LaunchedEffect 驱动 show/hide；退出动画期间内容读快照，不 NPE。
    val ttsEditSheet = rememberMochiSheetState()
    var lastTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    LaunchedEffect(editingTTSProvider) {
        if (editingTTSProvider != null) {
            lastTTSProvider = editingTTSProvider
            ttsEditSheet.show()
        } else {
            ttsEditSheet.hide()
        }
    }
    lastTTSProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }

        PrimaryBottomSheet(
            state = ttsEditSheet,
            title = stringResource(R.string.setting_tts_page_edit_provider),
            imageVector = volumeUp,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = {
                val newProviders = settings.ttsProviders.map {
                    if (it.id == provider.id) currentProvider else it
                }
                vm.updateSettings(settings.copy(ttsProviders = newProviders))
                editingTTSProvider = null
            },
            onDismiss = {
                editingTTSProvider = null
            },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 同上：ASR 编辑面板用最后一份快照 + state 驱动。
    val asrEditSheet = rememberMochiSheetState()
    var lastASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    LaunchedEffect(editingASRProvider) {
        if (editingASRProvider != null) {
            lastASRProvider = editingASRProvider
            asrEditSheet.show()
        } else {
            asrEditSheet.hide()
        }
    }
    lastASRProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }

        PrimaryBottomSheet(
            state = asrEditSheet,
            title = stringResource(R.string.setting_asr_page_edit_provider),
            imageVector = mic,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = {
                val newProviders = settings.asrProviders.map {
                    if (it.id == provider.id) currentProvider else it
                }
                vm.updateSettings(settings.copy(asrProviders = newProviders))
                editingASRProvider = null
            },
            onDismiss = {
                editingASRProvider = null
            },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ASRProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun TTSProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (TTSProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val listColors = MochiListDefaults.colors()
    // 长按整行拖动排序（Mochi 自带浮起、让位与震动），松手时才写回设置
    val reorder = rememberMochiReorderState { from, to ->
        val newProviders = settings.ttsProviders.toMutableList().apply {
            add(to, removeAt(from))
        }
        onUpdateSettings(settings.copy(ttsProviders = newProviders))
    }

    SpeechProviderListColumn(lazyListState = lazyListState, modifier = modifier) {
        if (settings.ttsProviders.isNotEmpty()) {
            item("tts_providers") {
                Column(Modifier.fillMaxWidth()) {
                    // 拖动时隐藏分隔线，免得浮起的行和分隔线叠在一起
                    MochiListCard(colors = listColors, showDividers = !reorder.isDragging) {
                        settings.ttsProviders.forEachIndexed { index, provider ->
                            key(provider.id) {
                                TTSProviderItem(
                                    modifier = Modifier.mochiReorderable(reorder, provider.id, index, listColors.container),
                                    provider = provider,
                                    isSelected = settings.selectedTTSProviderId == provider.id,
                                    onSelect = {
                                        onUpdateSettings(settings.copy(selectedTTSProviderId = provider.id))
                                    },
                                    onEdit = {
                                        onEdit(provider)
                                    },
                                    onDelete = {
                                        val newProviders = settings.ttsProviders - provider
                                        val newSelectedId =
                                            if (settings.selectedTTSProviderId == provider.id) DEFAULT_SYSTEM_TTS_ID else settings.selectedTTSProviderId
                                        onUpdateSettings(
                                            settings.copy(
                                                ttsProviders = newProviders,
                                                selectedTTSProviderId = newSelectedId
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    MochiListSectionFooter(SpeechProviderListHint)
                }
            }
        }
    }
}

@Composable
internal fun ASRProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (ASRProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val listColors = MochiListDefaults.colors()
    val reorder = rememberMochiReorderState { from, to ->
        val newProviders = settings.asrProviders.toMutableList().apply {
            add(to, removeAt(from))
        }
        onUpdateSettings(settings.copy(asrProviders = newProviders))
    }

    SpeechProviderListColumn(lazyListState = lazyListState, modifier = modifier) {
        if (settings.asrProviders.isNotEmpty()) {
            item("asr_providers") {
                Column(Modifier.fillMaxWidth()) {
                    MochiListCard(colors = listColors, showDividers = !reorder.isDragging) {
                        settings.asrProviders.forEachIndexed { index, provider ->
                            key(provider.id) {
                                ASRProviderItem(
                                    modifier = Modifier.mochiReorderable(reorder, provider.id, index, listColors.container),
                                    provider = provider,
                                    isSelected = settings.selectedASRProviderId == provider.id,
                                    onSelect = {
                                        onUpdateSettings(settings.copy(selectedASRProviderId = provider.id))
                                    },
                                    onEdit = {
                                        onEdit(provider)
                                    },
                                    onDelete = {
                                        val newProviders = settings.asrProviders - provider
                                        val newSelectedId =
                                            if (settings.selectedASRProviderId == provider.id) {
                                                newProviders.firstOrNull()?.id
                                            } else {
                                                settings.selectedASRProviderId
                                            }
                                        onUpdateSettings(
                                            settings.copy(
                                                asrProviders = newProviders,
                                                selectedASRProviderId = newSelectedId
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    MochiListSectionFooter(SpeechProviderListHint)
                }
            }
        }
    }
}

private const val SpeechProviderListHint = "轻点一行选用该服务，长按拖动调整顺序"

/**
 * 两个 Pager 页共用的列表外壳：顶边贴着顶栏渐隐，底边不遮（底栏浮在正文上，列表要从它底下透出来），
 * 底部按 contentBottomPadding() 留出浮动底栏的高度。
 */
@Composable
private fun SpeechProviderListColumn(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .kkScrollEdgeFade(state = lazyListState, bottom = 0.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = MochiTabScaffoldDefaults.contentBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = lazyListState,
        content = content,
    )
}

@Composable
internal fun AddTTSProviderButton(onAdd: (TTSProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentProvider: TTSProviderSetting by remember { mutableStateOf(TTSProviderSetting.SystemTTS()) }

    KkIconButton(
        onClick = {
            currentProvider = TTSProviderSetting.SystemTTS()
            showBottomSheet = true
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(add, stringResource(R.string.setting_tts_page_add_provider_content_description))
    }

    // U7b-2e：if 门控换 sheetState（内容只读 currentProvider，无解引用）。
    val ttsAddSheet = rememberMochiSheetState()
    LaunchedEffect(showBottomSheet) { if (showBottomSheet) ttsAddSheet.show() else ttsAddSheet.hide() }
        PrimaryBottomSheet(
            state = ttsAddSheet,
            title = stringResource(R.string.setting_tts_page_add_provider),
            imageVector = volumeUp,
            confirmText = stringResource(R.string.setting_tts_page_add),
            onConfirm = {
                onAdd(currentProvider)
                showBottomSheet = false
            },
            onDismiss = {
                showBottomSheet = false
            },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
}

@Composable
internal fun AddASRProviderButton(onAdd: (ASRProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var currentProvider: ASRProviderSetting by remember { mutableStateOf(ASRProviderSetting.OpenAIRealtime()) }

    Box {
        MochiDropdown(
            expanded = showTypeMenu,
            onExpandedChange = { showTypeMenu = it },
            items = kotlinx.collections.immutable.persistentListOf(
                MochiItem(label = "OpenAI Realtime"),
                MochiItem(label = "DashScope"),
                MochiItem(label = "Volcengine"),
                MochiItem(label = "MiMo"),
                MochiItem(label = "Step"),
                MochiItem(label = "本地识别 (sherpa-onnx)"),
            ),
            onItemClick = { index ->
                when (index) {
                    0 -> currentProvider = ASRProviderSetting.OpenAIRealtime()
                    1 -> currentProvider = ASRProviderSetting.DashScope()
                    2 -> currentProvider = ASRProviderSetting.Volcengine()
                    3 -> currentProvider = ASRProviderSetting.MiMo()
                    4 -> currentProvider = ASRProviderSetting.Step()
                    5 -> currentProvider = ASRProviderSetting.SherpaLocal()
                }
                showTypeMenu = false
                showBottomSheet = true
            },
            onClick = { showTypeMenu = !showTypeMenu },
            buttonContent = { Icon(add, stringResource(R.string.setting_asr_page_add_provider)) },
        )
    }

    // U7b-2e：if 门控换 sheetState（内容只读 currentProvider，无解引用）。
    val asrAddSheet = rememberMochiSheetState()
    LaunchedEffect(showBottomSheet) { if (showBottomSheet) asrAddSheet.show() else asrAddSheet.hide() }
        PrimaryBottomSheet(
            state = asrAddSheet,
            title = stringResource(R.string.setting_asr_page_add_provider),
            imageVector = mic,
            confirmText = stringResource(R.string.setting_tts_page_add),
            onConfirm = {
                onAdd(currentProvider)
                showBottomSheet = false
            },
            onDismiss = {
                showBottomSheet = false
            },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ASRProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
}

/** 行尾的「更多」菜单：编辑 / 删除。 */
@Composable
private fun SpeechProviderMenu(
    deleteEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDropdownMenu by remember { mutableStateOf(false) }
    MochiIconDropdown(
        expanded = showDropdownMenu,
        onExpandedChange = { showDropdownMenu = it },
        items = kotlinx.collections.immutable.persistentListOf(
            MochiItem(
                label = stringResource(R.string.edit),
                icon = { color -> Icon(edit, contentDescription = null, tint = color) },
            ),
            MochiItem(
                label = stringResource(R.string.delete),
                destructive = true,
                enabled = deleteEnabled,
                icon = { color -> Icon(delete, contentDescription = null, tint = color) },
            ),
        ),
        onItemClick = { index ->
            showDropdownMenu = false
            when (index) {
                0 -> onEdit()
                1 -> if (deleteEnabled) onDelete()
            }
        },
        size = 32.dp,
    ) {
        Icon(
            imageVector = moreVert,
            contentDescription = stringResource(R.string.setting_tts_page_more_options_content_description),
            tint = MochiTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TTSProviderItem(
    provider: TTSProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val isAvailable by tts.isAvailable.collectAsState()
    val name = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) }

    // 整行点击 = 选用；选中项行尾显示对勾（原单选按钮 +「已选择」标签）
    MochiListRow(
        title = name,
        modifier = modifier,
        onClick = onSelect,
        subtitle = when (provider) {
            is TTSProviderSetting.OpenAI -> stringResource(R.string.setting_tts_page_provider_openai)
            is TTSProviderSetting.Gemini -> stringResource(R.string.setting_tts_page_provider_gemini)
            is TTSProviderSetting.MiniMax -> "MiniMax"
            is TTSProviderSetting.SystemTTS -> stringResource(R.string.setting_tts_page_provider_system)
            is TTSProviderSetting.Qwen -> "Qwen"
            is TTSProviderSetting.Groq -> "Groq"
            is TTSProviderSetting.XAI -> "xAI"
            is TTSProviderSetting.MiMo -> "MiMo"
            is TTSProviderSetting.Step -> "Step"
            is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
            is TTSProviderSetting.FishAudio -> "Fish Audio"
            is TTSProviderSetting.Volcengine -> "火山引擎"
        },
        leading = {
            AutoAIIcon(name = name, modifier = Modifier.size(MochiListDefaults.LeadingSize))
        },
        checked = isSelected,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TTS测试播放按钮
                if (isSelected && isAvailable) {
                    val testText = stringResource(R.string.setting_tts_page_test_text)
                    MochiIconButton(
                        onClick = {
                            if (!isSpeaking) {
                                tts.speak(testText)
                            } else {
                                tts.stop()
                            }
                        },
                        style = MochiButtonStyle.Text,
                        size = 32.dp,
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) stopCircle else volumeUp,
                            contentDescription = if (isSpeaking) stringResource(R.string.stop) else stringResource(R.string.test_tts),
                            tint = if (isSpeaking) MochiTheme.colorScheme.error else MochiTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                SpeechProviderMenu(
                    deleteEnabled = provider.id != DEFAULT_SYSTEM_TTS_ID,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        },
    )
}

@Composable
private fun ASRProviderItem(
    provider: ASRProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val name = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) }

    MochiListRow(
        title = name,
        modifier = modifier,
        onClick = onSelect,
        subtitle = when (provider) {
            is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
            is ASRProviderSetting.DashScope -> "DashScope"
            is ASRProviderSetting.Volcengine -> "Volcengine"
            is ASRProviderSetting.MiMo -> "MiMo"
            is ASRProviderSetting.Step -> "Step"
            is ASRProviderSetting.OpenAITranscribe -> "OpenAI Transcribe"
            is ASRProviderSetting.GeminiTranscribe -> "Gemini Transcribe"
            is ASRProviderSetting.SherpaLocal -> "本地识别 (sherpa-onnx)"
        },
        leading = {
            AutoAIIcon(name = name, modifier = Modifier.size(MochiListDefaults.LeadingSize))
        },
        checked = isSelected,
        trailing = {
            SpeechProviderMenu(
                deleteEnabled = true,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        },
    )
}
