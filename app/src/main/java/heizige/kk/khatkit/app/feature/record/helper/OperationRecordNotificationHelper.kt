package heizige.kk.khatkit.app.feature.record.helper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.record.RecorderStopReceiver

/**
 * 录制中的常驻通知：展示已录制步数，并提供「停止」动作（App 切后台也能停）。
 */
object OperationRecordNotificationHelper {

    const val CHANNEL_ID = "khatkit_record"
    const val NOTIFICATION_ID = 2202

    fun show(context: Context, stepCount: Int) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        if (!canNotify(appContext)) return
        runCatching {
            // 再判一次权限：lint 看不清 canNotify 里的检查
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@runCatching
            }
            NotificationManagerCompat.from(appContext)
                .notify(NOTIFICATION_ID, build(appContext, stepCount))
        }
    }

    fun update(context: Context, stepCount: Int) {
        val appContext = context.applicationContext
        if (!canNotify(appContext)) return
        runCatching {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@runCatching
            }
            NotificationManagerCompat.from(appContext)
                .notify(NOTIFICATION_ID, build(appContext, stepCount))
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID) }
    }

    private fun build(context: Context, stepCount: Int): android.app.Notification {
        val openApp = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, RecorderStopReceiver::class.java).setAction(RecorderStopReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.record_notification_title))
            .setContentText(context.resources.getQuantityString(R.plurals.record_notification_steps, stepCount, stepCount))
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.record_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.record_label),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.record_label_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
