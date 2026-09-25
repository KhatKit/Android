package heizige.kk.khatkit.app.feature.automation

/**
 * 触发任务 FIFO 队列状态机（纯逻辑，无协程/Android 依赖，可 JVM 单测）。
 *
 * - 运行中数量由 [maxParallel]（1..3）限制
 * - 排队上限 [capacity]；满了再提交时丢弃最旧排队任务（返回在 [Batch.dropped]）
 * - 调用方负责真正启动协程，并在运行结束后调用 [complete] 取补位任务
 */
class TriggerRunQueue(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxParallel: () -> Int = { 1 },
) {
    data class Item(
        val card: String,
        val args: Map<String, Any?>,
        /** true = 用户主动触发（快捷方式/磁贴），不受总开关随后的关闭影响 */
        val external: Boolean = false,
    )

    data class Batch(
        val starts: List<Item>,
        /** 队列已满被挤掉的最旧任务 */
        val dropped: Item? = null,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Item>()
    private var running = 0

    val queuedCount: Int get() = synchronized(lock) { queue.size }
    val runningCount: Int get() = synchronized(lock) { running }

    fun submit(item: Item): Batch = synchronized(lock) {
        var dropped: Item? = null
        val starts = mutableListOf<Item>()
        if (running < maxParallel().coerceAtLeast(1)) {
            running++
            starts += item
        } else {
            if (queue.size >= capacity) dropped = queue.removeFirst()
            queue.addLast(item)
        }
        Batch(starts = starts, dropped = dropped)
    }

    /** 一次运行结束后调用，返回可补位启动的任务（按 FIFO）。 */
    fun complete(): List<Item> = synchronized(lock) {
        running = (running - 1).coerceAtLeast(0)
        val starts = mutableListOf<Item>()
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
