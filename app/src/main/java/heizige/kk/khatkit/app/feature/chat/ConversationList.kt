package heizige.kk.khatkit.app.feature.chat

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.insertSeparators
import androidx.paging.map
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.folder as folderIcon
import heizige.kk.khatkit.app.core.ui.icons.folderOpen
import heizige.kk.khatkit.app.core.ui.theme.extendColors
import heizige.kk.khatkit.app.core.ui.icons.forward
import heizige.kk.khatkit.app.core.ui.icons.keepOff
import heizige.kk.khatkit.app.core.ui.icons.pushPin
import heizige.kk.khatkit.app.core.ui.icons.refresh
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

/**
 * 会话列表按时间分组后的条目类型。
 */
sealed class ConversationListItem {
    data class DateHeader(
        val group: ConversationTimeGroup,
        val label: String,
    ) : ConversationListItem()

    data object PinnedHeader : ConversationListItem()

    data class Item(
        val conversation: Conversation,
    ) : ConversationListItem()
}

/**
 * 会话的时间分组：今天 / 昨天 / 更早。
 */
enum class ConversationTimeGroup { TODAY, YESTERDAY, EARLIER }

internal fun conversationTimeGroup(
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
): ConversationTimeGroup = when (date) {
    today -> ConversationTimeGroup.TODAY
    today.minusDays(1) -> ConversationTimeGroup.YESTERDAY
    else -> ConversationTimeGroup.EARLIER
}

internal fun Context.conversationGroupLabel(group: ConversationTimeGroup): String = getString(
    when (group) {
        ConversationTimeGroup.TODAY -> R.string.chat_page_today
        ConversationTimeGroup.YESTERDAY -> R.string.chat_page_yesterday
        ConversationTimeGroup.EARLIER -> R.string.chat_page_earlier
    }
)

/**
 * 把分页会话流转换为带分组标题（置顶 / 今天 / 昨天 / 更早）的列表流。
 */
fun PagingData<Conversation>.insertConversationSections(
    label: (ConversationTimeGroup) -> String,
): PagingData<ConversationListItem> {
    fun headerFor(conversation: Conversation): ConversationListItem {
        if (conversation.isPinned) return ConversationListItem.PinnedHeader
        val group = conversationTimeGroup(
            conversation.updateAt.atZone(ZoneId.systemDefault()).toLocalDate()
        )
        return ConversationListItem.DateHeader(group = group, label = label(group))
    }

    fun groupOf(conversation: Conversation): ConversationTimeGroup = conversationTimeGroup(
        conversation.updateAt.atZone(ZoneId.systemDefault()).toLocalDate()
    )

    return map { ConversationListItem.Item(it) }
        .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
            if (after !is ConversationListItem.Item) return@insertSeparators null
            if (before == null) return@insertSeparators headerFor(after.conversation)

            val beforeConversation = before.conversation
            val afterConversation = after.conversation
            when {
                beforeConversation.isPinned && !afterConversation.isPinned ->
                    headerFor(afterConversation)

                !beforeConversation.isPinned && !afterConversation.isPinned &&
                    groupOf(beforeConversation) != groupOf(afterConversation) ->
                    headerFor(afterConversation)

                else -> null
            }
        }
}

@Composable
fun ConversationList(
    conversations: LazyPagingItems<ConversationListItem>,
    currentId: Uuid?,
    modifier: Modifier = Modifier,
    conversationJobs: Collection<Uuid> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    onClick: (Conversation) -> Unit = {},
    onDelete: ((Conversation) -> Unit)? = null,
    onRegenerateTitle: ((Conversation) -> Unit)? = null,
    onPin: ((Conversation) -> Unit)? = null,
    onMoveToAssistant: ((Conversation) -> Unit)? = null,
    onMoveToFolder: ((Conversation) -> Unit)? = null,
    onRemoveFromFolder: ((Conversation) -> Unit)? = null,
) {
    var hasScrolledToCurrent by remember(currentId) { mutableStateOf(false) }
    val headerCount = if (header != null) 1 else 0

    LaunchedEffect(currentId, conversations.itemCount, hasScrolledToCurrent) {
        if (currentId == null || hasScrolledToCurrent) return@LaunchedEffect
        val currentIndex = conversations.itemSnapshotList.items.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == currentId
        }
        if (currentIndex >= 0) {
            val targetIndex = currentIndex + headerCount
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!isVisible) {
                listState.scrollToItem(targetIndex)
            }
            hasScrolledToCurrent = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        header?.let { content ->
            item(key = "conversation_list_header") {
                content()
            }
        }

        if (conversations.itemCount == 0) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = ConversationSectionShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = stringResource(id = R.string.chat_page_no_conversations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(
            count = conversations.itemCount,
            key = conversations.itemKey { item ->
                when (item) {
                    is ConversationListItem.DateHeader -> "date_${item.group}"
                    is ConversationListItem.PinnedHeader -> "pinned_header"
                    is ConversationListItem.Item -> item.conversation.id.toString()
                }
            }
        ) { index ->
            val item = conversations[index]
            val next = if (index + 1 < conversations.itemCount) conversations[index + 1] else null
            when (item) {
                is ConversationListItem.DateHeader -> {
                    SectionHeader(
                        label = item.label,
                        icon = null,
                        showTopSpacing = index > 0,
                        modifier = Modifier.animateItem(),
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    SectionHeader(
                        label = stringResource(R.string.pinned_chats),
                        icon = pushPin,
                        showTopSpacing = index > 0,
                        modifier = Modifier.animateItem(),
                    )
                }

                is ConversationListItem.Item -> {
                    val prev = if (index > 0) conversations[index - 1] else null
                    ConversationItem(
                        conversation = item.conversation,
                        selected = item.conversation.id == currentId,
                        loading = item.conversation.id in conversationJobs,
                        isSectionStart = prev is ConversationListItem.DateHeader ||
                            prev is ConversationListItem.PinnedHeader,
                        isSectionEnd = next !is ConversationListItem.Item,
                        onClick = onClick,
                        onDelete = onDelete,
                        onRegenerateTitle = onRegenerateTitle,
                        onPin = onPin,
                        onMoveToAssistant = onMoveToAssistant,
                        onMoveToFolder = onMoveToFolder,
                        onRemoveFromFolder = onRemoveFromFolder,
                        modifier = Modifier.animateItem(),
                    )
                }

                null -> {
                    // Placeholder for loading state
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    icon: ImageVector?,
    showTopSpacing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (showTopSpacing) 16.dp else 8.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = if (showTopSpacing) 16.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val ConversationSectionShape = RoundedCornerShape(20.dp)

private val ConversationRowShape = RoundedCornerShape(2.dp)

/** 滑到底判定：滑动距离达到行宽的比例。 */
private const val FullSwipeFraction = 0.85f

private fun conversationRowShape(
    isSectionStart: Boolean,
    isSectionEnd: Boolean,
): RoundedCornerShape = ConversationRowShape.copy(
    topStart = if (isSectionStart) ConversationSectionShape.topStart else ConversationRowShape.topStart,
    topEnd = if (isSectionStart) ConversationSectionShape.topEnd else ConversationRowShape.topEnd,
    bottomStart = if (isSectionEnd) ConversationSectionShape.bottomStart else ConversationRowShape.bottomStart,
    bottomEnd = if (isSectionEnd) ConversationSectionShape.bottomEnd else ConversationRowShape.bottomEnd,
)

@Suppress("DEPRECATION")
@Composable
private fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
    isSectionStart: Boolean,
    isSectionEnd: Boolean,
    modifier: Modifier = Modifier,
    onDelete: ((Conversation) -> Unit)? = null,
    onRegenerateTitle: ((Conversation) -> Unit)? = null,
    onPin: ((Conversation) -> Unit)? = null,
    onMoveToAssistant: ((Conversation) -> Unit)? = null,
    onMoveToFolder: ((Conversation) -> Unit)? = null,
    onRemoveFromFolder: ((Conversation) -> Unit)? = null,
    onClick: (Conversation) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var showDropdownMenu by remember { mutableStateOf(false) }
    var rowWidth by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pinTriggered by remember { mutableStateOf(false) }
    val shape = conversationRowShape(isSectionStart = isSectionStart, isSectionEnd = isSectionEnd)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
    )

    // 只有滑动距离达到行宽的 85% 才算“滑到底”：删除先弹确认，置顶直接执行。
    LaunchedEffect(dismissState, rowWidth) {
        if (rowWidth <= 0) return@LaunchedEffect
        val fullSwipeThreshold = rowWidth * FullSwipeFraction
        snapshotFlow { runCatching { dismissState.requireOffset() }.getOrDefault(0f) }
            .collect { offset ->
                when {
                    offset <= -fullSwipeThreshold && onDelete != null -> {
                        if (!showDeleteConfirm) showDeleteConfirm = true
                    }

                    offset >= fullSwipeThreshold && onPin != null -> {
                        if (!pinTriggered) {
                            pinTriggered = true
                            onPin(conversation)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    abs(offset) < fullSwipeThreshold -> pinTriggered = false
                }
            }
    }

    fun resetSwipe() {
        showDeleteConfirm = false
        pinTriggered = false
        scope.launch { dismissState.reset() }
    }

    if (showDeleteConfirm && onDelete != null) {
        AppAlertDialog(
            onDismissRequest = { resetSwipe() },
            title = { Text(stringResource(R.string.chat_page_delete_conversation)) },
            text = {
                Text(
                    conversation.title.ifBlank {
                        stringResource(R.string.chat_page_new_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(conversation)
                        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { resetSwipe() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onPin != null,
        enableDismissFromEndToStart = onDelete != null,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Icon(
                            imageVector = pushPin,
                            contentDescription = stringResource(R.string.pin_chat),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = delete,
                            contentDescription = stringResource(R.string.chat_page_delete),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                }

                else -> Unit
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width }
            .padding(
                top = if (selected && !isSectionStart) 2.dp else 0.dp,
                bottom = if (selected && !isSectionEnd) 2.dp else 0.dp,
            )
            .clip(shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onClick(conversation) },
                    onLongClick = {
                        // Also clear chat input focus when the drawer is permanently visible.
                        focusManager.clearFocus(force = true)
                        showDropdownMenu = true
                    }
                )
                .padding(vertical = 3.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Spacer(Modifier.weight(1f))

                // 置顶图标
                AnimatedVisibility(conversation.isPinned) {
                    Icon(
                        imageVector = pushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(loading) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.extendColors.green6)
                            .size(4.dp)
                            .semantics {
                                contentDescription = "Loading"
                            }
                    )
                }
                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (onPin != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                                )
                            },
                            onClick = {
                                onPin(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (conversation.isPinned) keepOff else pushPin,
                                    null
                                )
                            }
                        )
                    }

                    if (onRegenerateTitle != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(id = R.string.chat_page_regenerate_title))
                            },
                            onClick = {
                                onRegenerateTitle(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(refresh, null)
                            }
                        )
                    }

                    if (onMoveToAssistant != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.chat_page_move_to_assistant))
                            },
                            onClick = {
                                onMoveToAssistant(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(forward, null)
                            }
                        )
                    }

                    if (onMoveToFolder != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.chat_page_move_to_folder))
                            },
                            onClick = {
                                onMoveToFolder(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(folderIcon, null)
                            }
                        )
                    }

                    if (onRemoveFromFolder != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.chat_page_remove_from_folder))
                            },
                            onClick = {
                                onRemoveFromFolder(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(folderOpen, null)
                            }
                        )
                    }

                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(id = R.string.chat_page_delete))
                            },
                            onClick = {
                                onDelete(conversation)
                                showDropdownMenu = false
                            },
                            leadingIcon = {
                                Icon(delete, null)
                            }
                        )
                    }
                }
            }
        }
    }
}
