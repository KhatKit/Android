package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.LocalMorphingTitleLarge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.scrollLinkedTopBarColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
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
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
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
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantPickerSheet
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.ui.components.ai.FilesPicker
import heizige.kk.khatkit.app.core.ui.components.ai.SearchMode
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.ai.completion.WorkspaceCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.ai.rememberChatAttachmentPickerActions
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.app.core.ui.modifier.shimmer
import heizige.kk.khatkit.app.core.util.base64Decode
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.menu
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeMiuixMorphingTitleBar
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.khatkit.app.feature.chat.data.isArchivedGroupConversation

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null, messageId: Uuid? = null) {
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
    LaunchedEffect(messageId, conversation.messageNodes.size) {
        val target = messageId ?: return@LaunchedEffect
        if (conversation.messageNodes.isEmpty()) return@LaunchedEffect
        vm.focusMessage(target)
    }

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

    val archived = conversation.isArchivedGroupConversation()

    val scaffold: @Composable (Boolean) -> Unit = { bigScreen ->
        ChatScaffold(
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
            bigScreen = bigScreen,
            errors = errors,
            onDismissError = { vm.dismissError(it) },
            onClearAllErrors = { vm.clearAllErrors() },
            archived = archived,
        )
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
                scaffold(true)
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
                scaffold(false)
            }
        }
    }

}

/**
 * 角色聊天与历史记录共用的消息界面。
 * @param archived 旧群聊只展示历史和导出，不提供输入与消息修改。
 */
@Composable
internal fun ChatScaffold(
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
    archived: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val workspaceRepository: WorkspaceRepository = rememberAppEntryPoint().workspaceRepository()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var previewSearchQuery by rememberSaveable { mutableStateOf("") }
    val hazeState = rememberHazeState()
    val assistant = setting.getAssistantById(conversation.assistantId) ?: setting.getCurrentAssistant()
    var showRolePicker by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    if (showRolePicker) {
        AssistantPickerSheet(
            settings = setting,
            currentAssistant = assistant,
            onAssistantSelected = {
                showRolePicker = false
                vm.switchRole(it.id) { target ->
                    navigateToChatPage(navController, target)
                }
            },
            onDismiss = { showRolePicker = false },
        )
    }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )

    val completionProviders = remember(
        assistant.workspaceId,
        conversation.workspaceCwd,
        workspaceRepository,
    ) {
        val workspace = assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
        workspace
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    // 滚动消息列表时折叠/展开顶栏。
    //
    // Miuix 下由 KedgePageScaffold 建立**唯一**一个 Miuix 折叠行为，同时接上
    // nestedScroll 并通过 LocalKedgePageScrollBehavior 下发给顶栏（顶栏读同一个
    // 实例才能折叠大标题）。这里不要再自己 remember 一个，否则会出现多个实例、
    // 顶栏观察的那个没人喂滚动。
    // MD3 仍是页面自己接 scrollBehavior 的 nestedScroll —— 但要按风格取，
    // 详见 activeNestedScroll 的注释（多挂一个 MD3 连接会吃掉全部滚动增量）。
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeNestedScroll = activeNestedScroll(scrollBehavior)

    KedgeSurface(
        color = heizige.kk.kedge.theme.KedgeColors.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        // 用 KedgePageScaffold：Miuix 下自动建立 backdrop 并只包住内容槽，
        // 顶栏作为兄弟节点采样（KernelSU MainActivity.kt:306 同构）。
        // 助手背景要画在**内容槽里面**（见下面），不能留在 Scaffold 外面：
        // backdrop 只录制内容槽那棵子树，画在槽外就不在模糊源里，顶栏会糊不到它。
        KedgePageScaffold(
            modifier = activeNestedScroll,
            topBar = {
                    TopBar(
                        settings = setting,
                        // 顶栏角色与正文背景**同一个对象**：:315 已经按
                        // getAssistantById(conversation.assistantId) ?: getCurrentAssistant()
                        // 解析过，这里不再让顶栏自己解析一遍（两处各解析一次正是历史 bug 的温床）。
                        role = assistant,
                        hazeState = hazeState,
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
                        onOpenRolePanel = { roleId ->
                            // 右上角三横杠：进角色聊天面板。roleId 是顶栏**已解析出的那个
                            // 角色**（topBarRole.id，含 `?: 当前角色` 回退），导航与面板查询
                            // 同源；会话 id 只给「重命名当前聊天」用。
                            navController.navigate(
                                Screen.RoleChatPanel(
                                    roleId = roleId.toString(),
                                    conversationId = conversation.id.toString(),
                                )
                            )
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onRoleClick = {
                            showRolePicker = true
                        },
                    )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                if (archived) {
                    Text(
                        "历史群聊已归档",
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    )
                } else ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    onSendQueuedMessageNow = vm::sendQueuedMessageNow,
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
                            Toast.show("请先为角色配置模型", isError = true)
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
                    // 长按发送键但没配语音识别模型 → 弹「去配置」→ 跳到语音设置页
                    onConfigureVoice = { navController.navigate(Screen.SettingSpeech) },
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
                    onUpdateSearchRepository = { index ->
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
            // 助手背景（图片/流光）放在内容槽内的最底层：Miuix 下 backdrop 录制的
            // 就是这棵子树，画在槽外顶栏的毛玻璃采样不到它 —— 表现是「顶栏没有背景
            // 模糊」，只有消息列表被糊掉。
            Box(modifier = Modifier.fillMaxSize()) {
                // 角色由本函数 :315 解析好的 `assistant`（= getAssistantById(会话角色) ?: 当前角色）
                // 传入，与顶栏、ChatInput 全部同源。背景图/流光不再自己解析全局 currentAssistant。
                AssistantBackground(assistant = assistant, modifier = Modifier.hazeSource(hazeState))
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
                onEdit = { message ->
                    if (!archived) {
                        inputState.editingMessage = message.id
                        inputState.setContents(message.parts)
                    }
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

                // 空会话欢迎层：发出第一条消息（messageNodes 变非空）就整层淡出。
                // 盖在 ChatList 之上，而不是塞进它的 LazyColumn 当一个 item ——
                // 列表为空时本来没有项，塞进去会让 item 数凭空 +1，滚动条比例、
                // 「滚到底部」的目标索引、自动跟随这几套算术都得跟着改口径。
                ChatWelcomeHost(
                    visible = !archived && !previewMode && conversation.messageNodes.isEmpty(),
                    sessionKey = conversation.id.toString(),
                    manager = vm.welcome,
                    // 只填输入框，不代发：发送路径上还有「角色没配模型」等守卫，
                    // 欢迎层不该替用户跨过去。
                    onSuggestionClick = { inputState.setMessageText(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
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

/**
 * 顶栏角色信息还在从 DataStore 恢复时的轻量骨架。
 *
 * 宽高与真实标题区保持一致，避免启动时角色数据到达后发生横向跳动。
 */
@Composable
private fun RoleTitlePlaceholder(largeRow: Boolean) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (largeRow) 12.dp else 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(baseColor)
                .shimmer(isLoading = true),
        )
        Box(
            modifier = Modifier
                .width(if (largeRow) 128.dp else 88.dp)
                .height(if (largeRow) 20.dp else 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(baseColor)
                .shimmer(isLoading = true),
        )
    }
}

/**
 * Miuix 顶栏底色覆写：把 Miuix 主题里的 `surface` 换成透明，让正文那张角色背景透上来。
 *
 * 为什么在页面侧覆主题色、而不是给顶栏传颜色：`KedgeMiuixMorphingTitleBar` 没有颜色入参，
 * 底色在组件内部按 `LocalKedgePageBackdrop` 决定 —— 有 backdrop（毛玻璃开着，本页由
 * `KedgePageScaffold` 建立）时用 `Color.Transparent`，否则退回
 * `MiuixTheme.colorScheme.surface`。**关掉毛玻璃时 backdrop 为 null**，顶栏就是一块实色，
 * 角色自定义背景被它盖住 —— 这里只在「角色有自定义背景」时把 `surface` 换成透明。
 *
 * 只改 `surface` 一个槽位，`onSurface` / `primary` / `textStyles` 全部沿用当前主题
 * （`textStyles` 显式透传，避免 MiuixTheme 重载退化成默认字阶），因此字色、字阶与栏高
 * 都不受影响；`transparent = false` 时直接透传，不引入任何额外节点。
 */
@Composable
private fun MiuixMorphingBarSurface(
    transparent: Boolean,
    content: @Composable () -> Unit,
) {
    if (!transparent) {
        content()
        return
    }
    MiuixTheme(
        colors = MiuixTheme.colorScheme.copy(surface = Color.Transparent),
        textStyles = MiuixTheme.textStyles,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    settings: Settings,
    /**
     * 本会话的角色，由 [ChatScaffold] 用
     * `getAssistantById(conversation.assistantId) ?: getCurrentAssistant()` 解析后传入。
     * 顶栏与正文背景（[AssistantBackground]）共用同一个实例，避免两处各解析一次而分叉。
     */
    role: Assistant,
    /** 与正文背景同一个 haze 源，供顶栏在角色有自定义背景时做毛玻璃。 */
    hazeState: HazeState,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCollapseSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onClickMenu: () -> Unit,
    onOpenRolePanel: (roleId: Uuid) -> Unit,
    onRoleClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix

    // 搜索（预览）展开进度：预测返回手势跟手收起
    val searchExpand = rememberSearchExpandState(
        expanded = previewMode,
        onCollapse = onCollapseSearch,
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }

    val searchFocus = remember { FocusRequester() }
    var focusLargeTitleRow by remember { mutableStateOf(true) }
    LaunchedEffect(previewMode) {
        if (previewMode) {
            if (!isMiuix) {
                focusLargeTitleRow = scrollBehavior.state.collapsedFraction < 0.5f
            }
            delay(100)
            searchFocus.requestFocus()
        }
    }

    // 顶栏角色 = 调用方传入的那个角色（与正文背景同一个 Assistant 实例）。
    val topBarRole = role
    val topBarModel = settings.findModelById(topBarRole.chatModelId ?: settings.chatModelId)
    // 顶栏标题区改成「角色」驱动：头像 + 角色名，副标题是模型名。
    // 会话标题（conversation.title）不再上顶栏 —— 它是会话属性，不是角色身份；
    // 改名入口随长按标题一起移到角色聊天面板（ChatRolePanelPage）。
    val roleName = topBarRole.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
    val roleModelName = topBarModel?.displayName.orEmpty()

    // 角色有没有自定义底色（背景图 / 流光）。有的话顶栏不能再自带一块不透明色：
    // 正文里的 AssistantBackground 是按整页铺的（Scaffold 的 content 从 (0,0) 起布局，
    // innerPadding 只是传给列表当 contentPadding），顶栏只要画了实色就是「盖住角色背景」，
    // 也正是用户说的「顶栏和正文不是一个角色」的观感来源之一。
    val roleHasCustomBackground = role.background != null || role.useGradientBackground
    // MD3 顶栏底色：
    //  - 无自定义背景 → 与改造前逐像素一致（静止态 pageContainerColor，滚动后渐变到
    //    CustomColors.topBarColors 的 surfaceContainer）；
    //  - 有自定义背景 → 两态都透明，把底色让给正文那张背景；开模糊时再叠一层 haze
    //    （与 Miuix 侧 backdrop 毛玻璃口径对齐），关模糊时就是纯透明。
    // 折叠态与展开态吃的是同一份 colors，不会出现「展开透明、收起变灰块」。
    val chatTopBarColors = if (roleHasCustomBackground) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
    } else {
        scrollLinkedTopBarColors(CustomColors.topBarColors, scrollBehavior)
    }
    val chatTopBarModifier =
        if (roleHasCustomBackground && settings.displaySetting.enableBlurEffect) {
            Modifier.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle.Material3 { blurRadius(20.dp) },
            )
        } else {
            Modifier
        }

    /**
     * 标题区的富内容（头像 + 角色名 / 模型）。两条风格分支共用同一份：
     * - MD3：作为 `LargeFlexibleTopAppBar` 的 `title` 槽，展开行与收起行各渲染一次，
     *   行内字阶由栏下发（沿用改造前按 `LocalTextStyle` 判行的写法）；
     * - Miuix：`KedgeMiuixMorphingTitleBar` 的 `titleContent` 覆盖层，组件通过
     *   [LocalMorphingTitleLarge] 明确告知当前是折叠行还是大标题行。
     * 点击仍走 onRoleClick（Miuix 靠组件自带的透明点击覆盖层，MD3 靠下面 combinedClickable）。
     */
    val roleTitleContent: @Composable () -> Unit = {
        val largeRow = LocalMorphingTitleLarge.current ||
            LocalTextStyle.current.fontSize > MaterialTheme.typography.titleLarge.fontSize
        if (settings.init) {
            RoleTitlePlaceholder(largeRow = largeRow)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (largeRow) 12.dp else 8.dp),
            ) {
                UIAvatar(
                    name = roleName,
                    value = topBarRole.avatar,
                    avatarShape = topBarRole.avatarShape,
                    defaultIconName = settings.findModelProviderName(
                        topBarRole.chatModelId ?: settings.chatModelId
                    ),
                    avatarColor = topBarRole.avatarColor,
                    onUpdate = null,
                    // 折叠行/大标题行同尺寸 32dp（= UIAvatar 内部默认值）。
                    // 原来按 largeRow 分 40/26：Miuix 覆盖层的字阶判定不可靠，折叠行实际会拿到
                    // 大标题字阶而画成 40dp，26dp 分支等于空操作；顶栏高度两态都容得下 32dp。
                    // 需要改尺寸时只改这里，UIAvatar 内部的 `size(32.dp)` 会被外层固定尺寸覆盖
                    // （enforceIncoming），不会再抬回 32dp。
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        roleName,
                        style = LocalTextStyle.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (roleModelName.isNotBlank()) {
                        Text(
                            roleModelName,
                            style = KedgeTextStyles.body(),
                            color = LocalContentColor.current.copy(0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    val largeTitle: @Composable () -> Unit = {
        val titleStyle = LocalTextStyle.current
        val isLargeTitleRow = titleStyle.fontSize > MaterialTheme.typography.titleLarge.fontSize
        Box {
            if (titleVisible) {
                KedgeSurface(
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - searchProgress.value
                        translationX = -searchProgress.value * 24.dp.toPx()
                    }
                        .combinedClickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!settings.init) onRoleClick() },
                        ),
                    color = Color.Transparent,
                ) {
                    roleTitleContent()
                }
            }
            if (searchVisible) {
                Box(Modifier.fillMaxWidth(0.65f + 0.35f * searchProgress.value).graphicsLayer {
                    alpha = searchProgress.value
                    translationX = (1f - searchProgress.value) * 24.dp.toPx()
                }) {
                    if (searchQuery.isBlank()) {
                        Text(
                            stringResource(R.string.history_page_search), style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = titleStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().then(
                            if (isLargeTitleRow == focusLargeTitleRow) Modifier.focusRequester(searchFocus) else Modifier
                        ),
                    )
                }
            }
        }
    }

    // 会话重命名弹窗已随「长按标题改名」一起移到角色聊天面板（ChatRolePanelPage 的
    // 「重命名当前聊天」option item），顶栏不再持有 EditState。

    if (isMiuix) {
        // Miuix 顶栏的底色不由本页传参：`KedgeMiuixMorphingTitleBar` 内部按
        // `LocalKedgePageBackdrop` 决定 —— 有 backdrop（毛玻璃开着，本页由
        // `KedgePageScaffold` 建立）就是 `Color.Transparent`，否则取
        // `MiuixTheme.colorScheme.surface`（全站统一的页面色，不是 MD3 的
        // surfaceContainer）。所以「角色有自定义背景图/流光」时这里只把主题里的
        // `surface` 换成透明，让正文那张背景在两态（展开 / 折叠）都透上来；
        // 没有自定义背景时不包这层，顶栏颜色与现状逐像素一致。
        MiuixMorphingBarSurface(transparent = roleHasCustomBackground) {
            KedgeMiuixMorphingTitleBar(
                // 字符串标题只是「量具」：titleContent 非 null 时组件把它的颜色透明度归零，
                // 折叠区间/栏高仍按同一段文本度量，所以照传角色名，不要换成空串。
                title = roleName,
                expanded = previewMode,
                keyword = searchQuery,
                onKeywordChange = onSearchQueryChange,
                dragProgress = searchProgress.value,
                dragging = searchExpand.dragging.value,
                onExitSearch = onCollapseSearch,
                placeholder = stringResource(R.string.history_page_search),
                subtitle = roleModelName,
                focusRequester = searchFocus,
                titleContent = roleTitleContent,
                onTitleClick = { if (!settings.init) onRoleClick() },
                navigationIcon = {
                    // 一个按钮槽内做「菜单 ↔ 返回」交叉淡化：返回箭头**替换**菜单图标，
                    // 手法照侧边栏（只 alpha + 横移，不叠 scale）。大屏常驻返回箭头，
                    // 只是点击动作从「上一页」切成「退出搜索」。
                    val p = searchProgress.value
                    MiuixIconButton(
                        onClick = {
                            when {
                                p >= 0.5f -> onCollapseSearch()
                                bigScreen -> navController.popBackStack()
                                else -> scope.launch { drawerState.open() }
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!bigScreen) {
                                MiuixIcon(
                                    imageVector = menu,
                                    contentDescription = "Messages",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = 1f - p
                                        translationX = p * 24.dp.toPx()
                                    },
                                )
                            }
                            MiuixIcon(
                                imageVector = arrowBack,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer {
                                    alpha = p
                                    translationX = (1f - p) * -24.dp.toPx()
                                },
                            )
                        }
                    }
                },
                actions = {
                    // 搜索图标切「聊天内容预览搜索」(previewMode)；与 MD3 版一致，
                    // 判定同样照 ImageToolbox SettingsContent（搜索态无输入时留空）。
                    val searching = searchProgress.value >= 0.5f
                    val hasQuery = searchProgress.value >= 0.5f && searchQuery.isNotEmpty()
                    MiuixIconButton(onClick = onClickMenu) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!searching) {
                                MiuixIcon(
                                    imageVector = search,
                                    contentDescription = "Chat Options",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = 1f - searchProgress.value
                                    },
                                )
                            }
                            if (searching && hasQuery) {
                                MiuixIcon(
                                    imageVector = close,
                                    contentDescription = "Chat Options",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = searchProgress.value
                                    },
                                )
                            }
                        }
                    }
                    // 右上角：原来这里是「新建会话」(addComment)。改成三横杠，
                    // 打开角色聊天面板（角色配置 / 历史聊天 / 新建聊天 / 重命名当前聊天）。
                    MiuixIconButton(onClick = { if (!settings.init) onOpenRolePanel(topBarRole.id) }) {
                        MiuixIcon(menu, contentDescription = stringResource(R.string.chat_role_panel_title))
                    }
                },
            )
        }
        return
    }

    // MD3 顶栏走 M3 的 LargeFlexibleTopAppBar，而不是 `KedgePageLargeTopBar`：
    // 后者内部用 `scrollLinkedTopBarColors` 把 containerColor 硬写成
    // `CustomColors.pageContainerColor`（不透明），调用方传进去的 colors 的 containerColor
    // 会被丢掉 —— 角色有自定义背景时无论传什么都盖在背景上。这里改成由本页决定底色
    // （见上面的 `chatTopBarColors` / `chatTopBarModifier`）：
    //   - 无自定义背景：`scrollLinkedTopBarColors(CustomColors.topBarColors, scrollBehavior)`，
    //     与走 wrapper 时逐像素一致（静止态页面色、滚动后渐到 surfaceContainer）；
    //   - 有自定义背景：两态透明 + 可选 haze 毛玻璃，底色就是正文那张角色背景。
    // 折叠/展开共用同一份 colors，不会出现「展开透明、收起变灰块」。
    LargeFlexibleTopAppBar(
        title = largeTitle,
        modifier = chatTopBarModifier,
        colors = chatTopBarColors,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            // 菜单 ↔ 返回在**同一个槽位**交叉淡化：返回箭头替换菜单图标。
            // 一个按钮槽内画两张图标（手法照侧边栏：只 alpha + 横移，不叠 scale）；
            // 此前用两个按钮靠 enabled 互斥切换，0.5 附近既会被染成 disabled 色，
            // 叠 scale 还会把跳变放大。
            val p = searchProgress.value
            Box(
                // 大屏槽位随进度从 0 长到 48dp（小屏菜单常驻 48dp）：原本大屏没有
                // 导航图标，搜索态才让位给返回箭头，槽宽跟着长，标题/输入框不会
                // 因为占位突变而左右跳。
                modifier = Modifier.width(if (bigScreen) 48.dp * p else 48.dp),
                contentAlignment = if (bigScreen) Alignment.CenterStart else Alignment.Center,
            ) {
                if (!bigScreen || p > 0.001f) {
                    KedgeIconButton(
                        onClick = {
                            if (p >= 0.5f) {
                                onCollapseSearch()
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!bigScreen) {
                                Icon(
                                    imageVector = menu,
                                    contentDescription = "Messages",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = 1f - p
                                        translationX = p * 24.dp.toPx()
                                    },
                                )
                            }
                            Icon(
                                imageVector = arrowBack,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer {
                                    alpha = p
                                    translationX = (1f - p) * -24.dp.toPx()
                                },
                            )
                        }
                    }
                }
            }
        },
        // 标题槽只放一份富内容（头像 + 角色名 / 模型）：大标题行与折叠行都由
        // `LargeFlexibleTopAppBar` 各渲染一次同一个 lambda，行内字阶由栏下发。
        // roleName / roleModelName 只用于 Miuix 的量具文本与无障碍，不再重复传给 MD3 栏。
        actions = {
            KedgeIconButton(
                onClick = {
                    onClickMenu()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                // search↔close 同槽交叉淡化，判定照 ImageToolbox SettingsContent：
                // searching && hasQuery → close；!searching → search；
                // 搜索态但还没输入时图标槽留空（两边都不命中）。
                val searching = searchProgress.value >= 0.5f
                val hasQuery = searchProgress.value >= 0.5f && searchQuery.isNotEmpty()
                Box(contentAlignment = Alignment.Center) {
                    if (!searching) {
                        Icon(
                            search,
                            contentDescription = "Chat Options",
                            modifier = Modifier.graphicsLayer {
                                val progress = searchProgress.value
                                alpha = 1f - progress
                            },
                        )
                    }
                    if (searching && hasQuery) {
                        Icon(
                            close,
                            contentDescription = "Chat Options",
                            modifier = Modifier.graphicsLayer {
                                alpha = searchProgress.value
                            },
                        )
                    }
                }
            }

            // 右上角：原来这里是「新建会话」(addComment)。三横杠打开角色聊天面板。
            KedgeIconButton(
                onClick = {
                    if (!settings.init) onOpenRolePanel(topBarRole.id)
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(menu, stringResource(R.string.chat_role_panel_title))
            }
        },
    )
}
