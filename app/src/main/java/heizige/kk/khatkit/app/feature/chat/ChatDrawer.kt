package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.Folder
import heizige.kk.khatkit.hub.HubAccountInfo
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantItem
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantPicker
import heizige.kk.khatkit.app.core.ui.components.ui.BackupReminderCard
import heizige.kk.khatkit.app.core.ui.components.ui.UpdateCard
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import heizige.kk.khatkit.app.core.ui.components.ui.scrollLinkedTopBarColors
import androidx.compose.ui.draw.clip
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.readBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsPlayStoreVersion
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
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

    val roleChats by drawerVm.roleChats.collectAsStateWithLifecycle()

    // 抽屉顶栏的折叠变色（**只作用于 MD3Exp**，Miuix 仍走毛玻璃那套）。
    //
    // 与页面顶栏同一套联动：静止态取页面底色（栏与内容连成一片，看不出有一条栏），
    // 角色列表往上滚、内容压到栏下面之后，M3 按 `collapsedFraction` 在
    // containerColor → scrolledContainerColor（surfaceContainer）之间插值，色块才浮出来。
    //
    // ⚠️ 必须是**同一个实例**既喂给 TopAppBar、又经 [activeNestedScroll] 挂到列表上：
    // 前者只负责按 fraction 取色，后者负责喂滚动增量。两处各 remember 一个的话
    // fraction 恒为 0 —— 表现就是「顶栏永远一个颜色」（页面顶栏踩过的同一个坑）。
    val drawerBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 顶栏搜索（复刻 ImageToolbox 侧边栏：TopAppBar 内联搜索框）
    //
    // 关键字非空 = 搜索态：检索范围是**当前角色下所有会话的消息正文**（FTS 命中，见
    // ChatDrawerViewModel.messageSearch），结果行给会话标题 + 命中片段（关键词高亮）+ 时间，
    // 点进会话并定位到命中消息（nodeId）。关键字为空则完全回到原来的角色列表 / 会话列表。
    val searchKeyword by drawerVm.searchKeyword.collectAsStateWithLifecycle()
    val messageSearch by drawerVm.messageSearch.collectAsStateWithLifecycle()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            delay(100)
            searchFocus.requestFocus()
        }
    }

    // 打开一条消息命中：跳到具体会话并定位到 nodeId。id 解析失败（脏数据）就什么都不做，
    // 不许把抽屉崩掉。
    val openMessageHit: (MessageSearchResult) -> Unit = { hit ->
        val conversationId = runCatching { Uuid.parse(hit.conversationId) }.getOrNull()
        if (conversationId != null) {
            scope.launch { drawerState?.close() }
            navigateToChatPage(
                navController,
                conversationId,
                nodeId = runCatching { Uuid.parse(hit.nodeId) }.getOrNull(),
            )
        }
    }

    // 移动对话状态
    var showMoveToAssistantSheet by remember { mutableStateOf(false) }
    var conversationToMove by remember { mutableStateOf<Conversation?>(null) }

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
                        // MD3Exp：走 CustomColors.topBarColors（surfaceContainer），但把
                        // **静止态**换成页面底色（surface）—— 与页面顶栏同一条
                        // [scrollLinkedTopBarColors]：顶栏静止时与抽屉内容连成一片，
                        // 列表滚上来才渐变到 surfaceContainer 分出层次。
                        // 若这里仍写死 surfaceContainer，两色相同 ⇒ M3 的插值空转，
                        // 就回到「顶栏永远一个颜色、跟下面的滚动无关」。
                        scrollLinkedTopBarColors(CustomColors.topBarColors, drawerBarScrollBehavior)
                    },
                    // Miuix 不接 MD3 的滚动行为（那边由毛玻璃 + backdrop 负责外观）。
                    scrollBehavior = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                        null
                    } else {
                        drawerBarScrollBehavior
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
                                text = "角色",
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
                                    // 搜索范围是消息正文（不再按角色名/会话标题过滤）。占位文案
                                    // 与上面搜索图标的 contentDescription 复用同一个 res key，
                                    // 读屏与视觉上的这一处措辞只有一个来源。
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
                                    // 叉号是「关闭搜索」，不再借用搜索图标的描述（读屏会误报「搜索聊天」）
                                    contentDescription = stringResource(R.string.chat_drawer_search_clear),
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
                    .weight(1f)
                    // 喂抽屉顶栏的折叠变色：会话列表的滚动增量往上冒到这一层，
                    // 接到 drawerBarScrollBehavior 上（MD3 才挂，Miuix 下是空 Modifier）。
                    // ⚠️ 挂在这层**父节点**而不是列表自己：`Modifier.nestedScroll` 要靠
                    // 节点树找到下面的 scrollable 派发器，父节点这条路径与页面把连接挂在
                    // Scaffold 上（`ExploreMarketPage.kt:1211`）是同一种结构，最稳。
                    // 也不能挂在 KedgeOverlayScaffold 的 modifier 上：它 MD3 分支会
                    // 把这个 modifier 丢掉（只有 Miuix 分支透传给 MiuixScaffold）。
                    .then(activeNestedScroll(drawerBarScrollBehavior)),
            ) {
                if (searchKeyword.isNotBlank()) {
                    // 搜索态：命中**当前角色**会话的消息正文（角色名 + 「标题/片段/时间」行），
                    // 点命中行跳进会话并定位到 nodeId。清空关键字立刻回到角色列表。
                    DrawerMessageSearchList(
                        keyword = searchKeyword,
                        state = messageSearch,
                        roleName = settings.assistants
                            .firstOrNull { it.id == settings.assistantId }?.name
                            ?: "当前角色",
                        groupByConversation = false,
                        contentPadding = PaddingValues(
                            top = DrawerTopBarHeight + statusBarTop + 8.dp,
                            bottom = DrawerBottomBarHeight + navBarBottom + 68.dp,
                        ),
                        listTag = "drawer_role_search_list",
                        onOpenHit = openMessageHit,
                        onOpenConversation = { conversation ->
                            scope.launch { drawerState?.close() }
                            navigateToChatPage(navController, conversation.id)
                        },
                    )
                } else {
                    // 抽屉只有这一份列表：角色。会话不再单独列一栏（历史聊天在角色面板里），
                    // 文件夹功能整体移除，因此这里既没有切换 tab，也没有文件夹分组。
                    RoleDrawerList(
                        roles = settings.assistants,
                        selected = settings.assistantId,
                        chats = roleChats,
                        running = conversationJobs.filterValues { it?.isActive == true }.keys,
                        keyword = searchKeyword,
                        contentPadding = PaddingValues(
                            top = DrawerTopBarHeight + statusBarTop + 8.dp,
                            bottom = DrawerBottomBarHeight + navBarBottom + 68.dp,
                        ),
                        onRole = { role ->
                            vm.switchRole(role.id) { target ->
                                scope.launch { drawerState?.close() }
                                navigateToChatPage(navController, target)
                            }
                        },
                        providerName = { role ->
                            settings.findModelProviderName(role.chatModelId ?: settings.chatModelId)
                        },
                        onManage = { navController.navigate(Screen.Assistant) },
                        header = {
                            if (updateChecksEnabled && !isPlayStore) UpdateCard(vm)
                            BackupReminderCard(settings = settings, onClick = { navController.navigate(Screen.Backup) })
                        },
                    )
                }

                // 抽屉底栏上方原来还有一条 footer（记忆 / 角色设置两个图标）：记忆已搬到
                // 设置 → 数据设置，角色设置入口随之取消，整块删掉，不留空分隔线。
                // 列表底部留白由各自 contentPadding（DrawerBottomBarHeight + navBarBottom
                // + 68.dp）负责，与此处无关。
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
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    // 有 drawerState 时用带预测返回的 ModalDrawerSheet（返回手势跟手关闭抽屉）
    if (drawerState != null) {
        ModalDrawerSheet(
            drawerState = drawerState,
            modifier = Modifier.width(320.dp),
            windowInsets = WindowInsets(0),
            drawerContainerColor = drawerContainerColor,
        ) {
            sheetContent()
        }
    } else {
        ModalDrawerSheet(
            modifier = Modifier.width(320.dp),
            windowInsets = WindowInsets(0),
            drawerContainerColor = drawerContainerColor,
        ) {
            sheetContent()
        }
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
                // 圆角与行间空隙取全局列表卡片样式（与助手页 / 选择器 / 设置页同一套口径），
                // 首项只圆上边、末项只圆下边，整列看着是一张分组卡片。
                val cards = listCardStyle()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(cards.gap),
                ) {
                    itemsIndexed(settings.assistants) { index, assistant ->
                        val isCurrentAssistant = assistant.id == conversationToMove?.assistantId
                        // 与助手页 / 助手选择器共用 core 里的同一行实现（AssistantItem）：
                        // 抽屉只多「当前助手」副标题与当前项底色两处差异，容器与头像不再自绘。
                        AssistantItem(
                            assistant = assistant,
                            defaultAssistantName = stringResource(R.string.assistant_page_default_assistant),
                            shape = cards.indexedShape(index, settings.assistants.size),
                            providerName = settings.findModelProviderName(
                                assistant.chatModelId ?: settings.chatModelId
                            ),
                            backgroundColor = if (isCurrentAssistant) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            supportingContent = if (isCurrentAssistant) {
                                {
                                    Text(
                                        text = stringResource(R.string.assistant_page_current_assistant),
                                        style = KedgeTextStyles.body(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                conversationToMove?.let { conversation ->
                                    vm.moveConversationToAssistant(conversation, assistant.id)
                                    dismiss()
                                }
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

// ---------------------------------------------------------------------------
// 抽屉搜索态：当前角色消息正文检索结果（FTS 命中）
//
// 数据来自 ChatDrawerViewModel.messageSearch：MessageFtsManager 的 snippet 用
// simple_snippet(message_fts, 0, '[', ']', ..., 30) 生成，命中词被 `[` `]` 包住，
// 所以高亮只需把方括号之间那段套上背景色（与 SearchPage.SearchResultItem 同一套解析）。
// ---------------------------------------------------------------------------

/** FTS 片段（`…[关键词]…`）→ 带高亮背景的富文本。 */
private fun highlightedSnippet(snippet: String, highlight: Color): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start < 0) {
                append(snippet.substring(index))
                return@buildAnnotatedString
            }
            if (start > index) append(snippet.substring(index, start))
            val end = snippet.indexOf(']', start + 1)
            if (end < 0) {
                append(snippet.substring(start))
                return@buildAnnotatedString
            }
            withStyle(SpanStyle(background = highlight)) {
                append(snippet.substring(start + 1, end))
            }
            index = end + 1
        }
    }

/** 命中行的时间：今天给 HH:mm、昨天给「昨天」、更早给 M/d（与角色列表同一套口径）。 */
private fun drawerSearchTimeLabel(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (local.toLocalDate()) {
        today -> local.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "昨天"
        else -> local.format(DateTimeFormatter.ofPattern("M/d"))
    }
}

/**
 * 抽屉搜索态列表（角色 tab / 会话 tab 共用）。
 *
 * @param keyword 顶栏输入框的原始关键字。与 [state] 里的关键字对不上 = 还在防抖/查询中，
 *   此时显示「正在检索」，不闪上一轮的旧结果。
 * @param roleName 非空时在顶部显示角色名（角色 tab 的「角色名 + 命中行」）。
 * @param groupByConversation true = 按会话分组（会话 tab：会话标题作分组头，下面挂片段行）；
 *   false = 平铺，每行自带「会话标题 + 片段 + 时间」（角色 tab）。
 */
@Composable
private fun DrawerMessageSearchList(
    keyword: String,
    state: DrawerMessageSearchState,
    roleName: String?,
    groupByConversation: Boolean,
    contentPadding: PaddingValues,
    listTag: String,
    onOpenHit: (MessageSearchResult) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyStyle = KedgeTextStyles.body()
    val footnote = KedgeTextStyles.footnoteSmall()
    val grouped = remember(state.hits) { state.hits.groupBy { it.conversationId } }
    val pending = state.searching || state.keyword != keyword
    val hasAny = grouped.isNotEmpty() || state.titleFallback.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(listTag),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        roleName?.let { name ->
            item("search-role") {
                Text(
                    text = name,
                    style = footnote,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        when {
            pending -> item("search-pending") {
                Text(
                    text = stringResource(R.string.chat_drawer_search_searching),
                    style = bodyStyle,
                    color = muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            !hasAny -> item("search-empty") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(
                        text = stringResource(R.string.chat_drawer_search_empty),
                        style = bodyStyle,
                        color = muted,
                    )
                    // 中性表述：只陈述「正文没命中 / 索引可能还没建」，**不**给操作建议——
                    // 抽屉里没有可达的索引重建入口（SearchPage 在 HEAD 上没有路由），
                    // 指过去就是死链。标题兜底已由下面的分节显式列出，不需要再说「已回退」。
                    Text(
                        text = stringResource(R.string.chat_drawer_search_empty_hint),
                        style = footnote,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            else -> {
                if (grouped.isNotEmpty()) {
                    val conversationCount = grouped.size
                    item("search-heading") {
                        Text(
                            // 标注一次搜索的**吞吐上限**：FTS 查询只取 DRAWER_SEARCH_HIT_LIMIT 条，
                            // 不写出来用户会以为「就这么多命中」。这个常量与 MessageFtsManager 的
                            // LIMIT 对齐；分页/提高上限不在本任务 scope。
                            text = stringResource(
                                R.string.chat_drawer_search_hits_heading,
                                state.hits.size,
                                conversationCount,
                                DRAWER_SEARCH_HIT_LIMIT,
                            ),
                            style = footnote,
                            color = muted,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (groupByConversation) {
                        grouped.entries.forEach { (conversationId, hits) ->
                            val head = hits.first()
                            item("conv-$conversationId") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = head.title.ifBlank { "未命名会话" },
                                        style = KedgeTextStyles.title(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = drawerSearchTimeLabel(head.updateAt),
                                        style = footnote,
                                        color = muted,
                                        maxLines = 1,
                                    )
                                }
                            }
                            items(hits, key = { "hit-${it.conversationId}-${it.nodeId}-${it.messageId}" }) { hit ->
                                DrawerSearchHitRow(
                                    hit = hit,
                                    showTitle = false,
                                    highlight = highlight,
                                    onClick = { onOpenHit(hit) },
                                )
                            }
                        }
                    } else {
                        items(
                            state.hits,
                            key = { "hit-${it.conversationId}-${it.nodeId}-${it.messageId}" },
                        ) { hit ->
                            DrawerSearchHitRow(
                                hit = hit,
                                showTitle = true,
                                highlight = highlight,
                                onClick = { onOpenHit(hit) },
                            )
                        }
                    }
                }

                if (state.titleFallback.isNotEmpty()) {
                    item("fallback-heading") {
                        Text(
                            // 标题匹配是**降级路**，这里用可见的分节标题明确标出（不与消息命中混排），
                            // 也就是合同里要求的「保留标题检索必须给出可见入口」。
                            // 文案中性：只说「正文没找到，以下是标题匹配」，不提任何页面/操作。
                            text = stringResource(R.string.chat_drawer_search_title_fallback),
                            style = footnote,
                            color = muted,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    items(state.titleFallback, key = { "title-${it.id}" }) { conversation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(onClick = { onOpenConversation(conversation) })
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = conversation.title.ifBlank { "未命名会话" },
                                style = bodyStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = drawerSearchTimeLabel(conversation.updateAt),
                                style = footnote,
                                color = muted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条消息命中行：[showTitle] 时自带会话标题 + 时间，否则只有片段 + 时间（外层有分组头）。 */
@Composable
private fun DrawerSearchHitRow(
    hit: MessageSearchResult,
    showTitle: Boolean,
    highlight: Color,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val snippet = remember(hit.snippet, highlight) { highlightedSnippet(hit.snippet, highlight) }
    val time = remember(hit.updateAt) { drawerSearchTimeLabel(hit.updateAt) }
    KedgeSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hit.title.ifBlank { "未命名会话" },
                        style = KedgeTextStyles.title(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = time,
                        style = KedgeTextStyles.footnoteSmall(),
                        color = muted,
                        maxLines = 1,
                    )
                }
                Text(
                    text = snippet,
                    style = KedgeTextStyles.body(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = snippet,
                        style = KedgeTextStyles.body(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = time,
                        style = KedgeTextStyles.footnoteSmall(),
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
