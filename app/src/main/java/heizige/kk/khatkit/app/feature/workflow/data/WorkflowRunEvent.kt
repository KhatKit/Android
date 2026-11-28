package heizige.kk.khatkit.app.feature.workflow.data

import kotlinx.serialization.Serializable

/** 本机 `/api/events` 上的 workflow_run 事件负载（wire 名不变）。 */
@Serializable
data class WorkflowRunEvent(
    val workflowId: String,
    val runId: String,
    val status: String,
    val nodeId: String? = null,
    val ordinal: Int? = null,
)
