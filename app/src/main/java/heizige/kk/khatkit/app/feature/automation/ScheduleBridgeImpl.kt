package heizige.kk.khatkit.app.feature.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import heizige.kk.khatkit.bridge.CardBindable
import heizige.kk.khatkit.bridge.ScheduleBridge
import java.util.concurrent.TimeUnit

/**
 * `schedule` bridge 的宿主实现：脚本运行期自建的定时任务。
 *
 * 调度走 WorkManager 的**一次性任务链**（每次执行完按 [CardScheduleStore.nextDelayMs] 再排下一次），
 * 而不是 PeriodicWork：这样 `every` 能支持 1 分钟级间隔（WorkManager 周期任务下限 15 分钟），
 * 代价是不保证准点——需要准点请用 manifest 的 `schedule` 事件（宿主有精确闹钟通道）。
 *
 * 任务元数据存在 [CardScheduleStore]，jobId 形如 `<卡片名>:<自定义>`，跨卡片不会撞车。
 */
class ScheduleBridgeImpl(
    context: Context,
    private val cardName: String,
    private val store: CardScheduleStore = CardScheduleStore(context),
) : ScheduleBridge, CardBindable {

    /** `BridgeRegistry.inject` 按 manifest 绑定卡片名后换一张视图。 */
    override fun bindCard(cardName: String): Any = ScheduleBridgeImpl(appContext, cardName, store)

    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    override fun every(intervalMinutes: Int, jobId: String, payloadJson: String): String {
        require(intervalMinutes in 1..CardScheduleStore.MAX_INTERVAL_MINUTES) {
            "schedule.every 的间隔必须是 1..${CardScheduleStore.MAX_INTERVAL_MINUTES} 分钟：$intervalMinutes"
        }
        val payload = validatePayload(payloadJson)
        val job = CardScheduleJob(
            jobId = CardScheduleStore.jobId(cardName, jobId),
            cardName = cardName,
            kind = CardScheduleJob.KIND_EVERY,
            intervalMinutes = intervalMinutes,
            payloadJson = payload,
            createdAt = System.currentTimeMillis(),
        )
        store.save(job)
        enqueue(job)
        return job.jobId
    }

    override fun at(times: List<String>, days: List<Int>, jobId: String): String {
        val normalized = times.mapNotNull { CardScheduleStore.parseTime(it) }.distinct().sorted()
        require(normalized.isNotEmpty()) {
            "schedule.at 至少要有一个合法时间（HH:mm）：${times.joinToString(",")}"
        }
        require(times.size == normalized.size) {
            "schedule.at 的时间必须是 HH:mm（00:00–23:59）：${times.joinToString(",")}"
        }
        val job = CardScheduleJob(
            jobId = CardScheduleStore.jobId(cardName, jobId),
            cardName = cardName,
            kind = CardScheduleJob.KIND_AT,
            times = normalized,
            days = CardScheduleStore.parseDays(days),
            createdAt = System.currentTimeMillis(),
        )
        store.save(job)
        enqueue(job)
        return job.jobId
    }

    override fun cancel(jobId: String): Boolean {
        val full = if (jobId.contains(':')) jobId else CardScheduleStore.jobId(cardName, jobId)
        val removed = store.remove(full)
        runCatching { workManager.cancelUniqueWork(CardScheduleStore.workName(full)) }
        return removed
    }

    override fun list(): List<Map<String, Any?>> = store.load(cardName).map { job ->
        mapOf(
            "jobId" to job.jobId,
            "kind" to job.kind,
            "intervalMinutes" to if (job.isEvery) job.intervalMinutes.toDouble() else 0.0,
            "times" to job.times,
            "days" to job.days.map { it.toDouble() },
            "nextRunAt" to (System.currentTimeMillis() + CardScheduleStore.nextDelayMs(job)).toDouble(),
            "createdAt" to job.createdAt.toDouble(),
        )
    }

    /** 排下一次执行；同名任务覆盖（REPLACE），保证幂等。 */
    private fun enqueue(job: CardScheduleJob) {
        val delay = CardScheduleStore.nextDelayMs(job)
        val request = OneTimeWorkRequestBuilder<CardScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(CardScheduleWorker.KEY_JOB_ID, job.jobId).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(TAG_CARD_SCHEDULE)
            .build()
        runCatching { workManager.enqueueUniqueWork(CardScheduleStore.workName(job.jobId), ExistingWorkPolicy.REPLACE, request) }
    }

    /** payloadJson 必须是合法 JSON 对象；失败给中文错误。 */
    private fun validatePayload(payloadJson: String): String {
        val raw = payloadJson.trim().ifEmpty { "{}" }
        require(raw.startsWith("{") && raw.endsWith("}")) {
            "schedule 的 payloadJson 必须是 JSON 对象（如 {\"key\":\"value\"}）"
        }
        return raw
    }

    companion object {
        /** 便于设置页 / 诊断一键清空。 */
        const val TAG_CARD_SCHEDULE = "khatkit-schedule"
    }
}