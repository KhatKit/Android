package heizige.kk.khatkit.app.feature.workflow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

/** 本机 `/api/events` 上的 workflow_run 事件。不新开 Hub 协议。 */
object WorkflowEventBus {
    private val eventsFlow = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events: SharedFlow<String> = eventsFlow.asSharedFlow()

    fun tryEmit(json: String): Boolean = eventsFlow.tryEmit(json)
}

@Serializable
data class WorkflowRunEvent(
    val workflowId: String,
    val runId: String,
    val status: String,
    val nodeId: String? = null,
    val ordinal: Int? = null,
)
