package heizige.kk.khatkit.app.feature.automation

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 通知监听：把状态栏通知转成引擎事件。
 *
 * 需要用户在系统设置中手动授权；本服务独立于 TriggerService 运行，
 * 仅当主开关打开且命中卡片规则时才会真正执行卡片。
 *
 * 支持的事件：
 * - notification：新通知到达
 * - notification_click：用户在状态栏点击通知（[onNotificationRemoved] REASON_CLICK）
 * - notification_reply：**启发式**直接回复。点击带 RemoteInput 的通知后，
 *   监听同包名在 [REPLY_WINDOW_MS] 内到达的下一条通知，尽量从其 extras 恢复
 *   回复文本后投递；恢复不到时以空文本投递。
 *
 * 已知限制（notification_reply）：无法拦截 RemoteInput 实际发送的内容，也无法区分
 * 「回复」与「应用恰好推送了另一条同包通知」；部分应用只更新原通知而非重新发布，
 * 此时不会触发。因此该触发器只适合做尽力而为的自动化。
 */
@AndroidEntryPoint
class KhatKitNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var controller: TriggerController

    /** 最近一次点击「可回复」通知的登记（同包名下一跳消费一次，超时作废）。 */
    @Volatile
    private var pendingReply: PendingReply? = null

    private data class PendingReply(val packageName: String, val clickedAtElapsed: Long)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        // 跳过自己发出的通知（例如卡片运行失败提示），避免自触发循环
        if (packageName == applicationContext.packageName) return
        if (!controller.settings.masterEnabled) return

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notificationText(extras)

        // notification_reply 启发式：同包名且仍在窗口内 → 视为一次直接回复
        pendingReply?.takeIf { it.packageName == packageName }?.let { pending ->
            pendingReply = null
            if (SystemClock.elapsedRealtime() - pending.clickedAtElapsed <= REPLY_WINDOW_MS) {
                runCatching { controller.engine.onNotificationReply(packageName, title, text) }
                    .onFailure { Log.e(TAG, "onNotificationReply failed", it) }
            }
        }

        runCatching { controller.engine.onNotification(packageName, title, text) }
            .onFailure { Log.e(TAG, "onNotificationPosted failed", it) }
    }

    /**
     * 通知被移除。reason 为 [REASON_CLICK] 时投递 notification_click；
     * 若该通知带 RemoteInput 动作，则登记等待同包名下一条通知作为回复。
     */
    @Suppress("ObsoleteSdkInt")
    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        // onNotificationRemoved(sbn, ranking, reason) 为 API 24+；低版本没有点击回调
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val packageName = sbn?.packageName ?: return
        if (packageName == applicationContext.packageName) return
        if (!controller.settings.masterEnabled) return
        if (reason != REASON_CLICK) return

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notificationText(extras)

        runCatching { controller.engine.onNotificationClick(packageName, title, text) }
            .onFailure { Log.e(TAG, "onNotificationClick failed", it) }

        if (hasRemoteInput(sbn.notification)) {
            pendingReply = PendingReply(packageName, SystemClock.elapsedRealtime())
        }
    }

    private fun notificationText(extras: Bundle?): String =
        extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

    /** 通知是否带「直接回复」输入框（RemoteInput）。 */
    private fun hasRemoteInput(notification: Notification?): Boolean =
        notification?.actions?.any { action -> !action.remoteInputs.isNullOrEmpty() } == true

    companion object {
        private const val TAG = "KhatKitNotifListener"

        /** 点击「可回复」通知后，等待同包新通知判定为直接回复的时间窗。 */
        const val REPLY_WINDOW_MS = 10_000L

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
