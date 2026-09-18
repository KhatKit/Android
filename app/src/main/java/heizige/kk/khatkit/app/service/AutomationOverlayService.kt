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
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khatkit.uikit.KhatKitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AutomationOverlay"

/**
 * 自动化状态悬浮看板：自动化（卡片 / 事件触发 / AI 设备工具）运行期间，
 * 用 [WindowManager] + `TYPE_APPLICATION_OVERLAY` 在系统最上层显示当前步骤、
 * 最近 4 步历史与「停止」按钮；空闲约 3 秒后移除视图并 [stopSelf]。
 *
 * 未授予悬浮窗权限（SYSTEM_ALERT_WINDOW）时直接结束，不显示也不崩溃。
 */
class AutomationOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

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
        if (!Settings.canDrawOverlays(this)) {
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
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AutomationOverlayService)
            setViewTreeViewModelStoreOwner(this@AutomationOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutomationOverlayService)
            setContent {
                KhatKitTheme {
                    AutomationStatusBoard(
                        status = AutomationBus.status.collectAsState().value,
                        onStop = { AutomationBus.requestCancel() },
                        onDrag = ::moveOverlay,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }
        try {
            manager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            return
        }
        overlayView = view
        overlayParams = params
        windowManager = manager
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun detachOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
            .onFailure { Log.w(TAG, "removeView failed", it) }
        overlayView = null
        overlayParams = null
        windowManager = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private fun observeStatus() {
        if (observeJob != null) return
        observeJob = scope.launch {
            AutomationBus.status.collect { status ->
                if (status == null) {
                    delay(IDLE_HIDE_DELAY_MS)
                    if (AutomationBus.status.value == null) {
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
        params.y += dy.toInt()
        runCatching { windowManager?.updateViewLayout(view, params) }
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
        private const val IDLE_HIDE_DELAY_MS = 3_000L

        /** 启动看板服务；未授予悬浮窗权限时直接跳过，后台启动受限等异常也被吞掉。 */
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
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
    onStop: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val current = status ?: return
    KedgeSurface(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "自动化运行中",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                KedgeTextButton(onClick = onStop, enabled = !current.cancelRequested) {
                    Text(if (current.cancelRequested) "正在停止…" else "停止")
                }
            }
            Text(
                text = current.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (current.detail.isNotBlank()) {
                Text(
                    text = current.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val history = current.recent.dropLast(1).takeLast(3).reversed()
            if (history.isNotEmpty()) {
                Text(
                    text = "最近",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                history.forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
