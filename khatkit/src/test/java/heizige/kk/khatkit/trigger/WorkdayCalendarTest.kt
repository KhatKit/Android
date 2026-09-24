package heizige.kk.khatkit.trigger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class WorkdayCalendarTest {

    private val calendar = WorkdayCalendar.parse(
        """
        {
          "holidays": ["2026-10-01", "2026-10-02", "2026-02-16"],
          "workdays": ["2026-09-20", "2026-10-10"]
        }
        """.trimIndent()
    )

    @Test
    fun workdaySemantics() {
        // 调休上班的周日
        assertTrue(calendar.isMakeupWorkday(LocalDate.of(2026, 9, 20)))
        assertTrue(calendar.isWorkday(LocalDate.of(2026, 9, 20)))
        // 工作日正常判定
        assertTrue(calendar.isWorkday(LocalDate.of(2026, 9, 24)))
        // 法定假日的工作日不算工作日
        assertFalse(calendar.isWorkday(LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 10, 1)))
        // 自然周末不算工作日
        assertFalse(calendar.isWorkday(LocalDate.of(2026, 9, 26)))
        assertTrue(calendar.isNaturalWeekend(LocalDate.of(2026, 9, 26)))
        // 普通周末不是法定假日
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 9, 26)))
        // 休息日 = 工作日的补集（含法定假日与普通周末）
        assertTrue(calendar.isRestDay(LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.isRestDay(LocalDate.of(2026, 9, 26)))
        assertFalse(calendar.isRestDay(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun matchesCalendarFilter() {
        assertTrue(calendar.matches("any", LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.matches("", LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.matches("workday", LocalDate.of(2026, 9, 24)))
        assertFalse(calendar.matches("workday", LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.matches("weekend", LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.matches("weekend", LocalDate.of(2026, 9, 26)))
        assertFalse(calendar.matches("weekend", LocalDate.of(2026, 9, 20)))
        assertTrue(calendar.matches("holiday", LocalDate.of(2026, 10, 1)))
        assertFalse(calendar.matches("holiday", LocalDate.of(2026, 9, 26)))
    }

    @Test
    fun parseSkipsInvalidEntriesAndFallsBackToEmpty() {
        val tolerant = WorkdayCalendar.parse(
            """{"holidays":["2026-10-01","not-a-date",123,null],"workdays":null}"""
        )
        assertTrue(tolerant.isHoliday(LocalDate.of(2026, 10, 1)))
        assertFalse(tolerant.isHoliday(LocalDate.of(2026, 10, 2)))

        assertTrue(WorkdayCalendar.parse("not json").isEmpty)
        assertTrue(WorkdayCalendar.EMPTY.isEmpty)
        // 空数据按自然周退化，holiday 永不命中
        assertTrue(WorkdayCalendar.EMPTY.isWorkday(LocalDate.of(2026, 9, 24)))
        assertTrue(WorkdayCalendar.EMPTY.isRestDay(LocalDate.of(2026, 9, 26)))
        assertFalse(WorkdayCalendar.EMPTY.matches("holiday", LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun bundledAssetParses() {
        val file = File("src/main/assets/holidays_cn.json")
        assumeTrue(file.exists())
        val bundled = WorkdayCalendar.parse(file.readText())
        assertFalse(bundled.isEmpty)
        // 2026 国庆
        assertTrue(bundled.isHoliday(LocalDate.of(2026, 10, 1)))
        // 2026-09-20（周日）调休上班
        assertTrue(bundled.isWorkday(LocalDate.of(2026, 9, 20)))
        // 2026-09-25 中秋
        assertTrue(bundled.isHoliday(LocalDate.of(2026, 9, 25)))
    }
}
