package heizige.kk.khatkit.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 事件触发前台服务：用户在主开关打开后启动。
 *
 * - 每 60s tick 一次 schedule（分钟级精度），每秒 tick 一次失败重试队列
 * - 每 ~1s 轮询无障碍 bridge 的前台包名，检测 app_launch 变化
 * - Wi-Fi / 网络 / 电量 / 屏幕 / 剪贴板 / 蓝牙 / 位置由 [TriggerEventSources] 投递
 *
 * 通知由 NotificationListenerService 独立投递（不需要本服务）。
 */
class TriggerService : Service() {

    private val controller: TriggerController by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loopJob: Job? = null
    private var sources: TriggerEventSources? = null

    @Volatile
    private var lastForegroundPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            null -> {
                if (!controller.settings.masterEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        if (!startForegroundCompat()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startSources()
        startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        sources?.stop()
        sources = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSources() {
        if (sources != null) return
        sources = TriggerEventSources(this, controller.engine, serviceScope).also { it.start() }
    }

    private fun startLoop() {
        if (loopJob != null) return
        loopJob = serviceScope.launch {
            // 先刷新卡片清单，再立即 tick 一次（避免等到下一个整分钟）
            controller.refreshCards()
            controller.engine.tickSchedule()
            var ticks = 0
            while (isActive) {
                delay(TICK_MS)
                ticks++
                if (ticks % SCHEDULE_TICKS == 0) {
                    runCatching { controller.engine.tickSchedule() }
                        .onFailure { Log.e(TAG, "tickSchedule failed", it) }
                }
                runCatching { controller.engine.tickRetries() }
                    .onFailure { Log.e(TAG, "tickRetries failed", it) }
                pollForegroundApp()
            }
        }
    }

    private fun pollForegroundApp() {
        val pkg = runCatching { AccessibilityBridgeHolder.current()?.currentPackage() }.getOrNull()
            ?: return
        if (pkg.isBlank() || pkg == lastForegroundPackage) return
        lastForegroundPackage = pkg
        runCatching { controller.engine.onAppLaunch(pkg) }
            .onFailure { Log.e(TAG, "onAppLaunch failed", it) }
    }

    private fun startForegroundCompat(): Boolean = try {
        TriggerController.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "startForeground failed", e)
        false
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, TriggerController.CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.trigger_service_notification_title))
            .setContentText(getString(R.string.trigger_service_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "TriggerService"
        const val ACTION_START = "heizige.kk.khatkit.app.action.TRIGGER_START"
        const val ACTION_STOP = "heizige.kk.khatkit.app.action.TRIGGER_STOP"
        const val NOTIFICATION_ID = 2002

        private const val TICK_MS = 1_000L
        private const val SCHEDULE_TICKS = 60

        /** 启动前台服务；调用方应确认主开关已打开且已获得通知权限。 */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, TriggerService::class.java).setAction(ACTION_START)
                )
            }.onFailure { Log.e(TAG, "start failed", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TriggerService::class.java))
        }
    }
}
