package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `group_runs` 建表 DDL（30→31）。
 *
 * 抽成常量而不是内联在 [Migration_30_31] 里，是为了让纯 JVM 测试
 * （`GroupRunSchemaTest`）能直接把它与 Room 导出的 `31.json` 里 Room 自己生成的
 * `createSql` 逐项比对——迁移 DDL 与实体声明漂移是这类手写迁移最常见、
 * 且只在真机升级时才炸的 bug，这里把它变成单测里就能发现的错误。
 */
val GROUP_RUNS_CREATE_SQL = """
    CREATE TABLE IF NOT EXISTS `group_runs` (
      `conversation_id` TEXT NOT NULL,
      `round_id` TEXT NOT NULL,
      `run_token` TEXT NOT NULL,
      `status` TEXT NOT NULL,
      `started_at` INTEGER NOT NULL,
      `spent_tokens` INTEGER NOT NULL DEFAULT 0,
      `token_limit` INTEGER NOT NULL DEFAULT 0,
      `skipped_role_ids` TEXT NOT NULL DEFAULT '',
      `committed_role_ids` TEXT NOT NULL DEFAULT '',
      `reason` TEXT NOT NULL DEFAULT '',
      `error_message` TEXT NOT NULL DEFAULT '',
      `updated_at` INTEGER NOT NULL,
      `ended_at` INTEGER,
      PRIMARY KEY(`conversation_id`, `round_id`)
    )
""".trimIndent()

/** `run_token` 全局唯一：两次执行不得共用同一令牌。 */
val GROUP_RUNS_RUN_TOKEN_INDEX_SQL =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_group_runs_run_token` ON `group_runs` (`run_token`)"

/** 运行日志 UI / 「最近 N 轮」按会话 + 开始时间倒序。 */
val GROUP_RUNS_CONVERSATION_STARTED_AT_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS `index_group_runs_conversation_id_started_at`
    ON `group_runs` (`conversation_id`, `started_at`)
""".trimIndent()

/** 僵尸 `RUNNING` 回收（进程被杀后残留的运行实例）。 */
val GROUP_RUNS_STATUS_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS `index_group_runs_status` ON `group_runs` (`status`)"

/**
 * `memory_chunks` 加可空 `role_id`。
 *
 * ⚠️ SQLite 的 `ALTER TABLE ADD COLUMN` 只能加可空列、且这里**不给 DEFAULT**：
 * 实体 `MemoryChunkEntity.roleId` 是 `String? = null` 且**没有** `defaultValue` 声明，
 * 两边必须一致，否则 Room 的 `TableInfo` 校验（比对列默认值）会在真机升级时直接抛异常。
 * 旧行迁移后 `role_id IS NULL`，语义正确：存量单聊/助手记忆没有发言角色。
 */
val MEMORY_CHUNKS_ADD_ROLE_ID_SQL =
    "ALTER TABLE `memory_chunks` ADD COLUMN `role_id` TEXT"

/** 群记忆按角色过滤时的检索入口。 */
val MEMORY_CHUNKS_ROLE_ID_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS `index_memory_chunks_role_id` ON `memory_chunks` (`role_id`)"

/**
 * C1-D 数据层升级：Room 30 → 31（显式手写迁移，风格同 [Migration_27_28]）。
 *
 * 31 版只有两处变化：
 * 1. **新建 `group_runs`**（见 [GROUP_RUNS_CREATE_SQL]）——群聊轮次运行日志。契约闸门
 *    「同一 `conversationId + roundId` 的 run token 必须持久化后才可执行」，因此主键取
 *    `(conversation_id, round_id)`：同一群同一轮第二次插入必然主键冲突，由
 *    `GroupRunDAO.insert`（`OnConflictStrategy.ABORT`）抛异常暴露，绝不静默覆盖既有运行日志。
 *    `run_token` 另建唯一索引，区分「这一轮是谁」与「这一次进程执行是谁」
 *    （重试复用 round_id 但换新令牌）。
 *    预算口径按契约落列：`spent_tokens`（本轮已用 prompt + completion 累计）、
 *    `token_limit`（本轮上限快照，0 = 不限）、`skipped_role_ids`（超预算未运行角色）、
 *    `reason`（如 `token_budget_exceeded`）、`committed_role_ids`（已提交 turn，续跑据此跳过）、
 *    `error_message`（失败节点）。每轮一行 ⇒「下一轮重新计数」天然成立，不跨轮挪用预算。
 * 2. **`memory_chunks` 增加可空 `role_id`**（见 [MEMORY_CHUNKS_ADD_ROLE_ID_SQL]）——群记忆
 *    按角色隔离的依据。契约要求群消息写入记忆时带 `source_message_id` 与 `role_id`。
 *
 * 为什么用显式迁移而不是 `AutoMigration(from = 30, to = 31)`：Room 的自动迁移为了加索引会走
 * 「建 `_new_memory_chunks` → 全量拷贝 → `DROP TABLE memory_chunks` → `RENAME`」。
 * `DROP TABLE` 会连带删掉 `AppDatabaseFactory.onOpen` 建的 `memory_chunks_ai/ad/au` 三个
 * FTS5 触发器（迁移完成到 onOpen 之间存在无触发器窗口）。这里用
 * `ALTER TABLE ADD COLUMN` 直接加列：不动旧表、不动 rowid、不动索引、不动触发器，
 * 存量数据零风险。
 */
val Migration_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- 1. 新建 group_runs（轮次运行日志 / run token 幂等） ----
        db.execSQL(GROUP_RUNS_CREATE_SQL)
        db.execSQL(GROUP_RUNS_RUN_TOKEN_INDEX_SQL)
        db.execSQL(GROUP_RUNS_CONVERSATION_STARTED_AT_INDEX_SQL)
        db.execSQL(GROUP_RUNS_STATUS_INDEX_SQL)

        // ---- 2. memory_chunks 增可空 role_id（群记忆来源过滤的依据） ----
        db.execSQL(MEMORY_CHUNKS_ADD_ROLE_ID_SQL)
        db.execSQL(MEMORY_CHUNKS_ROLE_ID_INDEX_SQL)
    }
}
