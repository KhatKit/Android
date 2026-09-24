package heizige.kk.khatkit.trigger

import android.content.Context
import heizige.kk.khatkit.card.CardManifest
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 中国节假日日历（纯逻辑，无 Android 依赖部分可 JVM 单测）。
 *
 * 数据格式与内置资产 `assets/holidays_cn.json` 一致：
 * ```json
 * { "holidays": ["2026-10-01"], "workdays": ["2026-09-20"] }
 * ```
 * - `holidays`：法定放假日（含调休放假的工作日）
 * - `workdays`：调休上班日（周末上班）
 *
 * 用户可编辑覆盖文件（优先于内置资产）：`filesDir/[OVERRIDE_RELATIVE_PATH]`，
 * 修改后重新进入设置页/重启触发服务即生效。
 *
 * 语义约定（与 [CardManifest.Event.calendar] 对应）：
 * - `workday`：调休上班日，或「非法定假日的工作日」
 * - `weekend`：休息日 = `workday` 的补集（自然周末 + 法定假日 + 调休休息日）
 * - `holiday`：仅法定放假日（不含普通周末）
 * - `any` / 空：不做日历过滤
 *
 * 数据为空时退化为自然周（周一至周五为工作日，周末为休息日，holiday 永不命中）。
 */
class WorkdayCalendar(
    private val holidays: Set<LocalDate> = emptySet(),
    private val workdays: Set<LocalDate> = emptySet(),
) {
    /** 是否法定放假日（holidays 列表命中）。 */
    fun isHoliday(date: LocalDate): Boolean = date in holidays

    /** 是否调休上班日（workdays 列表命中）。 */
    fun isMakeupWorkday(date: LocalDate): Boolean = date in workdays

    /** 是否自然周六/周日。 */
    fun isNaturalWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** 是否工作日：调休上班日，或「非周末且非法定假日」。 */
    fun isWorkday(date: LocalDate): Boolean =
        date in workdays || (!isNaturalWeekend(date) && date !in holidays)

    /** 是否休息日：工作日的补集（含法定假日与普通周末）。 */
    fun isRestDay(date: LocalDate): Boolean = !isWorkday(date)

    /** calendar 过滤是否命中该日期。 */
    fun matches(calendar: String, date: LocalDate): Boolean = when (calendar) {
        CardManifest.CALENDAR_WORKDAY -> isWorkday(date)
        CardManifest.CALENDAR_WEEKEND -> isRestDay(date)
        CardManifest.CALENDAR_HOLIDAY -> isHoliday(date)
        else -> true
    }

    /** 是否没有任何日历数据（解析失败/资产缺失时的退化状态）。 */
    val isEmpty: Boolean get() = holidays.isEmpty() && workdays.isEmpty()

    companion object {
        /** 内置资产与覆盖文件共用的文件名。 */
        const val FILE_NAME = "holidays_cn.json"

        /** 用户覆盖文件相对 `filesDir` 的路径。 */
        const val OVERRIDE_RELATIVE_PATH = "khatkit/$FILE_NAME"

        /** 无数据实例：按自然周判定。 */
        val EMPTY = WorkdayCalendar()

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /** 解析日历 JSON；格式错误/条目非法时尽量跳过，整体不可解析返回 [EMPTY]。 */
        fun parse(json: String): WorkdayCalendar {
            val root = runCatching { JSON.parseToJsonElement(json) }.getOrNull() as? JsonObject
                ?: return EMPTY
            return WorkdayCalendar(
                holidays = root.dateSet("holidays"),
                workdays = root.dateSet("workdays"),
            )
        }

        /**
         * 优先读取用户覆盖文件 [OVERRIDE_RELATIVE_PATH]，否则读取内置资产；
         * 覆盖文件解析为空时回退到内置资产；都不可用时返回 [EMPTY]。
         */
        fun load(context: Context): WorkdayCalendar {
            val overrideText = runCatching {
                File(context.filesDir, OVERRIDE_RELATIVE_PATH)
                    .takeIf { it.isFile }
                    ?.readText()
            }.getOrNull()
            val override = overrideText?.let(::parse)
            if (override != null && !override.isEmpty) return override
            val assetText = runCatching {
                context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
            }.getOrNull()
            return assetText?.let(::parse)?.takeIf { !it.isEmpty } ?: EMPTY
        }

        private fun JsonObject.dateSet(key: String): Set<LocalDate> =
            runCatching {
                (this[key] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { element ->
                        (element as? JsonPrimitive)?.content?.trim()?.let(::dateOrNull)
                    }
                    .toSet()
            }.getOrDefault(emptySet())

        private fun dateOrNull(raw: String): LocalDate? =
            runCatching { LocalDate.parse(raw) }.getOrNull()
    }
}
