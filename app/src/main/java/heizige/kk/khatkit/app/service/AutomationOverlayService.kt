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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import heizige.kk.kedge.components.KedgeButtonDefaults
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
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
import kotlin.math.roundToInt

private const val TAG = "AutomationOverlay"

/** 与 Khromia Toast（GlobalToastHost 默认 durations=150L）完全一致的动画时长。 */
private const val ANIMATION_MS = 150

/** 自动化空闲后看板保持可见的时长，到时才播放退场动画并移除视图。 */
private const val IDLE_EXIT_DELAY_MS = 3_000L

/** 退场动画结束后再 detach 的余量，保证 AnimatedVisibility 播完。 */
private const val EXIT_SETTLE_MS = 200L

private val ToastRunningColor = Color(0xFF7ED9A7)
private val ToastStoppingColor = Color(0xFFFFB4AB)

/** Khromia Toast 的视觉常量：0.87 透明度、胶囊形、12dp 阴影、48dp 最小高度。 */
private const val TOAST_ALPHA = 0.87f
private val ToastMaxWidth = 300.dp
private val ToastMinHeight = 48.dp
private val ToastShadowElevation = 12.dp

/**
 * 自动化状态悬浮看板：自动化（卡片 / 事件触发 / AI 设备工具）运行期间，
 * 显示当前步骤、最近步骤与「停止」按钮；空闲后先保持 [IDLE_EXIT_DELAY_MS] 可见，
 * 再播放 Khromia Toast 同款退场动画（[ANIMATION_MS] ms）并移除视图、[stopSelf]。
 * 运行期间窗口保持常亮并持有 [PowerManager.WakeLock]，空闲或销毁时释放。
 *
 * 无障碍服务在线时优先走 `TYPE_ACCESSIBILITY_OVERLAY`（层级高于状态栏/通知栏），
 * 否则回退 `TYPE_APPLICATION_OVERLAY`（需要 SYSTEM_ALERT_WINDOW）。两者都没有时
 * 直接结束，不显示也不崩溃。
 */
class AutomationOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var hideJob: Job? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var overlayBridge: AccessibilityBridgeImpl? = null
    private val overlayVisible = mutableStateOf(false)
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
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
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
                        onStop = { AutomationBus.requestCancel() },
                        onApprove = { AutomationBus.approve() },
                        onDeny = { AutomationBus.deny() },
                        onDrag = ::moveOverlay,
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
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (BOTTOM_MARGIN_DP * resources.displayMetrics.density).roundToInt()
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
        overlayParams = params
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
        overlayParams = null
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
                        acquireWakeLock()
                    }
                } else {
                    // 空闲：保留最后一帧内容，先停留 IDLE_EXIT_DELAY_MS，再播退场动画并 detach
                    releaseWakeLock()
                    hideJob?.cancel()
                    hideJob = scope.launch {
                        delay(IDLE_EXIT_DELAY_MS)
                        if (AutomationBus.status.value != null || AutomationBus.pendingApproval.value != null) {
                            return@launch
                        }
                        if (overlayView != null) {
                            overlayVisible.value = false
                            delay(EXIT_SETTLE_MS)
                            detachOverlay()
                        }
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.x += dx.toInt()
        params.y -= dy.toInt()
        runCatching { windowManager?.updateViewLayout(view, params) }
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
        private const val BOTTOM_MARGIN_DP = 96
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
    onStop: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    // 动画参数与 Khromia Toast（GlobalToastHost）逐字一致：
    // slide ±it/2、scale 0.5f、tween(150)
    AnimatedVisibility(
        visible = visible && (status != null || approval != null),
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(ANIMATION_MS),
        ) + fadeIn(
            animationSpec = tween(ANIMATION_MS),
        ) + scaleIn(
            initialScale = 0.5f,
            animationSpec = tween(ANIMATION_MS),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
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
                onStop = onStop,
                onApprove = onApprove,
                onDeny = onDeny,
                onDrag = onDrag,
            )
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

@Composable
private fun AutomationToast(
    status: AutomationBus.AutomationStatus?,
    approval: AutomationBus.ApprovalRequest?,
    onStop: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "automation_status").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "automation_status_dot",
    )
    val recent = status?.recent?.dropLast(1)?.takeLast(3)?.reversed().orEmpty()
    val cancelRequested = status?.cancelRequested == true
    val containerColor = MaterialTheme.colorScheme.inverseSurface.harmonizeWithPrimary()
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface.harmonizeWithPrimary()
    KedgeSurface(
        modifier = Modifier
            .widthIn(max = ToastMaxWidth)
            .heightIn(min = ToastMinHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        color = containerColor.copy(alpha = TOAST_ALPHA),
        contentColor = contentColor,
        shape = CircleShape,
        shadowElevation = ToastShadowElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulse)
                            .clip(CircleShape)
                            .background(if (cancelRequested) ToastStoppingColor else ToastRunningColor),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (approval != null) {
                        val remainingSeconds = rememberApprovalRemainingSeconds(approval)
                        Text(
                            text = approval.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (approval.detail.isNotBlank()) {
                            Text(
                                text = approval.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "$remainingSeconds 秒后自动拒绝",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (status != null) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (status.detail.isNotBlank()) {
                            Text(
                                text = status.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        recent.forEach { line ->
                            Text(
                                text = "· $line",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(primary = contentColor.copy(alpha = 0.92f)),
                ) {
                    KedgeTextButton(
                        onClick = onStop,
                        enabled = !cancelRequested,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shapes = KedgeButtonDefaults.md3ButtonShapes(shape = CircleShape),
                    ) {
                        Text(
                            text = if (cancelRequested) "正在停止…" else "停止",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (approval != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KedgeTextButton(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shapes = KedgeButtonDefaults.md3ButtonShapes(shape = CircleShape),
                    ) {
                        Text(
                            text = "允许",
                            style = MaterialTheme.typography.labelMedium,
                            color = ToastRunningColor,
                        )
                    }
                    KedgeTextButton(
                        onClick = onDeny,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shapes = KedgeButtonDefaults.md3ButtonShapes(shape = CircleShape),
                    ) {
                        Text(
                            text = "拒绝",
                            style = MaterialTheme.typography.labelMedium,
                            color = ToastStoppingColor,
                        )
                    }
                }
            }
        }
    }
}
