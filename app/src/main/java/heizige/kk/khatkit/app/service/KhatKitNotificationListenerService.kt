package heizige.kk.khatkit.app.service

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.koin.android.ext.android.inject

/**
 * 通知监听：把状态栏通知转成引擎事件。
 *
 * 需要用户在系统设置中手动授权；本服务独立于 TriggerService 运行，
 * 仅当主开关打开且命中卡片规则时才会真正执行卡片。
 */
class KhatKitNotificationListenerService : NotificationListenerService() {

    private val controller: TriggerController by inject()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        // 跳过自己发出的通知（例如卡片运行失败提示），避免自触发循环
        if (packageName == applicationContext.packageName) return
        if (!controller.settings.masterEnabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        runCatching { controller.engine.onNotification(packageName, title, text) }
            .onFailure { Log.e(TAG, "onNotificationPosted failed", it) }
    }

    companion object {
        private const val TAG = "KhatKitNotifListener"

        /** 通知监听权限是否已授予（用于设置页展示）。 */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val expected = "${context.packageName}/${KhatKitNotificationListenerService::class.java.name}"
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
