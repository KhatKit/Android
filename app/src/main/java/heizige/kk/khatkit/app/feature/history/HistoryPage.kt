package heizige.kk.khatkit.app.feature.history;

import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.chat
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.pushPin
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiPageContentPadding
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.rememberMochiSnackbarHostState
import heizige.kk.khatkit.uikit.mochi.MochiSnackbarHost
import heizige.kk.khatkit.uikit.mochi.MochiSnackbarResult
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiSwipeAction
import ui.feice.mochi.list.MochiSwipeRow
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.theme.MochiTheme

@Composable
fun HistoryPage(vm: HistoryVM = hiltViewModel()) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = rememberMochiSnackbarHostState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    // 栏底渐显：正文滚进栏下面才把底色淡入（标题常显，底色跟滚动走）。
    val listState = rememberLazyListState()
    val barProgress = MochiScrollEdgeDefaults.progress(listState)

    val conversations by vm.conversations.collectAsStateWithLifecycle()

    Box {
        MochiSettingsPageScaffold(
            title = stringResource(R.string.history_page_title),
            // 正文没有大标题（列表直接从分组卡片开始），栏标题常显；
            // 栏底色仍然跟滚动走：滚动时淡入，停下时透明。
            titleProgress = { 1f },
            backgroundProgress = barProgress,
            actions = {
                MochiIconButton(
                    onClick = {
                        navController.navigate(Screen.MessageSearch)
                    }
                ) {
                    Icon(
                        travelExplore,
                        contentDescription = stringResource(R.string.history_page_search_messages)
                    )
                }
                MochiIconButton(onClick = { showDeleteAllDialog = true }) {
                    Icon(delete, contentDescription = stringResource(R.string.history_page_delete_all))
                }
            },
        ) { innerPadding ->
            val snackMessageDeleted = stringResource(R.string.history_page_conversation_deleted)
            val snackMessageUndo = stringResource(R.string.history_page_undo)

            val contentPadding = PaddingValues(
                start = MochiPageContentPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = MochiPageContentPadding.calculateEndPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            )

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = chat,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
                ) {
                    item(key = "conversations") {
                        // 与下载中心同构：一条 MochiListSection（一张分组卡片）+ MochiListRow 行，
                        // 行首同一档图标（MochiListDefaults.LeadingSize），行间分隔线由分组卡片画。
                        // 栏标题已经是「聊天历史」，正文不再重复分组小标题。
                        MochiListSection {
                            conversations.forEach { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    onClick = {
                                        navigateToChatPage(navController, conversation.id)
                                    },
                                    onDelete = {
                                        scope.launch {
                                            // 先获取完整的对话数据（包含 messageNodes），用于撤销恢复
                                            val fullConversation = vm.getFullConversation(conversation.id) ?: conversation
                                            vm.deleteConversation(conversation)
                                            val result = snackbarHostState.showSnackbar(
                                                message = snackMessageDeleted,
                                                actionLabel = snackMessageUndo,
                                                withDismissAction = true,
                                            )
                                            if (result == MochiSnackbarResult.ActionPerformed) {
                                                vm.restoreConversation(fullConversation)
                                            }
                                        }
                                    },
                                    onTogglePin = { vm.togglePinStatus(conversation.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        MochiSnackbarHost(state = snackbarHostState)
    }

    // 删全部是不可逆操作：用 Mochi 对话框 + 长按确认按钮（holdToConfirm），
    // 按住时颜色从左填满、填满才执行；提前松手什么都不做。
    MochiDialog(
        visible = showDeleteAllDialog,
        onDismissRequest = { showDeleteAllDialog = false },
        title = stringResource(R.string.history_page_delete_all_conversations),
        message = stringResource(R.string.history_page_delete_all_confirmation),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.history_page_delete),
                role = MochiDialogActionRole.Destructive,
                holdToConfirm = true,
                onClick = { vm.deleteAllConversations() },
            ),
            MochiDialogAction(
                label = stringResource(R.string.history_page_cancel),
                role = MochiDialogActionRole.Cancel,
            ),
        ),
    )
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val deleteAction = MochiSwipeAction(
        label = stringResource(R.string.history_page_delete),
        color = MochiTheme.colorScheme.error,
        onClick = onDelete,
        removesRow = true,
        icon = { tint -> Icon(delete, contentDescription = null, tint = tint) },
    )

    // 配色照 Mochi 实例（example ListPage「左滑操作」）：置顶用 secondary + onSecondary，
    // 删除用 error（内容色保持默认白）——按钮底色和文字色必须成对取，否则正文对比度不达。
    val pinAction = MochiSwipeAction(
        // 文案走资源（与页面其它动作同一套），不要写死中文。
        label = stringResource(if (conversation.isPinned) R.string.history_page_unpin else R.string.history_page_pin),
        color = MochiTheme.colorScheme.secondary,
        onClick = onTogglePin,
        contentColor = MochiTheme.colorScheme.onSecondary,
        icon = { tint -> Icon(pushPin, contentDescription = null, tint = tint) },
    )

    MochiSwipeRow(
        actions = listOf(deleteAction),
        leadingActions = listOf(pinAction),
        dividerInset = MochiListDefaults.RowPadding,
    ) {
        MochiListRow(
            // 空标题（新对话还没生成标题）用资源兜底，别让行看起来只剩日期。
            title = conversation.title.ifBlank { stringResource(R.string.history_page_new_conversation) },
            subtitle = buildConversationSubtitle(conversation),
            leading = {
                Icon(
                    imageVector = chat,
                    contentDescription = null,
                    modifier = Modifier.size(MochiListDefaults.LeadingSize),
                    tint = if (conversation.isPinned) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onClick,
        )
    }
}

/**
 * 行副标题。
 *
 * **不要拼「N 条消息」**：历史列表走 getConversationsOfAssistant，它刻意只映射摘要
 * （conversationEntityToConversation(entity, emptyList())），messageNodes 永远是空表，
 * 拼出来就是恒定的「0 条消息」。要显示真实条数得另开一条聚合查询，这里先只给更新时间；
 * 置顶状态已经在行首图标上用主色表达。
 */
private fun buildConversationSubtitle(conversation: Conversation): String =
    conversation.updateAt.toLocalDateTime()
