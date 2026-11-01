package heizige.kk.khatkit.app.feature.chat

import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.effect.content.mochiBlurReveal
import androidx.compose.runtime.SideEffect
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState

import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import heizige.kk.khatkit.uikit.mochi.kkSolidBarColors
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import ui.feice.mochi.navigation.MochiSidebarLayout
import ui.feice.mochi.navigation.MochiSidebarState
import ui.feice.mochi.navigation.rememberMochiSidebarState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.scrollLinkedTopBarColors
import heizige.kk.khatkit.app.core.ui.components.ui.greetingMessage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.foundation.text.BasicTextField
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
import heizige.kk.khatkit.uikit.mochi.Toast
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
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold



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

    // SIDE1：侧边栏状态交给 Mochi（开合/遮罩/跟手拖拽/预测性返回都在 MochiSidebarLayout 里）
    val sidebarState = rememberMochiSidebarState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(sidebarState.isOpen) {
        if (sidebarState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    // C1 大屏适配：只要窗口宽度 >= 840dp 就走「列表 + 会话」永久双栏（原来要求横屏且 >=1100dp，
    // 平板竖屏/折叠屏展开态都被漏掉）。宽度不够时走 MochiSidebarLayout 的抽屉侧栏（<600 与 600-840 同款，
    // 导航轨在 shell 层，见 t162 说明）。
    val isBigScreen = windowAdaptiveInfo.width >= 840.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && sidebarState.isOpen) {
            sidebarState.close()
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
            sidebarState = sidebarState,
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
            // 大屏（永久侧栏）：Mochi 没有"常驻"样式，这里用一排定宽栏放同一份内容，
            // 侧栏开着时抽屉语义不需要（没有遮罩/手势），所以 state 传 null。
            Row(modifier = Modifier.fillMaxSize()) {
                // 常驻侧栏不经过 MochiSidebarLayout，系统栏 insets 在这里自己垫（抽屉内容不再垫）
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                    )
                }
                Box(modifier = Modifier.weight(1f)) { scaffold(true) }
            }
        }

        else -> {
            // SIDE1：外层换成 Mochi 的侧栏布局 —— 遮罩、左缘拖出、跟手收起、
            // 预测性返回先收侧栏、圆角与按压反馈全部由 Mochi 负责；
            // 页面里没有任何外观分支。
            MochiSidebarLayout(
                state = sidebarState,
                sidebar = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        sidebarState = sidebarState,
                    )
                },
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
    sidebarState: MochiSidebarState,
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
    // U7b-2d：附件选择面板宿主接收 sheetState（退出动画），打开/关闭仍由 showFilesSheet 驱动。
    val filesSheet = rememberMochiSheetState()
    // U7b-6：state 由这里持有，去 if 门控。
    val roleSheet = rememberMochiSheetState()
    LaunchedEffect(showRolePicker) { if (showRolePicker) roleSheet.show() else roleSheet.hide() }
    AssistantPickerSheet(
            sheetState = roleSheet,
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
    // Miuix 下由 MochiPageScaffold 建立**唯一**一个 Miuix 折叠行为，同时接上
    // nestedScroll 并通过 LocalKedgePageScrollBehavior 下发给顶栏（顶栏读同一个
    // 实例才能折叠大标题）。这里不要再自己 remember 一个，否则会出现多个实例、
    // 顶栏观察的那个没人喂滚动。
    // MD3 仍是页面自己接 scrollBehavior 的 nestedScroll —— 但要按风格取，
    // 详见 activeNestedScroll 的注释（多挂一个 MD3 连接会吃掉全部滚动增量）。
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeNestedScroll = activeNestedScroll(scrollBehavior)

    KkSurface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        // 用 MochiPageScaffold：Miuix 下自动建立 backdrop 并只包住内容槽，
        // 顶栏作为兄弟节点采样（KernelSU MainActivity.kt:306 同构）。
        // 助手背景要画在**内容槽里面**（见下面），不能留在 Scaffold 外面：
        // backdrop 只录制内容槽那棵子树，画在槽外就不在模糊源里，顶栏会糊不到它。
        // U3b：输入栏改悬浮层，不再占 scaffold bottomBar 的纵向位置。
        val inputBarOverlay: @Composable () -> Unit = {
            val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
            val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
            if (archived) {
                Text(
                    "历史群聊已归档",
                    style = MaterialTheme.typography.bodyMedium,
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
        
        }
        var inputBarHeightPx by remember { mutableIntStateOf(0) }
        val inputBarHeight = with(LocalDensity.current) { inputBarHeightPx.toDp() }
        Box(modifier = Modifier.fillMaxSize()) {
        MochiPageScaffold(
            modifier = activeNestedScroll,
            topBar = {
                    TopBar(
                        settings = setting,
                        // 顶栏角色与正文背景**同一个对象**：:315 已经按
                        // getAssistantById(conversation.assistantId) ?: getCurrentAssistant()
                        // 解析过，这里不再让顶栏自己解析一遍（两处各解析一次正是历史 bug 的温床）。
                        role = assistant,
                        // 和下面的欢迎层同一个条件：空会话顶栏显示问候语，发出第一条就换回角色名 + 模型名
                        welcome = !archived && !previewMode && conversation.messageNodes.isEmpty(),
                        hazeState = hazeState,
                        bigScreen = bigScreen,
                        sidebarState = sidebarState,
                        previewMode = previewMode,
                        searchQuery = previewSearchQuery,
                        onSearchQueryChange = { previewSearchQuery = it },
                        onCollapseSearch = {
                            previewMode = false
                            previewSearchQuery = ""
                        },
                        scrollBehavior = scrollBehavior,
                        listState = chatListState,
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onRoleClick = {
                            showRolePicker = true
                        },
                    )
            },
            containerColor = Color.Transparent,
            // 页面底色是透明的（正文铺助手背景图/渐变），但栏玻璃取样必须垫一层不透明页面底色，
            // 否则录出来的取样源是半透明的 → 顶栏两枚按钮在浅色下变成灰圆盘（Mochi 实例里是白胶囊）
            barBackdropColor = CustomColors.pageContainerColor,
            // 正文里铺着助手背景图，整槽渐隐会把背景一起擦掉：交界过渡由 ChatList 自己加在消息列表上
            edgeFade = false,
    ) { innerPadding ->
            // 助手背景（图片/流光）放在内容槽内的最底层：Miuix 下 backdrop 录制的
            // 就是这棵子树，画在槽外顶栏的毛玻璃采样不到它 —— 表现是「顶栏没有背景
            // 模糊」，只有消息列表被糊掉。
            Box(modifier = Modifier.fillMaxSize()) {
                // 角色由本函数 :315 解析好的 `assistant`（= getAssistantById(会话角色) ?: 当前角色）
                // 传入，与顶栏、ChatInput 全部同源。背景图/流光不再自己解析全局 currentAssistant。
                AssistantBackground(assistant = assistant, modifier = Modifier.hazeSource(hazeState))
                ChatList(
                // U3b：列表底部补输入栏高度，滚到底时最后一条完整可见。
                // U3b2：底部留白**恰好等于悬浮输入栏实测高度**（onSizeChanged 量出来的值），
                // 不再叠加 scaffold 的 innerPadding.bottom，否则会多出一份空隙；
                // 输入栏本身跟随 navigationBarsPadding，键盘弹出时它的高度变化会重新回灌到这里。
                innerPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = inputBarHeight,
                ),
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

            // 悬浮输入栏：作为兄弟节点画在列表之上（不占纵向布局）；
            // navigationBarsPadding 让它跟随导航栏/IME，高度回灌给列表 contentPadding。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .onSizeChanged { inputBarHeightPx = it.height },
            ) {
                inputBarOverlay()
            }
        }

        LaunchedEffect(showFilesSheet) { if (showFilesSheet) filesSheet.show() else filesSheet.hide() }
        ChatFilesPickerSheet(
            sheetState = filesSheet,
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

@Composable
private fun ChatFilesPickerSheet(
    sheetState: MochiSheetState,
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
        state = sheetState,
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
 * `MochiPageScaffold` 建立）时用 `Color.Transparent`，否则退回
 * `MochiTheme.colorScheme.surface`。**关掉毛玻璃时 backdrop 为 null**，顶栏就是一块实色，
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
    content()
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
    /** 空会话：标题换成时段问候语、副标题换成「今天想完成什么?」，有消息后模糊交叉淡化回角色名 + 模型名。 */
    welcome: Boolean,
    /** 与正文背景同一个 haze 源，供顶栏在角色有自定义背景时做毛玻璃。 */
    hazeState: HazeState,
    sidebarState: MochiSidebarState,
    bigScreen: Boolean,
    previewMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCollapseSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    /** 消息列表：顶栏底色 / 渐隐跟着它是否滚进栏下面（照 Mochi demo 的聊天页）。 */
    listState: LazyListState,
    onClickMenu: () -> Unit,
    onRoleClick: () -> Unit,
) {
    // 照 Mochi demo 的聊天页（SidebarPage 的 ChatScreen）：MochiTitleBar 盖在列表上，标题居中、
    // 副标题是模型名；左边侧边栏按钮，右边搜索 + 角色面板，都是栏按钮。内容停在顶部时栏没有底色，
    // 滚进栏下面才渐渐出来。栏高固定，不再折叠：把共享折叠行为的可折叠高度设成 0，否则它会吃掉全部滚动。
    SideEffect { scrollBehavior.state.heightOffsetLimit = 0f }

    // 搜索（预览）展开进度：预测返回手势跟手收起
    val searchExpand = rememberSearchExpandState(
        expanded = previewMode,
        onCollapse = onCollapseSearch,
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(previewMode) {
        if (previewMode) {
            delay(100)
            searchFocus.requestFocus()
        }
    }

    // 顶栏角色 = 调用方传入的那个角色（与正文背景同一个 Assistant 实例）。
    // 标题是角色名、副标题是模型名；会话标题不上顶栏，改名入口在角色聊天面板（ChatRolePanelPage）。
    val topBarRole = role
    val topBarModel = settings.findModelById(topBarRole.chatModelId ?: settings.chatModelId)
    val roleName = if (settings.init) "" else topBarRole.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
    val roleModelName = topBarModel?.displayName.orEmpty()

    // 角色有自定义底色（背景图 / 流光）时栏不画底色，把底色让给正文那张背景；开模糊时叠一层 haze。
    val roleHasCustomBackground = role.background != null
    val barModifier =
        if (roleHasCustomBackground && settings.displaySetting.enableBlurEffect) {
            Modifier.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle.Material3 { blurRadius(20.dp) },
            )
        } else {
            Modifier
        }
    val cs = MochiTheme.colorScheme
    // 栏按钮配色照 Mochi 实例（demo 的聊天页/目录页）：白胶囊（浅色）/ 深胶囊（深色）+ 深色图标。
    // 纯色而不是玻璃，见 uikit 的 kkSolidBarColors：本页的栏取样源在透明底色/背景图上取样出来的面色发灰。
    val solidBarColors = kkSolidBarColors()
    val edgeProgress = MochiScrollEdgeDefaults.progress(listState)

    Box(barModifier.fillMaxWidth()) {
        // 标题区的点击（切换角色）与搜索框，放在 MochiTitleBar **下面**画：
        // 栏里的按钮（搜索 / 角色面板）在上面先吃触摸，标题区没有 pointerInput，
        // 点标题才会落到这一层。以前这层画在栏上面，左右各让 64dp 仍压住搜索按钮左半边，
        // 点搜索像没反应 —— 现在把层级换过来，两边都能点。
        // 栏高 56、左右各让出 64 给按钮
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(MochiTitleBarDefaults.Height)
                .padding(horizontal = MochiButtonDefaults.BarHorizontalPadding + 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!searchVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!settings.init) onRoleClick() },
                        ),
                )
            } else {
                val navTitle = MochiTheme.textStyles.navTitle
                Box(
                    Modifier
                        .fillMaxWidth()
                        .mochiBlurReveal({ searchProgress.value }, rise = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isBlank()) {
                        Text(
                            stringResource(R.string.history_page_search),
                            style = navTitle,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = navTitle.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                }
            }
        }
        // MochiBlurText 换字时自己做模糊交叉淡化，这里只换字符串
        MochiTitleBar(
            title = if (welcome) greetingMessage() else roleName,
            subtitle = if (welcome) {
                stringResource(R.string.chat_welcome_question)
            } else {
                roleModelName.takeIf { it.isNotBlank() && !settings.init }
            },
            background = if (roleHasCustomBackground) Color.Transparent else CustomColors.pageContainerColor,
            // 搜索展开时标题模糊退场，让位给搜索框
            titleProgress = { 1f - searchProgress.value },
            backgroundProgress = edgeProgress,
            // 页面骨架（MochiPageScaffold）统一录正文、下发来源；材质没开玻璃时它是 null
            backdrop = LocalKkBarBackdrop.current,
            navigationIcon = { KkBarIconScope {
                // 侧边栏 ↔ 返回在同一个按钮里交叉淡化。大屏常驻侧边栏，只有搜索态才出返回键。
                val p = searchProgress.value
                if (!bigScreen || p > 0.001f) {
                    MochiBarIconButton(
                        onClick = { if (p >= 0.5f) onCollapseSearch() else sidebarState.open() },
                        colors = solidBarColors,
                        modifier = if (bigScreen) Modifier.graphicsLayer { alpha = searchProgress.value } else Modifier,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!bigScreen) {
                                Icon(
                                    imageVector = menu,
                                    contentDescription = "Messages",
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(22.dp).graphicsLayer {
                                        alpha = 1f - searchProgress.value
                                        translationX = searchProgress.value * 12.dp.toPx()
                                    },
                                )
                            }
                            Icon(
                                imageVector = arrowBack,
                                contentDescription = null,
                                tint = cs.onSurface,
                                modifier = Modifier.size(22.dp).graphicsLayer {
                                    alpha = searchProgress.value
                                    translationX = (1f - searchProgress.value) * -12.dp.toPx()
                                },
                            )
                        }
                    }
                } }
            },
            actions = {
                KkBarIconScope {
                // 和设置页顶栏同一套图标按钮（MochiIconButton + shapes），不再单独用栏里的圆钮
                MochiBarIconButton(
                    onClick = onClickMenu,
                    colors = solidBarColors,
                ) {
                    // 搜索 ↔ 清除：没在搜 → 放大镜；在搜且有字 → ×；在搜还没字 → 空
                    val searching = searchProgress.value >= 0.5f
                    MochiBlurCrossfade(
                        when {
                            !searching -> 0
                            searchQuery.isNotEmpty() -> 1
                            else -> 2
                        },
                    ) { icon ->
                        when (icon) {
                            0 -> Icon(search, "Chat Options")
                            1 -> Icon(close, "Chat Options")
                            else -> Box(Modifier.size(24.dp))
                        }
                    }
                }
                }
            },
        )
    }
}
