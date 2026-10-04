package heizige.kk.khatkit.app.feature.chat

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.model.GroupRole
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.feature.chat.ChatError
import heizige.kk.khatkit.app.core.ui.components.message.ChatMessage
import heizige.kk.khatkit.app.core.ui.components.ui.ErrorCardsDisplay
import heizige.kk.khatkit.app.core.ui.components.ui.ListSelectableItem
import heizige.kk.khatkit.app.core.ui.components.ui.RabbitLoadingIndicator
import heizige.kk.khatkit.app.core.ui.components.ui.Tooltip
import heizige.kk.khatkit.app.core.ui.hooks.ImeLazyListAutoScroller
import heizige.kk.khatkit.app.core.ui.theme.ChatFontProvider
import heizige.kk.kedge.theme.KedgeColors
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.adsClick
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.app.core.ui.icons.keyboardDoubleArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardDoubleArrowUp
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeIconButtonVariant
import heizige.kk.kedge.containers.KedgeFloatingToolbar

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String? = null,
    previewMode: Boolean,
    previewSearchQuery: String = "",
    onPreviewSearchQueryChange: (String) -> Unit = {},
    settings: Settings,
    hazeState: HazeState,
    errors: List<ChatError> = emptyList(),
    onDismissError: (Uuid) -> Unit = {},
    onClearAllErrors: () -> Unit = {},
    onRegenerate: (UIMessage) -> Unit = {},
    onEdit: (UIMessage) -> Unit = {},
    onForkMessage: (UIMessage) -> Unit = {},
    onDelete: (UIMessage) -> Unit = {},
    onUpdateMessage: (MessageNode) -> Unit = {},
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    /**
     * 群聊会话。默认走 [isGroupConversation] 的严格口径（`group_config` 非空**且**
     * `type == GROUP`），调用方一般不用显式传——这个默认值本身就是单聊零影响的保证：
     * 单聊会话算出来恒为 false，行为与 C1 之前逐字相同。
     */
    groupChat: Boolean = isGroupConversation(conversation),
) {
    AnimatedContent(
        targetState = previewMode,
        label = "ChatListMode",
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.8f) togetherWith fadeOut() + scaleOut(targetScale = 0.8f))
        }
    ) { target ->
        if (target) {
            ChatListPreview(
                innerPadding = innerPadding,
                conversation = conversation,
                settings = settings,
                hazeState = hazeState,
                searchQuery = previewSearchQuery,
                onSearchQueryChange = onPreviewSearchQueryChange,
                onJumpToMessage = onJumpToMessage,
                animatedVisibilityScope = this@AnimatedContent,
                groupChat = groupChat,
            )
        } else {
            ChatListNormal(
                innerPadding = innerPadding,
                conversation = conversation,
                state = state,
                loading = loading,
                processingStatus = processingStatus,
                settings = settings,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onForkMessage = onForkMessage,
                onDelete = onDelete,
                onUpdateMessage = onUpdateMessage,
                onTranslate = onTranslate,
                onClearTranslation = onClearTranslation,
                animatedVisibilityScope = this@AnimatedContent,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                groupChat = groupChat,
            )
        }
    }
}

/**
 * 气泡上显示的模型（模型名 + 模型图标 + 「更多」面板里的模型行）。
 *
 * ## 口径
 *
 * - **群聊优先看角色绑定**：[GroupRole.modelId] 绑了就用那个模型，群配置的 `model_id`
 *   本来就是「每个角色用哪个模型」这条契约的落点（`docs/beyond-orit-client-changes.md` 的
 *   `roles[]` 最小 schema 里含「模型绑定」）。
 * - **绑不上就回落到 [messageModelId]**：`model_id` 为 null / 空串 / 不是合法 Uuid /
 *   指向一个已被删掉的模型，四种都回落——角色配置里的一个坏 id 不该让整条消息不显示模型名。
 * - **单聊完全不受影响**：[role] 为 null 时结果就是 `messageModelId?.let(modelById::get)`，
 *   与 C1 之前 `ChatList.kt` 里那一句逐字等价。
 *
 * 纯函数：不碰 Compose / 数据库，因此可用 JVM 单测钉死（见 `GroupMessageModelTest`）。
 *
 * @param messageModelId 消息自己记的 `modelId`（生成侧写进去的**实际**调用模型）。
 * @param role 这条消息的发言角色；单聊与群聊里的用户消息都是 null。
 * @param modelById `Model.id` → `Model` 的索引。
 */
internal fun resolveMessageModel(
    messageModelId: Uuid?,
    role: GroupRole?,
    modelById: Map<Uuid, Model>,
): Model? {
    val roleModel = role?.modelId
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        ?.let(modelById::get)
    return roleModel ?: messageModelId?.let(modelById::get)
}

@Composable
private fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String? = null,
    settings: Settings,
    hazeState: HazeState,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)?,
    onClearTranslation: (UIMessage) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    groupChat: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    val density = LocalDensity.current
    // LocalActivity 才是 activity-compose 认可的取法（LocalContext as? Activity 在 ComposeView
    // 或多 Activity 场景下可能拿到非 Activity 上下文）
    val activity = LocalActivity.current as? heizige.kk.khatkit.app.RouteActivity

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Boolean = { isVolumeUp ->
            if (settings.displaySetting.enableVolumeKeyScroll) {
                val bottomPaddingPx = with(density) {
                    (32.dp + innerPadding.calculateBottomPadding()).toPx()
                }
                val scrollAmount = (state.layoutInfo.viewportSize.height - bottomPaddingPx) *
                    settings.displaySetting.volumeKeyScrollRatio
                scope.launch { state.scrollBy(if (isVolumeUp) -scrollAmount else scrollAmount) }
                true
            } else false
        }
        activity?.volumeKeyListeners?.add(listener)
        onDispose {
            activity?.volumeKeyListeners?.remove(listener)
        }
    }

    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastItem = lastOrNull() ?: return false
        val inputBarHeight = with(density) { innerPadding.calculateBottomPadding().toPx() }
        val lastPos = lastItem.offset + lastItem.size
        val inputPos = (state.layoutInfo.viewportEndOffset - inputBarHeight.roundToInt())
        // println("lastPos = $lastPos, inputPos = $inputPos  | ${lastPos <= inputPos - 8}")
        return lastPos <= inputPos - 8
    }

    // 聊天选择
    val selectedItems = remember { mutableStateListOf<Uuid>() }
    var selecting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    // 对话大小警告对话框
    val sizeInfo = rememberConversationSizeInfo(conversation)
    var showSizeWarningDialog by rememberSaveable(conversation.id) { mutableStateOf(true) }
    if (sizeInfo.showWarning && showSizeWarningDialog) {
        ConversationSizeWarningDialog(
            sizeInfo = sizeInfo,
            onDismiss = { showSizeWarningDialog = false }
        )
    }

    val assistant = remember(settings.assistants, conversation.assistantId) {
        settings.getAssistantById(conversation.assistantId)
    }
    val assistantById = remember(settings.assistants) {
        settings.assistants.associateBy { it.id }
    }
    val modelById = remember(settings.providers) {
        settings.providers
            .flatMap { it.models }
            .associateBy { it.id }
    }
    val groupConfig = conversation.groupConfig
    val lastMessageIndex = conversation.messageNodes.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // 自动滚动到底部
        if (settings.displaySetting.enableAutoScroll) {
            val isAtBottom by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.isAtBottom() } }
            val loadingNow by rememberUpdatedState(loadingState)
            LaunchedEffect(state) {
                snapshotFlow { isAtBottom }
                    .distinctUntilChanged()
                    .collect { atBottom ->
                        if (atBottom && loadingNow && !state.isScrollInProgress) {
                            state.requestScrollToItem(conversationUpdated.messageNodes.lastIndex + 10)
                        }
                    }
            }
        }

        // 判断最近是否滚动
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        ChatFontProvider(displaySetting = settings.displaySetting) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
            itemsIndexed(
                items = conversation.messageNodes,
                key = { index, item -> item.id },
            ) { index, node ->
                Column {
                    ListSelectableItem(
                        key = node.id,
                        onSelectChange = {
                            if (!selectedItems.contains(node.id)) {
                                selectedItems.add(node.id)
                            } else {
                                selectedItems.remove(node.id)
                            }
                        },
                        selectedKeys = selectedItems,
                        enabled = selecting,
                    ) {
                        val currentMessage = node.currentMessage
                        // 群聊：这条消息是谁说的由 role_id 决定，不是会话级助手。
                        val groupSpeaker = remember(currentMessage, groupConfig) {
                            GroupSpeakerResolver.resolve(currentMessage, groupConfig)
                        }
                        val speaker = groupSpeaker as? GroupSpeakerIdentity.Group
                        // 群聊切角色必须换助手（头像/名字跟着角色走）；单聊保持会话级助手原样。
                        // 解析口径与发言时挑角色助手一致（`Uuid.parse(role.assistantId)`），
                        // 但这里必须 runCatching：配置里的 assistantId 非法不能让列表崩掉。
                        val messageAssistant = remember(speaker?.assistantId, assistant, assistantById) {
                            val roleAssistantId = speaker?.assistantId
                                ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                                ?.let { assistantById[it] }
                            roleAssistantId ?: assistant
                        }
                        // 群聊气泡显示的模型：角色自己的绑定优先，绑不上才回落这条消息实际记的
                        // modelId。单聊 role 恒为 null，走的就是原来那一句，一字不差。
                        val messageModel = remember(
                            currentMessage.modelId,
                            speaker?.roleId,
                            groupConfig,
                            modelById,
                        ) {
                            resolveMessageModel(
                                messageModelId = currentMessage.modelId,
                                role = speaker?.roleId
                                    ?.let { roleId -> groupConfig?.roles?.firstOrNull { it.id == roleId } },
                                modelById = modelById,
                            )
                        }
                        ChatMessage(
                            node = node,
                            model = messageModel,
                            assistant = messageAssistant,
                            loading = loading && index == lastMessageIndex,
                            speakerName = speaker?.displayName,
                            speakerBadge = speaker?.badge,
                            speakerBadgeIsError = speaker?.isError == true,
                            groupChat = groupChat,
                            // 群聊下这三个动作既不显示（见 ChatMessage）也不触发，双保险。
                            onRegenerate = {
                                if (!groupChat) onRegenerate(currentMessage)
                            },
                            onEdit = {
                                onEdit(currentMessage)
                            },
                            onFork = {
                                if (!groupChat) onForkMessage(currentMessage)
                            },
                            onDelete = {
                                if (!groupChat) onDelete(currentMessage)
                            },
                            onShare = {
                                selecting = true  // 使用 CoroutineScope 延迟状态更新
                                selectedItems.clear()
                                selectedItems.addAll(conversation.messageNodes.map { it.id }
                                    .subList(0, conversation.messageNodes.indexOf(node) + 1))
                            },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            onTranslate = onTranslate,
                            onClearTranslation = onClearTranslation,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                            lastMessage = index == lastMessageIndex,
                        )
                    }
                }
            }

            if (loading) {
                item(LoadingIndicatorKey) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RabbitLoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                        AnimatedVisibility(
                            visible = processingStatus != null,
                        ) {
                            Text(
                                text = processingStatus ?: "",
                                style = KedgeTextStyles.body(),
                                color = KedgeColors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 为了能正确滚动到这
            item(ScrollBottomKey) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 错误消息卡片
            ErrorCardsDisplay(
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )

            // 完成选择
            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it * 2 },
                ),
            ) {
                KedgeFloatingToolbar(
                    expanded = true,
                ) {
                    Tooltip(
                        text = "Clear selection",
                    ) {
                        KedgeIconButton(
                            variant = KedgeIconButtonVariant.Filled,
                            onClick = {
                                selecting = false
                                selectedItems.clear()
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(close, null)
                        }
                    }
                    Tooltip(
                        text = "Select all",
                    ) {
                        KedgeIconButton(
                            variant = KedgeIconButtonVariant.Filled,
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.addAll(conversation.messageNodes.map { it.id })
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(adsClick, null)
                        }
                    }
                    Tooltip(
                        text = "Confirm",
                    ) {
                        KedgeIconButton(
                            variant = KedgeIconButtonVariant.Filled,
                            onClick = {
                                selecting = false
                                val messages = conversation.messageNodes.filter { it.id in selectedItems }
                                if (messages.isNotEmpty()) {
                                    showExportSheet = true
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(check, null)
                        }
                    }
                }
            }

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current

            // Now in Android 风格 DraggableScrollbar：轨道 + 可拖动滑块 + 回答位置点
            // 纯索引比例（量化 5% 步进）；生成/流式期间冻结，避免每帧重排导致跳变
            val frozenTotal = remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val frozenVisible = remember { androidx.compose.runtime.mutableIntStateOf(1) }
            val frozenProgress = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val sbInfo = state.layoutInfo
            val liveTotal = sbInfo.totalItemsCount.coerceAtLeast(1)
            val liveVisible = sbInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val firstIndex = state.firstVisibleItemIndex
            val firstSize = sbInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
                ?.size?.coerceAtLeast(1) ?: 1
            val offsetFraction = (state.firstVisibleItemScrollOffset.toFloat() / firstSize)
                .coerceIn(0f, 1f)
            var liveProgress = if (liveTotal > 1) {
                ((firstIndex + offsetFraction) / (liveTotal - 1)).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (!state.canScrollForward) liveProgress = 1f
            if (!state.canScrollBackward) liveProgress = 0f

            LaunchedEffect(loading) {
                if (loading) {
                    frozenTotal.intValue = liveTotal
                    frozenVisible.intValue = liveVisible
                    frozenProgress.floatValue = liveProgress
                }
            }

            val sbTotal = if (loading) frozenTotal.intValue.coerceAtLeast(1) else liveTotal
            val sbVisible = if (loading) frozenVisible.intValue.coerceAtLeast(1) else liveVisible
            val thumbRatio = ((sbVisible.toFloat() / sbTotal).coerceIn(0.06f, 1f) * 20f)
                .let { kotlin.math.round(it) / 20f }
            val scrollProgress = if (loading) frozenProgress.floatValue else liveProgress
            val scrollbarTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            val scrollbarThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            val scrollbarDotColor = MaterialTheme.colorScheme.primary
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 24.dp, bottom = 24.dp)
                    .width(14.dp)
                    .fillMaxHeight()
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            val info = state.layoutInfo
                            val total = info.totalItemsCount
                            val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
                            if (total > 1) {
                                val raw = delta * total / visible
                                scope.launch { state.dispatchRawDelta(raw) }
                            }
                        },
                    ),
            ) {
                val info = state.layoutInfo
                val total = info.totalItemsCount
                if (total <= 1 || size.height <= 0f) return@Canvas
                val nodes = conversation.messageNodes
                val nodeCount = nodes.size.coerceAtLeast(1)
                val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
                val track = size.height
                val thickness = 4.dp.toPx()
                val radius = androidx.compose.ui.geometry.CornerRadius(thickness / 2f)
                val x = size.width - thickness

                // 轨道
                drawRoundRect(
                    color = scrollbarTrackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(thickness, track),
                    cornerRadius = radius,
                )

                // 滑块（动画比例；到底/到顶时严格贴边）
                val thumbH = (track * thumbRatio).coerceAtLeast(32.dp.toPx()).coerceAtMost(track)
                val thumbTop = scrollProgress * (track - thumbH)
                drawRoundRect(
                    color = scrollbarThumbColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, thumbTop),
                    size = androidx.compose.ui.geometry.Size(thickness, thumbH),
                    cornerRadius = radius,
                )

                // 回答位置点
                val dotColor = scrollbarDotColor
                nodes.forEachIndexed { index, node ->
                    if (node.currentMessage.role == MessageRole.ASSISTANT) {
                        val y = if (nodeCount <= 1) 0f else index.toFloat() / (nodeCount - 1) * track
                        drawCircle(
                            color = dotColor,
                            radius = 2.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width - thickness / 2f, y),
                        )
                    }
                }
            }

            // 消息快速跳转
            MessageJumper(
                show = isRecentScroll && !state.isScrollInProgress && settings.displaySetting.showMessageJumper && !captureProgress,
                onLeft = settings.displaySetting.messageJumperOnLeft,
                scope = scope,
                state = state
            )

        }
    }
}

/**
 * 提取包含搜索词的文本片段，确保匹配词在开头可见
 */
private fun extractMatchingSnippet(
    text: String,
    query: String
): String {
    if (query.isBlank()) {
        return text
    }

    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex == -1) {
        return text
    }

    // 直接从匹配词开始显示，确保匹配词在最前面
    val snippet = text.substring(matchIndex)

    // 只在前面有内容时添加省略号
    return if (matchIndex > 0) {
        "...$snippet"
    } else {
        snippet
    }
}

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var startIndex = 0
        var index = text.indexOf(query, startIndex, ignoreCase = true)

        while (index >= 0) {
            // 添加高亮前的文本
            append(text.substring(startIndex, index))

            // 添加高亮文本
            withStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = Color.Black
                )
            ) {
                append(text.substring(index, index + query.length))
            }

            startIndex = index + query.length
            index = text.indexOf(query, startIndex, ignoreCase = true)
        }

        // 添加剩余文本
        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }
}

@Composable
private fun ChatListPreview(
    innerPadding: PaddingValues,
    conversation: Conversation,
    settings: Settings,
    hazeState: HazeState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onJumpToMessage: (Int) -> Unit,
    /**
     * 群聊会话。与 [ChatList] 的同名参数同一口径（[isGroupConversation]），单聊恒为 false。
     *
     * false 时下面每一条群聊分支都不成立（说话者恒为 null），预览与 C1 之前逐字相同。
     */
    groupChat: Boolean = isGroupConversation(conversation),
) {
    val groupConfig = conversation.groupConfig
    // 过滤消息，同时保留原始 index 避免后续 O(n) indexOf 查找
    val filteredMessages = remember(conversation.messageNodes, searchQuery) {
        if (searchQuery.isBlank()) {
            conversation.messageNodes.mapIndexed { index, node -> index to node }
        } else {
            conversation.messageNodes.mapIndexed { index, node -> index to node }
                .filter { (_, node) -> node.currentMessage.toText().contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .padding(top = innerPadding.calculateTopPadding())
            .fillMaxSize()
            .hazeSource(state = hazeState),
    ) {
        // 消息预览
        LazyColumn(
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = filteredMessages,
                key = { index, item -> item.second.id },
            ) { _, (originalIndex, node) ->
                val message = node.currentMessage
                val isUser = message.role == heizige.kk.khatkit.ai.core.MessageRole.USER
                // 群聊：预览片段必须带说话者，否则搜出来的三段话长得一模一样，谁说的完全分不出来。
                // 口径与气泡层共用同一个解析器（`GroupSpeakerResolver.resolve`），单聊恒为 null。
                val speaker = remember(message, groupConfig, groupChat) {
                    if (groupChat) {
                        GroupSpeakerResolver.resolve(message, groupConfig) as? GroupSpeakerIdentity.Group
                    } else {
                        null
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!isUser) Modifier.padding(end = 24.dp) else Modifier
                        ),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    // 显示名 + 性质徽章（议长 / 投票小结 / 错误）。徽章错误时染 error 色，
                    // 和气泡层 `ChatMessage` 的 `ChatMessageSpeakerBadge` 同一套语义。
                    speaker?.let { who ->
                        Text(
                            text = who.badge?.let { "${who.displayName} · $it" } ?: who.displayName,
                            style = KedgeTextStyles.footnoteSmall(),
                            color = if (who.isError) {
                                KedgeColors.error
                            } else {
                                KedgeColors.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    KedgeSurface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isUser) KedgeColors.primaryContainer else KedgeColors.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onJumpToMessage(originalIndex)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val highlightColor = KedgeColors.tertiaryContainer
                            val highlightedText = remember(searchQuery, message) {
                                val fullText = message.toText().trim().ifBlank { "[...]" }
                                val messageText = extractMatchingSnippet(
                                    text = fullText,
                                    query = searchQuery
                                )
                                buildHighlightedText(
                                    text = messageText,
                                    query = searchQuery,
                                    highlightColor = highlightColor
                                )
                            }
                            Text(
                                text = highlightedText,
                                style = KedgeTextStyles.body(),
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

@Composable
private fun BoxScope.MessageJumper(
    show: Boolean,
    onLeft: Boolean,
    scope: CoroutineScope,
    state: LazyListState
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(
            initialOffsetX = { if (onLeft) -it * 2 else it * 2 },
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it * 2 else it * 2 },
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KedgeSurface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(0)
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = KedgeColors.surfaceContainerHigh.copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = keyboardDoubleArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            KedgeSurface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(
                            (state.firstVisibleItemIndex - 1).fastCoerceAtLeast(
                                0
                            )
                        )
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = KedgeColors.surfaceContainerHigh.copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = keyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            KedgeSurface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(state.firstVisibleItemIndex + 1)
                    }
                },
                shape = CircleShape,
                color = KedgeColors.surfaceContainerHigh.copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = keyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            KedgeSurface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(state.layoutInfo.totalItemsCount - 1)
                    }
                },
                shape = CircleShape,
                color = KedgeColors.surfaceContainerHigh.copy(alpha = 0.65f),
            ) {
                Icon(
                    imageVector = keyboardDoubleArrowDown,
                    contentDescription = stringResource(R.string.chat_page_scroll_to_bottom),
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
        }
    }
}
