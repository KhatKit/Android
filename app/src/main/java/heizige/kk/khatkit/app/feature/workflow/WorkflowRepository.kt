package heizige.kk.khatkit.app.feature.workflow

import android.content.Context
import heizige.kk.khatkit.app.core.data.db.dao.WorkflowDao
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunStepEntity
import heizige.kk.khatkit.app.feature.settings.WorkflowStore
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Room 里的工作流定义与运行记录。旧 SharedPreferences 在表为空时迁入一次。 */
class WorkflowRepository(
    private val context: Context,
    private val dao: WorkflowDao,
) {
    suspend fun importLegacyIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        WorkflowStore.load(context).forEach { stored ->
            val graph = WorkflowLegacy.fromStored(stored)
            dao.upsert(graph.toEntity(now))
        }
    }

    suspend fun save(workflow: VisualWorkflow) {
        dao.upsert(workflow.toEntity(System.currentTimeMillis()))
    }

    suspend fun record(workflow: VisualWorkflow, execution: WorkflowExecution, attempt: Int = 1): String {
        val runId = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()
        dao.insertRun(
            WorkflowRunEntity(
                id = runId,
                workflowId = workflow.id,
                status = execution.status,
                startedAt = started,
                finishedAt = started,
                durationMs = 0,
                attempt = attempt,
                costTokens = 0,
                error = execution.error,
            ),
        )
        dao.insertSteps(
            execution.steps.mapIndexed { index, step ->
                WorkflowRunStepEntity(
                    id = "$runId:$index",
                    runId = runId,
                    nodeId = step.nodeId,
                    status = step.status,
                    inputJson = WorkflowJson.encodeToString(stringMap, step.input),
                    outputJson = WorkflowJson.encodeToString(stringMap, step.output),
                    durationMs = 0,
                    ordinal = index,
                )
            },
        )
        WorkflowEventBus.tryEmit(
            WorkflowJson.encodeToString(
                WorkflowRunEvent(
                    workflowId = workflow.id,
                    runId = runId,
                    status = execution.status,
                ),
            ),
        )
        execution.steps.forEachIndexed { index, step ->
            WorkflowEventBus.tryEmit(
                WorkflowJson.encodeToString(
                    WorkflowRunEvent(
                        workflowId = workflow.id,
                        runId = runId,
                        status = step.status,
                        nodeId = step.nodeId,
                        ordinal = index,
                    ),
                ),
            )
        }
        return runId
    }

    suspend fun cancel(runId: String) {
        dao.updateRunStatus(runId, "cancelled", System.currentTimeMillis(), "cancelled")
    }

    suspend fun latest(workflowId: String): Pair<WorkflowRunEntity, List<WorkflowRunStepEntity>>? {
        val run = dao.latestRun(workflowId) ?: return null
        return run to dao.steps(run.id)
    }
}

private val stringMap = MapSerializer(String.serializer(), String.serializer())

private fun VisualWorkflow.toEntity(updatedAt: Long) = WorkflowEntity(
    id = id,
    name = name,
    enabled = enabled,
    graphJson = encode(),
    updatedAt = updatedAt,
)
