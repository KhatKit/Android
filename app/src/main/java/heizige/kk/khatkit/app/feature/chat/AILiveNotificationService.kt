package heizige.kk.khatkit.app.feature.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.util.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * AI 对话焦点通知服务
 *
 * 在 AI 生成回复时显示实时更新的焦点通知，支持 Android 15+ 的 Live Update 功能
 */
class AILiveNotificationService(
    private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "ai_generation"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AILiveNotification"

        // 通知文案
        private const val NOTIFICATION_TITLE = "KhatKit AI 正在思考..."
        private val THINKING_STATES = listOf(
            "正在理解您的问题",
            "正在分析上下文",
            "正在组织回复",
            "正在生成回复"
        )
    }

    private var currentJob: Job? = null
    private var thinkingStateIndex = 0
    private var elapsedSeconds = 0

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI 生成通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 AI 正在生成回复的实时状态"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 开始显示 AI 生成通知
     *
     * @param conversationId 对话 ID（用于点击通知后返回对话）
     */
    fun startGenerationNotification(conversationId: String) {
        if (!NotificationUtil.hasNotificationPermission(context)) {
            return
        }

        // 重置状态
        thinkingStateIndex = 0
        elapsedSeconds = 0

        // 显示初始通知
        showNotification(getThinkingText(), elapsedSeconds, conversationId)

        // 启动更新循环
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(2.seconds)
                elapsedSeconds += 2

                // 每 4 秒切换一次思考状态文案
                if (elapsedSeconds % 4 == 0) {
                    thinkingStateIndex = (thinkingStateIndex + 1) % THINKING_STATES.size
                }

                showNotification(getThinkingText(), elapsedSeconds, conversationId)
            }
        }
    }

    /**
     * 更新通知为 "正在生成" 状态
     *
     * @param tokenCount 已生成的 token 数量
     */
    fun updateGenerating(tokenCount: Int, conversationId: String) {
        if (!NotificationUtil.hasNotificationPermission(context)) {
            return
        }

        val content = "已生成 $tokenCount 个 token"
        showNotification(content, elapsedSeconds, conversationId, isGenerating = true)
    }

    /**
     * 完成通知并自动消失
     */
    fun completeGeneration() {
        currentJob?.cancel()
        currentJob = null

        // 显示完成通知 1 秒后自动消失
        if (NotificationUtil.hasNotificationPermission(context)) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    /**
     * 取消通知
     */
    fun cancelNotification() {
        currentJob?.cancel()
        currentJob = null
        NotificationUtil.cancel(context, NOTIFICATION_ID)
    }

    /**
     * 显示或更新通知
     */
    private fun showNotification(
        content: String,
        elapsedSeconds: Int,
        conversationId: String,
        isGenerating: Boolean = false
    ) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversation_id", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_khatkit)
            .setContentTitle(if (isGenerating) "KhatKit AI 正在生成" else NOTIFICATION_TITLE)
            .setContentText(content)
            .setSubText(formatElapsedTime(elapsedSeconds))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSilent(true)

        // Android 15+ Live Update 支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            builder.setRequestPromotedOngoing(true)
        }

        // Android 16+ 状态栏 chip 文本
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText("AI 中")
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 获取当前思考状态文案
     */
    private fun getThinkingText(): String {
        return THINKING_STATES[thinkingStateIndex]
    }

    /**
     * 格式化已用时间
     */
    private fun formatElapsedTime(seconds: Int): String {
        return if (seconds < 60) {
            "${seconds}秒"
        } else {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "${minutes}分${remainingSeconds}秒"
        }
    }
}
