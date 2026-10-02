package heizige.kk.khatkit.app.feature.automation

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.di.appEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 卡片定时任务的执行体：取出任务元数据 → 用 payload 当 `args` 跑卡片 → 排下一次。
 *
 * 任务链的续排放在这里，所以：
 * - 脚本进程被杀不影响后续触发（WorkManager 持久化了下一次请求）；
 * - 任务被 `schedule.cancel` 或卡片卸载后，链自然断开（查不到任务即 `Result.success()` 结束）。
 *
 * 永远返回 [Result.success]：单次失败不该让任务链断掉，下次继续。
 */
class CardScheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val store = CardScheduleStore(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        if (jobId.isBlank()) return@withContext Result.success()
        val job = store.readAll().firstOrNull { it.jobId == jobId }
        if (job == null) {
            // 已被取消或卡片已卸载：链到此为止
            return@withContext Result.success()
        }
        runCatching {
            appEntryPoint(applicationContext).khatKitToolProvider().runScheduledCard(job.cardName, job.payloadJson)
        }.onFailure { Log.w(TAG, "定时任务执行失败：$jobId（${it.message}）") }

        // 续排下一次；卡片已卸载 / 任务已取消时 readAll 里没有它，直接结束链
        store.readAll().firstOrNull { it.jobId == jobId }?.let { enqueue(it) }
        Result.success()
    }

    private fun enqueue(job: CardScheduleJob) {
        val request = OneTimeWorkRequestBuilder<CardScheduleWorker>()
            .setInitialDelay(CardScheduleStore.nextDelayMs(job), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_JOB_ID, job.jobId).build())
            .addTag(ScheduleBridgeImpl.TAG_CARD_SCHEDULE)
            .build()
        runCatching {
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(CardScheduleStore.workName(job.jobId), ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "续排定时任务失败：${job.jobId}（${it.message}）") }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val TAG = "CardScheduleWorker"
    }
}
