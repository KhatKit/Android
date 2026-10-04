package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
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
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
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
import heizige.kk.khatkit.app.core.ui.components.ai.ModelListSheet
import heizige.kk.khatkit.app.core.ui.components.ai.rememberModelListState
import heizige.kk.khatkit.app.core.ui.components.ai.FilesPicker
import heizige.kk.khatkit.app.core.ui.components.ai.SearchMode
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.ai.completion.WorkspaceCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.ai.rememberChatAttachmentPickerActions
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
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
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeMiuixMorphingTitleBar
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface

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
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
    }
}

/**
 * 单聊页和群聊页共用的消息区骨架。
 *
 * C1 之前群聊页（`GroupChatPage`）自己手写了一套 `LazyColumn` + `Text` 气泡 +
 * `KedgeOutlinedTextFieldWithSlots` 输入框，单聊这一侧已经调通的行为——抽屉、大屏分栏、
 * 语音模式、停止生成、毛玻璃、键盘跟随、@ 选择器插槽——一个都没接过去，两套页面各自漂移。
 * 这里把原先 `private` 的 `ChatPageContent` 提成 [ChatScaffold] 并开出三个**带默认值**的
 * 注入缝，让群聊页复用同一条消息管线，而不是复制一份再各自改。
 *
 * ## 纯加法：单聊零行为变化
 *
 * 三个新参数都有默认值，因此 [ChatPage] 里那两个调用点（`isBigScreen` 的
 * `PermanentNavigationDrawer` 分支与 `else` 的 `ModalNavigationDrawer` 分支）**一行都不用改**：
 * 不传即 `listOverlay = {}`、`bottomBarAboveInput = {}`、`extraCompletionProviders = emptyList()`，
 * 渲染结果与 C1 之前完全相同。**以后给本函数加参数，一律追加到列表末尾并给默认值，
 * 不要动这两个调用点的既有参数顺序。**
 *
 * @param listOverlay 画在 [ChatList] **之后**、仍在内容槽那个 `Box` 里的浮层。
 *   位置是硬要求：同一个 `Box` 里前面依次是 `AssistantBackground` 与 `ChatList`，
 *   插在它们之前会被消息列表整块压住（`AssistantBackground` 还是 `fillMaxSize`）。
 * @param bottomBarAboveInput 画在输入框正上方、仍在 `bottomBar` 槽里的内容（成员头像组等）。
 *   在 `ChatInput` 之前调用，这样它在布局上就在输入框上方，且同样被输入框的毛玻璃 backdrop
 *   覆盖（`ChatInput` 自己会 `hazeSource` 采样上方内容）。
 * @param extraCompletionProviders 追加到 `ChatInput` 的 @ 选择器 provider 列表**尾部**。
 *   ⚠️ 已知坑：`ChatInput` 里是 `val primary = lists.firstOrNull()`，多 provider 合并时
 *   **只认第一个非空列表的 `replacementRange`**，只有 `replacementRange` 相同的列表才会被
 *   合并进候选。所以追加的 provider 必须排在 workspace provider 之后，并且只在
 *   workspace provider 不触发时才给出候选，否则插入范围会错位。
 * @param topBar 替换默认的单聊 [TopBar]。为 null（单聊的默认值）时行为与 C1 之前完全相同。
 *   群聊页传自己的轻量顶栏：单聊那个 `TopBar` 有 394 行、含「点标题切模型」「长按改标题」
 *   「搜索预览」等一堆单聊语义，其中「切换模型」在群聊里根本没有定义（群配置里没有模型字段，
 *   每个角色的模型由 `GroupRole.modelId` 决定，且它当前不参与模型解析）。
 *   回调把本函数建的 [TopAppBarScrollBehavior] 传回去，需要折叠行为的调用方可以接。
 * @param canEditMessage 气泡「编辑」动作的门禁，默认恒 true（单聊行为与 C1 之前完全相同）。
 *   群聊传 `{ it.role == MessageRole.USER }`：改写**角色发言**会让群运行日志里的
 *   `committed_role_ids` / `last_user_message_id` 与实际消息错位——这与重新生成、删除、
 *   切分支是同一类风险（那三个在 `ChatList` 里已按 `groupChat` 关掉）。
 *   而改用户自己那条提问是合法且常用的操作：轮次由新的 user 消息重新派生，不会错位。
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
    listOverlay: @Composable BoxScope.() -> Unit = {},
    bottomBarAboveInput: @Composable () -> Unit = {},
    extraCompletionProviders: List<ChatCompletionProvider> = emptyList(),
    topBar: (@Composable (TopAppBarScrollBehavior) -> Unit)? = null,
    canEditMessage: (UIMessage) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    val workspaceRepository: WorkspaceRepository = rememberAppEntryPoint().workspaceRepository()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var previewSearchQuery by rememberSaveable { mutableStateOf("") }
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

    val completionProviders = remember(
        assistant.workspaceId,
        conversation.workspaceCwd,
        workspaceRepository,
        extraCompletionProviders,
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
        // extra 只能追加在**尾部**：ChatInput 合并多 provider 时只认第一个非空列表的
        // replacementRange（ChatInput.kt 的 lists.firstOrNull()）。把群聊的 @ 角色选择器
        // 排到 workspace 文件补全前面，会让它篡改文件补全的插入范围。
        workspace + extraCompletionProviders
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
                if (topBar != null) {
                    topBar(scrollBehavior)
                } else {
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
                }
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                // 输入框正上方的额外内容（群聊的成员头像组）。必须排在 ChatInput 之前：
                // ChatInput 自己 hazeSource 采样上方子树，画在它之后就采不到了。
                bottomBarAboveInput()
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
            // 助手背景（图片/流光）放在内容槽内的最底层：Miuix 下 backdrop 录制的
            // 就是这棵子树，画在槽外顶栏的毛玻璃采样不到它 —— 表现是「顶栏没有背景
            // 模糊」，只有消息列表被糊掉。
            Box(modifier = Modifier.fillMaxSize()) {
                AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
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
                    // 群聊通过 canEditMessage 关掉「改写角色发言」，单聊默认恒放行。
                    if (canEditMessage(message)) {
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
                // 浮层插槽排在 ChatList **之后**：同 Box 里 AssistantBackground 是
                // fillMaxSize、ChatList 也是 fillMaxSize，插在它们之前会被整块压住。
                listOverlay()
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
    val navController = LocalNavController.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix

    // 搜索（预览）展开进度：预测返回手势跟手收起
    val searchExpand = rememberSearchExpandState(
        expanded = previewMode,
        onCollapse = onCollapseSearch,
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }

    // 与侧边栏一致：展开后自动聚焦，delay(100) 等动画起手再抢焦点，
    // 直接 requestFocus() 会把入场动画顶掉。
    val searchFocus = remember { FocusRequester() }
    // MD3 侧：LargeFlexibleTopAppBar 内部是 TwoRowsTopAppBar，同一个 title lambda
    // 会被渲染两遍（收起态小标题行 + 展开态大标题行），两行按折叠进度交叉淡化，
    // 任何时刻只有一行可见。FocusRequester 只能挂一份（挂两份时 requestFocus()
    // 落在先注册的那份 = 收起态小标题行 = 看不见的那行），所以打开搜索时记下当时
    // 可见的是哪一行，只把 requester 挂到那一行，光标才不会落进隐藏副本。
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

    // 顶栏副标题只显示当前模型名（助手名 + 提供商名在顶栏里太挤，已去掉）。
    val topBarModel = settings.getCurrentChatModel()
    if (isMiuix) {
        // Miuix：照搬 KernelSU HomeMiuix 的顶栏 —— 原版 TopAppBar 默认
        // largeTitle = title，即真正的大标题栏，滚动时折叠成小标题。
        // 搜索态由 KedgeMiuixMorphingTitleBar 负责 morph：标题（大标题 + 副标题 +
        // 小标题）随同一进度淡出，输入框落在**大标题的位置**、同一字阶同一颜色。
        KedgeMiuixMorphingTitleBar(
            // 标题 morph 成搜索输入框，与 MD3 版行为一致（点标题/点搜索图标进入）。
            title = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
            expanded = previewMode,
            keyword = searchQuery,
            onKeywordChange = onSearchQueryChange,
            dragProgress = searchProgress.value,
            // 不传 dragging 的话 KedgeMiuixMorphingTitleBar 内部的 snapTo 被门控掉
            // （dragging 默认 false），手势拖动完全没有反馈，只剩 tween。
            dragging = searchExpand.dragging.value,
            onExitSearch = onCollapseSearch,
            placeholder = stringResource(R.string.history_page_search),
            subtitle = topBarModel?.displayName,
            // 不传的话 Miuix 侧没有可聚焦的节点，点开后要手动点输入框才弹键盘。
            focusRequester = searchFocus,
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
                MiuixIconButton(onClick = onNewChat) {
                    MiuixIcon(addComment, contentDescription = "New Message")
                }
            },
        )
        return
    }

    // 搜索态**不换顶栏组件**：输入框在同一个大标题栏的 title 槽里 morph 掉标题，
    // 返回箭头在同一个导航槽位里 morph 掉菜单图标。换成单行小标题栏虽然也能塞下
    // 输入框，但整条栏会从 152dp 缩到 64dp，正文被顶上去一截，聊天页观感上是「页面
    // 跳了一下」。
    //
    // 关于「同一个 title lambda 被渲染两遍」：LargeFlexibleTopAppBar 内部确实是
    // TwoRowsTopAppBar，把 title 同时当作收起态 smallTitle 与展开态 title 传下去，
    // 但两行是按折叠进度**交叉淡化**的（TopTitleAlphaEasing(0)=0 → 展开时只有大标题
    // 行可见，收起时只有小标题行可见），任何时刻只有一行能看见，所以原地 morph 成立。
    // 唯一要处理的是焦点：两行各有一份输入框，FocusRequester 只能挂一份，见
    // focusLargeTitleRow。
    // MD3Exp 下走 CustomColors.topBarColors（surfaceContainer，比页面底色 surface
    // 深一档，顶栏才有自己的颜色）；LargeFlexibleTopAppBar 的默认容器色是 surface，
    // 与页面底色同色，顶栏会整个「消失」。
    // Miuix 分支不读 colors，仍是现在的透明顶栏。
    KedgePageLargeTopBar(
        colors = CustomColors.topBarColors,
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
        title = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
        subtitle = topBarModel?.displayName,
        titleContent = {
            // 输入框与标题共用同一个字阶 token（displayTitle）：输入框是**替换**
            // 标题，字号字色必须严格相等。标题 28sp 而输入框另取一档（此前取
            // topBarTitle 22sp）就是「大小不一样」的来源。
            val titleStyle = KedgeTextStyles.displayTitle()
            // 同一份 lambda 会被两行各渲染一次，用行内标题字阶区分是哪一行：
            // 收起态小标题行是 titleLarge，展开态大标题行是 displaySmall
            // （material3 AppBar.kt 的 TwoRowsTopAppBar 传的就是这两个 token）。
            val isLargeTitleRow = LocalTextStyle.current.fontSize >
                MaterialTheme.typography.titleLarge.fontSize
            Box {
                if (titleVisible) {
                    val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
                    KedgeSurface(
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
                                style = titleStyle,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (model != null && provider != null) {
                                Text(
                                    text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    color = LocalContentColor.current.copy(0.65f),
                                    style = KedgeTextStyles.body(),
                                )
                            }
                        }
                    }
                }
                if (searchVisible) {
                    // 用 searchVisible 而不是 previewMode：previewMode 先变 false、
                    // 进度还在补间时若已卸载输入框，退场动画会被腰斩（标题直接闪回）。
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
                                style = titleStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = titleStyle.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                // 只挂到打开搜索那一刻可见的那一行（见 focusLargeTitleRow）
                                .then(
                                    if (isLargeTitleRow == focusLargeTitleRow) {
                                        Modifier.focusRequester(searchFocus)
                                    } else {
                                        Modifier
                                    }
                                ),
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
