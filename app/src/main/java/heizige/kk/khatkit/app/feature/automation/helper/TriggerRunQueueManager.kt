package heizige.kk.khatkit.app.feature.automation.helper

import heizige.kk.khatkit.app.feature.automation.data.TriggerRunBatch
import heizige.kk.khatkit.app.feature.automation.data.TriggerRunItem

/**
 * 触发任务 FIFO 队列状态机（纯逻辑，无协程/Android 依赖，可 JVM 单测）。
 *
 * - 运行中数量由 [maxParallel]（1..3）限制
 * - 排队上限 [capacity]；满了再提交时丢弃最旧排队任务（返回在 [TriggerRunBatch.dropped]）
 * - 调用方负责真正启动协程，并在运行结束后调用 [complete] 取补位任务
 * - 条目/批次模型在 data/TriggerRunModels.kt，流程与锁归本 Manager
 */
class TriggerRunQueueManager(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxParallel: () -> Int = { 1 },
) {
    private val lock = Any()
    private val queue = ArrayDeque<TriggerRunItem>()
    private var running = 0

    val queuedCount: Int get() = synchronized(lock) { queue.size }
    val runningCount: Int get() = synchronized(lock) { running }

    fun submit(item: TriggerRunItem): TriggerRunBatch = synchronized(lock) {
        var dropped: TriggerRunItem? = null
        val starts = mutableListOf<TriggerRunItem>()
        if (running < maxParallel().coerceAtLeast(1)) {
            running++
            starts += item
        } else {
            if (queue.size >= capacity) dropped = queue.removeFirst()
            queue.addLast(item)
        }
        TriggerRunBatch(starts = starts, dropped = dropped)
    }

    /** 一次运行结束后调用，返回可补位启动的任务（按 FIFO）。 */
    fun complete(): List<TriggerRunItem> = synchronized(lock) {
        running = (running - 1).coerceAtLeast(0)
        val starts = mutableListOf<TriggerRunItem>()
        val limit = maxParallel().coerceAtLeast(1)
        while (running < limit && queue.isNotEmpty()) {
            running++
            starts += queue.removeFirst()
        }
        starts
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
    }
}
