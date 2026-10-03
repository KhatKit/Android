package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workflows",
    indices = [Index(value = ["updated_at"])],
)
data class WorkflowEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "graph_json")
    val graphJson: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "workflow_runs",
    indices = [
        Index(value = ["workflow_id"]),
        Index(value = ["started_at"]),
    ],
)
data class WorkflowRunEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "workflow_id")
    val workflowId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "attempt")
    val attempt: Int,
    @ColumnInfo(name = "cost_tokens")
    val costTokens: Int,
    @ColumnInfo(name = "error")
    val error: String?,
)

@Entity(
    tableName = "workflow_run_steps",
    indices = [Index(value = ["run_id"])],
)
data class WorkflowRunStepEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "run_id")
    val runId: String,
    @ColumnInfo(name = "node_id")
    val nodeId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "input_json")
    val inputJson: String,
    @ColumnInfo(name = "output_json")
    val outputJson: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
)
