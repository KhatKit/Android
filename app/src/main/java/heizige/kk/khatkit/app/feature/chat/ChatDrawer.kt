package heizige.kk.khatkit.app.feature.chat

import ui.feice.mochi.navigation.MochiSidebarHeader
import ui.feice.mochi.navigation.MochiSidebarHeaderButton
import ui.feice.mochi.navigation.MochiSidebarItem
import ui.feice.mochi.navigation.MochiSidebarProfileRow
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.shape.MochiCapsule
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.vector.ImageVector
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.basic.rememberMochiSheetState

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
import heizige.kk.khatkit.app.core.ui.icons.chat
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
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import heizige.kk.khatkit.uikit.mochi.KkEditDialog
import heizige.kk.khatkit.uikit.mochi.KkEditField
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import ui.feice.mochi.navigation.MochiSidebarState
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
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
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

import heizige.kk.khatkit.uikit.mochi.MochiOverlayScaffold
import heizige.kk.khatkit.uikit.mochi.MochiButtonDefaults
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    vm: ChatViewModel,
    settings: Settings,
    current: Conversation,
    sidebarState: MochiSidebarState? = null,
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

    // 侧栏的「当前角色」和角色 sheet 同源：跟当前会话的角色走（会话角色缺失时才退回全局当前角色）。
    // 以前这里直接用 settings.assistantId：打开别的角色的老会话时，侧栏勾的是 A、角色 sheet 勾的是 B。
    val currentRoleId = settings.getAssistantById(current.assistantId)?.id ?: settings.assistantId

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 侧栏搜索（MochiSidebarHeader 的搜索框）
    //
    // 关键字非空 = 搜索态：检索范围是**当前角色下所有会话的消息正文**（FTS 命中，见
    // ChatDrawerViewModel.messageSearch），结果行给会话标题 + 命中片段（关键词高亮）+ 时间，
    // 点进会话并定位到命中消息（nodeId）。关键字为空则完全回到原来的角色列表 / 会话列表。
    val searchKeyword by drawerVm.searchKeyword.collectAsStateWithLifecycle()
    val messageSearch by drawerVm.messageSearch.collectAsStateWithLifecycle()

    // 打开一条消息命中：跳到具体会话并定位到 nodeId。id 解析失败（脏数据）就什么都不做，
    // 不许把抽屉崩掉。
    val openMessageHit: (MessageSearchResult) -> Unit = { hit ->
        val conversationId = runCatching { Uuid.parse(hit.conversationId) }.getOrNull()
        if (conversationId != null) {
            sidebarState?.close()
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

    // SIDE2：照 Mochi 侧边栏示例排版 —— 顶部是 MochiSidebarHeader（搜索框 + 圆钮），
    // 中间列表占满剩余高度、上下缘渐隐，底部是个人资料行（设置 + 套餐 chip）。状态栏/导航栏 insets 由
    // MochiSidebarLayout 统一垫好（宽屏常驻侧栏在 ChatPage 那层自己垫），这里不再叠加。
    val sheetContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            MochiSidebarHeader(
                query = searchKeyword,
                onQueryChange = drawerVm::updateSearchKeyword,
                // 搜索范围是当前角色的消息正文（不按角色名/会话标题过滤）
                placeholder = stringResource(R.string.chat_page_search_chats),
            ) {
                // Mochi 侧栏头部圆钮：与搜索框同高、同一套玻璃/色调底（MochiSidebarHeaderButton）
                MochiSidebarHeaderButton(onClick = { navController.navigate(Screen.Assistant) }) {
                    Icon(
                        imageVector = edit,
                        contentDescription = null,
                        tint = MochiTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 渐隐（含 FX 开启时的渐进模糊）不再挂死在这一层：之前用固定强度
            // MochiFadeMaskDefaults.Full，列表停在顶部时首行（探索市场/下载中心所在 header
            // 之后的第一项）照样被 24dp 渐隐 + 48dp 模糊盖住。改成每个列表按自己的滚动位置
            // 用 kkScrollEdgeFade(listState)：滚到顶那一边是实的，首行清晰。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (searchKeyword.isNotBlank()) {
                    // 搜索态：命中**当前角色**会话的消息正文（角色名 + 「标题/片段/时间」行），
                    // 点命中行跳进会话并定位到 nodeId。清空关键字立刻回到角色列表。
                    DrawerMessageSearchList(
                        keyword = searchKeyword,
                        state = messageSearch,
                        roleName = settings.assistants
                            .firstOrNull { it.id == currentRoleId }?.name
                            ?: "当前角色",
                        groupByConversation = false,
                        contentPadding = DrawerListPadding,
                        listTag = "drawer_role_search_list",
                        onOpenHit = openMessageHit,
                        onOpenConversation = { conversation ->
                            sidebarState?.close()
                            navigateToChatPage(navController, conversation.id)
                        },
                    )
                } else {
                    // 抽屉只有这一份列表：角色。会话不再单独列一栏（历史聊天在角色面板里），
                    // 文件夹功能整体移除，因此这里既没有切换 tab，也没有文件夹分组。
                    RoleDrawerList(
                        roles = settings.assistants,
                        selected = currentRoleId,
                        chats = roleChats,
                        running = conversationJobs.filterValues { it?.isActive == true }.keys,
                        keyword = searchKeyword,
                        contentPadding = DrawerListPadding,
                        onRole = { role ->
                            vm.switchRole(role.id) { target ->
                                sidebarState?.close()
                                navigateToChatPage(navController, target)
                            }
                        },
                        providerName = { role ->
                            settings.findModelProviderName(role.chatModelId ?: settings.chatModelId)
                        },
                        onManage = { navController.navigate(Screen.Assistant) },
                        header = {
                            // lazy item 里多个根节点会叠在一起画，这里显式竖排
                            Column {
                                if (updateChecksEnabled && !isPlayStore) UpdateCard(vm)
                                BackupReminderCard(settings = settings, onClick = { navController.navigate(Screen.Backup) })
                                // 照 Mochi 侧栏示例：功能入口作为列表顶部的普通行（胶囊高亮），
                                // 不再挤在底栏一排图标里
                                MochiSidebarItem(
                                    title = stringResource(R.string.explore_market_title),
                                    onClick = {
                                        sidebarState?.close()
                                        navController.navigate(Screen.ExploreMarket)
                                    },
                                    leading = { DrawerRowIcon(extension) },
                                    modifier = Modifier.testTag("drawer_explore_market"),
                                )
                                MochiSidebarItem(
                                    title = stringResource(R.string.chat_drawer_download_center),
                                    onClick = { navController.navigate(Screen.DownloadCenter) },
                                    leading = { DrawerRowIcon(download) },
                                    modifier = Modifier.testTag("drawer_download_center"),
                                )
                                MochiSidebarItem(
                                    title = stringResource(R.string.history_page_title),
                                    onClick = {
                                        sidebarState?.close()
                                        navController.navigate(Screen.History)
                                    },
                                    leading = { DrawerRowIcon(chat) },
                                    modifier = Modifier.testTag("drawer_history"),
                                )
                            }
                        },
                    )
                }
            }
            // 底部照 Mochi 侧栏示例用个人资料行：整行进设置，行尾胶囊 chip 显示套餐状态、
            // 点它直达套餐页（有效订阅时 chip 为选中色）。探索市场 / 下载中心已移到列表顶部。
            MochiSidebarProfileRow(
                name = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.settings),
                onClick = { navController.navigate(Screen.Setting) },
                avatar = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MochiTheme.colorScheme.primaryContainer, MochiCapsule),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = settingsIcon,
                            contentDescription = null,
                            tint = MochiTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailing = {
                    MochiChip(
                        label = subscriptionLabel,
                        selected = account?.active == true,
                        onClick = { navController.navigate(Screen.SettingPackage) },
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .testTag("drawer_plan_chip"),
                    )
                },
                modifier = Modifier.testTag("drawer_profile_row"),
            )
        }
    }

    // SIDE1：侧栏的容器（宽度 / 圆角 / 底色 / 遮罩 / 手势 / 预测性返回）全部由
    // MochiSidebarLayout 负责，这里只出内容，不再自套 material3 的 ModalDrawerSheet。
    sheetContent()

    // 移动到助手 Bottom Sheet：U7b-2b 去掉父级 if 门控，显隐交给 MochiSheetState，
    // 这样面板有收起动画；内容里对 conversationToMove 只做 null 安全读（:627/:654），退出期间不会 NPE。
    val moveToAssistantSheet = rememberMochiSheetState()
    LaunchedEffect(showMoveToAssistantSheet) {
        if (showMoveToAssistantSheet) moveToAssistantSheet.show() else moveToAssistantSheet.hide()
    }
    PrimaryBottomSheet(
        state = moveToAssistantSheet,
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
                                MochiTheme.colorScheme.surfaceVariant
                            } else {
                                MochiTheme.colorScheme.surface
                            },
                            supportingContent = if (isCurrentAssistant) {
                                {
                                    Text(
                                        text = stringResource(R.string.assistant_page_current_assistant),
                                        style = MochiTheme.textStyles.body,
                                        color = MochiTheme.colorScheme.onSurfaceVariant
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

/** 侧栏功能行的前导图标：与 Mochi 示例同一尺寸（20dp），次要文字色 */
@Composable
private fun DrawerRowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MochiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

// 侧栏列表的留白：上下缘有渐隐遮罩，底部照 Mochi 示例留 12dp，横向与 MochiSidebarItem 自带的内缩一致（0）
private val DrawerListPadding = PaddingValues(bottom = 12.dp)

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
    val highlight = MochiTheme.colorScheme.tertiaryContainer
    val muted = MochiTheme.colorScheme.onSurfaceVariant
    val bodyStyle = MochiTheme.textStyles.body
    val footnote = MochiTheme.textStyles.labelSmall
    val grouped = remember(state.hits) { state.hits.groupBy { it.conversationId } }
    val pending = state.searching || state.keyword != keyword
    val hasAny = grouped.isNotEmpty() || state.titleFallback.isNotEmpty()

    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(listTag).kkScrollEdgeFade(listState),
        state = listState,
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
                                        style = MochiTheme.textStyles.titleMedium,
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
    val muted = MochiTheme.colorScheme.onSurfaceVariant
    val snippet = remember(hit.snippet, highlight) { highlightedSnippet(hit.snippet, highlight) }
    val time = remember(hit.updateAt) { drawerSearchTimeLabel(hit.updateAt) }
    KkSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // UI-3：与侧栏行同一套水平 16dp / 垂直 8dp（原来 14dp，和角色行差 2dp 会看出一条错位）
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hit.title.ifBlank { "未命名会话" },
                        style = MochiTheme.textStyles.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = time,
                        style = MochiTheme.textStyles.labelSmall,
                        color = muted,
                        maxLines = 1,
                    )
                }
                Text(
                    text = snippet,
                    style = MochiTheme.textStyles.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = snippet,
                        style = MochiTheme.textStyles.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = time,
                        style = MochiTheme.textStyles.labelSmall,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
