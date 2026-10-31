package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiDialog
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import ui.feice.mochi.theme.MochiTheme

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
import androidx.compose.ui.graphics.compositeOver
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
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
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.asr.ASRStatus
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.LocalPageRoute
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
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint



import kotlin.time.Duration.Companion.seconds
import heizige.kk.khatkit.app.feature.chat.VoicePhase
import heizige.kk.khatkit.app.feature.chat.VoiceSessionState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.ui.icons.videocam
import heizige.kk.khatkit.app.core.ui.icons.tune
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
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import ui.feice.mochi.basic.MochiIconButton as MochiRealIconButton
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.flow.first
import ui.feice.mochi.basic.MochiButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import ui.feice.mochi.anim.MochiMotions
import heizige.kk.khatkit.uikit.mochi.kkPressState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.clipPath
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
    val hazeTintColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val composerFieldColor = MochiTextFieldDefaults.colors().container.compositeOver(CustomColors.pageContainerColor)
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
    val editorFlingVelocityPx = with(LocalDensity.current) { 600.dp.toPx() }
    val editorJobRef = remember { object { var job: Job? = null } }

    // velocity：松手时的揭示速度（进度/秒），弹簧从手指的速度接着走，松手那一帧不顿
    fun settleEditor(open: Boolean, velocity: Float = 0f) {
        editorJobRef.job?.cancel()
        editorJobRef.job = scope.launch {
            val target = if (open) 1f else 0f
            animate(
                initialValue = editorReveal,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = EditorRevealSpring,
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
    val pageRoute = LocalPageRoute.current
    // U7a-6：只在编辑器真正打开时登记。之前无条件 push（handler 在关闭态返回 false）会让
    // PageBackInterceptor.hasHandlers 在 Chat 页常驻 true，RouteActivity.PageBackGuard 便一直
    // 抢在 MochiNavHost 的 PredictiveBackHandler 前面，预测性返回动画整体消失。
    DisposableEffect(editorOpen) {
        if (editorOpen) {
            PageBackInterceptor.push(editorBackToken, pageRoute) {
                settleEditor(open = false)
                true
            }
        } else {
            PageBackInterceptor.pop(editorBackToken)
        }
        onDispose { PageBackInterceptor.pop(editorBackToken) }
    }

    // 预测返回：手风琴展开时返回手势跟手收起面板（KodeHead 同款）
    // NAV3：抬高启用阈值，避免面板收起尾帧还挂着 handler 多吃一次返回。
    PredictiveBackHandler(enabled = progress.value > 0.02f && !editorOpen) { backEvents ->
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
    val collapsedOuterRadiusPx = with(densityLocal) { (MochiTextFieldDefaults.Height / 2).toPx() } + gapPx
    // 展开后也保持和输入框一圈同心的大圆角，不做收角
    val outerRadiusPx = collapsedOuterRadiusPx
    val outerRadiusDp = with(densityLocal) { outerRadiusPx.toDp().value }
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
    val haptics = rememberKkHaptics()
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
                haptics.longPress()
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                haptics.confirm()
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

    // P1（上游 97060dd51）：底部渐变遮罩 —— 从输入区顶部过渡到屏幕底部（键盘弹出时到键盘上沿），
    // 避免消息透到系统导航栏。
    val scrimColor = MaterialTheme.colorScheme.background
    val imeInsets = WindowInsets.ime
    KkSurface(
        color = Color.Transparent,
        modifier = Modifier.drawWithCache {
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(brush) }
        },
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
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
            KkSurface(
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
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            // 松手：快甩按方向决定开合，慢放按距离阈值；弹簧接上手指的速度
                            fun releaseEditor() {
                                val velocityY = velocityTracker.calculateVelocity().y
                                val open = when {
                                    velocityY < -editorFlingVelocityPx -> true
                                    velocityY > editorFlingVelocityPx -> false
                                    else -> composerAiRevealCommitOnRelease(lastReveal.coerceIn(0f, 1f))
                                }
                                settleEditor(open, velocity = -velocityY / editorRevealDistancePx)
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
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
                                if (passedSlop && drivingEditor) releaseEditor()
                                throw cancelled
                            }

                            if (!passedSlop) return@awaitEachGesture

                            if (drivingEditor) {
                                releaseEditor()
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
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
                        MochiHorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                            // 能力 chip 行：联网搜索 + 推理（模型支持时）
                            val chatModel = settings.getCurrentChatModel()
                            val showReasoning = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                            val collapsePanel: () -> Unit = {
                                val start = progress.value
                                scope.launch {
                                    progress.animateTo(targetValue = 0f, animationSpec = flbSettleSpec(start, 0f))
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .panelEntrance({ easedProgress() }, 0)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SearchPickerButton(
                                    enableSearch = enableSearch,
                                    settings = settings,
                                    onUpdateSearchMode = onUpdateSearchMode,
                                    onUpdateSearchRepository = onUpdateSearchRepository,
                                    model = chatModel,
                                    chip = true,
                                )
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

                            // 动作行：Mochi 的 Tonal 圆按钮 + 一行小字，单色、不加底板；附件 / 语音点完顺手收起面板
                            val tiles = buildList {
                                attachmentActions?.let { actions ->
                                    add(PanelTile(photoCamera, "相机") { collapsePanel(); actions.onTakePicture() })
                                    add(PanelTile(image, "照片") { collapsePanel(); actions.onPickImage() })
                                    add(PanelTile(insertDriveFile, "文件") { collapsePanel(); actions.onPickFile() })
                                }
                                onStartVoiceMode?.let { start ->
                                    add(PanelTile(mic, "语音") { collapsePanel(); start() })
                                }
                                add(PanelTile(tune, "上下文") { collapsePanel(); onMoreClick() })
                                add(PanelTile(verifiedUser, "权限") { permissionSheetVisible = true })
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                tiles.forEachIndexed { index, tile ->
                                    PanelAction(
                                        tile = tile,
                                        modifier = Modifier.panelEntrance({ easedProgress() }, 1 + index),
                                    )
                                }
                            }
                        }
                    }


                    // 照 Mochi 的输入栏（demo 的 DemoComposerRow）：＋ 圆按钮 + 胶囊输入框，发送键是胶囊里同心的实心圆，
                    // 空 / 有字 / 停止之间模糊替换。胶囊压在消息上面，底色取 Mochi 输入框的填充叠在页面底色上（不透字）。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            // 上滑拉起编辑器时输入栏淡出（与编辑器工具栏 alpha 互补，见 aiFlbCapsuleAlpha）
                            .flbFadeOnEditorReveal(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiRealIconButton(
                            onClick = {
                                val start = progress.value
                                val target = if (start > 0.5f) 0f else 1f
                                scope.launch {
                                    progress.animateTo(
                                        targetValue = target,
                                        animationSpec = flbSettleSpec(start, target),
                                    )
                                }
                            },
                            // 与顶栏按钮同一套：设置里选玻璃就画玻璃，选实心时库退回 Tonal
                            style = MochiButtonStyle.Glass,
                        ) {
                            Icon(
                                imageVector = add,
                                contentDescription = stringResource(R.string.more_options),
                                tint = MochiTheme.colorScheme.onSurface,
                                // 展开时 + 旋转 135° 变成 ×
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(135f * easedProgress()),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = MochiTextFieldDefaults.Height)
                                .background(composerFieldColor, MochiTextFieldDefaults.Shape),
                            verticalAlignment = Alignment.Bottom,
                        ) {
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
                                                animationSpec = EditorRevealSpring,
                                            ) { value, _ -> editorReveal = value }
                                            editorReveal = 1f
                                        }
                                    } else {
                                        settleEditor(open = false)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SendButton(
                                state = when {
                                    asrState.isRecording -> SendButtonState.Recording
                                    loading && state.isEmpty() -> SendButtonState.Stop
                                    state.isEmpty() -> SendButtonState.Idle
                                    else -> SendButtonState.Send
                                },
                                onClick = {
                                    if (asrState.isRecording) asr.stop() else sendMessage()
                                },
                                onLongClick = { startVoiceInput() },
                                modifier = Modifier.padding(MochiTextFieldDefaults.TrailingInset),
                            )
                        }

                        if (loading) {
                            KeepScreenOn()
                        }
                    }
                }
            }

            }

        }
    }

    // 长按发送键但没配语音识别模型：只提示去配置，不进录音
    // U7b-2c：去 if 门控，显隐交给 MochiSheet 内部状态（收起有动画，内容只读布尔不读空值）。
    // U7b-5：纯提示 + 一个跳转动作，改成 MochiDialog（不再占半屏 sheet）。
    MochiDialog(
        show = asrSetupSheetVisible,
        onDismissRequest = { asrSetupSheetVisible = false },
        title = asrConfigureHint,
    ) {
        MochiTextButton(
            onClick = {
                asrSetupSheetVisible = false
                onConfigureVoice()
            },
        ) {
            Text(stringResource(R.string.setting_page_config))
        }
    }

    PrimaryBottomSheet(
            visible = permissionSheetVisible,
            title = stringResource(R.string.greeting_title_permissions),
            imageVector = verifiedUser,
            onDismiss = { permissionSheetVisible = false },
            scrollable = true,
        ) { _ ->
            PermissionChecklist(compact = true)
        }

}

/**
 * 「只加长按」的手势：按下超过系统长按时长触发 [onLongPress]，没到时长就抬手则完全不干预。
 *
 * 用在 [KkFloatingActionButton] 上（MD3 的 FAB 与 Miuix 的 FAB 内部都只有 clickable，
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

private enum class SendButtonState { Idle, Send, Stop, Recording }

/**
 * 胶囊里的发送键，照 Mochi 输入栏：能发时是主题色实心圆 + 上箭头；空的时候是一个淡色上箭头
 * （KhatKit 长按发送键 = 语音输入，所以空态不换成麦克风）；生成中且没字 = 停止，录音中 = 停止录音。
 * 状态之间用 [MochiBlurCrossfade] 模糊替换。
 */
@Composable
private fun SendButton(
    state: SendButtonState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    Box(
        modifier = modifier
            .size(MochiTextFieldDefaults.TrailingButtonSize)
            .testTag("chat_send_button")
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MochiBlurCrossfade(state) { s ->
            val container = when (s) {
                SendButtonState.Idle -> Color.Transparent
                SendButtonState.Send -> cs.primary
                SendButtonState.Stop, SendButtonState.Recording -> cs.onSurface
            }
            val content = when (s) {
                SendButtonState.Idle -> cs.onSurfaceVariant
                SendButtonState.Send -> cs.onPrimary
                SendButtonState.Stop, SendButtonState.Recording -> cs.surface
            }
            Box(
                Modifier
                    .size(MochiTextFieldDefaults.TrailingButtonSize)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (s == SendButtonState.Stop || s == SendButtonState.Recording) close else arrowUpward,
                    contentDescription = stringResource(
                        when (s) {
                            SendButtonState.Stop -> R.string.stop
                            SendButtonState.Recording -> R.string.asr_button_stop
                            else -> R.string.send
                        },
                    ),
                    tint = content,
                    modifier = Modifier.size(if (s == SendButtonState.Idle) 22.dp else 18.dp),
                )
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
            KkSurface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    MochiRealIconButton(
                        onClick = {
                            onFullScreenChange(!isFullScreen)
                        },
                        size = MochiTextFieldDefaults.TrailingButtonSize,
                        style = MochiButtonStyle.Text,
                    ) {
                        Icon(fullscreen, null, tint = MochiTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
    if (!multiline) {
        // 输入栏里的那一个：照 MochiTextField 的度量（单行 48dp 高、文字离边 16dp、正文字阶、主题色光标），
        // 胶囊底色由外层画；不发送即换行时随内容长高，最多 5 行。
        val cs = MochiTheme.colorScheme
        BasicTextField(
            state = state.textContent,
            modifier = modifier,
            textStyle = MochiTheme.textStyles.body.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            lineLimits = if (sendOnEnter) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(1, 5),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = { keyboardAction() },
            decorator = { innerTextField ->
                Row(
                    modifier = Modifier.heightIn(min = MochiTextFieldDefaults.Height),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingContent?.invoke()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (leadingContent == null) MochiTextFieldDefaults.HorizontalPadding else 0.dp,
                                end = 4.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.textContent.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MochiTheme.textStyles.body,
                                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
            },
        )
        return
    }
        TextField(
        state = state.textContent,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
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

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    KkSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                KkSurface(
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
                                tint = MaterialTheme.colorScheme.primary,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    MochiIconButton(
        onClick = {
            expanded = !expanded
        },
             shapes = IconButtonDefaults.shapes(),) {
        Icon(bolt, null)
        // U6d 保留 MochiMenuPopup（第 1/3 处）：菜单行是富内容卡片（KkSurface + 标题/正文/图标 + 每行独立点击区），
        // MochiItem 只有 label/icon/trailing 三个槽，表达不了这种整行自绘卡片；且这里要固定宽度 200–360dp。
        MochiMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                KkSurface(
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
                            style = MaterialTheme.typography.bodyMedium,
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
    // 系统栏 / IME 内边距在父组合里取值再传进浮层：父组合的 insets holder 就是输入框自己用的那个
    // （ChatInput 的 Column 已经 .imePadding()），取值必然有效，键盘升起时跟着 IME 动画逐帧走。
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

    // 单一预测性返回：编辑器是同一窗口里的浮层，handler 与 NavDisplay 注册在同一个 dispatcher 上、
    // 且注册更晚 ⇒ 返回先跟手收起编辑器，不会 pop 掉 ChatPage。只在 reveal 稳定 > 0.02 时接管。
    PredictiveBackHandler(enabled = reveal > 0.02f) { events ->
        try {
            events.collect { event ->
                onRevealProgress((1f - event.progress).coerceIn(0f, 1f))
            }
            animate(reveal, 0f, animationSpec = EditorRevealSpring) { value, _ ->
                onRevealProgress(value)
            }
            onRevealProgress(0f)
            onDone()
        } catch (_: CancellationException) {
            animate(reveal, 1f, animationSpec = EditorRevealSpring) { value, _ ->
                onRevealProgress(value)
            }
            onRevealProgress(1f)
        }
    }

    // 同步建好：第一帧就带着输入框里的字，揭示途中不会先空白再跳出内容
    val richState = remember { RichTextState().apply { setMarkdown(state.textContent.text.toString()) } }
    fun commitRichText() {
        state.setMessageText(richState.toMarkdown())
    }
    val hasContent = richState.annotatedString.text.isNotBlank() || state.messageContent.isNotEmpty()
    val editorLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var previewMode by remember { mutableStateOf(false) }
    // 底部工具栏 morph 的两端（都在窗口坐标里量，与 flbBounds 同一坐标系）
    var toolbarLayerBounds by remember { mutableStateOf(Rect.Zero) }
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    val latestReveal by androidx.compose.runtime.rememberUpdatedState(revealProgress)

    // 同一窗口的浮层（挂在 DecorView 上）：不开独立 Dialog 窗口，没有窗口动画和压暗跳变，
    // 上滑那一下的手势也不会被新窗口打断；坐标系和输入胶囊一致，不用再换算原点。
    EditorWindowOverlay(passThrough = { latestReveal < 0.5f }) {
        val focusRequester = remember { FocusRequester() }
        val editorFocusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val editorHaptics = rememberKkHaptics()
        val editorMotion = !heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion()
        fun leave(action: () -> Unit) {
            editorFocusManager.clearFocus(force = true)
            keyboard?.hide()
            action()
        }
        // 揭示完成后再聚焦：键盘在窗口铺满后升起，不和揭示动画抢同一段时间
        LaunchedEffect(Unit) {
            snapshotFlow { latestReveal >= 0.999f }.first { it }
            runCatching { focusRequester.requestFocus() }
        }

        val collapsedBounds = flbBounds
        val windowCornerRadiusPx = aiWindowCornerRadiusPx(
            toolbarWidthPx = collapsedBounds.width,
            toolbarHeightPx = collapsedBounds.height,
            revealProgress = reveal,
            screenCornerRadiusPx = screenCornerRadiusPx,
        )
        // 胶囊尚未测量到（空 Rect）时退化成普通圆角矩形：`aiChatGenieEffect` 对空
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
        val toolbarTargetBounds = Rect(
            left = toolbarLayerBounds.left,
            top = toolbarLayerBounds.bottom - toolbarHeightPx,
            right = toolbarLayerBounds.right,
            bottom = toolbarLayerBounds.bottom,
        )
        val toolbarMeasured = toolbarTargetBounds.width > 0f && toolbarTargetBounds.height > 0f
        // 胶囊 → 胶囊：工具栏落定后仍是满圆角药丸，和收起态的输入胶囊同一种形状
        val toolbarMorph = aiFlbToolbarMorph(
            revealProgress = reveal,
            collapsedBounds = flbBounds,
            targetBounds = toolbarTargetBounds,
            collapsedCornerRadiusPx = minOf(flbBounds.width, flbBounds.height) / 2f,
            targetCornerRadiusPx = toolbarHeightPx / 2f,
        )
        val cs = MochiTheme.colorScheme
        val toolbarHeight = with(density) { toolbarHeightPx.toDp() }
        val toolbarMargin = 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                .background(cs.surface.copy(alpha = windowSurfaceAlpha))
                .then(
                    if (windowBorderWidth > 0.001.dp) {
                        Modifier.border(
                            width = windowBorderWidth,
                            color = cs.outline,
                            shape = windowShape,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = popupPadStart,
                        top = popupPadTop,
                        end = popupPadEnd,
                        bottom = popupPadBottom,
                    ),
            ) {
                // 顶部：左边关闭（不保存），右边预览切换 + 完成（保存回输入框）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MochiRealIconButton(
                        onClick = { leave(onDone) },
                        style = MochiButtonStyle.Glass,
                        size = 44.dp,
                    ) {
                        Icon(
                            imageVector = close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = cs.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MochiRealIconButton(
                        onClick = { previewMode = !previewMode },
                        style = MochiButtonStyle.Glass,
                        size = 44.dp,
                    ) {
                        MochiBlurCrossfade(previewMode) { isPreview ->
                            Icon(
                                imageVector = if (isPreview) edit else preview,
                                contentDescription = if (isPreview) "编辑" else "预览",
                                tint = cs.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    MochiButton(
                        onClick = {
                            commitRichText()
                            leave(onDone)
                        },
                        style = MochiButtonStyle.Glass,
                        minHeight = 44.dp,
                    ) {
                        ui.feice.mochi.basic.Text(
                            text = stringResource(R.string.chat_page_save),
                            style = MochiTheme.textStyles.body,
                            color = cs.onSurface,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = toolbarHeight + toolbarMargin * 2),
                ) {
                    MochiBlurCrossfade(previewMode) { preview ->
                        if (preview) {
                            MarkdownBlock(
                                content = richState.toMarkdown(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            // KodeHeap 移植的弹簧光标编辑器（头尾双弹簧拖尾 + 呼吸闪烁 + 打字脉冲）
                            MochiRichTextEditor(
                                state = richState,
                                textStyle = MochiTheme.textStyles.body.copy(color = cs.onSurface),
                                placeholder = stringResource(R.string.chat_input_placeholder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 240.dp)
                                    .focusRequester(focusRequester),
                            )
                        }
                    }
                }
            }
        }

        // —— 底部工具栏图层（窗口图层的兄弟层）——
        // 画在窗口图层（genie 形变 + clip(windowShape)）之外：窗口形状在揭示中段仍只有胶囊大小，
        // 留在窗口图层里会被裁成胶囊、看不到 morph。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = popupPadStart,
                    top = popupPadTop,
                    end = popupPadEnd,
                    bottom = popupPadBottom,
                )
                .padding(horizontal = toolbarMargin, vertical = toolbarMargin)
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
            // morph 矩形换到工具栏自己的局部坐标里（工具栏按最终位置布局）
            val morphLeft = toolbarMorph.left - toolbarTargetBounds.left
            val morphTop = toolbarMorph.top - toolbarTargetBounds.top
            val morphRight = morphLeft + toolbarMorph.width
            val morphBottom = morphTop + toolbarMorph.height
            val containerColor = cs.surfaceContainerHigh.copy(alpha = toolbarMorph.containerAlpha)
            val hairlineColor = cs.outlineVariant.copy(alpha = 0.5f)
            val hairlinePx = with(density) { 0.5.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { toolbarHeightPx = it.height }
                    // 胶囊 → 工具栏：不再整条缩放（缩放会把图标压扁），而是让底板的圆角矩形从胶囊
                    // 拉伸到最终位置，按钮按最终布局摆好、被底板裁出来 ⇒ 底板一边变宽一边把按钮"展开"出来。
                    // 还没量到最终矩形的那一帧整条不画，不会先在原位闪一下再跳到胶囊上。
                    .graphicsLayer { alpha = if (toolbarMeasured) aiFlbToolbarAlpha(reveal) else 0f }
                    .drawWithContent {
                        if (!toolbarMeasured) return@drawWithContent
                        val radius = androidx.compose.ui.geometry.CornerRadius(toolbarMorph.cornerRadiusPx)
                        val topLeft = Offset(morphLeft, morphTop)
                        val size = androidx.compose.ui.geometry.Size(toolbarMorph.width, toolbarMorph.height)
                        drawRoundRect(containerColor, topLeft, size, radius)
                        drawRoundRect(
                            color = hairlineColor,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(hairlinePx),
                        )
                        val clip = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    morphLeft, morphTop, morphRight, morphBottom, radius,
                                ),
                            )
                        }
                        clipPath(clip) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 按钮整体跟着底板的竖直中线走：胶囊比工具栏高（多行输入）时也贴在底板中间
                        .graphicsLayer {
                            if (toolbarMeasured) {
                                translationY = (morphTop + morphBottom) / 2f - size.height / 2f -
                                    with(density) { 6.dp.toPx() }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 格式与附件按钮可以横向滚，发送键固定在最右边，不会被挤出屏幕
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { if (toolbarMeasured) translationX = morphLeft }
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val span = richState.currentSpanStyle
                        var index = 0
                        // 每个按钮按顺序错开 0.045 的揭示进度弹出来（从左往右一个接一个）
                        fun Modifier.entrance(): Modifier = editorToolEntrance(reveal, index++)
                        EditorHeadingTool(richState = richState, modifier = Modifier.entrance())
                        EditorToolButton(
                            description = "粗体",
                            active = span.fontWeight == FontWeight.Bold,
                            modifier = Modifier.entrance(),
                            onClick = { richState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                        ) { tint -> EditorToolGlyph("B", tint, fontWeight = FontWeight.Bold) }
                        EditorToolButton(
                            description = "斜体",
                            active = span.fontStyle == FontStyle.Italic,
                            modifier = Modifier.entrance(),
                            onClick = { richState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                        ) { tint -> EditorToolGlyph("I", tint, fontStyle = FontStyle.Italic) }
                        EditorToolButton(
                            description = "删除线",
                            active = span.textDecoration?.contains(TextDecoration.LineThrough) == true,
                            modifier = Modifier.entrance(),
                            onClick = {
                                richState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                            },
                        ) { tint -> EditorToolGlyph("S", tint, textDecoration = TextDecoration.LineThrough) }
                        EditorToolButton(
                            description = "代码",
                            active = richState.isCodeSpan,
                            modifier = Modifier.entrance(),
                            onClick = { richState.toggleCodeSpan() },
                        ) { tint -> Icon(code, null, tint = tint, modifier = Modifier.size(20.dp)) }
                        EditorToolButton(
                            description = "无序列表",
                            active = richState.isUnorderedList,
                            modifier = Modifier.entrance(),
                            onClick = { richState.toggleUnorderedList() },
                        ) { tint -> Icon(formatListBulleted, null, tint = tint, modifier = Modifier.size(20.dp)) }
                        EditorToolButton(
                            description = "有序列表",
                            active = richState.isOrderedList,
                            modifier = Modifier.entrance(),
                            onClick = { richState.toggleOrderedList() },
                        ) { tint -> EditorToolGlyph("1.", tint, fontWeight = FontWeight.SemiBold) }
                        EditorToolButton(
                            description = "链接",
                            modifier = Modifier.entrance(),
                            onClick = {
                                val selected = richState.toMarkdown(richState.selection)
                                richState.insertMarkdownAfterSelection("[$selected](url)")
                            },
                        ) { tint -> Icon(link, null, tint = tint, modifier = Modifier.size(20.dp)) }
                        attachmentActions?.let { actions ->
                            Box(
                                Modifier
                                    .entrance()
                                    .padding(horizontal = 4.dp)
                                    .size(width = 1.dp, height = 20.dp)
                                    .background(cs.outlineVariant, CircleShape),
                            )
                            EditorToolButton(
                                description = "图片",
                                modifier = Modifier.entrance(),
                                onClick = actions.onPickImage,
                            ) { tint -> Icon(image, null, tint = tint, modifier = Modifier.size(20.dp)) }
                            EditorToolButton(
                                description = "视频",
                                modifier = Modifier.entrance(),
                                onClick = actions.onPickVideo,
                            ) { tint -> Icon(videocam, null, tint = tint, modifier = Modifier.size(20.dp)) }
                            EditorToolButton(
                                description = "文件",
                                modifier = Modifier.entrance(),
                                onClick = actions.onPickFile,
                            ) { tint -> Icon(insertDriveFile, null, tint = tint, modifier = Modifier.size(20.dp)) }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // 和收起态胶囊里同一个发送键：一直贴着底板的右沿走，揭示一开始就在胶囊发送键的位置上，
                    // 上滑全程没有"消失再出现"。有字是主题色实心圆、空的时候是淡色箭头（点了不发），
                    // 由空变有字时弹一下。
                    val sendPop = remember { Animatable(1f) }
                    LaunchedEffect(hasContent) {
                        if (hasContent && editorMotion) {
                            sendPop.snapTo(0.82f)
                            sendPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = MochiMotions.stiffnessFor(0.35f)))
                        }
                    }
                    SendButton(
                        state = if (hasContent) SendButtonState.Send else SendButtonState.Idle,
                        onClick = {
                            if (hasContent) {
                                editorHaptics.confirm()
                                commitRichText()
                                leave(onSend)
                            }
                        },
                        onLongClick = {},
                        modifier = Modifier.graphicsLayer {
                            if (toolbarMeasured) {
                                // 右沿对齐：Row 的右内边距 6dp 在最终矩形和 morph 矩形里一样
                                translationX = morphRight - toolbarTargetBounds.width
                            }
                            scaleX = sendPop.value
                            scaleY = sendPop.value
                        },
                    )
                }
            }
        }
    }
}

/** 揭示后段按顺序弹出：每个按钮晚 0.045，0.4 的进度跑完；减弱动效时 [aiFlbToolbarAlpha] 之外不再加效果。 */
private fun Modifier.editorToolEntrance(reveal: Float, index: Int): Modifier = graphicsLayer {
    val t = ((reveal - 0.35f - index * 0.045f) / 0.4f).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(t)
    alpha = eased
    val scale = 0.6f + 0.4f * eased
    scaleX = scale
    scaleY = scale
    translationY = (1f - eased) * 10.dp.toPx()
}

/**
 * 编辑器工具栏按钮（Mochi 化的 KodeHeap ToolbarToggle）：圆形 40dp，按下缩到 0.88；
 * [active] 时底色淡入主题色 14%、图标变主题色，切到选中的那一下额外弹一下；点按震一下 tick。
 */
@Composable
private fun EditorToolButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable (tint: Color) -> Unit,
) {
    val cs = MochiTheme.colorScheme
    val haptics = rememberKkHaptics()
    val reduceMotion = heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val activeFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 1f, stiffness = MochiMotions.stiffnessFor(0.25f)),
        label = "editor_tool_active",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active && !reduceMotion) {
            pop.snapTo(1.16f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = MochiMotions.stiffnessFor(0.32f)))
        }
    }
    val tint = androidx.compose.ui.graphics.lerp(cs.onSurfaceVariant, cs.primary, activeFraction)
    Box(
        modifier = modifier
            .size(40.dp)
            .kkPressState(interaction, pressedScale = 0.88f)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(cs.primary.copy(alpha = 0.14f * activeFraction), CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                haptics.tick()
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

private val EditorHeadingOptions = listOf(
    com.mohamedrejeb.richeditor.model.HeadingStyle.Normal to "正文",
    com.mohamedrejeb.richeditor.model.HeadingStyle.H1 to "一级标题",
    com.mohamedrejeb.richeditor.model.HeadingStyle.H2 to "二级标题",
    com.mohamedrejeb.richeditor.model.HeadingStyle.H3 to "三级标题",
    com.mohamedrejeb.richeditor.model.HeadingStyle.H4 to "四级标题",
)

/**
 * 「标题」工具：点开在按钮正上方弹出一张 Mochi 小卡片（正文 / H1–H4），以按钮为原点按弹簧长出、
 * 各行错开浮入，当前段落的级别高亮打勾；按钮本身在非正文段落里显示成 H1/H2… 字形。
 * 弹层 focusable = false：不抢编辑器焦点，键盘不会被收起。
 */
@Composable
private fun EditorHeadingTool(
    richState: RichTextState,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val haptics = rememberKkHaptics()
    val reduceMotion = heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion()
    val density = LocalDensity.current
    val current = richState.currentHeadingStyle
    var open by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(open, reduceMotion) {
        val target = if (open) 1f else 0f
        if (reduceMotion) {
            reveal.snapTo(target)
        } else {
            reveal.animateTo(
                targetValue = target,
                animationSpec = if (open) {
                    spring(dampingRatio = 0.72f, stiffness = MochiMotions.stiffnessFor(0.32f))
                } else {
                    spring(dampingRatio = 1f, stiffness = MochiMotions.stiffnessFor(0.2f))
                },
            )
        }
    }
    // 光标挪到别处就收起，卡片不再指着一个不相干的段落
    val selection = richState.selection
    LaunchedEffect(selection) { open = false }

    Box(modifier = modifier) {
        EditorToolButton(
            description = "标题",
            active = current != com.mohamedrejeb.richeditor.model.HeadingStyle.Normal || open,
            onClick = { open = !open },
        ) { tint ->
            if (current == com.mohamedrejeb.richeditor.model.HeadingStyle.Normal) {
                Icon(title, null, tint = tint, modifier = Modifier.size(20.dp))
            } else {
                EditorToolGlyph("H${current.level}", tint, fontWeight = FontWeight.Bold)
            }
        }

        if (open || reveal.value > 0.001f) {
            // 卡片外圈留了 8dp 给阴影，视觉上离按钮 6dp：整体往下压 8 - 6 = 2dp
            val overlapPx = with(density) { 2.dp.roundToPx() }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = remember(overlapPx) { AboveAnchorPopupPosition(overlapPx) },
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            ) {
                val cardShape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = reveal.value
                            alpha = p.coerceIn(0f, 1f)
                            val s = 0.72f + 0.28f * p
                            scaleX = s
                            scaleY = s
                            // 从卡片底边中点（正对按钮）长出来
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - p) * 12.dp.toPx()
                        }
                        .padding(8.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = cardShape,
                            ambientColor = Color.Black.copy(alpha = 0.12f),
                            spotColor = Color.Black.copy(alpha = 0.18f),
                        )
                        .background(cs.surfaceContainerHigh, cardShape)
                        .border(0.5.dp, cs.outlineVariant.copy(alpha = 0.6f), cardShape)
                        .width(208.dp)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    EditorHeadingOptions.forEachIndexed { index, (style, label) ->
                        EditorHeadingRow(
                            style = style,
                            label = label,
                            selected = current == style,
                            modifier = Modifier.graphicsLayer {
                                val t = ((reveal.value - 0.12f - index * 0.07f) / 0.5f).coerceIn(0f, 1f)
                                val eased = FastOutSlowInEasing.transform(t)
                                alpha = eased
                                translationY = (1f - eased) * 8.dp.toPx()
                            },
                            onClick = {
                                haptics.confirm()
                                richState.setHeadingStyle(style)
                                open = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorHeadingRow(
    style: com.mohamedrejeb.richeditor.model.HeadingStyle,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val rowShape = RoundedCornerShape(14.dp)
    // 标签按对应级别的字号预览：一眼看出 H1 比 H3 大多少
    val previewSize = when (style) {
        com.mohamedrejeb.richeditor.model.HeadingStyle.H1 -> 20f
        com.mohamedrejeb.richeditor.model.HeadingStyle.H2 -> 18f
        com.mohamedrejeb.richeditor.model.HeadingStyle.H3 -> 16.5f
        com.mohamedrejeb.richeditor.model.HeadingStyle.H4 -> 15.5f
        else -> 15f
    }
    val badge = if (style == com.mohamedrejeb.richeditor.model.HeadingStyle.Normal) "T" else "H${style.level}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kkPressState(interaction, pressedScale = 0.96f)
            .clip(rowShape)
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    if (selected) cs.primary.copy(alpha = 0.18f) else cs.surfaceContainerHighest,
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            ui.feice.mochi.basic.Text(
                text = badge,
                color = if (selected) cs.primary else cs.onSurfaceVariant,
                style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Bold),
            )
        }
        ui.feice.mochi.basic.Text(
            text = label,
            color = if (selected) cs.primary else cs.onSurface,
            style = MochiTheme.textStyles.body.copy(
                fontSize = androidx.compose.ui.unit.TextUnit(previewSize, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = if (style == com.mohamedrejeb.richeditor.model.HeadingStyle.Normal) {
                    FontWeight.Normal
                } else {
                    FontWeight.SemiBold
                },
            ),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = heizige.kk.khatkit.app.core.ui.icons.check,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 弹层放在锚点正上方、水平居中，左右夹在窗口内；上方放不下时贴顶。[overlapPx] 是往锚点压回去的距离（抵掉阴影留白）。 */
private class AboveAnchorPopupPosition(
    private val overlapPx: Int,
) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, maxX)
        val y = (anchorBounds.top - popupContentSize.height + overlapPx).coerceAtLeast(0)
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

@Composable
private fun EditorToolGlyph(
    glyph: String,
    tint: Color,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
) {
    ui.feice.mochi.basic.Text(
        text = glyph,
        color = tint,
        style = MochiTheme.textStyles.body.copy(
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
        ),
    )
}

/**
 * 编辑器浮层：直接挂到当前 Activity 的 DecorView 上（同一个窗口，edge-to-edge 下铺满整屏），
 * CompositionLocal 通过父 CompositionContext 继承（同 Mochi 的 InWindowOverlay）。
 * [passThrough] 在按下时问一次：收起中返回 true，这一整个手势直接交给下面的页面。
 */
@Composable
private fun EditorWindowOverlay(passThrough: () -> Boolean, content: @Composable () -> Unit) {
    val hostView = LocalView.current
    val parent = androidx.compose.runtime.rememberCompositionContext()
    val currentContent by androidx.compose.runtime.rememberUpdatedState(content)
    val currentPassThrough by androidx.compose.runtime.rememberUpdatedState(passThrough)
    val overlay = remember(hostView) {
        EditorOverlayFrame(hostView.context) { currentPassThrough() }.apply {
            addView(
                androidx.compose.ui.platform.ComposeView(hostView.context).apply {
                    // 独立 id：SaveableStateRegistry 按 view id 注册，和别的无 id 浮层（Mochi 弹窗）错开
                    id = android.view.View.generateViewId()
                    setParentCompositionContext(parent)
                    setContent { currentContent() }
                },
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
    DisposableEffect(overlay) {
        val decor = hostView.rootView as android.view.ViewGroup
        decor.addView(
            overlay,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        onDispose {
            if (overlay.hasFocus()) {
                // 浮层里的编辑器聚焦着直接移除，系统会把焦点交回页面、键盘又弹出来：
                // 移除那一刻先不让页面接焦点，下一帧恢复
                val old = decor.descendantFocusability
                decor.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                decor.removeView(overlay)
                decor.post { decor.descendantFocusability = old }
            } else {
                decor.removeView(overlay)
            }
            (overlay.getChildAt(0) as androidx.compose.ui.platform.ComposeView).disposeComposition()
        }
    }
}

private class EditorOverlayFrame(
    context: android.content.Context,
    private val passThrough: () -> Boolean,
) : android.widget.FrameLayout(context) {
    private var passing = false

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) passing = passThrough()
        return if (passing) false else super.dispatchTouchEvent(ev)
    }
}

@Composable
private fun MarkdownEditButton(
    state: ChatInputState,
    icon: ImageVector,
    label: String,
    wrapper: () -> Pair<String, String>,
) {
    MochiIconButton(
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
    MochiIconButton(
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

private class PanelTile(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** ＋ 面板的错开入场：展开后段每个按钮晚 0.04 的进度淡入、从下方 6dp 浮上来。 */
private fun Modifier.panelEntrance(progress: () -> Float, index: Int): Modifier = graphicsLayer {
    val t = ((progress() - 0.3f - index * 0.04f) / 0.4f).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(t)
    alpha = eased
    translationY = (1f - eased) * 6.dp.toPx()
}

/** ＋ 面板的一个动作：Mochi Tonal 圆按钮（按压形变 / 触觉由按钮自己给）+ 下面一行脚注字。 */
@Composable
private fun PanelAction(
    tile: PanelTile,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MochiRealIconButton(
            onClick = tile.onClick,
            style = MochiButtonStyle.Tonal,
            size = 48.dp,
            modifier = Modifier.semantics { contentDescription = tile.label },
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        ui.feice.mochi.basic.Text(
            text = tile.label,
            color = cs.onSurfaceVariant,
            style = MochiTheme.textStyles.footnote,
            maxLines = 1,
        )
    }
}


/**
 * 全屏编辑器的揭示弹簧：临界阻尼（揭示进度在 1 处被 clamp，回弹看不见只会显得顿一下），
 * 刚度与 Mochi 的高度动画同档。手势松手时把手指速度作为 initialVelocity 接上。
 */
private val EditorRevealSpring = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 1f,
    stiffness = 380f,
)

/**
 * FLB 手风琴面板的落定曲线：
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

