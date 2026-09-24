package heizige.kk.khatkit.record

/**
 * 录制步骤累积器：连续去重、输入去抖（同一输入框只保留最终值）、步骤上限保护。
 *
 * 纯逻辑，可 JVM 单测；线程安全（无障碍回调与 UI 可能来自不同线程）。
 */
class RecordingSession(
    private val maxSteps: Int = RecordingCardFactory.MAX_STEPS,
) {
    private val steps = mutableListOf<RecordedStep>()
    private var overflow = false

    val size: Int @Synchronized get() = steps.size

    /** 是否因为超过 [maxSteps] 丢过步骤。 */
    val isFull: Boolean @Synchronized get() = overflow

    /**
     * 追加一步录制结果。
     *
     * @return true = 该步骤被接纳（输入去抖时可能替换了上一条输入步骤）
     */
    @Synchronized
    fun add(step: RecordedStep): Boolean {
        val last = steps.lastOrNull()
        // 输入去抖：同一输入框连续变化只保留最终文本，时间戳更新为最后一次输入
        if (step.type == RecordedStep.Type.SET_TEXT &&
            last != null &&
            last.type == RecordedStep.Type.SET_TEXT &&
            last.sameTarget(step)
        ) {
            steps[steps.lastIndex] = step
            return true
        }
        // 连续重复事件去重（系统常对一次点击发多次事件）
        if (last != null && last.isDuplicateOf(step)) return false
        if (steps.size >= maxSteps) {
            overflow = true
            return false
        }
        steps.add(step)
        return true
    }

    @Synchronized
    fun snapshot(): List<RecordedStep> = steps.toList()

    @Synchronized
    fun clear() {
        steps.clear()
        overflow = false
    }
}
