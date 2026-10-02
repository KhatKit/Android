package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.download
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import heizige.kk.kedge.overlays.KedgeEditDialog
import heizige.kk.khromia.components.EditFieldConfig
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.adaptive.KedgeOverlayScaffold
import heizige.kk.kedge.adaptive.KedgeTopAppBar
import heizige.kk.kedge.adaptive.KedgeOverlayBarColor
import heizige.kk.kedge.adaptive.KedgeBlurredBar
import heizige.kk.kedge.adaptive.rememberKedgeBlurBackdrop
import heizige.kk.kedge.adaptive.LocalKedgeEnableBlur
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeButtonVariant
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeTextField
import heizige.kk.kedge.components.KedgeTextTooltipBox
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import heizige.kk.kedge.components.KedgeSurface

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
        // 顶栏/底栏叠在内容之上，毛玻璃才有东西可透（见 KedgeOverlayScaffold）。
        // 毛玻璃只有 Miuix 风格有；MD3Exp 下把 backdrop 置空，各层就退化成
        // 不透明底色，不会出现 MD3 主题上的半透明糊层。
        val blurEnabled = LocalKedgeEnableBlur.current &&
            LocalKedgeStyle.current == KedgeStyle.Miuix
        val backdrop = rememberKedgeBlurBackdrop(blurEnabled)

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
        val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // 这个 Compose 版本里 WindowInsets.navigationBars 解析不到，用 systemBars
        // 的底部值减去状态栏顶部值得到导航栏高度（竖屏下等价）。
        val navBarBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        KedgeOverlayScaffold(
            backdrop = backdrop,
            // 不用 contentInset 内缩：那样列表与顶栏之间会留一道死间隙，滚不上去。
            // 改成内容用 Spacer / padding 顶开栏高，列表照样能一路滚到栏下面。
            contentInsetTop = 0.dp,
            contentInsetBottom = 0.dp,
            topBar = {
                // 走 KedgeTopAppBar 而不是裸 MD3 TopAppBar：Miuix 下它落到
                // SmallTopAppBar / 自绘同度量的标题栏，标题拿到 Miuix 的
                // LocalTextStyle（bodyLarge 16sp）。此前这里硬用 MD3 TopAppBar，
                // 标题槽给的是 titleLarge 22sp，Miuix 下字号偏大且字阶不对。
                // 标题与搜索输入框都取 LocalTextStyle.current，换过去后两者
                // 自动保持同字号，不用各自再写一遍。
                // Miuix 分支自己包 KedgeBlurredBar，所以这里不再外套一层。
                KedgeTopAppBar(
                    title = "",
                    titleContent = {
                        Box {
                            if (titleVisible) {
                                Text(
                                    text = "KhatKit",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = 1f - searchProgress.value
                                        translationX = -searchProgress.value * 24.dp.toPx()
                                    },
                                )
                            }
                            if (searchVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f + 0.35f * searchProgress.value)
                                        .graphicsLayer {
                                            alpha = searchProgress.value
                                            translationX = (1f - searchProgress.value) * 24.dp.toPx()
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        // 有 backdrop 时透明，让毛玻璃透出来；没有 backdrop 时退回
                        // surface，避免出现无底的栏。只在 MD3Exp 分支生效。
                        containerColor = KedgeOverlayBarColor(
                            backdrop = backdrop,
                            // MD3Exp 下没有 MiuixTheme，取它会拿到全 0 的透明色，
                            // 所以退回 MD3 的 surface。
                            fallback = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                                MiuixTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ),
                    navigationIcon = {
                        if (searchVisible) {
                            KedgeIconButton(
                                onClick = {
                                    drawerVm.updateSearchKeyword("")
                                    showSearch = false
                                },
                                modifier = Modifier.graphicsLayer {
                                    alpha = searchProgress.value
                                    translationX = (1f - searchProgress.value) * 24.dp.toPx()
                                },
                            ) {
                                Icon(arrowBack, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        // search↔close 同槽交叉淡化，三态判定照 ImageToolbox
                        // SettingsContent：!searching → search；
                        // searching && hasQuery → close；搜索态但还没输入时留空。
                        // 此前是硬切 imageVector，与 ChatPage 不一致。
                        val searching = searchProgress.value >= 0.5f
                        val hasQuery = searchProgress.value >= 0.5f && searchKeyword.isNotEmpty()
                        KedgeIconButton(
                            onClick = {
                                if (!showSearch) {
                                    showSearch = true
                                } else {
                                    drawerVm.updateSearchKeyword("")
                                }
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!searching) {
                                    Icon(
                                        imageVector = search,
                                        contentDescription = stringResource(R.string.chat_page_search_chats),
                                        modifier = Modifier.graphicsLayer {
                                            alpha = 1f - searchProgress.value
                                        },
                                    )
                                }
                                if (searching && hasQuery) {
                                    Icon(
                                        imageVector = close,
                                        contentDescription = stringResource(R.string.chat_page_search_chats),
                                        modifier = Modifier.graphicsLayer {
                                            alpha = searchProgress.value
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                // KedgeOverlayScaffold 内部是普通 Box，顶/底栏各自负责对齐；
                // 不写 align 的话底栏会被摆在 Box 的左上角（表现为"浮在天上"）。
                KedgeBlurredBar(
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 原 MD3 BottomAppBar 自带 windowInsets，去掉外壳后要自己补
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
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

                    KedgeTextTooltipBox("套餐") {
                        KedgeButton(
                            onClick = {
                                navController.navigate(Screen.SettingPackage)
                            },
                            modifier = Modifier.height(32.dp),
                            variant = KedgeButtonVariant.Secondary,
                            // Secondary 在两种风格下都偏淡（MD3 是 secondaryContainer、
                            // Miuix 是 secondaryVariant），抽屉底色接近时几乎看不出按钮
                            // 边界，这里各自往上抬到容器色档 secondaryContainer。
                            // 别再往实色 secondary 走——抽屉里已有实心按钮，实色会显得
                            // 过重。
                            colors = KedgeDrawerButtonColors.md3(),
                            miuixColors = KedgeDrawerButtonColors.miuix(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = subscriptionLabel,
                                style = KedgeTextStyles.footnoteSmall(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

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
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 只留横向内边距；上下由下面的顶栏 Spacer 与列表底部 padding 负责
                    .padding(horizontal = 8.dp),
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
                    contentPadding = PaddingValues(0.dp),
                    // 顶/底栏浮在列表之上，列表本身铺满并从栏下面穿过（毛玻璃才有
                    // 内容可透）；用列表内的 Spacer 占位，首尾项仍能完整滚出来。
                    topSpacerHeight = DrawerTopBarHeight + statusBarTop,
                    bottomSpacerHeight = DrawerBottomBarHeight + navBarBottom,
                    header = {
                        // 整宽浅色卡片（surfaceContainer），不是实心主色按钮——
                        // 抽屉里已经有实心按钮了，这里再一个会互相抢视觉。
                        KedgeButton(
                            onClick = { showCreateFolderDialog = true },
                            // 不写死高度、也不覆盖 contentPadding：KedgeButton 的 Miuix
                            // 分支会把 contentPadding 当 insideMargin 传给 MiuixButton，
                            // 沿用默认的 KedgeButtonDefaults.ContentPadding(vertical=10dp)
                            // 即可与其它默认 Miuix 按钮等高。此前写死 height(38.dp) 且把
                            // contentPadding 清零，两个叠加把按钮压得比常规矮一截。
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            variant = KedgeButtonVariant.Secondary,
                            shapes = ButtonDefaults.shapes(RoundedCornerShape(12.dp)),
                            miuixCornerRadius = 12.dp,
                            // 同上：Secondary 默认色太淡，这里加深一档
                            colors = KedgeDrawerButtonColors.md3(),
                            miuixColors = KedgeDrawerButtonColors.miuix(),
                        ) {
                            // 图标 + 文案整体居中（对齐 KernelSU 抽屉的「新建文件夹」）
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    createNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.chat_page_create_folder),
                                    style = KedgeTextStyles.bodyLarge(),
                                )
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
                KedgeSurface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        }
    }

    // 抽屉底色不能依赖 ModalDrawerSheet 的默认值：material3 1.5.0-alpha29 改了
    // DrawerDefaults 的容器色，MD3Exp 下会漏出灰紫底，和下面的白底分组卡片对不上。
    // 这里显式给色：Miuix 用 surface（页面底色），MD3Exp 用 surfaceContainerLow。
    val drawerContainerColor = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    // 有 drawerState 时用带预测返回的 ModalDrawerSheet（返回手势跟手关闭抽屉）
    if (drawerState != null) {
        ModalDrawerSheet(
            drawerState = drawerState,
            modifier = Modifier.width(300.dp),
            windowInsets = WindowInsets(0),
            drawerContainerColor = drawerContainerColor,
        ) {
            sheetContent()
        }
    } else {
        ModalDrawerSheet(
            modifier = Modifier.width(300.dp),
            windowInsets = WindowInsets(0),
            drawerContainerColor = drawerContainerColor,
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
                KedgeSurface(
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
                            style = KedgeTextStyles.title(),
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders) { folder ->
                        val isCurrent = folder.id == conversationToMoveFolder?.folderId
                        KedgeSurface(
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
                                    style = KedgeTextStyles.title(),
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

// 新建文件夹对话框。始终调用并用 visible 控制显隐，不要用 if 包一层：
// 那样退场动画播不出来（组件会在 visible 变 false 的瞬间被卸载）。
val emptyNameError = stringResource(R.string.chat_page_folder_name_empty)
KedgeEditDialog(
    visible = showCreateFolderDialog,
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

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AppAlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                KedgeTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { folderToRename = null }) {
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
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { folderToDelete = null }) {
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

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    KedgeTextTooltipBox(label) {
        KedgeIconButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            icon()
        }
    }
}

// 抽屉底栏高度：KedgeIconButton 48.dp + 上下各 8.dp 内边距
// 抽屉顶栏高度：MD3 TopAppBar 内容高 64.dp（不含状态栏 inset）
private val DrawerTopBarHeight = 64.dp

// 抽屉底栏高度：图标按钮 48.dp + 上下各 16.dp 内边距 = 80.dp，
// 与原先 MD3 BottomAppBar 的高度一致（去掉它之后曾缩到 64.dp，手感偏挤）。
private val DrawerBottomBarHeight = 80.dp

private val FolderRowShape = RoundedCornerShape(16.dp)

@Composable
private fun FolderSection(
    folders: List<Folder>,
    onClickFolder: (Folder) -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
) {
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
                        KedgeSurface(
                            shape = FolderRowShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FolderRowShape)
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = folder.name,
                                    style = KedgeTextStyles.body(),
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
    KedgeSurface(
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
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentAssistant) {
                    Text(
                        text = stringResource(R.string.assistant_page_current_assistant),
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 抽屉里两个次级按钮（新建文件夹、套餐/订阅）的加深配色。
 *
 * [KedgeButtonVariant.Secondary] 的默认色两边都偏淡：MD3 走
 * `filledTonalButton` 的 secondaryContainer，Miuix 走 `buttonColors()` 的
 * secondaryVariant。抽屉底色接近时按钮边界几乎看不见，所以统一抬到容器色档
 * secondaryContainer。
 *
 * 不要用实色 secondary 试过：在 Miuix 调色板里它会渲染成高饱和亮蓝实心块，
 * 抽屉里本来就有实心按钮，两个挨着显得过重。容器色档既能让边界看清，又不会
 * 抢视觉，且明暗主题都跟着主题色槽自动翻转。
 */
private object KedgeDrawerButtonColors {
    /**
     * 从 secondaryContainer 朝 secondary 混合的比例。
     *
     * 取值只允许三档：0.27f / 0.54f / 0.87f，与 MD3 的配色分档对齐，不要填中间值。
     * 当前取最深一档 0.87f：0.27f / 0.54f 实测都偏淡，抽屉底色接近时看不出按钮
     * 边界；0.87f 是三档里唯一能看清的，又还没到纯 secondary 实色蓝的程度。
     * 要调档只改这一个常量。
     *
     * 文字色不传 contentColor，交给 MiuixButton / filledTonalButton 各自的默认
     * 配对（Miuix 侧是 onSecondaryVariant、MD3 侧是 onTonalSurface）。手动指定
     * 试过 onSecondaryContainer / onSecondary / secondary 都不如默认的合适。
     */
    private const val TOWARD_SECONDARY = 0.87f

    @Composable
    fun md3(): androidx.compose.material3.ButtonColors {
        val scheme = androidx.compose.material3.MaterialTheme.colorScheme
        return androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = lerp(scheme.secondaryContainer, scheme.secondary, TOWARD_SECONDARY),
        )
    }

    @Composable
    fun miuix(): top.yukonga.miuix.kmp.basic.ButtonColors {
        val scheme = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        return top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
            color = lerp(scheme.secondaryContainer, scheme.secondary, TOWARD_SECONDARY),
        )
    }
}
