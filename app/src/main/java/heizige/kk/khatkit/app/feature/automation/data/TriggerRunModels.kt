package heizige.kk.khatkit.app.feature.automation.data

/** 触发队列里的一次待运行任务。 */
data class TriggerRunItem(
    val card: String,
    val args: Map<String, Any?>,
    /** true = 用户主动触发（快捷方式/磁贴），不受总开关随后的关闭影响 */
    val external: Boolean = false,
)

/** 一次 submit/complete 的结果：可立即启动的任务 + 被挤掉的最旧任务。 */
data class TriggerRunBatch(
    val starts: List<TriggerRunItem>,
    /** 队列已满被挤掉的最旧任务 */
    val dropped: TriggerRunItem? = null,
)
