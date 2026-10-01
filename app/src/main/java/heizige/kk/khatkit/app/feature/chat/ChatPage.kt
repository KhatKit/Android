package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.ui.icons.search
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.core.data.datastore.getSelectedASRProvider
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.feature.chat.ChatError
import heizige.kk.khatkit.app.core.ui.components.ai.ChatAttachmentPickerActions
import heizige.kk.khatkit.app.core.ui.components.ai.ChatInput
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.app.core.ui.components.ai.FilesPicker
import heizige.kk.khatkit.app.core.ui.components.ai.SearchMode
import heizige.kk.khatkit.app.core.ui.components.ai.completion.WorkspaceCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.ai.rememberChatAttachmentPickerActions
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.core.ui.hooks.EditStateContent
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.util.base64Decode
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.addComment
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.menu
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeMiuixMorphingTitleBar
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatViewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(creationCallback = { it.create(id.toString()) })
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            // 分享进来的附件复制与 MIME 查询都是磁盘 IO，不能在主线程做
            val (localFiles, contentTypes) = withContext(Dispatchers.IO) {
                filesManager.createChatFilesByContents(files) to files.mapNotNull { file ->
                    filesManager.getFileMimeType(file)
                }
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatViewModel,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = rememberAppEntryPoint().workspaceRepository()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var previewSearchQuery by remember { mutableStateOf("") }
    val hazeState = rememberHazeState()
    val assistant = setting.getCurrentAssistant()
    val modelListState = rememberModelListState(
        modelId = assistant.chatModelId ?: setting.chatModelId,
        providers = setting.providers,
        type = ModelType.CHAT,
    )
    var showFilesSheet by remember { mutableStateOf(false) }
    ModelListSheet(
        state = modelListState,
        onSelect = { vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it) },
    )
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        setting.getCurrentChatModel()?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    // 滚动消息列表时折叠/展开顶栏。
    //
    // Miuix 下由 KedgePageScaffold 建立**唯一**一个 Miuix 折叠行为，同时接上
    // nestedScroll 并通过 LocalKedgePageScrollBehavior 下发给顶栏（顶栏读同一个
    // 实例才能折叠大标题）。这里不要再自己 remember 一个，否则会出现多个实例、
    // 顶栏观察的那个没人喂滚动。
    // MD3 仍是页面自己接 scrollBehavior 的 nestedScroll。
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeNestedScroll = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        Modifier
    } else {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    Surface(
        color = heizige.kk.kedge.theme.KedgeColors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        // 用 KedgePageScaffold：Miuix 下自动建立 backdrop 并只包住内容槽，
        // 顶栏作为兄弟节点采样（KernelSU MainActivity.kt:306 同构）。
        KedgePageScaffold(
            modifier = activeNestedScroll,
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    searchQuery = previewSearchQuery,
                    onSearchQueryChange = { previewSearchQuery = it },
                    onCollapseSearch = {
                        previewMode = false
                        previewSearchQuery = ""
                    },
                    scrollBehavior = scrollBehavior,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onModelClick = {
                        modelListState.open()
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    }
                )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    enableSearch = enableWebSearch,
                    onUpdateSearchMode = { mode ->
                        val current = setting.getCurrentAssistant()
                        val model = setting.getCurrentChatModel()
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == current.id) {
                                        assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                                    } else {
                                        assistant
                                    }
                                },
                                providers = if (model == null) {
                                    setting.providers
                                } else {
                                    setting.providers.map { provider ->
                                        provider.editModel(
                                            model.copy(
                                                tools = if (mode == SearchMode.BUILT_IN) {
                                                    model.tools + BuiltInTools.Search
                                                } else {
                                                    model.tools - BuiltInTools.Search
                                                }
                                            )
                                        )
                                    }
                                },
                            )
                        )
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            Toast.show("请先选择模型", isError = true)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                    attachmentActions = attachmentPickerActions,
                )
            },
            containerColor = Color.Transparent,
    ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                previewSearchQuery = previewSearchQuery,
                onPreviewSearchQueryChange = { previewSearchQuery = it },
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatViewModel,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.more_options),
        imageVector = extension,
        onDismiss = { dismissAll() },
    ) { _ ->
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = {
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCollapseSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onModelClick: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    // 搜索（预览）展开进度：预测返回手势跟手收起
    val searchExpand = rememberSearchExpandState(
        expanded = previewMode,
        onCollapse = onCollapseSearch,
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }

    // 顶栏副标题只显示当前模型名（助手名 + 提供商名在顶栏里太挤，已去掉）。
    val topBarModel = settings.getCurrentChatModel()
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        // Miuix：照搬 KernelSU HomeMiuix 的顶栏 —— 原版 TopAppBar 默认
        // largeTitle = title，即真正的大标题栏，滚动时折叠成小标题。
        // 搜索态把输入框放进 bottomContent（title 让位），避免与折叠标题打架。
        KedgeMiuixMorphingTitleBar(
            // 标题 morph 成搜索输入框，与 MD3 版行为一致（点标题/点搜索图标进入）。
            title = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
            expanded = previewMode,
            keyword = searchQuery,
            onKeywordChange = onSearchQueryChange,
            dragProgress = searchProgress.value,
            onExitSearch = onCollapseSearch,
            placeholder = stringResource(R.string.history_page_search),
            subtitle = topBarModel?.displayName,
            navigationIcon = {
                if (bigScreen) {
                    BackButton(
                        onClick = onCollapseSearch,
                    )
                } else {
                    MiuixIconButton(onClick = { scope.launch { drawerState.open() } }) {
                        MiuixIcon(menu, contentDescription = "Messages")
                    }
                }
            },
            actions = {
                // 搜索图标切「聊天内容预览搜索」(previewMode)；与 MD3 版一致，
                // 带搜索↔关闭同槽交叉淡化。选模型走标题点击，不在此处。
                MiuixIconButton(onClick = onClickMenu) {
                    Box(contentAlignment = Alignment.Center) {
                        MiuixIcon(
                            imageVector = search,
                            contentDescription = "Chat Options",
                            modifier = Modifier.graphicsLayer {
                                val progress = searchProgress.value
                                alpha = 1f - progress
                                scaleX = 1f - 0.15f * progress
                                scaleY = 1f - 0.15f * progress
                            },
                        )
                        MiuixIcon(
                            imageVector = close,
                            contentDescription = "Chat Options",
                            modifier = Modifier.graphicsLayer {
                                val progress = searchProgress.value
                                alpha = progress
                                scaleX = 0.85f + 0.15f * progress
                                scaleY = 0.85f + 0.15f * progress
                            },
                        )
                    }
                }
                MiuixIconButton(onClick = onNewChat) {
                    MiuixIcon(addComment, contentDescription = "New Message")
                }
            },
        )
        return
    }

    KedgePageLargeTopBar(
        colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = Color.Transparent),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            // 导航槽宽度与搜索进度同步（大屏 0→48dp，小屏菜单常驻保持 48dp），
            // 菜单↔返回图标交叉淡化，避免占位突变导致标题/搜索框跳动。
            Box(
                modifier = Modifier
                    .width(if (bigScreen) 48.dp * searchProgress.value else 48.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (!bigScreen) {
                    KedgeIconButton(
                        onClick = {
                            scope.launch { drawerState.open() }
                        },
                        enabled = searchProgress.value < 0.5f,
                        modifier = Modifier
                            .width(48.dp)
                            .graphicsLayer {
                                val progress = searchProgress.value
                                alpha = 1f - progress
                                scaleX = 1f - 0.15f * progress
                                scaleY = 1f - 0.15f * progress
                            },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(menu, "Messages")
                    }
                }
                KedgeIconButton(
                    onClick = onCollapseSearch,
                    enabled = searchProgress.value >= 0.5f,
                    modifier = Modifier
                        .width(48.dp)
                        .graphicsLayer {
                            val progress = searchProgress.value
                            alpha = progress
                            scaleX = 0.85f + 0.15f * progress
                            scaleY = 0.85f + 0.15f * progress
                        },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(arrowBack, contentDescription = null)
                }
            }
        },
        title = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
        subtitle = topBarModel?.displayName,
        titleContent = {
            Box {
                if (titleVisible) {
                    val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
                    Surface(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = 1f - searchProgress.value
                                translationX = -searchProgress.value * 24.dp.toPx()
                            }
                            .combinedClickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = onModelClick,
                                onLongClick = {
                                    if (conversation.messageNodes.isNotEmpty()) {
                                        titleState.open(conversation.title)
                                    } else {
                                        Toast.show(editTitleWarning, isError = false)
                                    }
                                },
                            ),
                        color = Color.Transparent,
                    ) {
                        Column {
                            val assistant = settings.getCurrentAssistant()
                            val model = settings.getCurrentChatModel()
                            val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                            Text(
                                text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                                maxLines = 1,
                                style = MaterialTheme.typography.titleLarge,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (model != null && provider != null) {
                                Text(
                                    text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    color = LocalContentColor.current.copy(0.65f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                if (previewMode || searchVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f + 0.35f * searchProgress.value)
                            .graphicsLayer {
                                alpha = searchProgress.value
                                translationX = (1f - searchProgress.value) * 24.dp.toPx()
                            },
                    ) {
                        if (searchQuery.isBlank()) {
                            Text(
                                text = stringResource(R.string.history_page_search),
                                style = androidx.compose.material3.LocalTextStyle.current,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        actions = {
            KedgeIconButton(
                onClick = {
                    onClickMenu()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                // 搜索↔关闭图标同槽交叉淡化，不再整体切换 imageVector。
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        search,
                        contentDescription = "Chat Options",
                        modifier = Modifier.graphicsLayer {
                            val progress = searchProgress.value
                            alpha = 1f - progress
                            scaleX = 1f - 0.15f * progress
                            scaleY = 1f - 0.15f * progress
                        },
                    )
                    Icon(
                        close,
                        contentDescription = "Chat Options",
                        modifier = Modifier.graphicsLayer {
                            val progress = searchProgress.value
                            alpha = progress
                            scaleX = 0.85f + 0.15f * progress
                            scaleY = 0.85f + 0.15f * progress
                        },
                    )
                }
            }

            KedgeIconButton(
                onClick = {
                    onNewChat()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(addComment, "New Message")
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AppAlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                KedgeOutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        titleState.confirm()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                KedgeTextButton(
                    onClick = {
                        titleState.dismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
