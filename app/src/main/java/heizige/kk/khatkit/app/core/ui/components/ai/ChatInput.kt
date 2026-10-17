package heizige.kk.khatkit.app.core.ui.components.ai

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.musicNote
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.mic
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khromia.helper.Toast
import heizige.kk.khromia.layout.FullscreenPopup
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.asr.ASRStatus
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.PageBackInterceptor
import heizige.kk.khatkit.app.core.data.datastore.BackgroundEffectType
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getSelectedASRProvider
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.core.data.datastore.getQuickMessagesOfAssistant
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.QuickMessage
import heizige.kk.khatkit.app.feature.chat.data.MessageQueueState
import heizige.kk.khatkit.app.feature.chat.data.QueuedMessage
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionContext
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionItem
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionList
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionProvider
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.components.ui.KeepScreenOn
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionRecordAudio
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalASRState
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.seconds
import heizige.kk.khatkit.app.feature.chat.VoicePhase
import heizige.kk.khatkit.app.feature.chat.VoiceSessionState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.ui.icons.videocam
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.arrowUpward
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.fullscreen
import heizige.kk.khatkit.app.core.ui.icons.title
import heizige.kk.khatkit.app.core.ui.icons.formatListBulleted
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.link
import heizige.kk.khatkit.app.core.ui.icons.preview
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import heizige.kk.kedge.components.KedgeHorizontalDivider
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeFloatingActionButton
import kotlin.math.abs
import kotlin.math.hypot

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
    onUpdateSearchRepository: (Int) -> Unit,
    onMoreClick: () -> Unit,
    attachmentActions: ChatAttachmentPickerActions? = null,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    /** 长按发送键但未配置语音识别模型时，点「去配置」跳到语音设置页。 */
    onConfigureVoice: () -> Unit = {},
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onSendQueuedMessageNow: (Uuid) -> Unit = {},
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
    // KodeHeap 帖子详情页 FLB 的落定曲线：tween + CubicBezierEasing(0.16, 0.82, 0.18, 1)，
    // 时长按距离在 760ms..90ms 之间缩放（不再是 spring(NoBouncy, StiffnessMediumLow)）。
    // 见文件末尾的 flbSettleSpec。
    var permissionSheetVisible by remember { mutableStateOf(false) }

    // —— FLB（浮动输入胶囊）实测窗口坐标 ——
    // genie 的 collapsedBounds 直接取输入胶囊的真实 bounds（对齐 KodeHeap Post.kt:3925
    // 的 onGloballyPositioned { it.boundsInRoot() }），不再硬编码 60×56dp 矩形。
    var flbBounds by remember { mutableStateOf(Rect.Zero) }
    // —— 全屏编辑器：同 window 的页内浮层，进度单一来源 ——
    var editorOpen by remember { mutableStateOf(false) }
    var editorReveal by remember { mutableFloatStateOf(0f) }
    val editorRevealDistancePx = with(LocalDensity.current) { 160.dp.toPx() }
    val editorJobRef = remember { object { var job: Job? = null } }

    fun settleEditor(open: Boolean) {
        editorJobRef.job?.cancel()
        editorJobRef.job = scope.launch {
            val target = if (open) 1f else 0f
            animate(
                initialValue = editorReveal,
                targetValue = target,
                animationSpec = flbSettleSpec(editorReveal, target),
            ) { value, _ -> editorReveal = value }
            editorReveal = target
            if (!open) {
                editorOpen = false
            }
        }
    }

    // 上滑拉起编辑器时输入胶囊那一行（ActionIconButton + TextInputRow）与右侧 FAB 淡出：
    // alpha = 1 - smoothstep(reveal / 0.25)，与编辑器工具栏的 smoothstep(reveal / 0.25) 严格互补
    // ⇒ 任一时刻屏幕底部只有一条底栏 >0.5 可见。reveal = 0（静止态）不挂任何额外节点，
    // 渲染与改动前逐帧一致。
    fun Modifier.flbFadeOnEditorReveal(): Modifier =
        if (editorReveal > 0.001f) {
            graphicsLayer { alpha = aiFlbCapsuleAlpha(editorReveal) }
        } else {
            this
        }

    // 返回兜底：编辑器是页内浮层，它的 PredictiveBackHandler 与 NavDisplay 注册在同一个
    // 页面 dispatcher 上（后注册者优先）。这里再登记一层，保证即便顺序假设不成立，
    // 返回也只会关掉编辑器，不会把 ChatPage pop 掉（见 RouteActivity.PageBackInterceptor）。
    val editorBackToken = remember { Any() }
    DisposableEffect(editorBackToken) {
        PageBackInterceptor.push(editorBackToken) {
            if (editorOpen) {
                settleEditor(open = false)
                true
            } else {
                false
            }
        }
        onDispose { PageBackInterceptor.pop(editorBackToken) }
    }

    // 预测返回：手风琴展开时返回手势跟手收起面板（KodeHead 同款）
    PredictiveBackHandler(enabled = progress.value > 0.01f && !editorOpen) { backEvents ->
        try {
            backEvents.collect { event ->
                progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            progress.animateTo(
                targetValue = 0f,
                animationSpec = flbSettleSpec(progress.value, 0f),
            )
        } catch (e: CancellationException) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = flbSettleSpec(progress.value, 1f),
            )
        }
    }

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
    val soundEffectPlayer: SoundEffectPlayer = rememberAppEntryPoint().soundEffectPlayer()
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

    val asrConfigureHint = stringResource(R.string.chat_page_voice_configure_asr)
    // 未配置语音识别模型的提示弹层（长按发送键时弹出）
    var asrSetupSheetVisible by remember { mutableStateOf(false) }
    val selectedAsrProvider = settings.getSelectedASRProvider()

    /**
     * 长按发送键 = 语音输入（语音转文字填进输入框）。
     * 没配语音识别模型（[ASRState.isAvailable] == false）时不进录音，只弹「去配置」提示。
     */
    fun startVoiceInput() {
        if (asrState.isRecording) return
        // 同时检查设置中的选中项和控制器状态：空白 API key / 本地模型路径会被视为未配置。
        if (selectedAsrProvider == null || !asrState.isAvailable) {
            asrSetupSheetVisible = true
            return
        }
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
            return
        }
        asrBaseText = state.textContent.text.toString()
        asr.start { transcript ->
            val spacer = if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
            state.setMessageText(asrBaseText + spacer + transcript)
        }
    }

    KedgeSurface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
                onSendNow = onSendQueuedMessageNow,
                generating = loading,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            KedgeSurface(
                modifier = Modifier
                    .weight(1f)
                    // 收起态「一次上滑」＝ 拉起编辑器（KodeHeap 帖子详情页 FLB 手势模型）。
                    // Initial pass 抢在按钮/文本框之前处理并 consume，保证在它们之上也能稳定拖动。
                    .pointerInput(editorRevealDistancePx) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var passedSlop = false
                            var vertical = false
                            var drivingEditor = false
                            var accordionDriving = false
                            var lastReveal = editorReveal.coerceIn(0f, 1f)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = change.positionChange()
                                    if (!passedSlop) {
                                        totalX += delta.x
                                        totalY += delta.y
                                        if (hypot(totalX, totalY) > touchSlop) {
                                            // 锁轴：竖向留给 FLB，横向全部丢弃
                                            vertical = abs(totalY) >= abs(totalX)
                                            if (vertical) totalX = 0f else totalY = 0f
                                            passedSlop = true
                                            editorJobRef.job?.cancel()
                                            editorJobRef.job = null
                                        }
                                    } else {
                                        if (vertical) totalY += delta.y else totalX += delta.x
                                    }

                                    if (passedSlop) {
                                        change.consume()
                                        if (vertical) {
                                            if (totalY < 0f || drivingEditor) {
                                                // 上滑：直接用 KodeHeap 的揭示进度映射
                                                // （160dp 线性到 1，之后 rubber-band 到 1.12）
                                                drivingEditor = true
                                                lastReveal = composerAiRevealProgressFromDrag(
                                                    rawY = totalY,
                                                    commitDistance = editorRevealDistancePx,
                                                )
                                                editorReveal = lastReveal.coerceAtLeast(0f)
                                                if (!editorOpen && lastReveal > 0.001f) {
                                                    editorOpen = true
                                                }
                                            } else if (panelHeightPx > 0 && progress.value > 0f) {
                                                // 手风琴已展开时下滑：跟手收起面板（保留原行为）
                                                accordionDriving = true
                                                val next = (progress.value - totalY / panelHeightPx)
                                                    .coerceIn(0f, 1f)
                                                scope.launch { progress.snapTo(next) }
                                            }
                                        }
                                    }

                                    if (!change.pressed) break
                                }
                            } catch (cancelled: CancellationException) {
                                if (passedSlop && drivingEditor) {
                                    settleEditor(
                                        composerAiRevealCommitOnRelease(lastReveal.coerceIn(0f, 1f))
                                    )
                                }
                                throw cancelled
                            }

                            if (!passedSlop) return@awaitEachGesture

                            if (drivingEditor) {
                                // 松手提交阈值 0.35（KodeHeap composerAiRevealCommitOnRelease）
                                settleEditor(
                                    composerAiRevealCommitOnRelease(lastReveal.coerceIn(0f, 1f))
                                )
                                return@awaitEachGesture
                            }
                            if (accordionDriving) {
                                val target = if (progress.value > 0.5f) 1f else 0f
                                val start = progress.value
                                scope.launch {
                                    progress.animateTo(
                                        targetValue = target,
                                        animationSpec = flbSettleSpec(start, target),
                                    )
                                }
                            }
                        }
                    }
                    .clip(containerShape)
                    .then(
                        if (easedProgress() > 0.05f && settings.displaySetting.enableBlurEffect) {
                            when (settings.displaySetting.backgroundEffectType) {
                                BackgroundEffectType.BLUR -> Modifier.hazeBlur(
                                    input = HazeInput.Sources(hazeState),
                                    style = HazeBlurStyle.Material3 { blurRadius(40.dp) },
                                )

                                BackgroundEffectType.GLASS -> Modifier.hazeGlass(
                                    input = HazeInput.Sources(hazeState),
                                    style = GlassStyle.Material3(
                                        containerColor = hazeTintColor,
                                        tint = hazeTintColor.copy(alpha = 0.3f),
                                    ) {
                                        optics(
                                            GlassDefaults.optics.copy(
                                                blurRadius = OpticalSizeValue.Fixed(16.dp),
                                                depth = OpticalSizeValue.Fixed(0.5f),
                                            )
                                        )
                                        shape(containerShape)
                                    },
                                )
                            }
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
                        KedgeHorizontalDivider(
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
                                PanelAction(verifiedUser, "权限") { permissionSheetVisible = true }
                            }
                        }
                    }


                    KedgeSurface(
                        shape = RoundedCornerShape(innerRadiusDp.dp),
                        color = hazeTintColor,
                        modifier = Modifier
                            .height(56.dp)
                            .onSizeChanged { inputRowSize = it }
                            // FLB 实测 bounds（窗口坐标）：编辑器 genie 的 collapsedBounds 来源
                            .onGloballyPositioned { coords ->
                                val topLeft = coords.localToWindow(Offset.Zero)
                                flbBounds = Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coords.size.width,
                                    bottom = topLeft.y + coords.size.height,
                                )
                            }
                            // 上滑拉起编辑器时胶囊淡出（与编辑器工具栏 alpha 互补，见 aiFlbCapsuleAlpha）
                            .flbFadeOnEditorReveal(),
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
                                val start = progress.value
                                val target = if (start > 0.5f) 0f else 1f
                                scope.launch {
                                    progress.animateTo(
                                        targetValue = target,
                                        animationSpec = flbSettleSpec(start, target),
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
                            isFullScreen = editorOpen,
                            editorRevealProgress = editorReveal,
                            onEditorRevealProgress = { editorReveal = it },
                            flbBounds = flbBounds,
                            attachmentActions = attachmentActions,
                            onFullScreenChange = { open ->
                                if (open) {
                                    editorOpen = true
                                    editorJobRef.job?.cancel()
                                    editorJobRef.job = scope.launch {
                                        animate(
                                            initialValue = editorReveal,
                                            targetValue = 1f,
                                            animationSpec = flbSettleSpec(editorReveal, 1f),
                                        ) { value, _ -> editorReveal = value }
                                        editorReveal = 1f
                                    }
                                } else {
                                    settleEditor(open = false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )



                        if (loading) {
                            KeepScreenOn()
                        }

                    }
                    }
                }
            }

            // 发送 FAB：图标恒为「发送」（不再按输入框空/满、键盘显隐切成麦克风），
            // 长按 = 语音输入，录音中变成「停止」。
            val fabState = if (asrState.isRecording) FabState.Recording else FabState.Send
            Spacer(Modifier.width(((1f - easedProgress()) * 8f).dp))
            Box(
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val progress = easedProgress()
                    layout((placeable.width * (1f - progress)).toInt().coerceAtLeast(0), placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                    // 右侧 FAB 与胶囊同一曲线淡出（见 flbFadeOnEditorReveal 注释）
                    .flbFadeOnEditorReveal()
            ) {
            KedgeFloatingActionButton(
                onClick = {
                    when (fabState) {
                        FabState.Recording -> asr.stop()
                        FabState.Send -> sendMessage()
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val progress = easedProgress()
                        translationX = progress * (size.width + 12.dp.toPx())
                        scaleX = 1f - 0.2f * progress
                        scaleY = 1f - 0.2f * progress
                        alpha = 1f - progress
                    }
                    // KedgeFloatingActionButton 内部（MD3 与 Miuix 都是）只有 clickable，
                    // 没有 onLongClick 入口，所以长按在 Initial pass 上自行判定：
                    // 事件只观察不消费 ⇒ 短按仍走按钮自己的 onClick。
                    .longPressOnly { startVoiceInput() },
                containerColor = when (fabState) {
                    FabState.Recording -> KedgeColors.errorContainer
                    FabState.Send -> KedgeColors.primary
                },
                contentColor = when (fabState) {
                    FabState.Recording -> KedgeColors.onErrorContainer
                    FabState.Send -> KedgeColors.onPrimary
                },
            ) {
                AnimatedContent(
                    targetState = fabState,
                    transitionSpec = {
                        (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut())
                    },
                    label = "fabState",
                ) { fab ->
                    Icon(
                        imageVector = when (fab) {
                            FabState.Recording -> close
                            FabState.Send -> arrowUpward
                        },
                        contentDescription = when (fab) {
                            FabState.Recording -> stringResource(R.string.asr_button_stop)
                            FabState.Send -> stringResource(R.string.send)
                        },
                    )
                }
            }
            }
            }

        }
    }

    // 长按发送键但没配语音识别模型：只提示去配置，不进录音
    if (asrSetupSheetVisible) {
        PrimaryBottomSheet(
            visible = true,
            title = asrConfigureHint,
            imageVector = mic,
            confirmText = stringResource(R.string.setting_page_config),
            onConfirm = {
                asrSetupSheetVisible = false
                onConfigureVoice()
            },
            onDismiss = { asrSetupSheetVisible = false },
        ) { _ -> }
    }

    if (permissionSheetVisible) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.greeting_title_permissions),
            imageVector = verifiedUser,
            onDismiss = { permissionSheetVisible = false },
            scrollable = true,
        ) { _ ->
            PermissionChecklist(compact = true)
        }
    }


}

/**
 * 「只加长按」的手势：按下超过系统长按时长触发 [onLongPress]，没到时长就抬手则完全不干预。
 *
 * 用在 [KedgeFloatingActionButton] 上（MD3 的 FAB 与 Miuix 的 FAB 内部都只有 clickable，
 * 没有 onLongClick 入口）：事件在 Initial pass 上只观察不消费 ⇒ 按钮自己的 clickable 照常
 * 收到事件、短按该触发什么就触发什么；一旦判定成长按，再把后续事件消费掉，抑制抬手时
 * 按钮补发的 onClick（否则长按会「开始录音 + 顺手把消息发出去」）。
 */
private fun Modifier.longPressOnly(onLongPress: () -> Unit): Modifier = this.pointerInput(onLongPress) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
            }
        }
        if (releasedEarly == null) {
            onLongPress()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
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
        KedgeSurface(
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
    KedgeSurface(
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
    editorRevealProgress: Float = 0f,
    onEditorRevealProgress: (Float) -> Unit = {},
    flbBounds: Rect = Rect.Zero,
    attachmentActions: ChatAttachmentPickerActions? = null,
    onFullScreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val assistant = settings.getCurrentAssistant()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            KedgeSurface(
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
                    KedgeIconButton(
                        onClick = {
                            onFullScreenChange(!isFullScreen)
                        },
                             shapes = IconButtonDefaults.shapes(),) {
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
            FullScreenEditor(
                state = state,
                flbBounds = flbBounds,
                revealProgress = editorRevealProgress,
                onRevealProgress = onEditorRevealProgress,
                attachmentActions = attachmentActions,
                onSend = { onSendMessage(); onFullScreenChange(false) },
                onDone = { onFullScreenChange(false) },
            )
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
    multiline: Boolean = false,
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
            // 不传 textStyle 时 Miuix 用 textStyles.main，与抽屉里「新建文件夹」
            // 等按钮字号不一致；这里显式给 bodyLarge()，两种风格同一套字号。
            textStyle = KedgeTextStyles.bodyLarge(),
            colors = MiuixTextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                // borderColor 同时驱动聚焦描边与「光标默认 brush」：
                // Miuix 的 cursorBrush 默认值是 SolidColor(colors.borderColor)（miuix-ui
                // basic/TextField.kt:101），所以这里必须显式给 cursorBrush，否则光标解析成
                // Transparent，聚焦后字面不可见（#3 的根因）。
                borderColor = Color.Transparent,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            cornerRadius = 16.dp,
            label = placeholder,
            useLabelAsPlaceholder = true,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = { keyboardAction() },
            lineLimits = if (multiline) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
            leadingIcon = leadingContent,
            trailingIcon = trailingContent,
        )
    } else {
        TextField(
            state = state.textContent,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            textStyle = KedgeTextStyles.bodyLarge(),
            placeholder = {
                Text(placeholder)
            },
            lineLimits = if (multiline) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
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
    KedgeSurface(
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
                KedgeSurface(
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
                                style = KedgeTextStyles.body(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = KedgeTextStyles.footnoteSmall(),
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
    KedgeIconButton(
        onClick = {
            expanded = !expanded
        },
             shapes = IconButtonDefaults.shapes(),) {
        Icon(bolt, null)
        KedgeDropdownMenuSlots(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                KedgeSurface(
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
                            style = KedgeTextStyles.title(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
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

@Composable
private fun FullScreenEditor(
    state: ChatInputState,
    flbBounds: Rect = Rect.Zero,
    revealProgress: Float = 0f,
    onRevealProgress: (Float) -> Unit = {},
    attachmentActions: ChatAttachmentPickerActions? = null,
    onSend: () -> Unit,
    onDone: () -> Unit,
) {
    val reveal = revealProgress.coerceIn(0f, 1f)
    // 屏幕物理圆角（末段 morph 的终点；取不到时为 0，与 KodeHeap 默认值一致）
    val screenCornerRadiusPx = rememberScreenCornerRadiusPx()
    val windowMorphProgress = aiWindowMorphProgress(reveal)
    val windowBorderWidth = 2.dp * (1f - windowMorphProgress)
    val windowSurfaceAlpha = 0.94f - 0.16f * reveal
    val density = LocalDensity.current
    // 系统栏 / IME 内边距直接在**父组合**里取值再传进浮层，而不是在浮层子组合里用
    // safeDrawingPadding()/imePadding()：后者依赖「挂到 decorView 的浮层 view 也会被派发到
    // IME insets」这个隐式前提。父组合的 insets holder 就是输入框自己用的那个
    // （ChatInput 的 Column 已经 .imePadding()），取值必然有效。
    val safeDrawingInsets = WindowInsets.safeDrawing
    val imeInsets = WindowInsets.ime
    val popupPadTop = with(density) { safeDrawingInsets.getTop(density).toDp() }
    val layoutDirection = LocalLayoutDirection.current
    val popupPadStart = with(density) {
        safeDrawingInsets.getLeft(density, layoutDirection).toDp()
    }
    val popupPadEnd = with(density) {
        safeDrawingInsets.getRight(density, layoutDirection).toDp()
    }
    val popupPadBottom = with(density) {
        maxOf(safeDrawingInsets.getBottom(density), imeInsets.getBottom(density)).toDp()
    }

    // 单一预测性返回实现（原 BackHandler + PredictiveBackHandler 合并为一条）：
    // 编辑器现在是**同一 window 内的页内浮层**，这里的 handler 与 NavDisplay 注册在同一个
    // 页面 dispatcher 上、且注册更晚 ⇒ 返回先跟手收起编辑器，不会 pop 掉 ChatPage。
    PredictiveBackHandler(enabled = reveal > 0.01f) { events ->
        try {
            events.collect { event ->
                onRevealProgress((1f - event.progress).coerceIn(0f, 1f))
            }
            val from = reveal
            animate(from, 0f, animationSpec = flbSettleSpec(from, 0f)) { value, _ ->
                onRevealProgress(value)
            }
            onRevealProgress(0f)
            onDone()
        } catch (_: CancellationException) {
            val from = reveal
            animate(from, 1f, animationSpec = flbSettleSpec(from, 1f)) { value, _ ->
                onRevealProgress(value)
            }
            onRevealProgress(1f)
        }
    }

    val richState = remember { RichTextState() }
    LaunchedEffect(Unit) {
        richState.setMarkdown(state.textContent.text.toString())
    }
    fun commitRichText() {
        state.setMessageText(richState.toMarkdown())
    }
    val editorLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    // —— 底部工具栏 morph 的两端（都在**窗口坐标**里量，与 flbBounds 同一坐标系）——
    // toolbarLayerBounds：浮层里工具栏所在的可绘制区域（浮层去掉系统栏/IME 内边距后的矩形）
    // toolbarHeightPx：工具栏自身高度（onSizeChanged 实测，只影响绘制、不影响布局，无测量回环）
    var toolbarLayerBounds by remember { mutableStateOf(Rect.Zero) }
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    // 页内浮层：MATCH_PARENT 的 AbstractComposeView 直接挂 Activity decorView（同一 window、
    // 无独立 dialog window），内容以子组合继承 CompositionLocals。
    FullscreenPopup(modifier = Modifier.fillMaxSize()) {
        // 浮层 view 在窗口里的原点：FLB 的窗口坐标要减掉它才是浮层内的绘制坐标。
        // 浮层子组合里 LocalView 就是挂在 decorView 上的那个 AbstractComposeView。
        val popupView = LocalView.current
        var popupOrigin by remember(popupView) { mutableStateOf(Offset.Zero) }
        LaunchedEffect(popupView) {
            // 等这次 addView 的布局跑完再读，避免拿到 (0,0) 的初始值
            popupView.post {
                val location = IntArray(2)
                popupView.getLocationInWindow(location)
                popupOrigin = Offset(location[0].toFloat(), location[1].toFloat())
            }
        }
        // genie 的起点 = FLB 的实测 bounds（窗口坐标）换算到浮层本地坐标；
        // 揭示目标是整窗，圆角从 FLB 的 CircleShape 半径 morph 到屏幕圆角
        // （KodeHeap Post.kt:2849-2863）。
        val collapsedBounds = flbBounds.translate(-popupOrigin.x, -popupOrigin.y)
        val windowCornerRadiusPx = with(density) {
            aiWindowCornerRadiusPx(
                toolbarWidthPx = collapsedBounds.width,
                toolbarHeightPx = collapsedBounds.height,
                revealProgress = reveal,
                screenCornerRadiusPx = screenCornerRadiusPx,
            )
        }
        // FLB 尚未测量到（空 Rect）时退化成普通圆角矩形：`aiChatGenieEffect` 对空
        // collapsedBounds 会直接返回、连内容都不画，不能把空矩形喂给它。
        val flbMeasured = collapsedBounds.width > 0f && collapsedBounds.height > 0f
        val windowShape: androidx.compose.ui.graphics.Shape = if (flbMeasured) {
            aiChatGenieWindowShape(
                progress = reveal,
                collapsedBounds = collapsedBounds,
                destinationCornerRadiusPx = windowCornerRadiusPx,
            )
        } else {
            RoundedCornerShape(with(density) { screenCornerRadiusPx.toDp() })
        }
        // 工具栏 morph：起点 = FLB（输入胶囊）窗口坐标，终点 = 工具栏在浮层里的最终布局矩形。
        // 两端同一个坐标系 ⇒ 差值就是 graphicsLayer 需要的平移/缩放增量（与坐标系无关）。
        val toolbarTargetBounds = Rect(
            left = toolbarLayerBounds.left,
            top = toolbarLayerBounds.bottom - toolbarHeightPx,
            right = toolbarLayerBounds.right,
            bottom = toolbarLayerBounds.bottom,
        )
        val toolbarTargetCornerRadiusPx = with(density) { 18.dp.toPx() }
        val toolbarMorph = aiFlbToolbarMorph(
            revealProgress = reveal,
            collapsedBounds = flbBounds,
            targetBounds = toolbarTargetBounds,
            // 胶囊是 pill：圆角 = 较短边一半
            collapsedCornerRadiusPx = minOf(flbBounds.width, flbBounds.height) / 2f,
            targetCornerRadiusPx = toolbarTargetCornerRadiusPx,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 揭示窗口只做 genie 形变 + 圆角/透明/边框 morph；
                // 不再叠加 alpha / translationY / scale 三重额外变换（KodeHeap Post.kt:2844-2911）。
                .then(
                    if (flbMeasured) {
                        Modifier.aiChatGenieEffect(
                            progress = reveal,
                            collapsedBounds = collapsedBounds,
                            contentLayer = editorLayer,
                            maxBandCount = 36,
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(windowShape)
                .background(
                    // surface alpha 0.94 → 0.78（KodeHeap Post.kt:2864 同式）
                    KedgeColors.surface.copy(alpha = windowSurfaceAlpha)
                )
                .then(
                    if (windowBorderWidth > 0.001.dp) {
                        Modifier.border(
                            width = windowBorderWidth,
                            color = KedgeColors.outline,
                            shape = windowShape,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = popupPadStart,
                        top = popupPadTop,
                        end = popupPadEnd,
                        bottom = popupPadBottom,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .padding(bottom = 64.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (previewMode) {
                            MarkdownBlock(
                                content = richState.toMarkdown(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            BasicRichTextEditor(
                                state = richState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                                textStyle = KedgeTextStyles.bodyLarge(),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = false,
                            )
                        }
                    }
                }
            }
        }

        // —— 底部工具栏图层（窗口图层的兄弟层）——
        // 必须画在窗口图层（genie 形变 + clip(windowShape)）**之外**：窗口形状在揭示中段
        // 仍然只有胶囊大小，留在窗口图层里会被裁成胶囊、看不到 morph。本层只加「几何 morph +
        // 淡入」两个 graphicsLayer 参数，其余 modifier 链 / 颜色 / 圆角 / 内边距 / 子项顺序与
        // 回调全部保持原样；reveal = 1 时 graphicsLayer 是恒等变换、圆角 = 18dp。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = popupPadStart,
                    top = popupPadTop,
                    end = popupPadEnd,
                    bottom = popupPadBottom,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val topLeft = coords.localToWindow(Offset.Zero)
                        toolbarLayerBounds = Rect(
                            left = topLeft.x,
                            top = topLeft.y,
                            right = topLeft.x + coords.size.width,
                            bottom = topLeft.y + coords.size.height,
                        )
                    },
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { toolbarHeightPx = it.height }
                        // 几何 morph：transformOrigin 取左上角 ⇒ scale + translate 精确映射两个
                        // 矩形的四角；reveal = 1 时 morph 矩形 == 最终工具栏矩形 ⇒ 恒等变换。
                        .graphicsLayer {
                            alpha = aiFlbToolbarAlpha(reveal)
                            if (toolbarTargetBounds.width > 0f && toolbarTargetBounds.height > 0f) {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = toolbarMorph.width / toolbarTargetBounds.width
                                scaleY = toolbarMorph.height / toolbarTargetBounds.height
                                translationX = toolbarMorph.left - toolbarTargetBounds.left
                                translationY = toolbarMorph.top - toolbarTargetBounds.top
                            }
                        }
                        .background(
                            KedgeColors.surfaceContainerHigh.copy(alpha = toolbarMorph.containerAlpha),
                            // reveal = 1 时 cornerRadiusPx == 18.dp.toPx()，与改动前的
                            // RoundedCornerShape(18.dp) 像素级相同
                            RoundedCornerShape(toolbarMorph.cornerRadiusPx),
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Toolbar is anchored to the editor bottom, like KodeHeap's FLB.
                    RichMarkdownButton(richState, title, "标题") { "# " to "" }
                    RichStyleButton(richState, "B", "粗体", SpanStyle(fontWeight = FontWeight.Bold))
                    RichStyleButton(richState, "I", "斜体", SpanStyle(fontStyle = FontStyle.Italic))
                    RichMarkdownButton(richState, formatListBulleted, "列表") { "- " to "" }
                    RichMarkdownButton(richState, code, "代码") { "`" to "`" }
                    RichMarkdownButton(richState, link, "链接") { "[" to "](url)" }
                    KedgeIconButton(
                        onClick = { previewMode = !previewMode },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = if (previewMode) edit else preview,
                            contentDescription = if (previewMode) "编辑" else "预览",
                        )
                    }
                    attachmentActions?.let { actions ->
                        KedgeIconButton(
                            onClick = actions.onPickImage,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(image, contentDescription = "图片") }
                        KedgeIconButton(
                            onClick = actions.onPickVideo,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(videocam, contentDescription = "视频") }
                        KedgeIconButton(
                            onClick = actions.onPickFile,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(insertDriveFile, contentDescription = "文件") }
                    }
                    Spacer(Modifier.weight(1f))
                    KedgeTextButton(
                        onClick = {
                            commitRichText()
                            onDone()
                        },
                             shapes = ButtonDefaults.shapes(),) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                    KedgeIconButton(
                        onClick = {
                            commitRichText()
                            onSend()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(arrowUpward, contentDescription = stringResource(R.string.send))
                    }
                    KedgeIconButton(
                        onClick = onDone,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichMarkdownButton(
    state: RichTextState,
    icon: ImageVector,
    label: String,
    wrapper: () -> Pair<String, String>,
) {
    KedgeIconButton(
        onClick = {
            val (prefix, suffix) = wrapper()
            val selected = state.toMarkdown(state.selection)
            state.insertMarkdownAfterSelection(prefix + selected + suffix)
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun RichStyleButton(
    state: RichTextState,
    glyph: String,
    label: String,
    style: SpanStyle,
) {
    KedgeIconButton(
        onClick = { state.toggleSpanStyle(style) },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Text(
            text = glyph,
            fontWeight = if (glyph == "B") FontWeight.Bold else null,
            fontStyle = if (glyph == "I") FontStyle.Italic else null,
        )
    }
}

@Composable
private fun MarkdownEditButton(
    state: ChatInputState,
    icon: ImageVector,
    label: String,
    wrapper: () -> Pair<String, String>,
) {
    KedgeIconButton(
        onClick = {
            val (prefix, suffix) = wrapper()
            val selectedRange = state.textContent.selection
            val text = state.textContent.text.toString()
            val start = selectedRange.min.coerceIn(0, text.length)
            val end = selectedRange.max.coerceIn(start, text.length)
            val selected = text.substring(start, end)
            state.textContent.edit {
                replace(start, end, prefix + selected + suffix)
                selection = TextRange(start + prefix.length, start + prefix.length + selected.length)
            }
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun MarkdownEditTextButton(
    state: ChatInputState,
    glyph: String,
    label: String,
    wrapper: () -> Pair<String, String>,
) {
    KedgeIconButton(
        onClick = {
            val (prefix, suffix) = wrapper()
            val selectedRange = state.textContent.selection
            val text = state.textContent.text.toString()
            val start = selectedRange.min.coerceIn(0, text.length)
            val end = selectedRange.max.coerceIn(start, text.length)
            val selected = text.substring(start, end)
            state.textContent.edit {
                replace(start, end, prefix + selected + suffix)
                selection = TextRange(start + prefix.length, start + prefix.length + selected.length)
            }
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Text(
            text = glyph,
            modifier = Modifier,
            fontWeight = if (glyph == "B") androidx.compose.ui.text.font.FontWeight.Bold else null,
            fontStyle = if (glyph == "I") androidx.compose.ui.text.font.FontStyle.Italic else null,
        )
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
            style = KedgeTextStyles.footnoteSmall(),
            color = KedgeColors.onSurfaceVariant,
        )
    }
}

private enum class FabState { Send, Recording }

/**
 * FLB（手风琴面板 + 全屏编辑器）共用的落定曲线：
 * `tween(aiWindowSettleDurationMillis(start, target), CubicBezierEasing(0.16, 0.82, 0.18, 1))`。
 * 满行程 760ms、按距离缩放、下限 90ms（KodeHeap Post.kt:2042-2048）。
 */
private fun flbSettleSpec(start: Float, target: Float) = tween<Float>(
    durationMillis = aiWindowSettleDurationMillis(start, target),
    easing = CubicBezierEasing(0.16f, 0.82f, 0.18f, 1f),
)

/**
 * 屏幕物理圆角（px）。取不到就返回 0，等价于 KodeHeap `AndroidHelper().getCornerRadius()`
 * 的默认值（Post.kt:2035）。只在 API 31+ 且窗口 insets 带圆角时有值。
 */
@Composable
private fun rememberScreenCornerRadiusPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    var cornerRadiusPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            val radius = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
                ?: insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            if (radius != null && radius > 0) {
                cornerRadiusPx = with(density) { radius.toDp().toPx() }
            }
        }
    }
    return cornerRadiusPx
}

