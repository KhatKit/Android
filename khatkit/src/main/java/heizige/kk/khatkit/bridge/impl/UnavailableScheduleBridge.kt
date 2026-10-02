package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.ScheduleBridge

/** 宿主尚未接入调度能力时的明确错误实现。 */
class UnavailableScheduleBridge : ScheduleBridge {
    override fun every(intervalMinutes: Int, jobId: String, payloadJson: String): String =
        throw IllegalStateException("卡片定时任务未接入：宿主不支持后台调度")

    override fun at(times: List<String>, days: List<Int>, jobId: String): String =
        throw IllegalStateException("卡片定时任务未接入：宿主不支持后台调度")

    override fun cancel(jobId: String): Boolean = false

    override fun list(): List<Map<String, Any?>> = emptyList()
}
