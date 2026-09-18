package heizige.kk.khatkit.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.automation.AutomationBus
import heizige.kk.khatkit.app.ui.icons.check
import heizige.kk.khatkit.app.ui.icons.close
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeImpl
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khromia.data.harmonizeWithPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "AutomationOverlay"

/** 与 Khromia Toast（GlobalToastHost 默认 durations=150L）完全一致的动画时长。 */
private const val ANIMATION_MS = 150

/** 自动化空闲/结束提示后看板保持可见的时长，到时才播放退场动画并移除视图。 */
private const val IDLE_EXIT_DELAY_MS = 3_000L

/** 直接 AI 工具序列判定"会话结束"的空闲阈值：足够长，避免每个操作之间都提示已结束 */
private const val SESSION_END_IDLE_MS = 15_000L

/** 退场动画结束后再 detach 的余量，保证 AnimatedVisibility 播完。 */
private const val EXIT_SETTLE_MS = 200L

/** Khromia Toast 的视觉常量：0.87 透明度、胶囊形、12dp 阴影、48dp 最小高度。 */
private const val TOAST_ALPHA = 0.87f
private val ToastMaxWidth = 300.dp
private val ToastMinHeight = 48.dp
private val ToastShadowElevation = 12.dp

/**
 * 阴影渲染留白：窗口只包裹看板本身，四周留 16dp 让 graphicsLayer 的 12dp 阴影
 * 不被窗口边界裁掉（底部方向由看板自带的 Toast 边距提供空间）。
 */
private val ToastShadowRoom = 16.dp

/**
 * 自动化状态悬浮看板：自动化（卡片 / 事件触发 / AI 设备工具）运行期间，
 * 仅显示当前步骤；授权请求期间显示请求文本 + 倒计时与 MD3 ButtonGroup（✓ / ✗）。
 * 一次运行结束（[AutomationBus.finish]）
 * 后先展示「自动化已结束」约 [IDLE_EXIT_DELAY_MS]，再播放 Khromia Toast 同款退场动画
 * （[ANIMATION_MS] ms）并移除视图、[stopSelf]。空闲或结束提示期间出现新活动会取消退场。
 * 直接 AI 工具序列不调用 [AutomationBus.finish]：看板在有活动后超过一个
 * [IDLE_EXIT_DELAY_MS] 无更新即判定会话结束并走同样的完成态退场；卡片/触发运行由
 * [AutomationBus.begin] 会话持有标记保护，不会因执行期间无进度更新被误判。
 * 运行期间窗口保持常亮并持有 [PowerManager.WakeLock]，空闲或销毁时释放。
 *
 * 无障碍服务在线时优先走 `TYPE_ACCESSIBILITY_OVERLAY`（层级高于状态栏/通知栏），
 * 否则回退 `TYPE_APPLICATION_OVERLAY`（需要 SYSTEM_ALERT_WINDOW）。两者都没有时
 * 直接结束，不显示也不崩溃。
 *
 * 窗口不是全屏：全屏窗口的触摸区域覆盖整个屏幕（`WindowState#getSurfaceTouchableRegion`
 * → `getTouchableRegion`），且 Android 12+ 的 `InputDispatcher#computeTouchOcclusionInfoLocked`
 * 会把全屏可见悬浮窗视为遮挡并拦截下层应用的触摸（`getTouchOcclusionMode` 对
 * `TYPE_APPLICATION_OVERLAY` 返回 USE_OPACITY、对 `TYPE_ACCESSIBILITY_OVERLAY` 返回
 * BLOCK_UNTRUSTED）。因此窗口只包裹看板并让底边贴住屏幕物理底部：看板以外区域触摸
 * 正常穿透，退场动画也能一路滑到屏幕最底端。
 */
class AutomationOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var hiddenJob: Job? = null
    private var hideJob: Job? = null
    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var overlayBridge: AccessibilityBridgeImpl? = null
    private val overlayVisible = mutableStateOf(false)
    private val overlayHidden = mutableStateOf(false)
    private val overlayStatus = mutableStateOf<AutomationBus.AutomationStatus?>(null)
    private val overlayApproval = mutableStateOf<AutomationBus.ApprovalRequest?>(null)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务先落地，再判断权限，避免 startForegroundService 超时
        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (AccessibilityBridgeHolder.current() == null && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission not granted, skip")
            stopSelf()
            return START_NOT_STICKY
        }
        attachOverlay()
        if (overlayView == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        observeStatus()
        observeHidden()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        hiddenJob?.cancel()
        hiddenJob = null
        hideJob?.cancel()
        hideJob = null
        releaseWakeLock()
        detachOverlay()
        scope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean = try {
        ensureChannel(this)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "startForeground failed", e)
        false
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 无障碍服务在线时走 TYPE_ACCESSIBILITY_OVERLAY：层级在状态栏/通知栏之上；
        // 否则回退到 TYPE_APPLICATION_OVERLAY（需要悬浮窗权限）。
        val bridge = AccessibilityBridgeHolder.current()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AutomationOverlayService)
            setViewTreeViewModelStoreOwner(this@AutomationOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutomationOverlayService)
            setContent {
                KhatKitTheme {
                    AutomationStatusBoard(
                        status = overlayStatus.value,
                        approval = overlayApproval.value,
                        visible = overlayVisible.value,
                        hidden = overlayHidden.value,
                        onApprove = { AutomationBus.approve() },
                        onDeny = { AutomationBus.deny() },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (bridge != null) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // y = 0：窗口底边贴住屏幕物理底部，看板自身用 Khromia Toast 的
            // 48dp + systemBarsPadding 决定离屏距离，退场动画可以直接滑到最底端。
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        val attached = if (bridge != null) {
            bridge.addOverlay(view, params)
        } else {
            runCatching { manager.addView(view, params) }.isSuccess
        }
        if (!attached) {
            Log.e(TAG, "attach overlay failed (${if (bridge != null) "accessibility" else "application"})")
            return
        }
        overlayView = view
        windowManager = manager
        overlayBridge = bridge
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun detachOverlay() {
        overlayVisible.value = false
        val view = overlayView ?: return
        val bridge = overlayBridge
        if (bridge != null) {
            bridge.removeOverlay(view)
        } else {
            runCatching { windowManager?.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
        }
        overlayView = null
        windowManager = null
        overlayBridge = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private fun observeStatus() {
        if (observeJob != null) return
        observeJob = scope.launch {
            combine(AutomationBus.status, AutomationBus.pendingApproval) { status, approval ->
                status to approval
            }.collect { (status, approval) ->
                if (status != null || approval != null) {
                    overlayStatus.value = status
                    overlayApproval.value = approval
                    hideJob?.cancel()
                    hideJob = null
                    if (overlayView == null) attachOverlay()
                    if (overlayView != null) {
                        overlayVisible.value = true
                        when {
                            status?.finished == true && approval == null -> {
                                // 完成态：短暂展示「自动化已结束」，立即释放常亮，到时退场并复位总线
                                releaseWakeLock()
                                hideJob = scope.launch {
                                    delay(IDLE_EXIT_DELAY_MS)
                                    if (AutomationBus.status.value?.finished != true) return@launch
                                    hideBoard()
                                    AutomationBus.clear()
                                }
                            }

                            approval == null && status != null && !AutomationBus.isSessionHeld() -> {
                                // 直接 AI 工具序列没有显式 finish：空闲超过一个退场窗口即视为会话结束，
                                // 标记完成后走完成态分支展示「自动化已结束」并退场。
                                // 卡片/触发等显式运行由 begin()/finish() 的会话持有标记保护，不受此影响。
                                acquireWakeLock()
                                hideJob = scope.launch {
                                    delay(SESSION_END_IDLE_MS)
                                    val current = AutomationBus.status.value
                                    if (current == null || current.finished) return@launch
                                    if (AutomationBus.pendingApproval.value != null) return@launch
                                    if (AutomationBus.isSessionHeld()) return@launch
                                    AutomationBus.finish()
                                }
                            }

                            else -> acquireWakeLock()
                        }
                    }
                } else {
                    releaseWakeLock()
                    hideJob?.cancel()
                    hideJob = null
                    if (overlayView != null) {
                        // 空闲：保留最后一帧内容，先停留 IDLE_EXIT_DELAY_MS，再播退场动画并 detach
                        hideJob = scope.launch {
                            delay(IDLE_EXIT_DELAY_MS)
                            if (AutomationBus.status.value != null || AutomationBus.pendingApproval.value != null) {
                                return@launch
                            }
                            hideBoard()
                        }
                    } else {
                        // 完成态退场已 detach：总线复位引起的空闲分支无需再次等待
                        stopSelf()
                    }
                }
            }
        }
    }

    /** 播放退场动画、移除视图并结束服务；完成态结束时的总线复位由调用方负责。 */
    private suspend fun hideBoard() {
        if (overlayView != null) {
            overlayVisible.value = false
            delay(EXIT_SETTLE_MS)
            detachOverlay()
        }
        stopSelf()
    }

    /**
     * 订阅总线的临时隐藏标记：只驱动看板退场/进场动画，不 detach 窗口也不 stopSelf，
     * 因此隐藏期间服务保持存活、到时（或授权强制恢复）能原样播进入动画。
     */
    private fun observeHidden() {
        if (hiddenJob != null) return
        hiddenJob = scope.launch {
            AutomationBus.overlayHidden.collect { hidden ->
                overlayHidden.value = hidden
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = manager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    WAKE_LOCK_TAG,
                ).apply { setReferenceCounted(false) }
            }
            wakeLock?.takeIf { !it.isHeld }?.acquire()
        }.onFailure { Log.w(TAG, "acquire wake lock failed", it) }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        runCatching { if (lock.isHeld) lock.release() }
            .onFailure { Log.w(TAG, "release wake lock failed", it) }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.automation_overlay_notification_title))
            .setContentText(getString(R.string.automation_overlay_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "automation_overlay"
        private const val NOTIFICATION_ID = 2003
        private const val WAKE_LOCK_TAG = "KhatKit:AutomationOverlay"

        /**
         * 启动看板服务；无障碍服务在线时无需悬浮窗权限，
         * 两者都没有则直接跳过，后台启动受限等异常也被吞掉。
         */
        fun start(context: Context) {
            if (AccessibilityBridgeHolder.current() == null && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "overlay permission not granted, skip start")
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationOverlayService::class.java),
                )
            }.onFailure { Log.e(TAG, "start failed", it) }
        }

        /** 幂等创建低重要性通知渠道；startForeground 前必须先有渠道。 */
        fun ensureChannel(context: Context) {
            val manager = context.applicationContext
                .getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.automation_overlay_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

@Composable
private fun AutomationStatusBoard(
    status: AutomationBus.AutomationStatus?,
    approval: AutomationBus.ApprovalRequest?,
    visible: Boolean,
    hidden: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val boardVisible = visible && !hidden && (status != null || approval != null)

    // 与 Khromia Toast（GlobalToastHost）一致的可见性驱动：MutableTransitionState 从 false
    // 开始，首帧组合后再由 LaunchedEffect 翻到目标值，保证第一次展示也播进入动画。
    val visibleState = remember { MutableTransitionState(false) }

    // 滑动位移基准高度：只在可见性切换的那一刻冻结当前实测高度（等价 Toast 的 it），
    // 内容尺寸动画（SizeTransform）逐帧改高度时 it/2 不再跟着变，避免窗口 relayout
    // 与滑动 offset 相互追逐造成鬼畜/振荡。
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    var slideHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(boardVisible) {
        if (visibleState.targetState != boardVisible) {
            if (measuredHeightPx > 0) slideHeightPx = measuredHeightPx
            visibleState.targetState = boardVisible
        }
    }
    // 动画参数与 Khromia Toast（GlobalToastHost）逐字一致：
    // slide ±it/2、scale 0.5f、tween(150)（这里 it 用冻结的稳定高度）。
    // Box 只包住看板；窗口底边已在屏幕物理底部，因此 ±it/2 的位移会一直渲染到最底端。
    Box(
        modifier = Modifier.padding(start = ToastShadowRoom, top = ToastShadowRoom, end = ToastShadowRoom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                initialOffsetY = { slideHeightPx / 2 },
                animationSpec = tween(ANIMATION_MS),
            ) + fadeIn(
                animationSpec = tween(ANIMATION_MS),
            ) + scaleIn(
                initialScale = 0.5f,
                animationSpec = tween(ANIMATION_MS),
            ),
            exit = slideOutVertically(
                targetOffsetY = { slideHeightPx / 2 },
                animationSpec = tween(ANIMATION_MS),
            ) + fadeOut(
                animationSpec = tween(ANIMATION_MS),
            ) + scaleOut(
                targetScale = 0.5f,
                animationSpec = tween(ANIMATION_MS),
            ),
        ) {
            if (status != null || approval != null) {
                AutomationToast(
                    status = status,
                    approval = approval,
                    onApprove = onApprove,
                    onDeny = onDeny,
                    modifier = Modifier.onSizeChanged { size ->
                        measuredHeightPx = size.height
                        if (slideHeightPx == 0) slideHeightPx = size.height
                    },
                )
            }
        }
    }
}

/**
 * 授权剩余秒数：以 [AutomationBus.ApprovalRequest.requestedAt] 为起点每秒刷新，
 * 与总线 [AutomationBus.APPROVAL_TIMEOUT_MS] 同基准，归零即自动拒绝触发点。
 */
@Composable
private fun rememberApprovalRemainingSeconds(approval: AutomationBus.ApprovalRequest?): Int {
    val request = approval
    val remaining by produceState(
        initialValue = request?.let { remainingApprovalSeconds(it.requestedAt) } ?: 0,
        key1 = request?.id,
    ) {
        if (request == null) {
            value = 0
        } else {
            while (true) {
                val seconds = remainingApprovalSeconds(request.requestedAt)
                value = seconds
                if (seconds <= 0) break
                delay(1_000L)
            }
        }
    }
    return remaining
}

/** 向上取整的剩余秒数（0..60）。 */
private fun remainingApprovalSeconds(requestedAt: Long): Int {
    val remainingMs = (requestedAt + AutomationBus.APPROVAL_TIMEOUT_MS - System.currentTimeMillis())
        .coerceAtLeast(0L)
    return ((remainingMs + 999L) / 1_000L).toInt()
}

/**
 * 看板内容种类：作为 [AnimatedContent] 的 targetState 驱动内容切换与尺寸动画。
 * [Approval] 自带请求数据，过渡期间旧内容仍渲染旧请求（不会被新值覆盖）。
 */
private sealed interface BoardContent {
    data object Finished : BoardContent

    data class Approval(val request: AutomationBus.ApprovalRequest) : BoardContent

    data object Status : BoardContent
}

/**
 * 看板容器逐项对齐 Khromia Toast 的 ToastCard：
 * - color = inverseSurface.harmonizeWithPrimary().copy(alpha = 0.87)
 * - contentColor = inverseOnSurface.harmonizeWithPrimary()
 * - shape = CircleShape
 * - padding(bottom = 48.dp) + systemBarsPadding()（与 Toast 相同的屏幕边距）
 * - heightIn(min = 48.dp)、widthIn(max = 300.dp)
 * - graphicsLayer { shadowElevation = 12.dp.toPx(); shape = CircleShape; clip = false }
 * - 单行 Row：普通/完成态仅当前步骤文本；授权态为「请求文本（剩余秒数）」+
 *   右侧 MD3 ButtonGroup（允许 / 拒绝），文本 12.sp / Medium / letterSpacing 0.5.sp。
 *
 * 内容由 [AnimatedContent] 承载：尺寸变化交给 [SizeTransform] 做容器动画，子内容
 * 始终按目标尺寸测量，避免独立 animateContentSize 在 WRAP_CONTENT 悬浮窗里
 * 逐帧把高度反哺测量导致抖动；clip = false 由外层 Surface 的 shape 裁剪兜底，
 * 不会裁掉 graphicsLayer 的 12dp 阴影。
 */
@Composable
private fun AutomationToast(
    status: AutomationBus.AutomationStatus?,
    approval: AutomationBus.ApprovalRequest?,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = status?.finished == true && approval == null
    val content: BoardContent = when {
        finished -> BoardContent.Finished
        approval != null -> BoardContent.Approval(approval)
        else -> BoardContent.Status
    }
    val containerColor = MaterialTheme.colorScheme.inverseSurface.harmonizeWithPrimary()
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface.harmonizeWithPrimary()

    Surface(
        color = containerColor.copy(alpha = TOAST_ALPHA),
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
            // 透明留白：给 12dp 阴影留出窗口内空间，避免被窗口边界裁剪
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .padding(bottom = 48.dp)
            .systemBarsPadding()
            .heightIn(min = ToastMinHeight)
            .widthIn(max = ToastMaxWidth)
            .graphicsLayer {
                // 关键点：通过 graphicsLayer 强制渲染阴影
                // 这能保证在 scale 和 fade 动画过程中阴影依然存在
                shadowElevation = ToastShadowElevation.toPx()
                shape = CircleShape
                clip = false
            },
    ) {
        AnimatedContent(
            targetState = content,
            transitionSpec = {
                (fadeIn(tween(ANIMATION_MS)) togetherWith fadeOut(tween(ANIMATION_MS)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(ANIMATION_MS) })
            },
            contentAlignment = Alignment.BottomCenter,
            label = "automationToastContent",
        ) { target ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                when (target) {
                    BoardContent.Finished -> {
                        // 纯文本提示，不出现任何图标/按钮
                        Text(
                            text = "自动化已结束",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    is BoardContent.Approval -> {
                        val remainingSeconds = rememberApprovalRemainingSeconds(target.request)
                        Text(
                            text = "${target.request.title}（${remainingSeconds}s）",
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        ApprovalButtonGroup(onApprove = onApprove, onDeny = onDeny)
                    }

                    BoardContent.Status -> {
                        // 步骤文本每次变化都做一次淡入淡出 + 轻微垂直滑动，
                        // 尺寸变化交给 SizeTransform（clip = false），避免窗口 relayout 抖动
                        AnimatedContent(
                            targetState = status?.label.orEmpty(),
                            transitionSpec = {
                                (
                                    fadeIn(tween(ANIMATION_MS)) +
                                        slideInVertically(tween(ANIMATION_MS)) { it / 4 }
                                    ) togetherWith (
                                    fadeOut(tween(ANIMATION_MS)) +
                                        slideOutVertically(tween(ANIMATION_MS)) { -it / 4 }
                                    ) using SizeTransform(clip = false) { _, _ -> tween(ANIMATION_MS) }
                            },
                            contentAlignment = Alignment.Center,
                            label = "boardText",
                        ) { label ->
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center,
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

/** 授权按钮尺寸：紧凑适配 Toast（32dp 触摸区，16dp 图标）。 */
private val ApprovalButtonSize = 32.dp

/**
 * 授权操作组：官方 MD3 [ButtonGroup] + connected 首尾形状，内含 ✓（允许）/ ✗（拒绝）
 * 两个 [FilledIconButton]，颜色取自 [MaterialTheme.colorScheme]；关闭最小交互尺寸约束
 * 以保持 Toast 紧凑（按钮与图标均为小尺寸）。
 */
@Composable
private fun ApprovalButtonGroup(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val leadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val trailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = false,
                        onCheckedChange = { onDeny() },
                        shapes = leadingShapes,
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(ApprovalButtonSize),
                    ) {
                        Icon(
                            imageVector = close,
                            contentDescription = "拒绝",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                menuContent = { },
            )
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = false,
                        onCheckedChange = { onApprove() },
                        shapes = trailingShapes,
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(ApprovalButtonSize),
                    ) {
                        Icon(
                            imageVector = check,
                            contentDescription = "允许",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                menuContent = { },
            )
        }
    }
}
