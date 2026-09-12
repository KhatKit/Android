package heizige.kk.khatkit.app.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import heizige.kk.khromia.components.AnimatedRadioButton
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.app.data.datastore.DEFAULT_SYSTEM_TTS_ID
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.ui.components.ui.Tag
import heizige.kk.khatkit.app.ui.components.ui.TagType
import heizige.kk.khatkit.app.ui.context.LocalTTSState
import heizige.kk.khatkit.app.ui.pages.setting.components.ASRProviderConfigure
import heizige.kk.khatkit.app.ui.pages.setting.components.TTSProviderConfigure
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.utils.plus
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import heizige.kk.khatkit.app.ui.icons.add
import heizige.kk.khatkit.app.ui.icons.build
import heizige.kk.khatkit.app.ui.icons.delete
import heizige.kk.khatkit.app.ui.icons.dragIndicator
import heizige.kk.khatkit.app.ui.icons.edit
import heizige.kk.khatkit.app.ui.icons.mic
import heizige.kk.khatkit.app.ui.icons.stopCircle
import heizige.kk.khatkit.app.ui.icons.volumeUp

@Composable
fun SettingSpeechPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    var selectedPage by remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.speech_page_title),
                navigationIcon = {
                    BackButton()
                },
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
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CustomColors.cardColorsOnSurfaceContainer.containerColor
            ) {
                NavigationBarItem(
                    selected = selectedPage == 0,
                    onClick = { selectedPage = 0 },
                    icon = { Icon(volumeUp, contentDescription = null) },
                    label = { Text(stringResource(R.string.speech_tab_tts)) }
                )
                NavigationBarItem(
                    selected = selectedPage == 1,
                    onClick = { selectedPage = 1 },
                    icon = { Icon(mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.speech_tab_asr)) }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when (selectedPage) {
            0 -> Column(modifier = Modifier.padding(innerPadding)) {
                TTSProviderList(
                    settings = settings,
                    onUpdateSettings = vm::updateSettings,
                    onEdit = { editingTTSProvider = it },
                    modifier = Modifier.weight(1f),
                )
            }

            1 -> ASRProviderList(
                settings = settings,
                onUpdateSettings = vm::updateSettings,
                onEdit = { editingASRProvider = it },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    // Edit TTS Provider Bottom Sheet
    editingTTSProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }

        PrimaryBottomSheet(
            visible = true,
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
                    .padding(16.dp)
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

    editingASRProvider?.let { provider ->
        var currentProvider by remember(provider) { mutableStateOf(provider) }

        PrimaryBottomSheet(
            visible = true,
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
                    .padding(16.dp)
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
private fun TTSProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (TTSProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.ttsProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings(settings.copy(ttsProviders = newProviders))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState
    ) {
        items(settings.ttsProviders, key = { it.id }) { provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                TTSProviderItem(
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .fillMaxWidth(),
                    provider = provider,
                    dragHandle = {
                        val haptic = LocalHapticFeedback.current
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = dragIndicator,
                                contentDescription = null
                            )
                        }
                    },
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
}

@Composable
private fun ASRProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (ASRProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.asrProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings(settings.copy(asrProviders = newProviders))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState
    ) {
        items(settings.asrProviders, key = { it.id }) { provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                ASRProviderItem(
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .fillMaxWidth(),
                    provider = provider,
                    dragHandle = {
                        val haptic = LocalHapticFeedback.current
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = dragIndicator,
                                contentDescription = null
                            )
                        }
                    },
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
}

@Composable
private fun AddTTSProviderButton(onAdd: (TTSProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentProvider: TTSProviderSetting by remember { mutableStateOf(TTSProviderSetting.SystemTTS()) }

    IconButton(
        onClick = {
            currentProvider = TTSProviderSetting.SystemTTS()
            showBottomSheet = true
        }
    ) {
        Icon(add, stringResource(R.string.setting_tts_page_add_provider_content_description))
    }

    if (showBottomSheet) {
        PrimaryBottomSheet(
            visible = true,
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
                    .padding(16.dp)
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
}

@Composable
private fun AddASRProviderButton(onAdd: (ASRProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var currentProvider: ASRProviderSetting by remember { mutableStateOf(ASRProviderSetting.OpenAIRealtime()) }

    Box {
        IconButton(
            onClick = { showTypeMenu = true }
        ) {
            Icon(add, stringResource(R.string.setting_asr_page_add_provider))
        }
        DropdownMenu(
            expanded = showTypeMenu,
            onDismissRequest = { showTypeMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("OpenAI Realtime") },
                onClick = {
                    currentProvider = ASRProviderSetting.OpenAIRealtime()
                    showTypeMenu = false
                    showBottomSheet = true
                }
            )
            DropdownMenuItem(
                text = { Text("DashScope") },
                onClick = {
                    currentProvider = ASRProviderSetting.DashScope()
                    showTypeMenu = false
                    showBottomSheet = true
                }
            )
            DropdownMenuItem(
                text = { Text("Volcengine") },
                onClick = {
                    currentProvider = ASRProviderSetting.Volcengine()
                    showTypeMenu = false
                    showBottomSheet = true
                }
            )
            DropdownMenuItem(
                text = { Text("MiMo") },
                onClick = {
                    currentProvider = ASRProviderSetting.MiMo()
                    showTypeMenu = false
                    showBottomSheet = true
                }
            )
            DropdownMenuItem(
                text = { Text("Step") },
                onClick = {
                    currentProvider = ASRProviderSetting.Step()
                    showTypeMenu = false
                    showBottomSheet = true
                }
            )
        }
    }

    if (showBottomSheet) {
        PrimaryBottomSheet(
            visible = true,
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
                    .padding(16.dp)
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
private fun TTSProviderItem(
    provider: TTSProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    dragHandle: @Composable () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val isAvailable by tts.isAvailable.collectAsState()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                CustomColors.listItemColors.containerColor
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoAIIcon(
                    name = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = when (provider) {
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
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedRadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )

                dragHandle()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态标签
                if (isSelected) {
                    Tag(type = TagType.SUCCESS) {
                        Text(stringResource(R.string.setting_tts_page_selected))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // TTS测试播放按钮
                if (isSelected && isAvailable) {
                    val testText = stringResource(R.string.setting_tts_page_test_text)
                    IconButton(
                        onClick = {
                            if (!isSpeaking) {
                                tts.speak(testText)
                            } else {
                                tts.stop()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) stopCircle else volumeUp,
                            contentDescription = if (isSpeaking) stringResource(R.string.stop) else stringResource(R.string.test_tts),
                            tint = if (isSpeaking) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                }

                IconButton(
                    onClick = { showDropdownMenu = true }
                ) {
                    Icon(
                        imageVector = build,
                        contentDescription = stringResource(R.string.setting_tts_page_more_options_content_description)
                    )
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showDropdownMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showDropdownMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(delete, contentDescription = null)
                            },
                            enabled = provider.id != DEFAULT_SYSTEM_TTS_ID
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ASRProviderItem(
    provider: ASRProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    dragHandle: @Composable () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                CustomColors.listItemColors.containerColor
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoAIIcon(
                    name = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) },
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = when (provider) {
                            is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
                            is ASRProviderSetting.DashScope -> "DashScope"
                            is ASRProviderSetting.Volcengine -> "Volcengine"
                            is ASRProviderSetting.MiMo -> "MiMo"
                            is ASRProviderSetting.Step -> "Step"
                            is ASRProviderSetting.OpenAITranscribe -> "OpenAI Transcribe"
                            is ASRProviderSetting.GeminiTranscribe -> "Gemini Transcribe"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedRadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )

                dragHandle()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Tag(type = TagType.SUCCESS) {
                        Text(stringResource(R.string.setting_tts_page_selected))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { showDropdownMenu = true }
                ) {
                    Icon(
                        imageVector = build,
                        contentDescription = stringResource(R.string.setting_tts_page_more_options_content_description)
                    )
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showDropdownMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showDropdownMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}
