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

    @Test
    fun nextTriggerRespectsCalendarFilter() {
        // 2026-09-25（周五）中秋放假，09-26/27 周末
        val calendar = WorkdayCalendar.parse("""{"holidays":["2026-09-25"],"workdays":[]}""")
        val now = at(2026, 9, 24, 9, 0)

        val workdayOnly = listOf(
            card(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "workday"))
        )
        assertEquals(
            at(2026, 9, 28, 8, 0),
            TriggerSchedulePlanner.nextTriggerAt(workdayOnly, now, zone, calendar),
        )

        val holidayOnly = listOf(
            card(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "holiday"))
        )
        assertEquals(
            at(2026, 9, 25, 8, 0),
            TriggerSchedulePlanner.nextTriggerAt(holidayOnly, now, zone, calendar),
        )

        // 休息日语义：最近的休息日为中秋 09-25
        val weekendOnly = listOf(
            card(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "weekend"))
        )
        assertEquals(
            at(2026, 9, 25, 8, 0),
            TriggerSchedulePlanner.nextTriggerAt(weekendOnly, now, zone, calendar),
        )

        // 无日历（EMPTY）时退化为自然周：下一工作日为 09-25（周五）
        assertEquals(
            at(2026, 9, 25, 8, 0),
            TriggerSchedulePlanner.nextTriggerAt(workdayOnly, now, zone),
        )
    }

    @Test
    fun nextTriggerSpansLongHolidayGap() {
        // 春节 2026-02-15..02-23 连续 9 天休息，02-14（周六）调休上班
        val calendar = WorkdayCalendar.parse(
            """
            {
              "holidays": ["2026-02-15","2026-02-16","2026-02-17","2026-02-18","2026-02-19",
                            "2026-02-20","2026-02-21","2026-02-22","2026-02-23"],
              "workdays": ["2026-02-14"]
            }
            """.trimIndent()
        )
        val workdayOnly = listOf(
            card(CardManifest.Event(type = "schedule", times = listOf("09:00"), calendar = "workday"))
        )
        // 02-14 10:00（已过当日时间）→ 下一次工作日为 02-24（周二）09:00
        assertEquals(
            at(2026, 2, 24, 9, 0),
            TriggerSchedulePlanner.nextTriggerAt(workdayOnly, at(2026, 2, 14, 10, 0), zone, calendar),
        )
    }
}
