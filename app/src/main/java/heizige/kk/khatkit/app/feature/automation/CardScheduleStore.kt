package heizige.kk.khatkit.app.feature.automation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import kotlin.math.max

/** 一条脚本自建的定时任务。 */
@Serializable
data class CardScheduleJob(
    /** 完整 jobId：`<cardName>:<custom>`，卡片内自定义值为空时用自动生成的名字。 */
    val jobId: String,
    val cardName: String,
    /** `every` / `at`。 */
    val kind: String,
    val intervalMinutes: Int = 0,
    /** `at` 用：严格 `HH:mm`。 */
    val times: List<String> = emptyList(),
    /** `at` 用：1=周一 … 7=周日，空 = 每天。 */
    val days: List<Int> = emptyList(),
    /** 触发时注入的 `args`（JSON 对象文本）。 */
    val payloadJson: String = "{}",
    val createdAt: Long = 0L,
) {
    val isEvery: Boolean get() = kind == KIND_EVERY

    companion object {
        const val KIND_EVERY = "every"
        const val KIND_AT = "at"
    }
}

/**
 * 卡片定时任务的持久化 + 下次触发时间计算。
 *
 * 任务本身由 WorkManager 排队（[CardScheduleWorker] 每次执行后按这里的规则再排下一次），
 * 这里只保存任务元数据，WorkManager 被清空/重装后也能恢复。
 */
class CardScheduleStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 本卡片当前任务。 */
    fun load(cardName: String): List<CardScheduleJob> = readAll().filter { it.cardName == cardName }

    /** 全部任务（worker 按 jobId 查、卸载时按卡片清理）。 */
    fun readAll(): List<CardScheduleJob> {
        val raw = prefs.getString(KEY_JOBS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CardScheduleJob>>(raw)
        }.getOrDefault(emptyList())
    }

    /** 新增或覆盖（同 jobId 覆盖）。 */
    fun save(job: CardScheduleJob) {
        val jobs = readAll().filterNot { it.jobId == job.jobId } + job
        write(jobs)
    }

    /** 删除一条；返回是否命中。 */
    fun remove(jobId: String): Boolean {
        val jobs = readAll()
        val kept = jobs.filterNot { it.jobId == jobId }
        if (kept.size == jobs.size) return false
        write(kept)
        return true
    }

    /** 卸载卡片时清掉它留下的所有任务。 */
    fun removeAll(cardName: String): List<CardScheduleJob> {
        val jobs = readAll()
        val removed = jobs.filter { it.cardName == cardName }
        if (removed.isNotEmpty()) write(jobs.filterNot { it.cardName == cardName })
        return removed
    }

    private fun write(jobs: List<CardScheduleJob>) {
        prefs.edit().putString(KEY_JOBS, json.encodeToString(jobs)).apply()
    }

    companion object {
        const val PREFS = "khatkit_schedule_jobs"
        const val KEY_JOBS = "jobs"

        /** WorkManager 唯一任务名；与 jobId 一一对应，天然按卡片隔离。 */
        fun workName(jobId: String): String = "khatkit-schedule:$jobId"

        /** `<cardName>:<custom>`；custom 为空时自动生成。 */
        fun jobId(cardName: String, custom: String): String {
            val safeCard = cardName.replace(INVALID_CHARS, "_")
            val safeCustom = custom.trim().ifEmpty { "job${System.currentTimeMillis()}" }
                .replace(INVALID_CHARS, "_")
            return "$safeCard:$safeCustom"
        }

        /** 卡片内自定义 id 的合法字符。 */
        private val INVALID_CHARS = Regex("""[^\w.-]""")

        private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

        /** 校验 `HH:mm`，返回规范化文本。 */
        fun parseTime(raw: String): String? {
            val match = TIME_REGEX.find(raw.trim()) ?: return null
            return "${match.groupValues[1]}:${match.groupValues[2]}"
        }

        /** 校验星期列表（1..7，空 = 每天），返回去重排序后的结果。 */
        fun parseDays(raw: List<Int>): List<Int> = raw.filter { it in 1..7 }.distinct().sorted()

        /** intervalMinutes 上限：7 天。 */
        const val MAX_INTERVAL_MINUTES = 7 * 24 * 60

        /**
         * 下次触发的延迟毫秒数（相对 [now]）。
         *
         * - `every`：`intervalMinutes` 分钟；
         * - `at`：下一个命中 [CardScheduleJob.times] 且星期在 [CardScheduleJob.days] 的分钟，
         *   跨天最多等 7 天；已经过点的分钟顺延到下一个合法时间。
         *
         * 恒为正数（至少 1 分钟），避免 0 延迟造成忙循环。
         */
        fun nextDelayMs(job: CardScheduleJob, now: Long = System.currentTimeMillis()): Long {
            if (job.isEvery) {
                return max(1, job.intervalMinutes).toLong() * 60_000L
            }
            val minutes = job.times.mapNotNull { parseTime(it) }
                .map { it.substring(0, 2).toInt() * 60 + it.substring(3, 5).toInt() }
                .distinct()
                .sorted()
            if (minutes.isEmpty()) return 24L * 60 * 60 * 1000
            val days = parseDays(job.days).ifEmpty { (1..7).toList() }
            val cursor = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // 最多往后找 8 天，保证覆盖一整周
            repeat(8) {
                val minutesOfDay = cursor.get(Calendar.HOUR_OF_DAY) * 60 + cursor.get(Calendar.MINUTE)
                val isoDay = cursor.get(Calendar.DAY_OF_WEEK).toIsoDayOfWeek()
                val candidate = minutes.firstOrNull { it > minutesOfDay }
                if (isoDay in days && candidate != null) {
                    val target = cursor.timeInMillis + (candidate - minutesOfDay).toLong() * 60_000L
                    return (target - now).coerceAtLeast(60_000L)
                }
                // 跳到明天 00:00 继续找
                val millisAtMidnight = cursor.timeInMillis - minutesOfDay * 60_000L
                cursor.timeInMillis = millisAtMidnight + 24 * 60 * 60 * 1000L
            }
            return 24 * 60 * 60 * 1000L
        }

        /** Calendar 的 SUNDAY=1 换成 1=周一 … 7=周日。 */
        private fun Int.toIsoDayOfWeek(): Int = if (this == Calendar.SUNDAY) 7 else this - 1
    }
}