package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import java.time.Instant
import java.time.ZoneId

/**
 * 分钟级 schedule 的精确闹钟规划（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 宿主仅在存在启用的 `times` 定时事件时使用 [nextTriggerAt] 排精确闹钟；
 * `intervalMinutes` 仍由 60s ticker 兜底。用户按事件下标停用的事件会被跳过。
 */
object TriggerSchedulePlanner {

    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    /** 是否存在启用的分钟级（`times` 非空）定时事件。 */
    fun hasMinuteSchedule(cards: List<TriggerCard>): Boolean = cards.any { card ->
        card.events.indices.any { index ->
            index !in card.disabledIndexes && card.events[index].let {
                it.type == CardManifest.EVENT_SCHEDULE && it.times.isNotEmpty()
            }
        }
    }

    /** 下一次命中的分钟时间戳（epoch millis）；没有可命中的时间返回 null。 */
    fun nextTriggerAt(
        cards: List<TriggerCard>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var best: Long? = null
        cards.forEach { card ->
            card.events.forEachIndexed { index, event ->
                if (index in card.disabledIndexes) return@forEachIndexed
                if (event.type != CardManifest.EVENT_SCHEDULE || event.times.isEmpty()) return@forEachIndexed
                for (offset in 0L..7L) {
                    val date = today.plusDays(offset)
                    if (event.days.isNotEmpty() && date.dayOfWeek.value !in event.days) continue
                    for (raw in event.times) {
                        val match = TIME_REGEX.matchEntire(raw) ?: continue
                        val candidate = date
                            .atTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        val currentBest = best
                        if (candidate > now && (currentBest == null || candidate < currentBest)) {
                            best = candidate
                        }
                    }
                }
            }
        }
        return best
    }
}
