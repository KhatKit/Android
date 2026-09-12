package heizige.kk.khatkit.bridge.impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 下载前台服务：脚本退出、界面关闭后保持进程，下载继续跑（设计文档 7.3）。
 * 断点续传由 [DownloadManagerImpl] 的 Range 逻辑负责，这里只保证"活着 + 可见"。
 */
class KhatKitDownloadService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(this))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "khatkit_downloads"
        private const val NOTIFICATION_ID = 0x4B4D

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KhatKitDownloadService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, KhatKitDownloadService::class.java))
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
        }

        fun update(context: Context) {
            runCatching {
                ensureChannel(context)
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context))
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "KhatKit 下载",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "卡片后台下载" }
            )
        }

        private fun buildNotification(context: Context): Notification {
            val active = DownloadStatus.activeCount
            val text = DownloadStatus.label.ifBlank { "正在下载 $active 个任务" }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("KhatKit 正在下载")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, active > 0)
                .build()
        }
    }
}

/** 进程内下载状态，供前台服务通知展示。 */
object DownloadStatus {
    @Volatile
    var activeCount: Int = 0

    @Volatile
    var label: String = ""
}
