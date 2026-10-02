package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CardScheduleStoreTest {

    private fun job(
        kind: String = CardScheduleJob.KIND_EVERY,
        intervalMinutes: Int = 30,
        times: List<String> = emptyList(),
        days: List<Int> = emptyList(),
    ) = CardScheduleJob(
        jobId = "card:job",
        cardName = "card",
        kind = kind,
        intervalMinutes = intervalMinutes,
        times = times,
        days = days,
        createdAt = 0L,
    )

    /** 2026-01-05 是周一：构造指定「星期几 + 时分」的 epoch 毫秒。 */
    private fun at(isoDay: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 5)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, isoDay - 1)
    }.timeInMillis

    @Test
    fun `every delay is the interval`() {
        assertEquals(30 * 60_000L, CardScheduleStore.nextDelayMs(job(intervalMinutes = 30)))
        assertEquals(1 * 60_000L, CardScheduleStore.nextDelayMs(job(intervalMinutes = 1)))
        // 脏数据（0 或负数）兜底成 1 分钟，避免忙循环
        assertEquals(60_000L, CardScheduleStore.nextDelayMs(job(intervalMinutes = 0)))
        assertEquals(60_000L, CardScheduleStore.nextDelayMs(job(intervalMinutes = -5)))
    }

    @Test
    fun `at picks the next time later today`() {
        val schedule = job(kind = CardScheduleJob.KIND_AT, times = listOf("08:00", "21:30"))
        assertEquals(30 * 60_000L, CardScheduleStore.nextDelayMs(schedule, at(1, 21, 0)))
        assertEquals(2 * 60_000L, CardScheduleStore.nextDelayMs(schedule, at(1, 21, 28)))
    }

    @Test
    fun `at rolls over to tomorrow when all times passed`() {
        val schedule = job(kind = CardScheduleJob.KIND_AT, times = listOf("08:00"))
        assertEquals(11 * 60 * 60_000L, CardScheduleStore.nextDelayMs(schedule, at(1, 21, 0)))
    }

    @Test
    fun `days filter skips non matching weekdays`() {
        // 只在周三触发：周一 12:00 应顺延到周三 09:00
        val wednesdayOnly = job(kind = CardScheduleJob.KIND_AT, times = listOf("09:00"), days = listOf(3))
        val expected = 45 * 60 * 60_000L // 周一 12:00 → 周三 09:00
        assertEquals(expected, CardScheduleStore.nextDelayMs(wednesdayOnly, at(1, 12, 0)))
    }

    @Test
    fun `empty days means every day`() {
        val daily = job(kind = CardScheduleJob.KIND_AT, times = listOf("09:00"))
        assertEquals(60_000L, CardScheduleStore.nextDelayMs(daily, at(7, 8, 59)))
    }

    @Test
    fun `sunday iso day is seven`() {
        val sundayOnly = job(kind = CardScheduleJob.KIND_AT, times = listOf("10:00"), days = listOf(7))
        // 2026-01-05 是周一；下一个周日是 01-11，周一 09:00 → 周日 10:00
        assertEquals(6 * 24 * 60 * 60_000L + 60 * 60_000L, CardScheduleStore.nextDelayMs(sundayOnly, at(1, 9, 0)))
    }

    @Test
    fun `empty times fall back to a day`() {
        assertEquals(24 * 60 * 60_000L, CardScheduleStore.nextDelayMs(job(kind = CardScheduleJob.KIND_AT)))
    }

    @Test
    fun `time parsing accepts only HH mm`() {
        assertEquals("08:00", CardScheduleStore.parseTime("8:00".padStart(5, '0')))
        assertEquals("23:59", CardScheduleStore.parseTime("23:59"))
        assertNull(CardScheduleStore.parseTime("24:00"))
        assertNull(CardScheduleStore.parseTime("8:0"))
        assertNull(CardScheduleStore.parseTime("0800"))
        assertNull(CardScheduleStore.parseTime(""))
    }

    @Test
    fun `days parsing filters and sorts`() {
        assertEquals(listOf(1, 3, 5), CardScheduleStore.parseDays(listOf(5, 3, 1, 3)))
        assertEquals(listOf(2), CardScheduleStore.parseDays(listOf(2, 0, 8, -1)))
        assertEquals(emptyList<Int>(), CardScheduleStore.parseDays(emptyList()))
    }

    @Test
    fun `job id is namespaced by card and sanitized`() {
        assertEquals("card:daily", CardScheduleStore.jobId("card", "daily"))
        assertTrue(CardScheduleStore.jobId("card", "").startsWith("card:job"))
        assertEquals("my_card:a-b_c", CardScheduleStore.jobId("my/card", "a-b c"))
    }

    @Test
    fun `work name derives from job id`() {
        assertEquals("khatkit-schedule:card:daily", CardScheduleStore.workName("card:daily"))
        assertEquals(CardScheduleStore.MAX_INTERVAL_MINUTES, 7 * 24 * 60)
    }
}