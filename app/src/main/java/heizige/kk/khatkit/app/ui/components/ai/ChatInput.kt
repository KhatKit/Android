package heizige.kk.khatkit.app.ui.components.ai

import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.PredictiveBackHandler
import heizige.kk.khatkit.app.ui.icons.moreVert
import heizige.kk.khatkit.app.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.ui.icons.musicNote
import heizige.kk.khatkit.app.ui.icons.image
import heizige.kk.khatkit.app.ui.icons.mic
import heizige.kk.khatkit.app.ui.icons.photoCamera
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.asr.ASRStatus
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.data.datastore.getQuickMessagesOfAssistant
import heizige.kk.khatkit.app.data.files.FilesManager
import heizige.kk.khatkit.app.data.model.Assistant
import heizige.kk.khatkit.app.data.model.QuickMessage
import heizige.kk.khatkit.app.service.MessageQueueState
import heizige.kk.khatkit.app.service.QueuedMessage
import heizige.kk.khatkit.app.ui.components.ai.completion.ChatCompletionContext
import heizige.kk.khatkit.app.ui.components.ai.completion.ChatCompletionItem
import heizige.kk.khatkit.app.ui.components.ai.completion.ChatCompletionList
import heizige.kk.khatkit.app.ui.components.ai.completion.ChatCompletionProvider
import heizige.kk.khatkit.app.ui.components.ui.KeepScreenOn
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionRecordAudio
import heizige.kk.khatkit.app.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.ui.context.LocalASRState
import heizige.kk.khatkit.app.ui.context.LocalNavController
import heizige.kk.khatkit.app.ui.context.LocalSettings
import heizige.kk.khatkit.app.ui.context.LocalToaster
import heizige.kk.khatkit.app.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.utils.SoundEffectPlayer
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.seconds
import heizige.kk.khatkit.app.ui.pages.chat.VoicePhase
import heizige.kk.khatkit.app.ui.pages.chat.VoiceSessionState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.ui.icons.verifiedUser
import heizige.kk.khatkit.app.ui.icons.videocam
import heizige.kk.khatkit.app.ui.icons.add
import heizige.kk.khatkit.app.ui.icons.arrowUpward
import heizige.kk.khatkit.app.ui.icons.bolt
import heizige.kk.khatkit.app.ui.icons.close
import heizige.kk.khatkit.app.ui.icons.fullscreen

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    hazeState: HazeState,
    enableSearch: Boolean,
    onUpdateSearchMode: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onMoreClick: () -> Unit,
    attachmentActions: ChatAttachmentPickerActions? = null,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val hazeTintColor = KedgeColors.surfaceContainerHigh
    val inputHazeStyle = HazeBlurStyle.Material3 {
        blurRadius(20.dp)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 展开进度：0f 收起（胶囊）→ 1f 展开（24dp 圆角），拖动时实时跟手
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var panelHeightPx by remember { mutableIntStateOf(0) }
    // KodeHead 同款 ease-out 三次曲线（放大前期行程）
    fun easedProgress(): Float {
        val raw = progress.value
        return 1f - (1f - raw) * (1f - raw) * (1f - raw)
    }
    // 面板全开后继续上滑 → 进入全屏编辑
    var fullScreenEditor by remember { mutableStateOf(false) }
    var overscrollPx by remember { mutableFloatStateOf(0f) }
    val fullscreenThresholdPx = with(LocalDensity.current) { 110.dp.toPx() }

    // 预测返回：展开时返回手势跟手收起面板（KodeHead 同款）
    PredictiveBackHandler(enabled = progress.value > 0.01f) { backEvents ->
        try {
            backEvents.collect { event ->
                progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } catch (e: CancellationException) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val expanded = progress.value > 0.5f
    val genieLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var panelSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var inputRowSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val densityLocal = LocalDensity.current
    val gapPx = with(densityLocal) { 8.dp.toPx() }
    val expandedRadiusPx = with(densityLocal) { 24.dp.toPx() }
    // 背景圆角随输入框圆角走：收起时 = 输入框圆角 + 间距（同心），
    // 展开后段用 late-morph 过渡到 24dp。
    val collapsedOuterRadiusPx = (inputRowSize.height / 2f).takeIf { it > 0f }
        ?.plus(gapPx)
        ?: with(densityLocal) { 36.dp.toPx() }
    // 展开后也保持和输入框一圈同心的大圆角，不做收角
    val outerRadiusPx = collapsedOuterRadiusPx
    val outerRadiusDp = with(densityLocal) { outerRadiusPx.toDp().value }
    val innerRadiusDp = (outerRadiusDp - 8f).coerceAtLeast(0f)
    val containerShape = RoundedCornerShape(outerRadiusDp.dp)
    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }


    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Toast.show(message = message, isError = true)
        }
    }

    Surface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = (12f + 10f * easedProgress()).dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            KedgeSurface(
                modifier = Modifier
                    .weight(1f)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (progress.value >= 0.999f && delta < 0f) {
                                // 已全开还继续上滑：累计到阈值进入全屏
                                overscrollPx += -delta
                                if (overscrollPx > fullscreenThresholdPx) {
                                    overscrollPx = 0f
                                    fullScreenEditor = true
                                }
                            } else if (panelHeightPx > 0) {
                                overscrollPx = 0f
                                val next = (progress.value - delta / panelHeightPx).coerceIn(0f, 1f)
                                scope.launch { progress.snapTo(next) }
                            }
                        },
                        onDragStopped = { velocity ->
                            val target = when {
                                velocity < -1200f -> 1f
                                velocity > 1200f -> 0f
                                progress.value > 0.5f -> 1f
                                else -> 0f
                            }
                            scope.launch {
                                progress.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                    )
                    .clip(containerShape)
                    .then(
                        if (easedProgress() > 0.05f) {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = HazeBlurStyle.Material3 { blurRadius(40.dp) },
                            )
                        } else Modifier
                    ),
                shape = containerShape,
                color = Color.Transparent,
                // 收起时不描边，展开过程中出现（KodeHead 风格）
                border = if (easedProgress() > 0.05f) {
                    BorderStroke(
                        width = (2f * easedProgress()).dp,
                        color = KedgeColors.outlineVariant.copy(alpha = 0.6f),
                    )
                } else null,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        // 展开后上下边距与左右一致（8dp）
                        vertical = (4f + 4f * easedProgress()).dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = KedgeColors.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }

                    // 展开面板：搜索/推理/权限 + 附件动作（拖动实时跟手展开）
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minHeight = 0,
                                        maxHeight = androidx.compose.ui.unit.Constraints.Infinity,
                                    )
                                )
                                val height = (placeable.height * easedProgress()).toInt()
                                layout(constraints.maxWidth, height) {
                                    placeable.place(0, 0)
                                }
                            }
                            .graphicsLayer { alpha = easedProgress() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged {
                                    panelHeightPx = it.height
                                    panelSize = it
                                }
                                .aiChatGenieEffect(
                                    progress = easedProgress(),
                                    // 从输入框中间的小圆球展开
                                    collapsedBounds = run {
                                        val centerX = panelSize.width / 2f
                                        val centerY = panelSize.height + inputRowSize.height / 2f
                                        val radius = 36f
                                        androidx.compose.ui.geometry.Rect(
                                            left = centerX - radius,
                                            top = centerY - radius,
                                            right = centerX + radius,
                                            bottom = centerY + radius,
                                        )
                                    },
                                    contentLayer = genieLayer,
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                // 能力 chip 行（在输入框上方）
                val chatModel = settings.getCurrentChatModel()
                val showReasoning = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {

    
                    if (showReasoning) {
                        ReasoningButton(
                            chip = true,
                            reasoningLevel = assistant.reasoningLevel,
                            onUpdateReasoningLevel = {
                                onUpdateAssistant(assistant.copy(reasoningLevel = it))
                            },
                        )
                    }
    

                }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                attachmentActions?.let { actions ->
                                    PanelAction(photoCamera, "拍摄", actions.onTakePicture)
                                    PanelAction(image, "图像", actions.onPickImage)
                                    PanelAction(insertDriveFile, "文件", actions.onPickFile)
                                }
                            }
                        }
                    }

                    // 单行输入栏：[+] [输入框] [发送]；语音改成独立的 FAB
                    val imeVisible = WindowInsets.isImeVisible
                    val showSend = !asrState.isRecording &&
                        (imeVisible || loading || !asrState.isAvailable)

                    Surface(
                        shape = RoundedCornerShape(innerRadiusDp.dp),
                        color = hazeTintColor,
                        modifier = Modifier
                            .height(56.dp)
                            .onSizeChanged { inputRowSize = it },
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ActionIconButton(
                            onClick = {
                                scope.launch {
                                    progress.animateTo(
                                        targetValue = if (progress.value > 0.5f) 0f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = add,
                                contentDescription = stringResource(R.string.more_options),
                                // 展开时 + 旋转 135° 变成 ×
                                modifier = Modifier.rotate(135f * easedProgress()),
                            )
                        }

                        TextInputRow(
                            state = state,
                            completionProviders = completionProviders,
                            onSendMessage = { sendMessage() },
                            isFullScreen = fullScreenEditor,
                            onFullScreenChange = { fullScreenEditor = it },
                            modifier = Modifier.weight(1f),
                        )



                        if (loading) {
                            KeepScreenOn()
                        }

                        AnimatedVisibility(
                            visible = showSend,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SendButton(
                                loading = loading,
                                empty = state.isEmpty(),
                                onClick = { sendMessage() },
                                onLongClick = { sendMessageWithoutAnswer() },
                            )
                        }
                    }
                    }
                }
            }

            // 独立的语音 / 打字切换按钮（和输入框同高）
            if (asrState.isAvailable || asrState.isRecording) {
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        when (asrState.status) {
                            ASRStatus.Listening -> asr.stop()
                            ASRStatus.Idle, ASRStatus.Error -> {
                                if (!asrPermission.allRequiredPermissionsGranted) {
                                    asrPermission.requestPermissions()
                                } else {
                                    asrBaseText = state.textContent.text.toString()
                                    asr.start { transcript ->
                                        val spacer =
                                            if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                        state.setMessageText(asrBaseText + spacer + transcript)
                                    }
                                }
                            }

                            ASRStatus.Connecting, ASRStatus.Stopping -> {}
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = if (asrState.isRecording) KedgeColors.errorContainer else KedgeColors.primary,
                    contentColor = if (asrState.isRecording) KedgeColors.onErrorContainer else KedgeColors.onPrimary,
                ) {
                    Icon(
                        imageVector = if (asrState.isRecording) close else mic,
                        contentDescription = null,
                    )
                }
            }
            }

        }
    }


}

@Composable
private fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStop = loading && empty
    val containerColor = when {
        showStop -> KedgeColors.errorContainer
        empty -> KedgeColors.surfaceContainerHigh
        else -> KedgeColors.primary
    }
    val contentColor = when {
        showStop -> KedgeColors.onErrorContainer
        empty -> KedgeColors.onSurface.copy(alpha = 0.38f)
        else -> KedgeColors.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(end = 2.dp)
            .size(36.dp)
            .testTag("chat_send_button")
            .clip(RoundedCornerShape(13.dp))
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(13.dp),
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = if (showStop) close else arrowUpward,
            contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides KedgeColors.onSurfaceVariant
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val assistant = settings.getCurrentAssistant()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KedgeColors.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = close,
                        contentDescription = stringResource(R.string.cancel_edit),
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.debounce(120).collectLatest { context ->
                val lists = withContext(Dispatchers.Default) {
                    completionProviders.mapNotNull { provider ->
                        try {
                            provider.complete(context)
                                ?.takeIf { it.items.isNotEmpty() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        ChatInputTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            placeholder = stringResource(R.string.chat_input_placeholder),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onSendMessage = onSendMessage,
            sendOnEnter = settings.displaySetting.sendOnEnter,
            // 只在聚焦时提供全屏按钮，避免未展示仍占位
            trailingContent = if (isFocused) {
                {
                    IconButton(
                        onClick = {
                            onFullScreenChange(!isFullScreen)
                        }) {
                        Icon(fullscreen, null)
                    }
                }
            } else null,
            leadingContent = if (quickMessages.isNotEmpty()) {
                {
                    QuickMessageButton(quickMessages = quickMessages, state = state)
                }
            } else null,
        )
        if (isFullScreen) {
            FullScreenEditor(state = state) {
                onFullScreenChange(false)
            }
        }
    }
}

@Composable
private fun ChatInputTextField(
    state: ChatInputState,
    modifier: Modifier = Modifier,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    onSendMessage: () -> Unit = {},
    sendOnEnter: Boolean = false,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val keyboardAction: () -> Unit = {
        if (sendOnEnter && !state.isEmpty()) {
            onSendMessage()
        }
    }
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        MiuixTextField(
            state = state.textContent,
            modifier = modifier,
            insideMargin = DpSize(8.dp, 8.dp),
            colors = MiuixTextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                borderColor = Color.Transparent,
            ),
            cornerRadius = 16.dp,
            label = placeholder,
            useLabelAsPlaceholder = true,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = { keyboardAction() },
            lineLimits = TextFieldLineLimits.SingleLine,
            leadingIcon = leadingContent,
            trailingIcon = trailingContent,
        )
    } else {
        TextField(
            state = state.textContent,
            modifier = modifier,
            shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(placeholder)
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = { keyboardAction() },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = trailingContent,
            leadingIcon = leadingContent,
        )
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, KedgeColors.outlineVariant.copy(alpha = 0.5f)),
        color = KedgeColors.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = KedgeColors.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KedgeColors.onSurfaceVariant,
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
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(bolt, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                        expanded = false
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState, onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        // 和普通展开同一曲线：ease-out 三次 + 同一个 spring
        val appear = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        val appearEased = 1f - (1f - appear.value) * (1f - appear.value) * (1f - appear.value)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .graphicsLayer {
                    alpha = appearEased
                    translationY = (1f - appearEased) * 64f
                },
            verticalArrangement = Arrangement.Bottom
        ) {
            KedgeSurface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    ChatInputTextField(
                        state = state,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        placeholder = stringResource(R.string.chat_input_placeholder),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ActionIconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = KedgeColors.onSurfaceVariant,
        )
    }
}
