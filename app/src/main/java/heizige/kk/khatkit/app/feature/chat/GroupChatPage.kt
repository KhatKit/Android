package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupConfigError
import heizige.kk.khatkit.app.core.data.model.GroupImportResult
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.QRCode
import heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheet
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.tune
import heizige.kk.khatkit.app.core.util.base64Decode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * 会话路由：按 `type` 分流到群聊页或单聊页。
 *
 * `text` / `files` / `nodeId` / `messageId` 必须**原样透传**给两个分支：
 * - 分享进来的文本与附件要进输入框（`text` 是 base64）；
 * - `nodeId` 是从收藏/历史跳过来时要滚动定位的节点；
 * - `messageId` 是要聚焦的消息。
 *
 * C1 之前这里给 `GroupChatPage(id)` 只传了 `id`，群聊里这四项全被丢弃。
 */
@Composable
fun GroupOrDirectPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid?,
    messageId: Uuid?,
) {
    val repo = rememberAppEntryPoint().conversationRepository()
    var type by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        type = repo.getConversationById(id)?.type ?: GroupChat.TYPE_DIRECT
    }
    when (type) {
        null -> Unit
        GroupChat.TYPE_GROUP -> GroupChatPage(
            id = id,
            text = text,
            files = files,
            nodeId = nodeId,
            messageId = messageId,
        )

        else -> ChatPage(id = id, text = text, files = files, nodeId = nodeId, messageId = messageId)
    }
}

/**
 * 把群成员摊成角色卡元数据（[RoleCardMeta]），随 [GroupChat.encodeQr] 一起写进分享载荷。
 *
 * **实际取到什么**（逐字段）：
 * - `role_id` / `assistant_id` / `card_id`：直接来自群配置的 `GroupRole`，不加工；
 * - `name`：优先群配置里的 `role.name`，为空时退回它指向的助手的 `name`；
 * - `persona`：取 `role.assistantId` 指向的助手的 `systemPrompt`。解析方式与 `ChatManager`
 *   发言时挑角色助手完全一致（`Settings.getAssistantById(Uuid.parse(role.assistantId))`），
 *   所以二维码里的 persona 就是该角色真正会用的那段系统提示词；
 * - `avatar_ref`：留 null——助手头像存的是 `Avatar`（本地文件 URI 或远程 URL），
 *   不是可移植引用，写进载荷会让对方拿到一个指不到东西的路径。
 *
 * 取不到真实角色卡时（`assistantId` 不是合法 Uuid、助手已被删除）**照样生成二维码**，
 * 退化成 `role.name` + 空 `persona`，不因为缺角色卡就把整个二维码入口禁掉。
 *
 * 行为与位置都保持 C1 之前的原样（重写群聊页时它被搬到了本文件末尾，逻辑一个字没改）。
 * ⚠️ 已知重复：另一个子包在 `ConversationExport.kt` 里**另外实现了一份**同样的
 * `RoleCardMeta` 构造（因为它不能碰本文件）。两份**故意不合并**——跨文件统一要动
 * `ConversationExport.kt`（禁区），且收益只是去掉十来行重复，不值得在这个包里引入冲突。
 */
private fun roleCards(roles: List<GroupRole>, settings: Settings): List<RoleCardMeta> =
    roles.map { role ->
        val assistant = runCatching { Uuid.parse(role.assistantId) }.getOrNull()
            ?.let { settings.getAssistantById(it) }
        RoleCardMeta(
            roleId = role.id,
            name = role.name.ifBlank { assistant?.name.orEmpty() },
            assistantId = role.assistantId,
            cardId = role.cardId,
            persona = assistant?.systemPrompt.orEmpty(),
        )
    }

/**
 * 群聊页：**复用单聊页的消息管线**（[ChatScaffold]），只把单聊语义换掉。
 *
 * C1 之前这里自己手写了一套 `LazyColumn` + `Text` 气泡 + `KedgeOutlinedTextFieldWithSlots` 输入框，
 * 于是单聊侧已经调通的行为——抽屉、大屏分栏、语音模式、停止生成、毛玻璃、键盘跟随、
 * 说话者与角色头像、@ 选择器插槽——群聊一个都没接上，两套页面各自漂移。现在：
 *
 * - 骨架 = [ChatScaffold]，抽屉 / 大屏分栏 / 输入框 / 语音 / 停止生成全部复用；
 * - 顶栏 = 自己的轻量版 [GroupTopBar]（单聊那个 `TopBar` 有 394 行，含「切换模型」这种
 *   群聊没有定义的交互）；
 * - 输入框上方 = [GroupMemberBar]（成员头像组，点一下在**光标处**插 `@角色名 `）；
 * - @ 选择器 = [GroupRoleCompletionProvider]，走 [ChatScaffold] 的 `extraCompletionProviders`；
 * - 群配置 / 导入 / 导出全部收进 [GroupConfigSheet]。
 *
 * ## 视角隔离不需要 UI 过滤
 *
 * 群聊的视角隔离只发生在**生成侧**（`ChatManager` 把 `GroupPerspectiveTransformer` 塞进
 * `inputTransformers`），落库的消息是全量追加的。所以 [ChatList] 遍历
 * `conversation.messageNodes` 天然就能看到全部角色的消息，本页不做任何过滤。
 *
 * ## 抽屉里的 `vm`
 *
 * [ChatDrawerContent] 的 `vm` 参数类型锁定 [ChatViewModel]，没有替代品；好消息是它只用到
 * `.id`（当前会话高亮），所以群聊可以原样复用。
 *
 * ## 已知的两个残留（UI 层改不动，只能记录）
 *
 * - `onEdit` 本页只放行**用户自己那条**（见下面的 `onEdit` 注释）。角色发言的编辑会改写
 *   节点内容，与重新生成/删除同类的「账面与实际轮次错位」风险。
 * - [androidx.compose.material3.TopAppBarScrollBehavior] 无关的
 *   `ChatMessageBranchSelector`（分支切换）在群聊下**仍然可用**且本页无法关闭：它由
 *   `ChatMessage` 内部弹出，而 `ChatMessage.kt` / `ChatList.kt` / `ChatMessageActions.kt`
 *   都不在本包可改范围内。切分支同样会换掉某个角色那条消息的内容，风险与上面一条同级。
 */
@Composable
fun GroupChatPage(
    id: Uuid,
    text: String? = null,
    files: List<Uri> = emptyList(),
    nodeId: Uuid? = null,
    messageId: Uuid? = null,
) {
    val vm: ChatViewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
        creationCallback = { it.create(id.toString()) }
    )
    val filesManager = rememberAppEntryPoint().filesManager()
    val navController = LocalNavController.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 抽屉打开时收起键盘，否则弹窗动画会把键盘顶回来。
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，避免从横屏旋转回竖屏后模态抽屉
    // 残留为打开且无法关闭（与 ChatPage 同一个 #1304）。
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)
    val inputState = vm.inputState
    val config = conversation.groupConfig

    // 分享进来的附件 / 文本要进输入框。与 ChatPage 同一段逻辑：附件复制与 MIME 查询
    // 是磁盘 IO，不能在主线程做。C1 之前群聊页压根没有这段，text/files 全被丢弃。
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
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

    // @ 角色选择器。provider 持有 config 的读取器而不是快照，配置换新时由上面的
    // remember(config) 一起换掉；extraCompletionProviders 只在末尾追加（见 ChatScaffold KDoc）。
    val roleCompletionProvider = remember(config) { GroupRoleCompletionProvider { config } }

    var showConfigSheet by rememberSaveable { mutableStateOf(false) }

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
            topBar = { scrollBehavior ->
                GroupTopBar(
                    title = conversation.title.ifBlank { "群聊" },
                    subtitle = config?.let { "${it.mode} · ${it.roles.size} 个角色" },
                    scrollBehavior = scrollBehavior,
                    onBack = { navController.popBackStack() },
                    onOpenConfig = { showConfigSheet = true },
                )
            },
            listOverlay = {
                GroupInfoChip(
                    config = config,
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { showConfigSheet = true },
                )
            },
            bottomBarAboveInput = {
                GroupMemberBar(config = config, settings = setting, inputState = inputState)
            },
            extraCompletionProviders = listOf(roleCompletionProvider),
            // 群聊只放行「编辑用户自己那条提问」：改写角色发言会让群运行日志的
            // committed_role_ids / last_user_message_id 与实际消息错位，与重新生成/删除/
            // 切分支同级（那三个已由 ChatList 按 groupChat 关掉）。改用户提问是合法且常用的，
            // 轮次由新的 user 消息重新派生，不会错位。
            canEditMessage = { it.role == MessageRole.USER },
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
                        settings = setting,
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

    if (showConfigSheet) {
        GroupConfigSheet(
            conversationId = id,
            config = config,
            settings = setting,
            onSave = { newConfig ->
                // 落库前必须过一遍 validate，失败绝不入库。与 importShare 同一份判定口径。
                val errors = GroupChat.validate(newConfig, id.toString())
                if (errors.isNotEmpty()) {
                    errors
                } else {
                    vm.updateConversation(
                        conversation.copy(
                            type = GroupChat.TYPE_GROUP,
                            groupConfig = newConfig,
                            title = conversation.title.ifBlank { "群聊" },
                        )
                    )
                    vm.saveConversationAsync()
                    null
                }
            },
            onDismiss = { showConfigSheet = false },
        )
    }
}

/**
 * 群聊轻量顶栏：返回 + 群标题 + 打开群配置面板的入口。
 *
 * **不复用** `ChatPage.kt` 里的 `TopBar`（394 行）：那个顶栏含「点标题切模型」
 * （`onModelClick` → `ModelListSheet`）与「长按改标题」等交互，「切换模型」在群聊里
 * 没有定义——群配置的 9 个字段里没有模型字段，每个角色用哪个模型由 `GroupRole.modelId`
 * 决定，而它当前**不参与模型解析**。挂一个点了没定义的入口比不挂更糟。
 *
 * 视觉走仓里现成的 [KedgePageLargeTopBar]：它内部已经按 `LocalKedgeStyle` 分流
 * Miuix / MD3，这里不另造一套。
 */
@Composable
private fun GroupTopBar(
    title: String,
    subtitle: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
) {
    KedgePageLargeTopBar(
        title = title,
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            KedgeIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(arrowBack, "返回")
            }
        },
        actions = {
            KedgeIconButton(
                onClick = onOpenConfig,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(tune, "群配置")
            }
        },
    )
}

/**
 * 消息列表右上角的群概况小条（`listOverlay` 插槽）。
 *
 * C1 之前群聊页在顶部用一行文字显示「模式 · 预算」，这里保留同一份信息，并让它成为
 * 群配置面板的第二个入口（顶栏收起后仍然点得到）。挂在 `TopEnd` 而不是 `BottomEnd`：
 * 内容槽在布局上位于 `bottomBar`（输入框）**上方**，挂 `BottomEnd` 会紧贴输入框上沿、
 * 抢成员头像组的位置。
 */
@Composable
private fun GroupInfoChip(
    config: GroupConfig?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (config == null) {
        "未配置群成员"
    } else {
        "${config.mode} · 预算 ${config.tokenBudgetPerRound} · ${config.roles.size} 个角色"
    }
    KedgeFilterChip(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        label = { Text(text) },
    )
}

/**
 * 群配置面板：配置编辑 + 导出（二维码 / 文本分享）+ 导入（扫码 / 粘贴）。
 *
 * C1 之前这些能力全都直接摊在群聊页正文里，本包把它们整体收进这个面板，**一条都没丢**：
 * 二维码生成、扫码导入、文本粘贴导入、`importShare` 的字段级错误逐条显示、
 * `ACTION_SEND` 文本分享、`GroupConfig` 九个字段的编辑。
 *
 * ## 导入绝不在校验失败时落库
 *
 * 扫码与粘贴共用 [applyImport] 这一个入口，两条路径的校验口径必须完全一致
 * （`GroupChat.decodeQr` 既不校验 `schema_version` 也不跑密钥黑名单、还会丢掉 cards，
 * 绝不能用来导入）。只有 [GroupImportResult.Accepted] 才写库；[GroupImportResult.Rejected]
 * 一律只显示 `reason` 与逐条 `fieldErrors`（`field` 已经是契约 snake_case 键，直接展示）。
 *
 * ## 遗留：导入的 cards 暂未落库
 *
 * [GroupChat.importShare] 成功时返回的 `payload.cards`（角色卡元数据）**只显示、不持久化**。
 * 原因：`Conversation` 只有 `groupConfig: GroupConfig?` 一个群相关字段，数据库侧也只有
 * `conversationentity.group_config` 一列，`GroupConfigSerializer` 的契约里没有 cards 的位置。
 * 要落库就得动 `Conversation` / `ConversationEntity` / `ConversationDAO` 与迁移脚本，
 * 超出本包范围。因此这里把 cards 列出来让用户至少能确认「导入了什么」，
 * 群配置的落库内容仍然是纯 `GroupConfig`。
 */
@Composable
private fun GroupConfigSheet(
    conversationId: Uuid,
    config: GroupConfig?,
    settings: Settings,
    onSave: (GroupConfig) -> List<GroupConfigError>?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config ?: GroupConfig()) }
    var importText by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var saveErrors by remember { mutableStateOf<List<GroupConfigError>>(emptyList()) }
    var importResult by remember { mutableStateOf<GroupImportResult?>(null) }

    // 分享载荷只算一次：二维码与 ACTION_SEND 文本分享共用同一份，不各算各的。
    val sharePayload = remember(draft, settings) {
        // encodeQr 命中密钥黑名单会抛 IllegalStateException（check），这里兜住不让面板崩。
        runCatching { GroupChat.encodeQr(draft, roleCards(draft.roles, settings)) }.getOrNull()
    }

    // 扫码与粘贴共用这一个入口：两条路径的校验口径必须完全一致。
    val applyImport: (String) -> Unit = { raw ->
        when (val result = GroupChat.importShare(raw, conversationId.toString())) {
            is GroupImportResult.Accepted -> {
                importResult = result
                draft = result.payload.config
                // Accepted 才落库；Rejected 一律不落库（见 KDoc）。
                onSave(result.payload.config)
            }

            is GroupImportResult.Rejected -> {
                importResult = result
                saveErrors = emptyList()
            }
        }
    }

    if (showScanner) {
        // QrScannerSheet 自己用仓里的权限范式申请相机权限；权限被拒或设备无相机时回调
        // onResult(null)，所以本页不重复申请权限，只把 null 落回「手动粘贴」这条入口。
        QrScannerSheet(
            onResult = { scanned ->
                showScanner = false
                if (scanned != null) applyImport(scanned)
            },
            onDismiss = { showScanner = false },
        )
    }

    PrimaryBottomSheet(
        visible = true,
        title = "群配置",
        onDismiss = onDismiss,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------- 配置编辑：GroupConfig 的九个字段 ----------
            Text("协作模式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    GroupChat.MODE_PIPELINE,
                    GroupChat.MODE_ROUNDTABLE,
                    GroupChat.MODE_VOTE,
                ).forEach { mode ->
                    KedgeFilterChip(
                        selected = draft.mode == mode,
                        onClick = { draft = draft.copy(mode = mode) },
                        label = { Text(mode) },
                    )
                }
            }

            Text("议长（chair_role_id）", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                KedgeFilterChip(
                    selected = draft.chairRoleId == null,
                    onClick = { draft = draft.copy(chairRoleId = null) },
                    label = { Text("未指定") },
                )
                draft.roles.forEach { role ->
                    KedgeFilterChip(
                        selected = draft.chairRoleId == role.id,
                        onClick = { draft = draft.copy(chairRoleId = role.id) },
                        label = { Text(role.name.ifBlank { role.id }) },
                    )
                }
            }

            NumberField(
                label = "每轮预算（token_budget_per_round）",
                value = draft.tokenBudgetPerRound,
                onValueChange = { draft = draft.copy(tokenBudgetPerRound = it) },
            )
            NumberField(
                label = "revision",
                value = draft.revision,
                onValueChange = { draft = draft.copy(revision = it) },
            )
            NumberField(
                label = "schema_version",
                value = draft.schemaVersion,
                onValueChange = { draft = draft.copy(schemaVersion = it) },
            )

            Text("平票策略（tie_policy）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(GroupChat.TIE_FAIL, GroupChat.TIE_CHAIR).forEach { policy ->
                    KedgeFilterChip(
                        selected = draft.tiePolicy == policy,
                        onClick = { draft = draft.copy(tiePolicy = policy) },
                        label = { Text(policy) },
                    )
                }
            }

            TextField(
                label = "投票候选（vote_candidates，逗号分隔）",
                value = draft.voteCandidates.joinToString(","),
                onValueChange = { text ->
                    draft = draft.copy(
                        voteCandidates = text.split(',', '，')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    )
                },
            )

            TextField(
                label = "未知字段（extras，原始 JSON）",
                value = draft.extras.toString(),
                onValueChange = { text ->
                    // 解析失败就**保留上一份能解析的值**，而不是塞一个空对象进去：
                    // extras 是「别人文档里多写的字段」，静默清空等于丢数据。
                    val parsed = runCatching {
                        Json.parseToJsonElement(text) as? JsonObject
                    }.getOrNull()
                    if (parsed != null) draft = draft.copy(extras = parsed)
                },
            )

            Text("成员（roles）", style = MaterialTheme.typography.labelLarge)
            draft.roles.forEachIndexed { index, role ->
                RoleEditor(
                    role = role,
                    index = index,
                    onChange = { updated ->
                        draft = draft.copy(
                            roles = draft.roles.toMutableList().also { it[index] = updated }
                        )
                    },
                    onRemove = {
                        draft = draft.copy(roles = draft.roles.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            KedgeTextButton(
                onClick = {
                    val newId = "role-${draft.roles.size + 1}"
                    draft = draft.copy(
                        roles = draft.roles + GroupRole(
                            id = newId,
                            name = "新角色",
                            assistantId = "",
                        )
                    )
                },
            ) { Text("添加成员") }

            // ---------- 保存（校验不过不落库） ----------
            KedgeTextButton(
                onClick = { saveErrors = onSave(draft) ?: emptyList() },
            ) { Text("保存群配置") }
            saveErrors.forEach { error ->
                Text(
                    text = "· ${error.field}：${error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---------- 导出：二维码 + ACTION_SEND 文本分享（共用同一份载荷） ----------
            Text("导出", style = MaterialTheme.typography.labelLarge)
            KedgeTextButton(
                onClick = {
                    val raw = sharePayload ?: return@KedgeTextButton
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, raw)
                    }
                    context.startActivity(Intent.createChooser(send, "分享群配置"))
                },
                enabled = sharePayload != null,
            ) { Text("分享") }
            KedgeTextButton(
                onClick = { showQr = !showQr },
                enabled = sharePayload != null,
            ) { Text(if (showQr) "收起二维码" else "生成二维码") }

            if (showQr) {
                val payload = sharePayload
                if (payload == null) {
                    Text(
                        "当前群配置无法编码为分享载荷",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        QRCode(
                            value = payload,
                            modifier = Modifier.size(220.dp),
                        )
                        Text(
                            text = "载荷 ${payload.length} 字节 · ${draft.roles.size} 个角色",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // ---------- 导入：扫码 + 文本粘贴 ----------
            Text("导入", style = MaterialTheme.typography.labelLarge)
            TextField(
                label = "导入群配置 JSON",
                value = importText,
                onValueChange = { importText = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KedgeTextButton(onClick = { applyImport(importText) }) { Text("恢复群配置") }
                KedgeTextButton(onClick = { showScanner = true }) { Text("扫码导入") }
            }
            ImportResultView(importResult)
        }
    }
}

/** 单个角色的字段编辑。角色层 6 个可写字段（`id` 只读，`assistantId` 必填由 validate 兜）。 */
@Composable
private fun RoleEditor(
    role: GroupRole,
    index: Int,
    onChange: (GroupRole) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "role_id = ${role.id}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextField(
            label = "name",
            value = role.name,
            onValueChange = { onChange(role.copy(name = it)) },
        )
        TextField(
            label = "assistant_id",
            value = role.assistantId,
            onValueChange = { onChange(role.copy(assistantId = it.trim())) },
        )
        TextField(
            label = "model_id（当前不参与模型解析）",
            value = role.modelId.orEmpty(),
            onValueChange = { onChange(role.copy(modelId = it.trim().ifBlank { null })) },
        )
        TextField(
            label = "card_id",
            value = role.cardId.orEmpty(),
            onValueChange = { onChange(role.copy(cardId = it.trim().ifBlank { null })) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KedgeFilterChip(
                selected = role.chair,
                onClick = { onChange(role.copy(chair = !role.chair)) },
                label = { Text("议长") },
            )
            KedgeTextButton(onClick = onRemove) { Text("移除 #$index") }
        }
    }
}

@Composable
private fun TextField(label: String, value: String, onValueChange: (String) -> Unit) {
    KedgeOutlinedTextFieldWithSlots(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 数字字段。输入非法就**保留上一份能解析的值**，避免半截数字被写进配置。 */
@Composable
private fun NumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    KedgeOutlinedTextFieldWithSlots(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.trim().toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 导入结果。`Rejected` 的 `fieldErrors` 逐条显示：`field` 已是契约 snake_case 键，
 * 直接展示，不做键名映射。
 */
@Composable
private fun ImportResultView(result: GroupImportResult?) {
    when (result) {
        null -> Unit

        is GroupImportResult.Rejected -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "导入被拒：${result.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            result.fieldErrors.forEach { error ->
                Text(
                    text = "· ${error.field}：${error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is GroupImportResult.Accepted -> {
            val payload = result.payload
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "已导入 ${payload.config.roles.size} 个角色 · ${payload.cards.size} 张角色卡",
                    style = MaterialTheme.typography.bodySmall,
                )
                // cards 目前没有落库位置（见 GroupConfigSheet 的 KDoc），至少让用户看见导入了什么。
                payload.cards.forEach { card ->
                    Text(
                        text = "· ${card.name.ifBlank { card.roleId }}（${card.roleId}）" +
                            if (card.persona.isBlank()) "" else " · 含 persona",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
