package heizige.kk.khatkit.app.feature.automation

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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import heizige.kk.khatkit.app.feature.automation.data.AutomationBus
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeImpl
import heizige.kk.khromia.components.ToastCardContainer
import heizige.kk.khromia.components.ToastDefaults
import heizige.kk.khatkit.uikit.KhatKitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "AutomationOverlay"

/** 看板内部内容切换（AnimatedContent/SizeTransform）的动画时长。 */
private const val ANIMATION_MS = 150

/** 自动化空闲/结束提示后看板保持可见的时长，到时才播放退场动画并移除视图。 */
private const val IDLE_EXIT_DELAY_MS = 3_000L

/** 无显式 finish 的运行判定"会话结束"的空闲阈值：短暂无更新即收尾，保证看板不滞留 */
private const val SESSION_END_IDLE_MS = 5_000L

/** 退场动画结束后再 detach 的余量，保证 AnimatedVisibility 播完（slideOut tween 500 为最长分量）。 */
private const val EXIT_SETTLE_MS = 700L

/**
 * 悬浮窗外围留白（host 级，不是卡片规格）：上游/软件内的宿主是全屏 window，动画怎么冲都在窗口内；
 * 这里窗口只包裹看板（全屏会触发 Android 12+ 触摸遮挡，见类注释），所以显式留出上/左/右 16dp 给
 * `scaleIn` 弹簧的约 6.8% 过冲（dampingRatio 0.65）与卡片外的 6dp 阴影，避免被窗口边界裁掉。
 * 不加底部留白：窗口底边贴屏幕物理底部，卡片停靠位置完全由 Toast 规格的屏短边 * 0.2f 留白决定，
 * 与软件内/ Khromia 一致。
 */
private val HostRoom = 16.dp

// 卡片视觉（形状 / 0.95 透明度 / 48dp 最小高度 / 屏短边 0.7 最大宽度 / 6dp 黑色阴影 /
// 屏短边 0.2f 底部留白 + 24dp 顶部与 12dp 左右留白 / 16dp 内容内边距 / 文字样式）全部来自
// heizige.kk.khromia.components.ToastDefaults + ToastCardContainer，与软件内看板、
// Khromia toast（上游 ImageToolbox 规格）共用同一份常量，本文件不再硬编码副本。

/**
 * 自动化状态悬浮看板：自动化（卡片 / 事件触发 / AI 设备工具）运行期间，
 * 仅显示当前步骤；授权请求期间显示请求文本 + 倒计时与 MD3 ButtonGroup（✓ / ✗）。
 * 一次运行结束（[AutomationBus.finish]）
 * 后先展示「任务完成」约 [IDLE_EXIT_DELAY_MS]，再播放 Khromia Toast（ImageToolbox 规格）同款
 * 退场动画并等待 [EXIT_SETTLE_MS] 后移除视图、[stopSelf]。空闲或结束提示期间出现新活动会取消退场。
 * 直接 AI 工具序列不调用 [AutomationBus.finish]：看板在有活动后超过 [SESSION_END_IDLE_MS]
 * 无更新即判定会话结束并走同样的完成态退场；卡片/触发运行由 [AutomationBus.begin]
 * 会话持有标记保护，但持有超过 [AutomationBus.SESSION_HOLD_TTL_MS] 无活动（含异常路径
 * 泄漏的陈旧持有）也会被强制收尾，保证悬浮板必定消失。
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
 * 正常穿透，退场动画也在窗口底边处收住。
 *
 * 因为窗口不是全屏，窗口底边就是硬裁切边界（软件内看板挂 decorView，全屏宿主没有这个问题），
 * 所以入场/退场的滑位移取「节点高度一半」与「卡片下方窗口内留白」的较小值：位移 ≤ 留白
 * ⇒ 动画全程卡片都不会越过窗口底边。留白本身来自 Toast 规格的屏短边 * 0.2f。
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
                        onRemember = { AutomationBus.approveAndRemember() },
                        onDismiss = { AutomationBus.hideOverlayTemporarily() },
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
            // y = 0：窗口底边贴住屏幕物理底部，看板自身用 Toast 规格的
            // 屏短边 * 0.2f 底部留白（+ systemBarsPadding）决定离屏距离；
            // 入场/退场的滑位移被 clamp 在这块留白内，不会越过窗口底边被裁掉。
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
                                // 完成态：短暂展示「任务完成」，立即释放常亮，到时退场并复位总线
                                releaseWakeLock()
                                hideJob = scope.launch {
                                    delay(IDLE_EXIT_DELAY_MS)
                                    if (AutomationBus.status.value?.finished != true) return@launch
                                    hideBoard()
                                    AutomationBus.clear()
                                }
                            }

                            approval == null && status != null -> {
                                // 没有显式 finish 的运行：空闲 SESSION_END_IDLE_MS 即视为会话结束；
                                // 有效会话持有先等到 TTL 过期再强制收尾，陈旧/泄漏的持有不会让看板滞留。
                                acquireWakeLock()
                                hideJob = scope.launch {
                                    delay(SESSION_END_IDLE_MS)
                                    val holdRemaining = AutomationBus.sessionHoldRemainingMs()
                                    if (holdRemaining > 0) delay(holdRemaining)
                                    val current = AutomationBus.status.value
                                    if (current == null || current.finished) return@launch
                                    if (AutomationBus.pendingApproval.value != null) return@launch
                                    AutomationBus.finish()
                                }
                            }

                            else -> acquireWakeLock()
                        }
                    } else if (status?.finished == true && approval == null) {
                        // 窗口未能挂载：完成态不会自行退场，直接复位总线并结束服务
                        AutomationBus.clear()
                        stopSelf()
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

        /** 应用回到前台时移除后台悬浮看板，状态仍保留在 AutomationBus。 */
        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, AutomationOverlayService::class.java),
                )
            }.onFailure { Log.w(TAG, "stop failed", it) }
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
    onRemember: () -> Unit,
    onDismiss: () -> Unit,
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
    // 进入/退场与 Khromia Toast（GlobalToastHost → 上游 ImageToolbox ToastDefaults.transition）完全同源：
    // fadeIn(tween(300)) + scaleIn(spring(0.65f, MediumLow), 底部中点) + slideIn(spring(StiffnessHigh))；
    // 退场 fadeOut(tween(250)) + slideOut(tween(500)) + scaleOut(spring(MediumBouncy, MediumLow), 底部中点)，
    // 滑动距离仍用冻结的稳定高度 slideHeightPx / 2。
    //
    // 与软件内唯一的不同：这里不是全屏窗口（全屏会触发 Android 12+ 触摸遮挡，见类注释），
    // 窗口底边就是硬裁切边界，而卡片下方窗口内只有 Toast 自身的
    // 屏短边 * 0.2f 底部留白（`it` 是整个节点 = 卡片 + 该留白 + 顶部/左右留白 + 系统栏内边距，
    // 在 411dp 宽屏上节点约 card + 154dp，因此 `it / 2` 基本恒大于这块留白）。所以把位移
    // clamp 到留白本身：位移 ≤ 留白 ≤ 卡片下方窗口内实际空闲 ⇒ 入场/退场全程卡片都不会越过窗口底边。
    // 数值：留白 = 0.2 * 屏短边 ≈ 82dp @411dp；节点 `it / 2` ≈ 107dp（卡片 60dp 时）→ clamp 生效，
    // 位移取 82dp。这正是旧实现的问题：当时卡片下方只有 48dp 留白，`it / 2` ≈ 107dp 超出约 59dp，
    // 入场时卡片底部被窗口底边裁掉；clamp 之后位移 ≤ 留白，全程不越界。
    val bottomRoomPx = with(LocalDensity.current) {
        (ToastDefaults.screenSizeMin() * ToastDefaults.BottomScreenFraction).roundToPx()
    }
    val slideOffset: (Int) -> Int = { fullHeight ->
        val half = if (slideHeightPx > 0) slideHeightPx / 2 else fullHeight / 2
        minOf(half, bottomRoomPx)
    }
    Box(
        modifier = Modifier.padding(start = HostRoom, top = HostRoom, end = HostRoom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = ToastDefaults.enterTransition(slideOffset),
            exit = ToastDefaults.exitTransition(slideOffset),
        ) {
            if (status != null || approval != null) {
                AutomationToast(
                    status = status,
                    approval = approval,
                    onApprove = onApprove,
                    onDeny = onDeny,
                    onRemember = onRemember,
                    onDismiss = onDismiss,
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
 * 看板容器：卡片规格由 Khromia 的 [ToastCardContainer] 提供（同一份常量也喂给软件内看板），
 * 即上游 ImageToolbox `ToastHost.kt` 的 Toast：
 * - 形状 AutoCornersShape(32.dp)、透明度 0.95、heightIn(min = 48.dp)
 * - 宽度上限 = 屏短边 * 0.7f、6dp 黑色（ambient/spot）阴影
 * - 宿主留白 = 屏短边 * 0.2f 底部 + 24dp 顶部 + 12dp 左右 + systemBarsPadding/imePadding
 * - 内容内边距 16dp、文字 MaterialTheme.typography.bodySmall（12.sp 常规 / 0.4sp）
 * 本文件不再硬编码这些值。
 *
 * 内容由 [AnimatedContent] 承载：尺寸变化交给 [SizeTransform] 做容器动画，子内容
 * 始终按目标尺寸测量，避免独立 animateContentSize 在 WRAP_CONTENT 悬浮窗里
 * 逐帧把高度反哺测量导致抖动。
 */
@Composable
private fun AutomationToast(
    status: AutomationBus.AutomationStatus?,
    approval: AutomationBus.ApprovalRequest?,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRemember: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = status?.finished == true && approval == null
    val content: BoardContent = when {
        finished -> BoardContent.Finished
        approval != null -> BoardContent.Approval(approval)
        else -> BoardContent.Status
    }

    // 卡片视觉与软件内看板/ Khromia toast 同源：形状、0.95 透明度、48dp 最小高度、
    // 屏短边 0.7 最大宽度、6dp 黑色阴影、屏短边 0.2f 底部留白 + 24/12dp 宿主留白
    // （含 systemBarsPadding）全部由 ToastCardContainer 提供。
    ToastCardContainer(
        modifier = modifier.clickable(onClick = onDismiss),
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
                modifier = Modifier.padding(ToastDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                when (target) {
                    BoardContent.Finished -> {
                        // 纯文本提示，不出现任何图标/按钮
                        Text(
                            text = "任务完成",
                            style = ToastDefaults.textStyle,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    is BoardContent.Approval -> {
                        val remainingSeconds = rememberApprovalRemainingSeconds(target.request)
                        AnimatedContent(
                            targetState = target.request.title,
                            transitionSpec = {
                                (
                                    fadeIn(tween(ANIMATION_MS)) +
                                        scaleIn(tween(ANIMATION_MS), initialScale = 0.92f)
                                    ) togetherWith (
                                    fadeOut(tween(ANIMATION_MS)) +
                                        scaleOut(tween(ANIMATION_MS), targetScale = 0.92f)
                                    ) using SizeTransform(clip = false) { _, _ -> tween(ANIMATION_MS) }
                            },
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                            label = "approvalTitle",
                        ) { title ->
                            Text(
                                text = "$title（${remainingSeconds}s）",
                                style = ToastDefaults.textStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ApprovalButtonGroup(
                            onApprove = onApprove,
                            onDeny = onDeny,
                            onRemember = onRemember,
                        )
                    }

                    BoardContent.Status -> {
                        // 步骤文本每次变化都做一次淡入淡出 + 轻微垂直滑动，
                        // 尺寸变化交给 SizeTransform（clip = false），避免窗口 relayout 抖动
                        AnimatedContent(
                            targetState = status?.label.orEmpty(),
                            transitionSpec = {
                                (
                                    fadeIn(tween(ANIMATION_MS)) +
                                        scaleIn(tween(ANIMATION_MS), initialScale = 0.92f)
                                    ) togetherWith (
                                    fadeOut(tween(ANIMATION_MS)) +
                                        scaleOut(tween(ANIMATION_MS), targetScale = 0.92f)
                                    ) using SizeTransform(clip = false) { _, _ -> tween(ANIMATION_MS) }
                            },
                            contentAlignment = Alignment.Center,
                            label = "boardText",
                        ) { label ->
                            Text(
                                text = label,
                                style = ToastDefaults.textStyle,
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
 * 授权操作组：官方 MD3 [ButtonGroup] + connected 形状，固定 2 项：
 * ✗（拒绝，leading）/ ✓（允许，trailing）。✓ 单击即允许本次，长按弹出
 * 「仅本次允许 / 10 分钟内自动允许」菜单；关闭最小交互尺寸约束以保持
 * Toast 紧凑（按钮与图标均为小尺寸）。
 */
@Composable
private fun ApprovalButtonGroup(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRemember: () -> Unit,
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
                    ApproveButton(
                        shapes = trailingShapes,
                        onApprove = onApprove,
                        onRemember = onRemember,
                    )
                },
                menuContent = { },
            )
        }
    }
}

/**
 * ✓ 允许按钮：外观沿用 MD3 [ToggleButton]（connected trailing 形状、primary 配色及
 * 按压形变/涟漪），指针事件交给上层透明 [Box] 的 combinedClickable：单击允许本次，
 * 长按弹出与 Toast 深色反色底一致的 [DropdownMenu]；两者共享 interactionSource 且
 * combinedClickable 不附带 indication，避免双层涟漪。
 */
@Composable
private fun ApproveButton(
    shapes: ToggleButtonShapes,
    onApprove: () -> Unit,
    onRemember: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    // 菜单沿用卡片同款反色底与文字规格（同一份 ToastDefaults 来源）。
    val menuContainerColor = ToastDefaults.color
    val menuContentColor = ToastDefaults.contentColor
    val itemColors = MenuDefaults.itemColors(
        textColor = menuContentColor,
        leadingIconColor = menuContentColor,
        trailingIconColor = menuContentColor,
    )

    Box(modifier = Modifier.size(ApprovalButtonSize)) {
        ToggleButton(
            checked = false,
            onCheckedChange = { },
            shapes = shapes,
            colors = ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(0.dp),
            interactionSource = interactionSource,
            modifier = Modifier
                .size(ApprovalButtonSize)
                .clearAndSetSemantics { },
        ) {
            Icon(
                imageVector = check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { contentDescription = "允许" }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = "仅本次允许",
                    onLongClickLabel = "更多允许选项",
                    onLongClick = { menuExpanded = true },
                    onClick = onApprove,
                ),
        ) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = menuContainerColor.copy(alpha = ToastDefaults.Alpha),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "仅本次允许",
                            style = ToastDefaults.textStyle,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onApprove()
                    },
                    colors = itemColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(32.dp),
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "10 分钟内自动允许",
                            style = ToastDefaults.textStyle,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRemember()
                    },
                    colors = itemColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(32.dp),
                )
            }
        }
    }
}
