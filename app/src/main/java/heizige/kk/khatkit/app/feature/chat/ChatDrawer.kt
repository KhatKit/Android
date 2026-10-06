package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.download
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import heizige.kk.khatkit.hub.HubAccountInfo
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantPicker
import heizige.kk.khatkit.app.core.ui.components.ui.BackupReminderCard
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.UpdateCard
import androidx.compose.ui.draw.clip
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.readBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsPlayStoreVersion
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.createNewFolder
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.folder as folderIcon
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings as settingsIcon
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.adaptive.KedgeOverlayScaffold
import heizige.kk.kedge.adaptive.KedgeOverlayBarColor
import heizige.kk.kedge.adaptive.KedgeBlurredBar
import heizige.kk.kedge.adaptive.rememberKedgeBlurBackdrop
import heizige.kk.kedge.adaptive.LocalKedgeEnableBlur
import heizige.kk.kedge.components.KedgeButtonDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

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
        account == null -> stringResource(R.string.chat_drawer_plan)
        !account.active -> stringResource(R.string.chat_drawer_subscription_expired)
        account.toolCallsRemaining != null -> stringResource(
            R.string.chat_drawer_plan_remaining_calls,
            account.toolCallsRemaining!!,
        )

        account.aiTokensRemaining != null -> stringResource(
            R.string.chat_drawer_plan_remaining_tokens,
            account.aiTokensRemaining!!,
        )

        else -> stringResource(R.string.chat_drawer_plan)
    }

    val activity = context as ComponentActivity
    val drawerVm: ChatDrawerViewModel = hiltViewModel(viewModelStoreOwner = activity)

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val typeFilter by drawerVm.typeFilter.collectAsStateWithLifecycle()
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
                // 这里刻意用裸 MD3 TopAppBar（不分流），不用 KedgeTopAppBar：
                // Kedge 的 Miuix 分支走 KedgeMiuixCustomTitleBar，标题区套 26dp
                // TitlePadding 而 actions 侧只有 16dp ActionIconPadding，左右留白
                // 不对称（实测 43.7dp vs 27.7dp）。抽屉顶栏要的是左右对齐。
                KedgeBlurredBar(backdrop = backdrop) {
                TopAppBar(
                    colors = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                        TopAppBarDefaults.topAppBarColors(
                            // 有 backdrop 时透明，让 KedgeBlurredBar 的模糊透出来；
                            // 没有 backdrop 时退回 surface，避免出现无底的栏。
                            containerColor = KedgeOverlayBarColor(
                                backdrop = backdrop,
                                fallback = MiuixTheme.colorScheme.surface,
                            ),
                        )
                    } else {
                        // MD3Exp：走 CustomColors.topBarColors（surfaceContainer），
                        // 顶栏才有自己的颜色；TopAppBar 的默认容器色是 surface，
                        // 与抽屉底色同色，顶栏会整个「消失」。
                        CustomColors.topBarColors
                    },
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
                title = {
                    // 标题与搜索输入框共用 topBarTitle()，保证两边字号严格相等
                    // （MD3 22sp / Miuix 24sp）。
                    val topBarStyle = KedgeTextStyles.topBarTitle()
                    Box {
                        if (titleVisible) {
                            Text(
                                text = "KhatKit",
                                style = topBarStyle,
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
                                        style = topBarStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                BasicTextField(
                                    value = searchKeyword,
                                    onValueChange = drawerVm::updateSearchKeyword,
                                    singleLine = true,
                                    textStyle = topBarStyle.copy(
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
                    // search↔close 同槽交叉淡化，三态判定照 ImageToolbox
                    // SettingsContent：!searching → search；
                    // searching && hasQuery → close；搜索态但还没输入时留空。
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
                }
            },
            bottomBar = {
                // KedgeOverlayScaffold 内部是普通 Box，顶/底栏各自负责对齐；
                // 不写 align 的话底栏会被摆在 Box 的左上角（表现为"浮在天上"）。
                //
                // 底色与顶栏同一套来源：Miuix 有 backdrop 时透明走毛玻璃，否则退回
                // Miuix surface；MD3Exp 用 BottomAppBar 的默认容器色
                // （BottomAppBarTokens.ContainerColor = surfaceContainer），
                // 不再靠抽屉底色透出来。
                val bottomBarColor = KedgeOverlayBarColor(
                    backdrop = backdrop,
                    fallback = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                        MiuixTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                KedgeBlurredBar(
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(bottomBarColor),
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

                    KedgeTextTooltipBox(stringResource(R.string.chat_drawer_plan)) {
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

                    // 探索市场入口：原先这里是「卡片市场」（跳 Screen.KhatKitMarket），
                    // cd20c4f6 把卡片市场页并进 Screen.ExploreMarket 时连入口一起删了，
                    // 结果这个页只剩设置页一个入口。入口加回来，跳合并后的市场页。
                    DrawerAction(
                        icon = {
                            Icon(extension, null)
                        },
                        label = stringResource(R.string.explore_market_title),
                        onClick = {
                            if (drawerState != null) {
                                scope.launch { drawerState.close() }
                            }
                            navController.navigate(Screen.ExploreMarket)
                        },
                    )

                    DrawerAction(
                        icon = {
                            Icon(download, null)
                        },
                        label = stringResource(R.string.chat_drawer_download_center),
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
                    // 只留横向内边距；上下由列表内的顶/底 Spacer 负责
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    // **顶栏下面的一切（更新卡片 / 备份提醒 / 文件夹区 / 新建按钮）
                    // 都必须放进这个 header**：它们在列表流里，列表顶端本来就在顶栏
                    // 之下；放到 Column 里会整块藏进顶栏底下（文件夹「建了看不到、
                    // 点不到，只留一段空白」就是这么来的），而且顶栏那段留白会和
                    // 列表的 topSpacer 叠成两倍，把文件夹和「新建」按钮拉得很开。
                    topSpacerHeight = DrawerTopBarHeight + statusBarTop,
                    bottomSpacerHeight = DrawerBottomBarHeight + navBarBottom,
                    header = {
                        if (updateChecksEnabled && !isPlayStore) {
                            UpdateCard(vm)
                        }

                        BackupReminderCard(
                            settings = settings,
                            onClick = { navController.navigate(Screen.Backup) },
                        )

                        // 文件夹与「新建文件夹」是同一组（Miuix 下零间距、首末圆角，
                        // 圆角收口全靠这最后一组），所以它总是要出现——即使一个文件夹
                        // 都没有，也要渲染出那一行「新建文件夹」。组底留 8dp 断开与
                        // 会话列表的间距（header 是列表里的单个 item，内部的
                        // spacedBy 管不到这里）。
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
                            onCreateFolder = { showCreateFolderDialog = true },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            listOf(
                                heizige.kk.khatkit.app.core.data.model.GroupChat.FILTER_ALL to "全部",
                                heizige.kk.khatkit.app.core.data.model.GroupChat.TYPE_DIRECT to "单聊",
                                heizige.kk.khatkit.app.core.data.model.GroupChat.TYPE_GROUP to "群聊",
                            ).forEach { (value, label) ->
                                heizige.kk.kedge.components.KedgeFilterChip(
                                    selected = typeFilter == value,
                                    onClick = { drawerVm.updateTypeFilter(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        heizige.kk.kedge.components.KedgeTextButton(
                            onClick = {
                                drawerVm.createGroup { created ->
                                    navController.navigate(Screen.Chat(created.toString()))
                                }
                            },
                        ) { Text("新建群聊") }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // C1 真机 UI 测试的定位锚点：会话列表要能被滚动到指定条目
                        // （performScrollToNode），chip 过滤断言才能覆盖到屏幕外的行。
                        // 仅新增语义属性，不改布局/行为。
                        .testTag("drawer_conversation_list"),
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
                        .padding(start = 4.dp, end = 4.dp)
                        // 底栏（设置/订阅/下载中心）是浮在内容之上的，且自带
                        // navigationBarsPadding。助手胶囊在内容流里，必须自己垫出
                        // 「底栏高 + 导航栏」的高度，否则会被底栏整块盖住、点不到。
                        .padding(bottom = DrawerBottomBarHeight + navBarBottom + 8.dp),
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
    // DrawerDefaults 的容器色，MD3Exp 下会漏出灰紫底。
    // 这里显式给色，且两种风格都与聊天页同底色（页面用 KedgeColors.surface）：
    // 有色的一档只留给顶/底栏，抽屉本身跟正文同色。
    val drawerContainerColor = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface
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
                    // 不写死 shape：KedgeSurface 在 MD3Exp 下取 shapes.medium、
                    // Miuix 下取自己的 16dp 圆角，写死就等于把 MD3 圆角带进 Miuix。
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
                            // 同上：形状交给 KedgeSurface 按风格取默认
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
        val generatingMessage = stringResource(R.string.chat_page_delete_folder_generating)
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
                            Toast.show(generatingMessage, isError = false)
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

/**
 * 文件夹行的圆角。MD3 下每行各自圆角（[FolderRowShape]），Miuix 下整组收口
 * （[FolderCorner]，只有首项上圆角、末项下圆角）。
 *
 * 取 20dp 与下方历史记录分组（`ConversationSectionShape`）一致，两块区域圆角统一。
 */
private val FolderRowShape = RoundedCornerShape(20.dp)

private val FolderCorner = 20.dp

/**
 * Miuix 组内圆角：**只有第一项上圆角、最后一项下圆角，中间项直角**，项与项之间
 * 不留间距（卡片之间本来就有 8dp 圆角，硬拼成方角会在接缝处露出两个小缺口）。
 */
private fun folderGroupShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) FolderCorner else 0.dp,
    topEnd = if (index == 0) FolderCorner else 0.dp,
    bottomStart = if (index == count - 1) FolderCorner else 0.dp,
    bottomEnd = if (index == count - 1) FolderCorner else 0.dp,
)

/**
 * 抽屉里的文件夹区。
 *
 * Miuix 下「文件夹行 + 新建文件夹行」是**同一组**：组内零间距，首项上圆角、
 * 末项（新建文件夹）下圆角、中间项直角。为此这里不能再套一层限高 156dp 的
 * LazyColumn —— 内层滚动会把「最后一项」滚出可视区，方角与圆角就接不上了；
 * 抽屉本身可滚，文件夹多的时候跟着抽屉一起滚即可。
 *
 * MD3 保持原来的观感：文件夹行各自圆角、行间 6dp，新建文件夹是独立的整宽按钮。
 */
@Composable
private fun FolderSection(
    folders: List<Folder>,
    onClickFolder: (Folder) -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix

    if (isMiuix) {
        // 新建文件夹也算一项，所以 count 要 +1
        val count = folders.size + 1
        Column(modifier = modifier.fillMaxWidth()) {
            folders.forEachIndexed { index, folder ->
                FolderRow(
                    folder = folder,
                    shape = folderGroupShape(index, count),
                    onClick = { onClickFolder(folder) },
                    onRename = { onRename(folder) },
                    onDelete = { onDelete(folder) },
                )
            }
            CreateFolderButton(
                onClick = onCreateFolder,
                modifier = Modifier.fillMaxWidth(),
                // 并入文件夹组：末项直角 + 下圆角由外层 clip 收口，按钮自身的
                // Miuix 圆角设 0，避免内外两套圆角打架。
                miuixCornerRadius = 0.dp,
                modifierClip = folderGroupShape(count - 1, count),
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
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
                        FolderRow(
                            folder = folder,
                            shape = FolderRowShape,
                            onClick = { onClickFolder(folder) },
                            onRename = {
                                menuExpanded = false
                                onRename(folder)
                            },
                            onDelete = {
                                menuExpanded = false
                                onDelete(folder)
                            },
                        )
                    }
                }
            }
        }

        // MD3：整宽浅色卡片（surfaceContainer），不是实心主色按钮——
        // 抽屉里已经有实心按钮了，这里再一个会互相抢视觉。
        CreateFolderButton(
            onClick = onCreateFolder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    shape: Shape,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        KedgeSurface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    onClick = onClick,
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
                onClick = onRename,
            )
            KedgeDropdownItemSlot(
                text = { Text(stringResource(R.string.chat_page_delete)) },
                leadingIcon = { Icon(delete, null) },
                onClick = onDelete,
            )
        }
    }
}

/**
 * 抽屉里的「新建文件夹」按钮：配色与大小一直沿用原来那个 Secondary 整宽按钮
 * （[KedgeDrawerButtonColors] 加深一档 + 15dp 竖向内边距），Miuix 下并入文件夹组
 * 只需要外层 [modifierClip] 收口。
 */
@Composable
private fun CreateFolderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    miuixCornerRadius: Dp = 12.dp,
    modifierClip: Shape? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val basePadding = KedgeButtonDefaults.ContentPadding
    Box(modifier = modifier.then(modifierClip?.let { Modifier.clip(it) } ?: Modifier)) {
        KedgeButton(
            onClick = onClick,
            // 文字离背景边缘再远一点：默认 vertical 10dp，这里 +50% 到
            // 15dp。水平方向保持默认——按钮是 fillMaxWidth 且内容居中，
            // 水平 padding 对居中内容没有视觉影响，只会缩小可用宽度。
            contentPadding = PaddingValues(
                start = basePadding.calculateStartPadding(layoutDirection),
                end = basePadding.calculateEndPadding(layoutDirection),
                top = basePadding.calculateTopPadding() * 1.5f,
                bottom = basePadding.calculateBottomPadding() * 1.5f,
            ),
            modifier = Modifier.fillMaxWidth(),
            variant = KedgeButtonVariant.Secondary,
            shapes = ButtonDefaults.shapes(RoundedCornerShape(12.dp)),
            miuixCornerRadius = miuixCornerRadius,
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
        // 同上：形状交给 KedgeSurface 按风格取默认
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
 * 抽屉里两个次级按钮（新建文件夹、套餐/订阅）的配色。
 *
 * [KedgeButtonVariant.Secondary] 的默认色两边都偏淡：MD3 走
 * `filledTonalButton` 的 secondaryContainer，Miuix 走 `buttonColors()` 的
 * secondaryVariant。抽屉底色接近时按钮边界几乎看不见，所以各自往上抬了一档。
 *
 * ## MD3 为什么不用 secondary 混色
 *
 * 之前 MD3 侧是 `lerp(secondaryContainer, secondary, 0.87f)`（几乎就是实色
 * secondary），但**内容色没跟着换**——`filledTonalButtonColors()` 默认给
 * `onSecondaryContainer`/`onTonalSurface`，那是**浅底专用**的深色字。深底配深字，
 * 浅色主题下对比度极低，文字几乎读不出来（就是反馈的「配色反人类」）。
 * 混色本身还破坏了 M3 的容器色/内容色配对关系，再怎么调比例都是补丁。
 *
 * 现在 MD3 直接用中性 `surfaceContainerHigh` + `onSurface`：抽屉里其它卡片
 * （文件夹行 `surfaceContainer`、空态 `surfaceContainer`、助手胶囊
 * `surfaceContainerHigh`）都是这一族，两个按钮回到同一族才是同一套视觉语言；
 * 深浅主题都由 `onSurface` 自动配对，可读性稳定。
 *
 * Miuix 侧保持原来的 secondary 混色：那边按钮本来就小而轻（32dp 胶囊），
 * 提亮后边界够清晰，且没有「深底深字」的问题。
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
            // 中性容器色 + 同族内容色：与抽屉其它卡片一致，且不出现深底深字。
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
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
