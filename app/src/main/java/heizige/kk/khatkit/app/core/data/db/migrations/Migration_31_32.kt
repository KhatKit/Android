package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `ConversationEntity` 加 `group_cards` 列的 DDL（31→32）。
 *
 * 抽成常量而不是内联在 [Migration_31_32] 里，理由与 [MEMORY_CHUNKS_ADD_ROLE_ID_SQL] 一样：
 * 让纯 JVM 测试（`ConversationGroupCardsSchemaTest`）能把它与 Room 导出的 `32.json` 里
 * Room 自己生成的 `createSql` 逐项比对。迁移 DDL 与实体声明漂移（差一个 DEFAULT /
 * NOT NULL / 列名）是这类手写迁移最常见、且只在真机升级那一刻才炸的 bug。
 *
 * 列的语义：`TEXT NOT NULL DEFAULT ''`，空串 = 这一列没有角色卡。
 * 实体 `ConversationEntity.groupCards` 是 `String = ""` 且**声明了** `defaultValue = ""`，
 * 两边必须一致，否则 Room 的 `TableInfo` 校验（比对列默认值）会在真机升级时直接抛异常。
 *
 * ⚠️ SQLite 的 `ALTER TABLE ADD COLUMN` 允许加 `NOT NULL` 列，**前提是给了非 NULL 的
 * DEFAULT**（这正是这里写 `DEFAULT ''` 而不是不写的原因）：存量行迁移后 `group_cards`
 * 都是 `''`，Repository 侧解码成 `null`（见 `GroupChat.decodeRoleCards`），语义正确——
 * 这些会话本来就没有导入过角色卡。
 *
 * 加列而不是加表：本列只随会话行整体读写（导入时写一次、之后只读），没有任何「按
 * conversationId 单独查 cards」的查询需求，独立成表只会多一张实体、一套 DAO 和一份迁移面。
 */
val CONVERSATION_ADD_GROUP_CARDS_SQL =
    "ALTER TABLE `ConversationEntity` ADD COLUMN `group_cards` TEXT NOT NULL DEFAULT ''"

/**
 * C1-P 数据层升级：Room 31 → 32（显式手写迁移，风格同 [Migration_30_31]）。
 *
 * 32 版只有一处变化：`ConversationEntity` 增加 `group_cards`（见
 * [CONVERSATION_ADD_GROUP_CARDS_SQL]）——群聊导入带进来的角色卡元数据
 * （`GroupSharePayload.cards`）从此落库，刷新页面 / 重启进程不再丢。
 *
 * 为什么用显式迁移而不是 `AutoMigration(from = 31, to = 32)`：Room 的自动迁移为了加列会走
 * 「建 `_new_ConversationEntity` → 全量拷贝 → `DROP TABLE` → `RENAME`」。`DROP TABLE` 会连带
 * 删掉 `AppDatabaseFactory.onOpen` 建的 `memory_chunks_ai/ad/au` 三个 FTS5 触发器
 * （迁移完成到 onOpen 之间存在无触发器窗口），并且 `message_node` 上有
 * `ON DELETE CASCADE REFERENCES ConversationEntity(id)`（`Migration_11_12.kt:32`），
 * 重建父表期间外键关系会被拆掉再重建。这里用 `ALTER TABLE ADD COLUMN` 直接加列：
 * 不动旧表、不动 rowid、不动索引、不动触发器、不动外键，存量数据零风险。
 */
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CONVERSATION_ADD_GROUP_CARDS_SQL)
    }
}