package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khromia.components.TextTooltip
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khromia.components.EditDialog
import heizige.kk.khromia.components.EditFieldConfig
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.Folder
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.hub.HubAccountInfo
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantPicker
import heizige.kk.khatkit.app.core.ui.components.ui.BackupReminderCard
import heizige.kk.khatkit.app.core.ui.components.ui.Tooltip
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.UpdateCard
import androidx.compose.ui.draw.clip
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.readBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsPlayStoreVersion
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.toDp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.createNewFolder
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.folder as folderIcon
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings as settingsIcon

// Miuix 侧边栏皮肤：只换外观层，抽屉手势/预测返回仍走 MD3 的 ModalDrawerSheet。
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults as MiuixTopBarDefaults
import androidx.compose.ui.graphics.Color

/** Miuix 抽屉圆角：KSU 观感的 28dp 外侧圆角，内侧贴屏边。 */
private val MiuixDrawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    vm: ChatViewModel,
    settings: Settings,
    current: Conversation,
    drawerState: DrawerState? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val isPlayStore = rememberIsPlayStoreVersion()
    val repo = rememberAppEntryPoint().conversationRepository()
    val hubAccountRepository = rememberAppEntryPoint().hubAccountRepository()
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix

    var hubAccount by remember { mutableStateOf<HubAccountInfo?>(null) }
    LaunchedEffect(Unit) {
        hubAccount = withContext(Dispatchers.IO) { hubAccountRepository.cachedAccount() }
    }
    val account = hubAccount
    val subscriptionLabel = when {
        account == null -> "套餐"
        !account.active -> "订阅"
        account.toolCallsRemaining != null -> "套餐 · 剩余 ${account.toolCallsRemaining} 次"
        account.aiTokensRemaining != null -> "套餐 · ${account.aiTokensRemaining} tokens"
        else -> "套餐"
    }

    val activity = context as ComponentActivity
    val drawerVm: ChatDrawerViewModel = hiltViewModel(viewModelStoreOwner = activity)

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val conversationListState = rememberLazyListState(
        initialFirstVisibleItemIndex = drawerVm.scrollIndex,
        initialFirstVisibleItemScrollOffset = drawerVm.scrollOffset,
    )

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            conversationListState.firstVisibleItemIndex to
                conversationListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                drawerVm.saveScrollPosition(index, offset)
            }
    }

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 顶栏搜索（复刻 ImageToolbox 侧边栏：TopAppBar 内联搜索框，按标题过滤会话）
    val searchKeyword by drawerVm.searchKeyword.collectAsStateWithLifecycle()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            delay(100)
            searchFocus.requestFocus()
        }
    }

    // 移动对话状态
    var showMoveToAssistantSheet by remember { mutableStateOf(false) }
    var conversationToMove by remember { mutableStateOf<Conversation?>(null) }

    // 文件夹相关状态
    var showMoveToFolderSheet by remember { mutableStateOf(false) }
    var conversationToMoveFolder by remember { mutableStateOf<Conversation?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    // Menu popup 状态
    val updateCheckDisabledUntil = settings.displaySetting.updateCheckDisabledUntilEpochMillis
    var updateChecksEnabled by remember(updateCheckDisabledUntil) {
        mutableStateOf(updateCheckDisabledUntil <= System.currentTimeMillis())
    }
    LaunchedEffect(updateCheckDisabledUntil) {
        while (true) {
            val remaining = updateCheckDisabledUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                updateChecksEnabled = true
                break
            }
            updateChecksEnabled = false
            delay(minOf(remaining, 60 * 60 * 1_000L))
        }
    }

    // 抽屉不垫状态栏/导航栏 insets：由 TopAppBar / BottomAppBar 自己消费，
    // 这样两条 bar 的背景能画到状态栏/导航栏后面，和系统栏颜色一致。
    val sheetContent: @Composable () -> Unit = {
        // 搜索展开进度：预测返回手势跟手收起。组合位置在 ModalDrawerSheet 内容里，
        // 晚于抽屉自身的预测返回处理器，因此搜索展开时返回手势优先收起搜索。
        val searchExpand = rememberSearchExpandState(
            expanded = showSearch,
            onCollapse = {
                drawerVm.updateSearchKeyword("")
                showSearch = false
            },
        )
        val searchProgress = searchExpand.progress
        val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
        Column(modifier = Modifier.fillMaxSize()) {
            if (isMiuix) {
                // Miuix 头部：普通标题（不是 large），标题随进度 morph 成搜索框。
                MiuixDrawerHeader(
                    expanded = showSearch,
                    dragProgress = searchProgress.value,
                    dragging = searchExpand.dragging.value,
                    keyword = searchKeyword,
                    onKeywordChange = drawerVm::updateSearchKeyword,
                    focusRequester = searchFocus,
                    onExitSearch = {
                        drawerVm.updateSearchKeyword("")
                        showSearch = false
                    },
                    onToggleSearch = {
                        if (!showSearch) {
                            showSearch = true
                        } else {
                            drawerVm.updateSearchKeyword("")
                        }
                    },
                )
            } else {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    // 导航槽宽度与搜索进度同一进度：0dp→48dp，返回箭头淡入/缩放，
                    // 避免图标出现/消失时标题区宽度突变量导致的输入框瞬移。
                    Box(
                        modifier = Modifier
                            .width(48.dp * searchProgress.value)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        KedgeIconButton(
                            onClick = {
                                drawerVm.updateSearchKeyword("")
                                showSearch = false
                            },
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
                title = {
                    Box {
                        // 标题常驻，仅由进度驱动透明度/位移；搜索框入场时不会先跳到标题位置。
                        Text(
                            text = "KhatKit",
                            modifier = Modifier.graphicsLayer {
                                val progress = searchProgress.value
                                alpha = 1f - progress
                                translationX = -progress * 24.dp.toPx()
                            },
                        )
                        if (showSearch || searchVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f + 0.35f * searchProgress.value)
                                    .graphicsLayer {
                                        val progress = searchProgress.value
                                        alpha = progress
                                        translationX = (1f - progress) * 24.dp.toPx()
                                    },
                            ) {
                                if (searchKeyword.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.chat_page_search_chats),
                                        style = androidx.compose.material3.LocalTextStyle.current,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                BasicTextField(
                                    value = searchKeyword,
                                    onValueChange = drawerVm::updateSearchKeyword,
                                    singleLine = true,
                                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocus),
                                )
                            }
                        }
                    }
                },
                actions = {
                    // 搜索↔清除图标同槽交叉淡化（对齐 ImageToolbox：fade+scale，不换占位）。
                    val hasKeyword = searchKeyword.isNotEmpty()
                    KedgeIconButton(
                        onClick = {
                            if (!showSearch) {
                                showSearch = true
                            } else {
                                drawerVm.updateSearchKeyword("")
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = search,
                                contentDescription = stringResource(R.string.chat_page_search_chats),
                                modifier = Modifier.graphicsLayer {
                                    val progress = searchProgress.value
                                    alpha = 1f - progress
                                    scaleX = 1f - 0.15f * progress
                                    scaleY = 1f - 0.15f * progress
                                },
                            )
                            Icon(
                                imageVector = close,
                                contentDescription = stringResource(R.string.chat_page_search_chats),
                                modifier = Modifier.graphicsLayer {
                                    val progress = searchProgress.value * if (hasKeyword) 1f else 0f
                                    alpha = progress
                                    scaleX = 0.85f + 0.15f * progress
                                    scaleY = 0.85f + 0.15f * progress
                                },
                            )
                        }
                    }
                },
            )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (updateChecksEnabled && !isPlayStore) {
                UpdateCard(vm)
            }

            BackupReminderCard(
                settings = settings,
                onClick = { navController.navigate(Screen.Backup) },
            )

            if (folders.isNotEmpty()) {
                FolderSection(
                    folders = folders,
                    onClickFolder = { folder ->
                        if (drawerState != null) {
                            scope.launch { drawerState.close() }
                        }
                        navController.navigate(Screen.FolderDetail(folder.id.toString()))
                    },
                    onRename = { folderToRename = it },
                    onDelete = { folderToDelete = it },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ConversationList(
                    conversations = conversations,
                    currentId = current.id,
                    conversationJobs = conversationJobs.keys,
                    listState = conversationListState,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    header = {
                        if (isMiuix) {
                            MiuixButton(
                                onClick = { showCreateFolderDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            ) {
                                MiuixIcon(
                                    createNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                MiuixText(stringResource(R.string.chat_page_create_folder))
                            }
                        } else {
                            KedgeButton(
                                onClick = { showCreateFolderDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .height(46.dp),
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    createNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.chat_page_create_folder))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        navigateToChatPage(navController, it.id)
                    },
                    onRegenerateTitle = {
                        vm.generateTitle(it, true)
                    },
                    onDelete = {
                        scope.launch {
                            vm.deleteConversation(it).join()
                            conversations.refresh()
                            if (it.id == current.id) {
                                navigateToChatPage(navController)
                            }
                        }
                    },
                    onPin = {
                        vm.updatePinnedStatus(it)
                    },
                    onMoveToAssistant = {
                        conversationToMove = it
                        showMoveToAssistantSheet = true
                    },
                    onMoveToFolder = {
                        conversationToMoveFolder = it
                        showMoveToFolderSheet = true
                    }
                )

                // 助手选择器（默认助手卡片：圆角胶囊 + surfaceContainerHigh 底，悬浮于会话列表之上）
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isMiuix) {
                        MiuixTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                ) {
                    AssistantPicker(
                        settings = settings,
                        onUpdateSettings = {
                            val updateJob = vm.updateSettings(it)
                            scope.launch {
                                updateJob.join()
                                val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                                    Uuid.random()
                                } else {
                                    repo.getConversationsOfAssistant(it.assistantId)
                                        .first()
                                        .firstOrNull()
                                        ?.id ?: Uuid.random()
                                }
                                navigateToChatPage(navigator = navController, chatId = id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClickSetting = {
                            val currentAssistantId = settings.assistantId
                            navController.navigate(Screen.AssistantDetail(id = currentAssistantId.toString()))
                        }
                    )
                }
            }

            }

            BottomAppBar(
                containerColor = if (isMiuix) {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    DrawerAction(
                        icon = {
                            Icon(settingsIcon, null)
                        },
                        label = stringResource(R.string.settings),
                        onClick = {
                            navController.navigate(Screen.Setting)
                        },
                    )

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                        tooltip = { TextTooltip("套餐") },
                        state = rememberTooltipState(),
                    ) {
                        KedgeButton(
                            onClick = {
                                navController.navigate(Screen.SettingPackage)
                            },
                            modifier = Modifier.height(32.dp),
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMiuix) {
                                    MiuixTheme.colorScheme.surfaceContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isMiuix) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = subscriptionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    DrawerAction(
                        icon = {
                            Icon(extension, null)
                        },
                        label = "探索市场",
                        onClick = {
                            navController.navigate(Screen.ExploreMarket)
                        },
                    )

                    DrawerAction(
                        icon = {
                            Icon(download, null)
                        },
                        label = "下载中心",
                        onClick = {
                            navController.navigate(Screen.DownloadCenter)
                        },
                    )
                }
            }
        }
    }

    // 有 drawerState 时用带预测返回的 ModalDrawerSheet（返回手势跟手关闭抽屉）
    if (drawerState != null) {
        ModalDrawerSheet(
            drawerState = drawerState,
            modifier = Modifier.width(300.dp),
            windowInsets = WindowInsets(0),
            drawerShape = if (isMiuix) MiuixDrawerShape else DrawerDefaults.shape,
            drawerContainerColor = if (isMiuix) {
                MiuixTheme.colorScheme.surface
            } else {
                DrawerDefaults.modalContainerColor
            },
            drawerContentColor = if (isMiuix) {
                MiuixTheme.colorScheme.onSurface
            } else {
                Color.Unspecified
            },
            drawerTonalElevation = if (isMiuix) 0.dp else DrawerDefaults.ModalDrawerElevation,
        ) {
            sheetContent()
        }
    } else {
        ModalDrawerSheet(
            modifier = Modifier.width(300.dp),
            windowInsets = WindowInsets(0),
            drawerShape = if (isMiuix) MiuixDrawerShape else DrawerDefaults.shape,
            drawerContainerColor = if (isMiuix) {
                MiuixTheme.colorScheme.surface
            } else {
                DrawerDefaults.modalContainerColor
            },
            drawerContentColor = if (isMiuix) {
                MiuixTheme.colorScheme.onSurface
            } else {
                Color.Unspecified
            },
            drawerTonalElevation = if (isMiuix) 0.dp else DrawerDefaults.ModalDrawerElevation,
        ) {
            sheetContent()
        }
    }

    // 移动到文件夹 Bottom Sheet
    if (showMoveToFolderSheet) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.chat_page_move_to_folder),
            imageVector = folderIcon,
            onDismiss = {
                showMoveToFolderSheet = false
                conversationToMoveFolder = null
            },
            scrollable = false,
        ) { dismiss ->
            val doMove: (Uuid?) -> Unit = { folderId ->
                conversationToMoveFolder?.let { conversation ->
                    drawerVm.moveConversationToFolder(conversation.id, folderId)
                    dismiss()
                    scope.launch {
                        conversations.refresh()
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 移出文件夹（未归类）
                Surface(
                    onClick = { doMove(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (conversationToMoveFolder?.folderId == null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(folderIcon, null)
                        Text(
                            text = stringResource(R.string.chat_page_remove_from_folder),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders) { folder ->
                        val isCurrent = folder.id == conversationToMoveFolder?.folderId
                        Surface(
                            onClick = { doMove(folder.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isCurrent) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(folderIcon, null)
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        val emptyNameError = stringResource(R.string.chat_page_folder_name_empty)
        EditDialog(
            visible = true,
            title = stringResource(R.string.chat_page_create_folder),
            fields = listOf(
                EditFieldConfig(
                    label = stringResource(R.string.chat_page_folder_name),
                    onValidate = { input ->
                        if (input.trim().isEmpty()) emptyNameError else null
                    },
                )
            ),
            confirmText = stringResource(R.string.chat_page_save),
            dismissText = stringResource(R.string.chat_page_cancel),
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { values ->
                drawerVm.createFolder(values.first())
                showCreateFolderDialog = false
            },
        )
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AppAlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                KedgeOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { folderToRename = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AppAlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        if (drawerVm.deleteFolder(folder.id)) {
                            folderToDelete = null
                            conversations.refresh()
                        } else {
                            Toast.show(context.getString(R.string.chat_page_delete_folder_generating), isError = false)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { folderToDelete = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到助手 Bottom Sheet
    if (showMoveToAssistantSheet) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.chat_page_move_to_assistant),
            imageVector = groups,
            onDismiss = {
                showMoveToAssistantSheet = false
                conversationToMove = null
            },
            scrollable = false,
        ) { dismiss ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(settings.assistants) { assistant ->
                        AssistantItem(
                            assistant = assistant,
                            isCurrentAssistant = assistant.id == conversationToMove?.assistantId,
                            onClick = {
                                conversationToMove?.let { conversation ->
                                    vm.moveConversationToAssistant(conversation, assistant.id)
                                    dismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Miuix 侧边栏头部。
 *
 * 与 MD3 版一致：标题常驻、只由进度驱动透明度/位移，搜索框入场时不会先跳到标题位置；
 * 导航槽宽度也跟同一进度（0→48dp），返回箭头淡入缩放，避免图标出现/消失时输入框瞬移。
 *
 * 高度用 Miuix `SmallTopAppBar` 的 50dp 中心高，标题字号用 `textStyles.title3`，
 * 即 Miuix 的**普通**标题而不是 large 标题。
 */
@Composable
private fun MiuixDrawerHeader(
    expanded: Boolean,
    dragProgress: Float,
    dragging: Boolean,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onExitSearch: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val hasKeyword = keyword.isNotEmpty()
    // 单一进度源：自己持有 Animatable。
    // - expanded 变化 -> 补间到目标（点搜索/退出、以及预测返回提交后的收起）
    // - 手势进行中 -> 直接 snap 到跟手值
    // 之前用「dragging 时用外部值、否则用 animateFloatAsState」切两个来源，
    // 释放瞬间两个来源不同步，收起动画会重播一遍。
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, tween(220))
    }
    LaunchedEffect(dragProgress, dragging) {
        if (dragging) progress.snapTo(dragProgress.coerceIn(0f, 1f))
    }
    val searchProgress = progress.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiuixTopBarDefaults.SmallTopAppBarCenterHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 导航槽：宽度与搜索进度同步，返回箭头淡入/缩放。
                Box(
                    modifier = Modifier
                        // 原版 SmallTopAppBarLayout 的度量：导航槽 start padding 16dp，
                        // 槽宽随搜索进度 0→48dp；标题再自带 26dp，合计起点 42dp（与原版一致）。
                        .width(40.dp * searchProgress)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIconButton(
                        onClick = onExitSearch,
                        modifier = Modifier.graphicsLayer {
                            alpha = searchProgress
                            scaleX = 0.85f + 0.15f * searchProgress
                            scaleY = 0.85f + 0.15f * searchProgress
                        },
                    ) {
                        MiuixIcon(arrowBack, contentDescription = null)
                    }
                }

                // 标题区：标题与搜索框同槽交叉淡化。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 关闭态 26dp（原版度量，标题左右对称）；搜索态收到 8dp，
                        // 让输入框紧贴返回箭头，不再留出大片空白。
                        .padding(start = MiuixTopBarDefaults.TitlePadding - 18.dp * searchProgress),
                ) {
                    MiuixText(
                        text = "KhatKit",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = MiuixTheme.textStyles.title3.fontSize,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = 1f - searchProgress },
                    )
                    // 仅在真正进入搜索态时才组合：BasicTextField 首次组合会自动
                    // 获取焦点，若常驻组合，打开抽屉就会自动弹出输入法。
                    if (searchProgress > 0.001f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = searchProgress },
                    ) {
                        if (keyword.isBlank()) {
                            MiuixText(
                                text = stringResource(R.string.chat_page_search_chats),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = MiuixTheme.textStyles.title3.fontSize,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            singleLine = true,
                            textStyle = MiuixTheme.textStyles.title3.copy(
                                color = MiuixTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                }

                // 搜索↔清除同槽交叉淡化（不换占位）。
                // end 用 Miuix 原版 ActionIconPadding(16dp)：实测标题左边距 26dp 时
                // 图标右边距原本只有 9.3dp，补 16dp 后左右对称。
                MiuixIconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier.padding(end = MiuixTopBarDefaults.ActionIconPadding),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MiuixIcon(
                            imageVector = search,
                            contentDescription = stringResource(R.string.chat_page_search_chats),
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - searchProgress
                                scaleX = 1f - 0.15f * searchProgress
                                scaleY = 1f - 0.15f * searchProgress
                            },
                        )
                        MiuixIcon(
                            imageVector = close,
                            contentDescription = stringResource(R.string.chat_page_search_chats),
                            modifier = Modifier.graphicsLayer {
                                val p = searchProgress * if (hasKeyword) 1f else 0f
                                alpha = p
                                scaleX = 0.85f + 0.15f * p
                                scaleY = 0.85f + 0.15f * p
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        // 抽屉里仍复用 MD3 的 Icon 槽，这里下发 Miuix 前景色，避免图标颜色错位。
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                MiuixTheme.colorScheme.onSurface,
        ) {
            MiuixIconButton(onClick = onClick, modifier = modifier) { icon() }
        }
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { TextTooltip(label) },
            state = rememberTooltipState(),
        ) {
            KedgeIconButton(
                onClick = onClick,
                modifier = modifier,
                shapes = IconButtonDefaults.shapes(),
            ) {
                icon()
            }
        }
    }
}

private val FolderRowShape = RoundedCornerShape(16.dp)

@Composable
private fun FolderSection(
    folders: List<Folder>,
    onClickFolder: (Folder) -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
) {
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix
    val rowShape = if (isMiuix) RoundedCornerShape(20.dp) else FolderRowShape
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (folders.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 156.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(folders, key = { it.id }) { folder ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.animateItem()) {
                        Surface(
                            shape = rowShape,
                            color = if (isMiuix) {
                                MiuixTheme.colorScheme.surfaceContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .combinedClickable(
                                    onClick = { onClickFolder(folder) },
                                    onLongClick = { menuExpanded = true },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    folderIcon,
                                    contentDescription = null,
                                    tint = if (isMiuix) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    chevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        KedgeDropdownMenuSlots(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            KedgeDropdownItemSlot(
                                text = { Text(stringResource(R.string.chat_page_rename)) },
                                leadingIcon = { Icon(edit, null) },
                                onClick = {
                                    onRename(folder)
                                    menuExpanded = false
                                }
                            )
                            KedgeDropdownItemSlot(
                                text = { Text(stringResource(R.string.chat_page_delete)) },
                                leadingIcon = { Icon(delete, null) },
                                onClick = {
                                    onDelete(folder)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    isCurrentAssistant: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentAssistant) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrentAssistant) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                onUpdate = {},
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentAssistant) {
                    Text(
                        text = stringResource(R.string.assistant_page_current_assistant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
