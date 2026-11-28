package heizige.kk.khatkit.app.feature.automation.data

/** 一次轨迹回放的结果（数据模型，无状态）。 */
data class TraceReplayResult(
    val totalSteps: Int,
    val executedSteps: Int,
    val failures: List<String>,
) {
    val success: Boolean get() = failures.isEmpty()
}
