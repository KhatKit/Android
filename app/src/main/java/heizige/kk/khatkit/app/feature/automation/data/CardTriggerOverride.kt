package heizige.kk.khatkit.app.feature.automation.data

import heizige.kk.khatkit.card.CardManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户对单张卡片的触发覆盖（SharedPreferences JSON 持久化，按卡片名存储）。
 *
 * - events = null：沿用卡片 manifest 声明的 events
 * - events = 空列表：显式关闭该卡片的所有事件
 * - disabledEvents：按 events 下标逐个停用事件（与 events 一同保存，下标即保存时的事件顺序）
 * - maxRetries / retryDelaySeconds：运行失败后的重试策略（0 = 保持默认不重试）
 */
@Serializable
data class CardTriggerOverride(
    val events: List<CardManifest.Event>? = null,
    @SerialName("max_retries") val maxRetries: Int = 0,
    @SerialName("retry_delay_seconds") val retryDelaySeconds: Int = 0,
    @SerialName("disabled_events") val disabledEvents: List<Int> = emptyList(),
) {
    val isEmpty: Boolean
        get() = events == null && maxRetries <= 0 && retryDelaySeconds <= 0 && disabledEvents.isEmpty()

    companion object {
        val NONE = CardTriggerOverride()
    }
}
