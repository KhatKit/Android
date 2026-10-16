package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified local task record for direct card runs and agent-triggered card runs.
 *
 * The record is deliberately independent from chat messages: a card can be run
 * directly, resumed from a result, or started by an event without creating a
 * conversation.
 */
@Entity(
    tableName = "card_runs",
    indices = [
        Index(value = ["started_at"]),
        Index(value = ["card_name", "started_at"]),
        Index(value = ["status"]),
    ],
)
data class CardRunEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "parent_run_id")
    val parentRunId: String? = null,
    @ColumnInfo(name = "card_name")
    val cardName: String,
    @ColumnInfo(name = "card_version")
    val cardVersion: String,
    @ColumnInfo(name = "trigger")
    val trigger: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    @ColumnInfo(name = "args_summary")
    val argsSummary: String = "",
    @ColumnInfo(name = "args_template", defaultValue = "{}")
    val argsTemplate: String = "{}",
    @ColumnInfo(name = "output_summary")
    val outputSummary: String = "",
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
) {
    companion object {
        const val QUEUED = "queued"
        const val PREPARING = "preparing"
        const val RUNNING = "running"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
        const val CANCELLED = "cancelled"
        const val INTERRUPTED = "interrupted"
        const val WAITING_USER = "waiting_user"
    }
}
