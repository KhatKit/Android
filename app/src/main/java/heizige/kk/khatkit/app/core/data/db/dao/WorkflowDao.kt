package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunStepEntity

@Dao
interface WorkflowDao {
    @Query("SELECT COUNT(*) FROM workflows")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkflowEntity)

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun get(id: String): WorkflowEntity?

    @Query("SELECT * FROM workflow_runs WHERE workflow_id = :workflowId ORDER BY started_at DESC LIMIT 1")
    suspend fun latestRun(workflowId: String): WorkflowRunEntity?

    @Query("SELECT * FROM workflow_run_steps WHERE run_id = :runId ORDER BY ordinal ASC")
    suspend fun steps(runId: String): List<WorkflowRunStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: WorkflowRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<WorkflowRunStepEntity>)

    @Query("UPDATE workflow_runs SET status = :status, finished_at = :finishedAt, error = :error WHERE id = :id")
    suspend fun updateRunStatus(id: String, status: String, finishedAt: Long?, error: String?)
}
