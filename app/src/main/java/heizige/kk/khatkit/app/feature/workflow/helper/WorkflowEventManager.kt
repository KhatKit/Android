package heizige.kk.khatkit.app.feature.workflow.helper

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 本机 `/api/events` 上的 workflow_run 事件。不新开 Hub 协议。 */
object WorkflowEventManager {
    private val eventsFlow = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events: SharedFlow<String> = eventsFlow.asSharedFlow()

    fun tryEmit(json: String): Boolean = eventsFlow.tryEmit(json)
}
