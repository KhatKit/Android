package heizige.kk.khatkit.app.core.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import heizige.kk.khatkit.app.core.data.db.entity.ConversationEntity
import heizige.kk.khatkit.app.core.data.repository.CONVERSATION_LIKE_ESCAPE_SQL
import heizige.kk.khatkit.app.core.data.repository.LightConversationEntity

/**
 * 会话类型筛选谓词，`:type` 传空串表示不筛（抽屉的「全部」chip）。
 *
 * 「按助手取未归档」和「按助手搜索」两条查询共用同一份文本，两条路只有一套 type 语义，
 * 不会各自长出一个空串含义不同的写法。抽成常量也是为了能在 JVM 单测里直接核对这份文本。
 */
internal const val CONVERSATION_TYPE_PREDICATE_SQL = " AND (:type = '' OR type = :type)"

/**
 * 本文件里**每一条**用 `:searchText` 做 LIKE 的查询都必接 [CONVERSATION_LIKE_ESCAPE_SQL]。
 *
 * 不接的话，SQLite 会把用户输入里的 `%` / `_` 当通配符：`LIKE '%' || :searchText || '%'`
 * 搜 `100%` 会连不含 `%` 的会话一起命中，搜 `a_b` 会命中 `axbxc`，搜单个 `%` 命中全部 ——
 * **只多命中、不漏命中**，所以是「搜出来的比想搜的多」这种很难被用户当成 bug 报上来的坑。
 *
 * 转义字符选 `~` 的理由见 `CONVERSATION_LIKE_ESCAPE_CHAR` 的 KDoc（不用 `\`/`%`/`_`）。
 * 转义本身由 `escapeConversationLikePattern` 在**进 DAO 之前**完成，`ConversationRepository`
 * 是唯一收敛点，DAO 的形参约定因此收紧为：**`:searchText` 必须是已转义片段**。
 *
 * ESCAPE 子句在 SQL 里的位置是被 SQLite 的文法钉死的：它必须紧跟在 LIKE 的右操作数后面，
 * 而 type 谓词是另一个 AND 合取项，所以拼接顺序是 `LIKE ... '%'` + `ESCAPE '~'` +
 * `CONVERSATION_TYPE_PREDICATE_SQL`；反过来写（谓词在前、ESCAPE 收尾）实测直接报
 * `near "ESCAPE": syntax error`。
 */

@Dao
interface ConversationDAO {
    @Query("SELECT * FROM conversationentity ORDER BY is_pinned DESC, update_at DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversationentity ORDER BY is_pinned DESC, update_at DESC")
    fun getAllPaging(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC")
    fun getConversationsOfAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC")
    fun getConversationsOfAssistantPaging(assistantId: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE assistant_id = :assistantId AND folder_id = '' ORDER BY is_pinned DESC, update_at DESC")
    fun getUnfiledConversationsOfAssistantPaging(assistantId: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE assistant_id = :assistantId AND folder_id = ''" + CONVERSATION_TYPE_PREDICATE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun getUnfiledConversationsOfAssistantByType(assistantId: String, type: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE folder_id = :folderId ORDER BY is_pinned DESC, update_at DESC")
    fun getConversationsOfFolderPaging(folderId: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC LIMIT :limit")
    suspend fun getRecentConversationsOfAssistant(assistantId: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE title LIKE '%' || :searchText || '%'" + CONVERSATION_LIKE_ESCAPE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversations(searchText: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE title LIKE '%' || :searchText || '%'" + CONVERSATION_LIKE_ESCAPE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsPaging(searchText: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId AND title LIKE '%' || :searchText || '%'" + CONVERSATION_LIKE_ESCAPE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsOfAssistant(assistantId: String, searchText: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE assistant_id = :assistantId AND title LIKE '%' || :searchText || '%'" + CONVERSATION_LIKE_ESCAPE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsOfAssistantPaging(assistantId: String, searchText: String): PagingSource<Int, LightConversationEntity>

    /**
     * 按助手 + 标题关键字搜索会话，可再按会话类型收窄。
     *
     * type 的空串语义与 [getUnfiledConversationsOfAssistantByType] 完全一致：`''` = 不筛，
     * 只有一个筛选维度（搜索 / 类型）在同一条 SQL 里判定，不存在两套口径。
     * 默认值 `''` 让旧的单聊调用点（不传 type）行为不变。
     *
     * `searchText` 必须是 [heizige.kk.khatkit.app.core.data.repository.escapeConversationLikePattern]
     * 转义过的片段（由 `ConversationRepository` 统一转义）：没有 ESCAPE 时用户输入的
     * `%` / `_` 会被当通配符，「只多命中不漏命中」。
     */
    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, folder_id as folderId, type, group_config as groupConfig FROM conversationentity WHERE assistant_id = :assistantId AND title LIKE '%' || :searchText || '%'" + CONVERSATION_LIKE_ESCAPE_SQL + CONVERSATION_TYPE_PREDICATE_SQL + " ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsOfAssistantByType(
        assistantId: String,
        searchText: String,
        type: String = "",
    ): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE id = :id")
    fun getConversationFlowById(id: String): Flow<ConversationEntity?>

    @Query("SELECT id FROM conversationentity")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM conversationentity WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM conversationentity WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("UPDATE conversationentity SET nodes = '[]' WHERE id = :id")
    suspend fun resetConversationNodes(id: String)

    @Query("DELETE FROM conversationentity WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversationentity")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversationentity WHERE is_pinned = 1 ORDER BY update_at DESC")
    fun getPinnedConversations(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversationentity SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean)

    @Query("UPDATE conversationentity SET assistant_id = :assistantId, folder_id = '' WHERE id = :id")
    suspend fun updateAssistantId(id: String, assistantId: String)

    @Query("UPDATE conversationentity SET folder_id = :folderId WHERE id = :id")
    suspend fun updateFolderId(id: String, folderId: String)

    @Query("UPDATE conversationentity SET folder_id = '' WHERE folder_id = :folderId")
    suspend fun clearFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM conversationentity")
    suspend fun countAll(): Int

    @Query("SELECT assistant_id AS assistantId, COUNT(*) AS count FROM conversationentity GROUP BY assistant_id")
    suspend fun countByAssistant(): List<AssistantConversationCount>

    @Query(
        "SELECT strftime('%Y-%m-%d', create_at/1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS count " +
            "FROM conversationentity " +
            "WHERE create_at >= :startMillis " +
            "GROUP BY day"
    )
    suspend fun getConversationCountPerDay(startMillis: Long): List<ConversationDayCount>
}

data class ConversationDayCount(val day: String, val count: Int)

data class AssistantConversationCount(val assistantId: String, val count: Int)
