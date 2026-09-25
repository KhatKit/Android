package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.event.AppEvent
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.util.sendNotification
import kotlin.uuid.Uuid

/**
 * 订阅 [AppEventBus] 上的聊天生成事件，负责后台生成相关的系统通知
 * （焦点通知进度更新和生成完成通知）。
 */
class ChatNotificationManager(
    private val context: Application,
    appScope: AppScope,
    eventBus: AppEventBus,
    private val settingsStore: SettingsRepository,
) {
    private val isForeground = MutableStateFlow(false)
    private val liveNotificationService = AILiveNotificationService(context)
    private var currentGeneratingConversation: String? = null

    init {
        // ProcessLifecycleOwner 要求在主线程注册观察者
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> isForeground.value = true
                        Lifecycle.Event.ON_STOP -> isForeground.value = false
                        else -> {}
                    }
                }
            )
        }
        appScope.launch(Dispatchers.Default) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.ChatGenerationUpdate -> handleGenerationUpdate(event)
                    is AppEvent.ChatGenerationEnded -> handleGenerationEnded(event)
                    else -> {}
                }
            }
        }
    }

    private fun handleGenerationUpdate(event: AppEvent.ChatGenerationUpdate) {
        if (isForeground.value) return
        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        if (!displaySetting.enableNotificationOnMessageGeneration) return
        if (!displaySetting.enableLiveUpdateNotification) return

        val conversationId = event.conversationId.toString()

        // 如果是新对话的第一次生成，启动通知
        if (currentGeneratingConversation != conversationId) {
            currentGeneratingConversation = conversationId
            liveNotificationService.startGenerationNotification(conversationId)
        }

        // 更新生成进度
        val tokenCount = calculateTokenCount(event.lastMessage.parts)
        liveNotificationService.updateGenerating(tokenCount, conversationId)
    }

    private fun handleGenerationEnded(event: AppEvent.ChatGenerationEnded) {
        // 重置当前生成对话 ID
        currentGeneratingConversation = null

        // 完成焦点通知
        liveNotificationService.completeGeneration()

        val contentPreview = event.contentPreview ?: return
        if (isForeground.value) return
        if (!settingsStore.settingsFlow.value.displaySetting.enableNotificationOnMessageGeneration) return
        sendGenerationDoneNotification(event.conversationId, event.senderName, contentPreview)
    }

    /**
     * 计算消息中的 token 数量（简单估算）
     */
    private fun calculateTokenCount(parts: List<UIMessagePart>): Int {
        var count = 0
        for (part in parts) {
            when (part) {
                is UIMessagePart.Text -> count += part.text.length / 4
                is UIMessagePart.Reasoning -> count += part.reasoning.length / 4
                is UIMessagePart.Tool -> count += part.input.length / 4
                else -> {}
            }
        }
        return count
    }

    private fun sendGenerationDoneNotification(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String
    ) {
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = contentPreview
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
