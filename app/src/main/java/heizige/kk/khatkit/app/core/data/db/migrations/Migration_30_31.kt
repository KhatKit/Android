package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * C1-D 数据层升级：Room 30 → 31（显式手写迁移，风格同 [Migration_27_28]）。
 *
 * 31 版只有两处变化：
 * 1. **新建 `group_runs`**——群聊轮次运行日志。契约闸门「同一 `conversationId + roundId`
 *    的 run token 必须持久化后才可执行」，因此主键取 `(conversation_id, round_id)`：
 *    同一群同一轮第二次插入必然主键冲突，由 `GroupRunDAO.insert`（`OnConflictStrategy.ABORT`）
 *    抛异常暴露，绝不静默覆盖。`run_token` 另建唯一索引，区分「这一轮是谁」与
 *    「这一次进程执行是谁」（重试复用 round_id 但换新令牌）。
 *    预算口径按契约落列：`spent_tokens`（本轮已用 prompt+completion）、`token_limit`
 *    （本轮上限快照，0 = 不限）、`skipped_role_ids`（超预算未运行角色）、
 *    `reason`（如 `token_budget_exceeded`）、`committed_role_ids`（已提交 turn，续跑据此跳过）、
 *    `error_message`（失败节点）。每轮一行 ⇒「下一轮重新计数」天然成立，不跨轮挪用预算。
 * 2. **`memory_chunks` 增加可空 `role_id`**——群记忆按角色隔离的依据。契约要求群消息写入
 *    记忆时带 `source_message_id` 与 `role_id`。存量单聊/助手记忆没有角色，故必须可空：
 *    实体上**不声明** `defaultValue`，DDL 也不给 DEFAULT，旧行迁移后自然为 NULL。
 *
 * 为什么用显式迁移而不是 `AutoMigration(from = 30, to = 31)`：Room 的自动迁移为了加索引会走
 * 「建 `_new_memory_chunks` → 全量拷贝 → `DROP TABLE memory_chunks` → `RENAME`」。
 * `DROP TABLE` 会连带删掉 `AppDatabaseFactory.onOpen` 建的 `memory_chunks_ai/ad/au` 三个 FTS5
 * 触发器与 `memory_chunk_fts` 的内容对应关系（虽然 onOpen 会重建，但迁移完成到 onOpen 之间
 * 存在窗口）。这里用 `ALTER TABLE ADD COLUMN` 直接加列：不动旧表、不动 rowid、不动索引、
 * 不动触发器，旧数据零风险。
 *
 * Room 校验口径：`TableInfo` 会比对列名/类型/NOT NULL/默认值、主键与索引，
 * 所以下面的 DDL 必须与 `GroupRunEntity` / `MemoryChunkEntity` 逐项一致；
 * `GroupRunSchemaTest`（JVM）会拿导出的 `31.json` 反向核对这段 DDL，防止漂移。
 */
val Migration_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- 1. 新建 group_runs（轮次运行日志 / run token 幂等） ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `group_runs` (
              `conversation_id` TEXT NOT NULL,
              `round_id` TEXT NOT NULL,
              `run_token` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `spent_tokens` INTEGER NOT NULL DEFAULT 0,
              `token_limit` INTEGER NOT NULL DEFAULT 0,
              `skipped_role_ids` TEXT NOT NULL DEFAULT '',
              `committed_role_ids` TEXT NOT NULL DEFAULT '',
              `reason` TEXT NOT NULL DEFAULT '',
              `error_message` TEXT NOT NULL DEFAULT '',
              `started_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              `ended_at` INTEGER,
              PRIMARY KEY(`conversation_id`, `round_id`)
            )
            """.trimIndent()
        )
        // run token 全局唯一：两次执行不得共用同一令牌
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_group_runs_run_token` ON `group_runs` (`run_token`)"
        )
        // 运行日志 UI / 最近 N 轮
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_group_runs_conversation_id_started_at`
            ON `group_runs` (`conversation_id`, `started_at`)
            """.trimIndent()
        )
        // 僵尸 RUNNING 回收
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_runs_status` ON `group_runs` (`status`)")

        // ---- 2. memory_chunks 增可空 role_id（群记忆来源过滤的依据） ----
        // 可空且无 DEFAULT：SQLite 的 ADD COLUMN 只能加可空列，旧行迁移后 role_id IS NULL，
        // 与实体 `roleId: String? = null`（无 defaultValue）一致，Room 校验可通过。
        db.execSQL("ALTER TABLE `memory_chunks` ADD COLUMN `role_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_chunks_role_id` ON `memory_chunks` (`role_id`)"
        )
    }
}
