package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import heizige.kk.khatkit.uikit.mochi.KkGrowRiseText
import heizige.kk.khatkit.uikit.mochi.KkRisingText
import heizige.kk.khatkit.uikit.mochi.LocalKkEffectsEnabled
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiDialog
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion
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
import androidx.activity.BackEventCompat
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatInputCommand
import heizige.kk.khatkit.app.core.ui.components.ai.completion.findCommand
import heizige.kk.khatkit.app.core.ui.components.ai.completion.suggestCommands
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.components.ui.KeepScreenOn
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionRecordAudio
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalASRState
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
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
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.LocalMochiGlassShadow
import ui.feice.mochi.effect.glass.MochiGlassSurface
import ui.feice.mochi.effect.glass.MochiNoGlassShadow
import ui.feice.mochi.effect.metaball.MochiBlob
import ui.feice.mochi.effect.metaball.MochiMetaballColors
import ui.feice.mochi.effect.metaball.MochiMetaballFrame
import ui.feice.mochi.effect.metaball.drawMochiMetaball
import ui.feice.mochi.effect.metaball.proximityFuse
import ui.feice.mochi.effect.metaball.rememberMochiMetaballRenderer
import ui.feice.mochi.effect.content.mochiBlurReveal
import ui.feice.mochi.theme.MochiGlass
import ui.feice.mochi.theme.MochiGlassStyle
import ui.feice.mochi.theme.MochiGraphics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import ui.feice.mochi.basic.MochiIconButton as MochiRealIconButton
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.effect.elastic.mochiPressDeform
import kotlinx.collections.immutable.toPersistentList
import ui.feice.mochi.basic.MochiAlign
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiPopoverButton
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    editorRevealState: MutableFloatState,
    enableSearch: Boolean,
    onUpdateSearchMode: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    commands: List<ChatInputCommand> = emptyList(),
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

    val assistant = settings.getCurrentAssistant()
    val composerFieldColor = MochiTextFieldDefaults.colors().container.compositeOver(CustomColors.pageContainerColor)
    val sheetGlass = MochiTheme.materials.sheetGlass?.let { glass ->
        if (!settings.displaySetting.enableBlurEffect) glass else glass.copy(
            style = when (settings.displaySetting.backgroundEffectType) {
                BackgroundEffectType.BLUR -> MochiGlassStyle.Frosted
                BackgroundEffectType.GLASS -> MochiGlassStyle.Regular
            },
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 展开进度：0f 收起（胶囊）→ 1f 展开（24dp 圆角），拖动时实时跟手
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
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
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    // —— 全屏编辑器：同 window 的页内浮层，进度单一来源 ——
    var editorOpen by remember { mutableStateOf(false) }
    var editorOverlayReady by remember { mutableStateOf(false) }
    var editorSettled by remember { mutableStateOf(false) }
    var openedByTap by remember { mutableStateOf(false) }
    var fullscreenButtonCenter by remember { mutableStateOf(Offset.Unspecified) }
    var editorOpeningX by remember { mutableFloatStateOf(Float.NaN) }
    var editorBackMotion by remember { mutableStateOf(EditorBackMotion()) }
    var editorReveal by editorRevealState
    val editorRevealDistancePx = with(LocalDensity.current) { 160.dp.toPx() }
    val editorFlingVelocityPx = with(LocalDensity.current) { 600.dp.toPx() }
    val editorJobRef = remember { object { var job: Job? = null } }
    val reduceMotion = rememberKkReduceMotion()
    DisposableEffect(editorRevealState) {
        onDispose { editorRevealState.floatValue = 0f }
    }

    // velocity：松手时的揭示速度（进度/秒），弹簧从手指的速度接着走，松手那一帧不顿
    fun settleEditor(open: Boolean, velocity: Float = 0f, waitForOverlay: Boolean = false) {
        editorJobRef.job?.cancel()
        editorSettled = false
        editorJobRef.job = scope.launch {
            if (waitForOverlay) snapshotFlow { editorOverlayReady }.first { it }
            val target = if (open) 1f else 0f
            animate(
                initialValue = editorReveal,
                targetValue = target,
                initialVelocity = velocity.coerceIn(-9f, 9f),
                animationSpec = when {
                    reduceMotion -> androidx.compose.animation.core.snap()
                    open && waitForOverlay -> EditorTapSpring
                    open -> MochiMotions.Ios27Menu.openSpatial
                    else -> MochiMotions.Ios27Menu.closeSpatial
                },
            ) { value, _ -> editorReveal = value }
            editorReveal = target
            editorSettled = open
            if (!open) {
                editorOpen = false
            }
        }
    }

    fun changeEditorOpen(open: Boolean) {
        if (open) {
            openedByTap = true
            editorOpeningX = if (fullscreenButtonCenter != Offset.Unspecified) fullscreenButtonCenter.x else flbBounds.center.x
            editorOpen = true
        }
        settleEditor(open = open, waitForOverlay = open)
    }

    fun composerGlassAlpha(): Float =
        if (editorOverlayReady) 1f - editorSurfaceHandoff(editorReveal) else 1f

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
    // 背景圆角随输入框圆角走：收起时 = 输入框圆角 + 间距（同心），
    // 展开后段用 late-morph 过渡到 24dp。
    val collapsedOuterRadiusPx = with(densityLocal) { (MochiTextFieldDefaults.Height / 2).toPx() } + gapPx
    // 展开后也保持和输入框一圈同心的大圆角，不做收角
    val outerRadiusPx = collapsedOuterRadiusPx
    val outerRadiusDp = with(densityLocal) { outerRadiusPx.toDp().value }
    val containerShape = RoundedCornerShape(outerRadiusDp.dp)
    fun runCommand(command: ChatInputCommand) {
        state.setMessageText("")
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        command.onRun()
    }

    fun runTypedCommand(): Boolean {
        if (state.isEditing()) return false
        val command = commands.findCommand(state.textContent.text.toString()) ?: return false
        runCommand(command)
        return true
    }

    fun sendMessage() {
        if (runTypedCommand()) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        if (runTypedCommand()) return
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
            val composerSwipe = Modifier
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
                            var drivingEditor = false
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
                                    totalX += delta.x
                                    totalY += delta.y
                                    if (!passedSlop && hypot(totalX, totalY) > touchSlop) {
                                        // 只用纵向位移推进展开，横向位移留给页面形变。
                                        if (abs(totalY) < abs(totalX) || totalY >= 0f) break
                                        passedSlop = true
                                        editorJobRef.job?.cancel()
                                        editorJobRef.job = null
                                        editorSettled = false
                                        openedByTap = false
                                    }

                                    if (passedSlop) {
                                        change.consume()
                                        if (totalY < 0f || drivingEditor) {
                                            drivingEditor = true
                                            editorOpeningX = flbBounds.left + change.position.x
                                            lastReveal = composerAiRevealProgressFromDrag(
                                                rawY = (totalY + touchSlop).coerceAtMost(0f),
                                                commitDistance = editorRevealDistancePx,
                                            )
                                            editorReveal = lastReveal.coerceAtLeast(0f)
                                            if (!editorOpen && lastReveal > 0.001f) {
                                                editorOpen = true
                                            }
                                        }
                                    }

                                    if (!change.pressed) break
                                }
                            } catch (cancelled: CancellationException) {
                                if (drivingEditor) settleEditor(open = false)
                                throw cancelled
                            }

                            if (drivingEditor) {
                                releaseEditor()
                            }
                        }
                    }
            KkSurface(
                modifier = Modifier
                    .weight(1f)
                    .clip(containerShape),
                shape = containerShape,
                // 收起时保持输入栏原有的轻量感；展开后变成独立的 Mochi surface，
                // 让 FAB 拉起的能力面板与工具台/设置页的分组容器有明确层次。
                color = Color.Transparent,
                contentColor = MochiTheme.colorScheme.onSurface,
                // 收起时不描边，展开过程中出现 Mochi outline。
                border = if (easedProgress() > 0.05f) {
                    BorderStroke(
                        width = (2f * easedProgress()).dp,
                        color = MochiTheme.colorScheme.outlineVariant.copy(alpha = 0.7f * easedProgress()),
                    )
                } else null,
            ) {
                Box {
                if (easedProgress() > 0.05f) {
                    MochiGlassSurface(
                        modifier = Modifier.matchParentSize().graphicsLayer { alpha = easedProgress() },
                        material = (sheetGlass ?: MochiGlass()).copy(fallback = MochiTheme.colorScheme.surfaceContainer),
                        backdrop = LocalMochiBackdrop.current.takeIf { sheetGlass != null },
                        cornerRadius = outerRadiusDp.dp,
                    ) {}
                }
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
                            color = MochiTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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


                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { inputRowSize = it },
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComposerGlass(
                            modifier = Modifier
                                .size(MochiTextFieldDefaults.Height)
                                // 玻璃底板在按钮外层；把 Mochi 的按压形变挂到整块容器上，避免只缩放透明图标按钮。
                                .mochiPressDeform(scale = 1.08f, follow = 4.dp)
                                .onGloballyPositioned { coords ->
                                    if (!editorOpen || fabBounds == Rect.Zero) {
                                        fabBounds = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                                    }
                                },
                            fallback = composerFieldColor,
                            glassAlpha = ::composerGlassAlpha,
                            lift = { 16.dp * editorComposerLift(editorReveal) },
                        ) {
                            MochiRealIconButton(
                                onClick = onMoreClick,
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (editorOverlayReady) 1f - editorPhase(editorReveal, 0.04f, 0.20f) else 1f
                                    translationY = -editorComposerLift(editorReveal) * 16.dp.toPx()
                                },
                                size = MochiTextFieldDefaults.Height,
                                style = MochiButtonStyle.Text,
                                // 由外层玻璃容器承接缩放，内部按钮保留 Mochi 高光但不再重复形变。
                                behavior = MochiButtonDefaults.behavior(pressScale = 1f, follow = 0.dp),
                            ) {
                                Icon(
                                    imageVector = add,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = MochiTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        ComposerGlass(
                            modifier = Modifier.weight(1f)
                                .onGloballyPositioned { coords ->
                                    if (!editorOpen || flbBounds == Rect.Zero) {
                                        flbBounds = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                                    }
                                }
                                .then(composerSwipe),
                            fallback = composerFieldColor,
                            glassAlpha = ::composerGlassAlpha,
                            lift = { 20.dp * editorComposerLift(editorReveal) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = MochiTextFieldDefaults.Height)
                                    .clip(MochiTextFieldDefaults.Shape)
                                    .graphicsLayer {
                                        alpha = if (editorOverlayReady) 1f - editorPhase(editorReveal, 0.04f, 0.28f) else 1f
                                        translationY = -editorComposerLift(editorReveal) * 20.dp.toPx()
                                    },
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                TextInputRow(
                                    state = state,
                                    isFullScreen = editorOpen,
                                    completionProviders = completionProviders,
                                    commands = commands,
                                    onRunCommand = ::runCommand,
                                    onSendMessage = { sendMessage() },
                                    modifier = Modifier.weight(1f),
                                )
                                MochiRealIconButton(
                                    onClick = { changeEditorOpen(!editorOpen) },
                                    modifier = Modifier
                                        .padding(MochiTextFieldDefaults.TrailingInset)
                                        .onGloballyPositioned { coords ->
                                            if (!editorOpen) {
                                                fullscreenButtonCenter = coords.localToWindow(
                                                    Offset(coords.size.width / 2f, coords.size.height / 2f),
                                                )
                                            }
                                        }
                                        .testTag("chat_fullscreen_button"),
                                    size = MochiTextFieldDefaults.TrailingButtonSize,
                                    style = MochiButtonStyle.Text,
                                ) {
                                    Icon(
                                        fullscreen,
                                        contentDescription = stringResource(R.string.text_area_fullscreen_edit),
                                        tint = MochiTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp).graphicsLayer {
                                            if (openedByTap && !reduceMotion) {
                                                val pulse = editorTapPulse(editorReveal)
                                                scaleX = 1f + 0.24f * pulse
                                                scaleY = scaleX
                                                rotationZ = -10f * pulse
                                            }
                                        },
                                    )
                                }
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
    }

    PredictiveBackHandler(enabled = editorOpen) { events ->
        editorJobRef.job?.cancel()
        val startReveal = editorReveal.coerceIn(0f, 1f)
        var touchStart = Offset.Unspecified
        try {
            events.collect { event ->
                if (touchStart == Offset.Unspecified) touchStart = Offset(event.touchX, event.touchY)
                val direction = when (event.swipeEdge) {
                    BackEventCompat.EDGE_LEFT -> 1f
                    BackEventCompat.EDGE_RIGHT -> -1f
                    else -> 0f
                }
                val follow = event.progress.coerceIn(0f, 1f)
                editorReveal = startReveal * (1f - follow)
                editorBackMotion = EditorBackMotion(
                    direction = direction,
                    progress = follow,
                    touchY = event.touchY,
                    travelX = (direction * (event.touchX - touchStart.x)).coerceAtLeast(0f),
                    travelY = event.touchY - touchStart.y,
                )
            }
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            val releaseReveal = editorReveal
            val releaseMotion = editorBackMotion
            animate(0f, 1f, animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else MochiMotions.Ios27Menu.closeSpatial) { value, _ ->
                val remaining = 1f - value.coerceIn(0f, 1f)
                editorReveal = releaseReveal * remaining
                editorBackMotion = releaseMotion.copy(progress = releaseMotion.progress * remaining)
            }
            editorReveal = 0f
            editorBackMotion = EditorBackMotion()
            editorSettled = false
            editorOpen = false
        } catch (_: CancellationException) {
            val cancelReveal = editorReveal
            val cancelMotion = editorBackMotion
            editorJobRef.job = scope.launch {
                animate(0f, 1f, animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else MochiMotions.Ios27Menu.openSpatial) { value, _ ->
                    val fraction = value.coerceIn(0f, 1f)
                    editorReveal = cancelReveal + (startReveal - cancelReveal) * fraction
                    editorBackMotion = cancelMotion.copy(progress = cancelMotion.progress * (1f - fraction))
                }
                editorBackMotion = EditorBackMotion()
                if (!editorSettled) settleEditor(open = true)
            }
        }
    }

    if (editorOpen) {
        FullScreenEditor(
            state = state,
            flbBounds = flbBounds,
            fabBounds = fabBounds,
            fallback = composerFieldColor,
            blurChatPage = settings.displaySetting.effectsEnabled && MochiGraphics.blur,
            revealProgress = editorReveal,
            tapOrigin = fullscreenButtonCenter.takeIf { openedByTap && !reduceMotion } ?: Offset.Unspecified,
            openingAnchorX = editorOpeningX,
            backMotion = editorBackMotion,
            settled = editorSettled,
            onOverlayReady = { editorOverlayReady = it },
            attachmentActions = attachmentActions,
            onSend = { sendMessage(); changeEditorOpen(false) },
            onDone = { changeEditorOpen(false) },
        )
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
private fun ComposerGlass(
    modifier: Modifier,
    fallback: Color,
    glassAlpha: () -> Float,
    lift: () -> Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val material = MochiTheme.materials.controlGlass
    val backdrop = LocalMochiBackdrop.current
    Box(modifier) {
        val plateModifier = Modifier.matchParentSize().graphicsLayer {
            alpha = glassAlpha()
            translationY = -lift().toPx()
        }
        if (glassAlpha() > 0f) {
            if (material == null || backdrop == null) {
                Box(plateModifier.background(fallback, MochiTextFieldDefaults.Shape))
            } else {
                CompositionLocalProvider(LocalMochiGlassShadow provides MochiNoGlassShadow) {
                    MochiGlassSurface(plateModifier, material, backdrop) {}
                }
            }
        }
        content()
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    commands: List<ChatInputCommand>,
    onRunCommand: (ChatInputCommand) -> Unit,
    onSendMessage: () -> Unit,
    isFullScreen: Boolean,
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

        fun commandContext() = ChatCompletionContext(state.textContent.text.toString(), state.textContent.selection)
        val commandMatches = if (isFocused && !isFullScreen && !state.isEditing() && commands.isNotEmpty()) {
            commands.suggestCommands(commandContext())
        } else emptyList()
        if (commandMatches.isNotEmpty()) {
            CompletionPopup(
                items = commandMatches.map { ChatCompletionItem("/${it.name}", it.name, detail = it.label) },
                onItemClick = { item ->
                    // Validate against the live text before replacing what may now be a different draft.
                    if (!state.isEditing()) {
                        commands.suggestCommands(commandContext()).firstOrNull { it.name == item.insertText }
                            ?.let(onRunCommand)
                    }
                },
            )
        } else completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                items = list.items,
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
            leadingContent = if (quickMessages.isNotEmpty()) {
                {
                    QuickMessageButton(quickMessages = quickMessages, state = state)
                }
            } else null,
        )
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
        val fieldScroll = rememberScrollState()
        val text = state.textContent.text
        // A decorative copy cannot follow the editable field's scroll or single-line transformation.
        val inputMotionEnabled = !rememberKkReduceMotion() && LocalKkEffectsEnabled.current &&
            text.length in 1..256 && fieldScroll.maxValue == 0 && (!sendOnEnter || '\n' !in text)
        BasicTextField(
            state = state.textContent,
            scrollState = fieldScroll,
            modifier = modifier,
            // 文字由 KkGrowRiseText 绘制，BasicTextField 只保留光标、选区和输入行为。
            textStyle = MochiTheme.textStyles.body.copy(
                color = if (inputMotionEnabled) Color.Transparent else cs.onSurface,
            ),
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
                            // 计数器式进场：每个字符从基线下方升上来（MochiCounterText 同款弹簧）
                            KkRisingText(
                                text = placeholder,
                                style = MochiTheme.textStyles.body,
                                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        if (inputMotionEnabled) {
                            // Only the native text field may measure this box (one to five lines).
                            Box(Modifier.matchParentSize().clipToBounds().clearAndSetSemantics {},
                                contentAlignment = Alignment.CenterStart) {
                                KkGrowRiseText(text = text.toString(), style = MochiTheme.textStyles.body, color = cs.onSurface)
                            }
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
    items: List<ChatCompletionItem>,
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
                items = items,
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
    val colors = MochiTheme.colorScheme
    MochiPopoverButton(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        width = 280.dp,
        buttonMinHeight = 40.dp,
        buttonPadding = PaddingValues(horizontal = 8.dp),
        contentPadding = PaddingValues(6.dp),
        colors = MochiDropdownDefaults.iconDropdownColors(buttonColor = Color.Transparent),
        behavior = MochiDropdownDefaults.iconBehavior(),
        popover = {
            quickMessages.forEach { quickMessage ->
                val interaction = remember { MutableInteractionSource() }
                val haptics = rememberKkHaptics()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kkPressState(interaction)
                        .clip(MochiTheme.shapes.medium)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClickLabel = "追加到输入框",
                            onLongClickLabel = "替换输入框内容",
                            onClick = {
                                haptics.confirm()
                                state.appendText(quickMessage.content)
                                expanded = false
                            },
                            onLongClick = {
                                haptics.confirm()
                                state.replaceText(quickMessage.content)
                                expanded = false
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(quickMessage.title, style = MochiTheme.textStyles.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(quickMessage.content, style = MochiTheme.textStyles.footnote, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
    ) {
        Icon(bolt, "快捷消息")
    }
}

internal const val EditorFusionContact = 0.14f
internal const val EditorFullWidth = 0.25f
private val EditorTapSpring = MochiMotions.responseSpring(response = 0.43f, damping = 0.82f)

private fun editorTapPulse(reveal: Float) =
    editorPhase(reveal, 0f, 0.16f) * (1f - editorPhase(reveal, 0.16f, 0.66f))

private fun editorPhase(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun Rect.asGlassBlob() = MochiBlob(this, minOf(width, height).coerceAtLeast(0f) / 2f)

internal fun editorBackdropProgress(reveal: Float) = editorPhase(reveal, 0f, 0.84f)

internal fun editorSurfaceHandoff(reveal: Float) = editorPhase(reveal, 0f, 0.16f)

internal fun editorGlassAlpha(reveal: Float) = 1f - editorPhase(reveal, 0.92f, 1f)

private fun editorExpansion(reveal: Float): Float {
    return editorPhase(reveal, EditorFusionContact, 1f)
}

private fun editorComposerLift(reveal: Float) =
    editorPhase(reveal, 0f, EditorFusionContact) * (1f - editorPhase(reveal, 0.45f, 1f))

internal fun editorComposerBounds(pill: Rect, fab: Rect, viewport: Rect, reveal: Float, liftPx: Float): Rect {
    if (pill.isEmpty) return viewport
    val contact = editorPhase(reveal, 0f, EditorFusionContact)
    val overlap = fab.minDimension * 0.2f
    val joined = if (fab.isEmpty) {
        pill
    } else if (fab.center.x < pill.center.x) {
        pill.copy(left = lerp(pill.left, minOf(pill.left, fab.right - overlap), contact))
    } else {
        pill.copy(right = lerp(pill.right, maxOf(pill.right, fab.left + overlap), contact))
    }
    val widen = editorPhase(reveal, EditorFusionContact, EditorFullWidth)
    val rise = editorExpansion(reveal)
    val lift = liftPx * editorComposerLift(reveal)
    return Rect(
        left = lerp(joined.left, viewport.left, widen),
        top = lerp(joined.top, viewport.top, rise) - lift,
        right = lerp(joined.right, viewport.right, widen),
        bottom = lerp(joined.bottom, viewport.bottom, rise) - lift,
    )
}

@Composable
private fun EditorBackdropSurface(
    modifier: Modifier,
    main: MochiBlob,
    fab: MochiBlob,
    fusePx: Float,
    tint: Color,
    rimAlpha: Float,
) {
    val renderer = rememberMochiMetaballRenderer()
    val innerRenderer = rememberMochiMetaballRenderer()
    val layerPaint = remember { Paint() }
    val erasePaint = remember { Paint().apply { blendMode = BlendMode.DstOut } }
    val dark = MochiTheme.colorScheme.isDark
    Canvas(modifier) {
        if (main.rect.isEmpty) return@Canvas
        val rimWidth = 0.8.dp.toPx()
        val rim = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = rimAlpha * if (dark) 0.34f else 0.78f),
                Color.White.copy(alpha = rimAlpha * if (dark) 0.08f else 0.22f),
                (if (dark) Color.White else Color.Black).copy(alpha = rimAlpha * 0.18f),
            ),
            startY = main.rect.top,
            endY = maxOf(main.rect.bottom, fab.rect.bottom),
        )
        if (fab.rect.minDimension <= rimWidth * 2f) {
            drawRoundRect(tint, main.rect.topLeft, main.rect.size, CornerRadius(main.cornerRadius))
            if (rimAlpha > 0f) {
                val inset = rimWidth / 2f
                val strokeRect = main.rect.deflate(inset)
                drawRoundRect(
                    rim, strokeRect.topLeft, strokeRect.size,
                    CornerRadius((main.cornerRadius - inset).coerceAtLeast(0f)),
                    style = Stroke(rimWidth),
                )
            }
            return@Canvas
        }

        val fuse = if (fusePx > 0f) fusePx * proximityFuse(main.rect, fab.rect, fusePx) else 0f
        val frame = MochiMetaballFrame(main, fab, fuse)
        drawMochiMetaball(renderer, frame, MochiMetaballColors(tint, tint, Color.Transparent), smoothing = 0f)
        if (rimAlpha <= 0f) return@Canvas

        // Keep only the outer edge of the fused shapes. The live ChatPage supplies all blur.
        val bounds = Rect(
            minOf(main.rect.left, fab.rect.left), minOf(main.rect.top, fab.rect.top),
            maxOf(main.rect.right, fab.rect.right), maxOf(main.rect.bottom, fab.rect.bottom),
        ).inflate(fuse + 2f)
        val mask = MochiMetaballColors(Color.White, Color.White, Color.Transparent)
        val inner = MochiMetaballFrame(
            MochiBlob(main.rect.deflate(rimWidth), (main.cornerRadius - rimWidth).coerceAtLeast(0f)),
            MochiBlob(fab.rect.deflate(rimWidth), (fab.cornerRadius - rimWidth).coerceAtLeast(0f)),
            fuse,
        )
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, layerPaint)
            drawMochiMetaball(renderer, frame, mask, smoothing = 0f)
            canvas.saveLayer(bounds, erasePaint)
            drawMochiMetaball(innerRenderer, inner, mask, smoothing = 0f)
            canvas.restore()
            drawRect(rim, bounds.topLeft, bounds.size, blendMode = BlendMode.SrcIn)
            canvas.restore()
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState,
    flbBounds: Rect,
    fabBounds: Rect,
    fallback: Color,
    blurChatPage: Boolean,
    revealProgress: Float = 0f,
    tapOrigin: Offset,
    openingAnchorX: Float,
    backMotion: EditorBackMotion,
    settled: Boolean,
    onOverlayReady: (Boolean) -> Unit,
    attachmentActions: ChatAttachmentPickerActions? = null,
    onSend: () -> Unit,
    onDone: () -> Unit,
) {
    val reveal = revealProgress.coerceIn(0f, 1f)
    val screenCornerRadiusPx = rememberScreenCornerRadiusPx()
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

    // 同步建好：第一帧就带着输入框里的字，揭示途中不会先空白再跳出内容
    val richState = remember { RichTextState().apply { setMarkdown(state.textContent.text.toString()) } }
    fun commitRichText() {
        state.setMessageText(richState.toMarkdown())
    }
    val hasContent = richState.annotatedString.text.isNotBlank() || state.messageContent.isNotEmpty()
    var previewMode by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    var overlayBounds by remember { mutableStateOf(Rect.Zero) }
    val latestReveal by androidx.compose.runtime.rememberUpdatedState(revealProgress)
    val latestSettled by androidx.compose.runtime.rememberUpdatedState(settled)
    val latestOverlayReady by androidx.compose.runtime.rememberUpdatedState(onOverlayReady)
    val hasOverlayBounds = !overlayBounds.isEmpty
    DisposableEffect(Unit) {
        onDispose { latestOverlayReady(false) }
    }
    LaunchedEffect(hasOverlayBounds) {
        if (hasOverlayBounds) latestOverlayReady(true)
    }

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
            snapshotFlow { latestSettled }.first { it }
            runCatching { focusRequester.requestFocus() }
        }

        val cs = MochiTheme.colorScheme
        val toolbarHeight = with(density) { toolbarHeightPx.toDp() }
        val toolbarMargin = 12.dp
        val expansion = editorExpansion(reveal)
        val liftPx = with(density) { 20.dp.toPx() }
        val tapCenter = tapOrigin - overlayBounds.topLeft
        val tapPulse = if (tapOrigin != Offset.Unspecified) editorTapPulse(reveal) else 0f
        val tapLift = with(density) { 14.dp.toPx() } * tapPulse
        val windowRect = editorComposerBounds(flbBounds, fabBounds, overlayBounds, reveal, liftPx)
            .translate(-overlayBounds.topLeft - Offset(0f, tapLift))
        val backStrength = if (editorMotion) backMotion.progress else 0f
        val windowRadius = flbBounds.asGlassBlob().cornerRadius * (1f - expansion) +
            screenCornerRadiusPx * expansion
        val fabSize = 1f - editorPhase(reveal, EditorFusionContact, EditorFullWidth)
        val fabCenter = fabBounds.center - overlayBounds.topLeft -
            Offset(0f, liftPx * 0.8f * editorComposerLift(reveal) + tapLift * 0.65f)
        val fabRect = Rect(
            center = fabCenter,
            radius = minOf(fabBounds.width, fabBounds.height) * 0.5f * fabSize,
        )
        val fusePx = with(density) { 26.dp.toPx() } *
            editorPhase(reveal, 0f, 0.08f) * fabSize
        val surfaceHandoff = editorSurfaceHandoff(reveal)
        val rimAlpha = if (blurChatPage) {
            surfaceHandoff * maxOf(editorGlassAlpha(reveal), 0.75f * backStrength)
        } else 0f
        val windowShape = androidx.compose.foundation.shape.GenericShape { _, _ ->
            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                windowRect, androidx.compose.ui.geometry.CornerRadius(windowRadius),
            ))
        }
        val backing = if (blurChatPage) {
            val tintAlpha = 0.12f + 0.10f * editorBackdropProgress(reveal) + 0.14f * backStrength
            cs.surface.copy(alpha = tintAlpha * surfaceHandoff)
        } else {
            val solid = androidx.compose.ui.graphics.lerp(fallback, cs.surface, expansion)
            solid.copy(alpha = solid.alpha * surfaceHandoff)
        }
        val backdrop = LocalMochiBackdrop.current

        Box(Modifier.fillMaxSize().onGloballyPositioned {
            overlayBounds = Rect(it.localToWindow(Offset.Zero), it.size.toSize())
        }) {
        Box(
            // Record the editor surface, title and draft together. The format bar is a sibling.
            Modifier.matchParentSize().editorGenieWarp(
                window = windowRect,
                reveal = reveal,
                openingAnchorX = if (openingAnchorX.isFinite()) openingAnchorX - overlayBounds.left else windowRect.center.x,
                back = backMotion.copy(touchY = backMotion.touchY - overlayBounds.top),
                enabled = editorMotion,
            ),
        ) {
        Box(Modifier.matchParentSize()) {
            if (reveal > 0f && !overlayBounds.isEmpty) {
                EditorBackdropSurface(
                    modifier = Modifier.matchParentSize(),
                    main = MochiBlob(windowRect, windowRadius),
                    fab = fabRect.asGlassBlob(),
                    fusePx = fusePx,
                    tint = backing,
                    rimAlpha = rimAlpha,
                )
            }
            Box(Modifier.matchParentSize().clip(windowShape)) {
            if (tapPulse > 0f && blurChatPage) {
                Canvas(Modifier.matchParentSize()) {
                    val glassLight = androidx.compose.ui.graphics.lerp(cs.primary, Color.White, 0.58f)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glassLight.copy(alpha = 0.22f * tapPulse), Color.Transparent),
                            center = tapCenter - Offset(0f, tapLift),
                            radius = (36.dp + 112.dp * reveal).toPx(),
                        ),
                    )
                }
            }
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
                        .mochiBlurReveal({ editorPhase(reveal, 0.52f, 0.92f) }, rise = if (editorMotion) 12.dp else 0.dp)
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
                        .mochiBlurReveal({ editorPhase(reveal, 0.38f, 0.84f) }, rise = if (editorMotion) 16.dp else 0.dp)
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
        }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = popupPadStart,
                    top = popupPadTop,
                    end = popupPadEnd,
                    bottom = popupPadBottom,
                )
                .padding(horizontal = toolbarMargin, vertical = toolbarMargin),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { toolbarHeightPx = it.height }
                    .mochiBlurReveal({ editorPhase(reveal, 0.57f, 0.92f) }, rise = if (editorMotion) 12.dp else 0.dp),
            ) {
                if (reveal > 0.57f) {
                    MochiGlassSurface(
                        modifier = Modifier.matchParentSize(),
                        material = (MochiTheme.materials.controlGlass ?: MochiGlass())
                            .copy(adaptive = false, fallback = cs.surfaceContainerHigh),
                        backdrop = backdrop.takeIf { MochiTheme.materials.controlGlass != null },
                    ) {}
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 格式与附件按钮可以横向滚，发送键固定在最右边，不会被挤出屏幕
                    Row(
                        modifier = Modifier
                            .weight(1f)
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
                            scaleX = sendPop.value
                            scaleY = sendPop.value
                        },
                    )
                }
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

/** Mochi's in-window menu leaves the editor and its keyboard in the same window. */
@Composable
private fun EditorHeadingTool(
    richState: RichTextState,
    modifier: Modifier = Modifier,
) {
    val current = richState.currentHeadingStyle
    val active = current.level > 0
    val colors = MochiTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(richState.selection) { open = false }
    val items = remember {
        EditorHeadingOptions.map { (style, label) ->
            MochiItem(label, icon = { tint ->
                EditorToolGlyph(if (style.level == 0) "T" else "H${style.level}", tint, fontWeight = FontWeight.Bold)
            })
        }.toPersistentList()
    }
    MochiIconDropdown(
        expanded = open,
        onExpandedChange = { open = it },
        items = items,
        selectedIndex = EditorHeadingOptions.indexOfFirst { it.first == current },
        onItemClick = { index -> richState.setHeadingStyle(EditorHeadingOptions[index].first) },
        modifier = modifier,
        size = 40.dp,
        menuWidth = 208.dp,
        colors = MochiDropdownDefaults.iconDropdownColors(
            buttonColor = if (active) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
            buttonContentColor = if (active) colors.primary else colors.onSurfaceVariant,
        ),
        behavior = MochiDropdownDefaults.behavior(align = MochiAlign.Center),
    ) {
        if (active) {
            Box(Modifier.semantics { contentDescription = "标题" }) {
                EditorToolGlyph("H${current.level}", colors.primary, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(title, "标题", modifier = Modifier.size(20.dp))
        }
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

