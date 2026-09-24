package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TriggerSchedulePlannerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun card(vararg events: CardManifest.Event, disabled: Set<Int> = emptySet()): TriggerCard =
        TriggerCard("c", events.toList(), disabledIndexes = disabled)

    @Test
    fun hasMinuteScheduleOnlyForTimesEvents() {
        assertTrue(
            TriggerSchedulePlanner.hasMinuteSchedule(
                listOf(card(CardManifest.Event(type = "schedule", times = listOf("08:00"))))
            )
        )
        assertFalse(
            TriggerSchedulePlanner.hasMinuteSchedule(
                listOf(card(CardManifest.Event(type = "schedule", intervalMinutes = 30)))
            )
        )
        assertFalse(
            TriggerSchedulePlanner.hasMinuteSchedule(
                listOf(
                    card(
                        CardManifest.Event(type = "schedule", times = listOf("08:00")),
                        disabled = setOf(0),
                    )
                )
            )
        )
    }

    @Test
    fun nextTriggerPicksComingTimeToday() {
        val cards = listOf(card(CardManifest.Event(type = "schedule", times = listOf("08:00", "21:30"))))
        assertEquals(at(2026, 9, 18, 21, 30), TriggerSchedulePlanner.nextTriggerAt(cards, at(2026, 9, 18, 8, 1), zone))
    }

    @Test
    fun nextTriggerRollsToTomorrowAfterLastTime() {
        val cards = listOf(card(CardManifest.Event(type = "schedule", times = listOf("08:00"))))
        assertEquals(at(2026, 9, 19, 8, 0), TriggerSchedulePlanner.nextTriggerAt(cards, at(2026, 9, 18, 9, 0), zone))
    }

    @Test
    fun nextTriggerRespectsDaysAndDisabledIndexes() {
        // 2026-09-18 周五（ISO 5）；仅周一触发 → 下一次为 2026-09-21 周一 08:00
        val weekdayOnly = listOf(
            card(CardManifest.Event(type = "schedule", times = listOf("08:00"), days = listOf(1)))
        )
        assertEquals(
            at(2026, 9, 21, 8, 0),
            TriggerSchedulePlanner.nextTriggerAt(weekdayOnly, at(2026, 9, 18, 9, 0), zone),
        )

        val disabled = listOf(
            card(
                CardManifest.Event(type = "schedule", times = listOf("08:00")),
                disabled = setOf(0),
            )
        )
        assertNull(TriggerSchedulePlanner.nextTriggerAt(disabled, at(2026, 9, 18, 9, 0), zone))
    }
}
