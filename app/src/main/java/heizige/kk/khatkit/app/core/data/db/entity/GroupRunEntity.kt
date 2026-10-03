package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import heizige.kk.khatkit.app.core.util.JsonInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * C1-D 群聊轮次运行记录（run token）。
 *
 * 契约闸门：「同一 `conversationId + roundId` 的 run token 必须持久化后才可执行」。
 * 因此本表的 [id] 就是 run token 本身（执行器必须先落库再发起模型调用），
 * 并且 `conversation_id + round_id` 建**唯一索引**，在数据库层兜底
 * 「同一群同一 round_id 只允许一个运行实例」——即使上层 mutex 失效，
 * 第二次插入也会由 `OnConflictStrategy.ABORT` 直接抛异常而不是静默覆盖。
 *
 * 重试语义：`round_id` 由 `GroupChat.roundIdFor(triggerMessageId)` 派生，重试必然
 * 复用同一个 [roundId]；执行器读回 [committedRoleIds] 跳过已提交 turn，避免重复消息。
 *
 * 预算口径：[spentTokens] 只累计本轮 prompt + completion，达到 [limitTokens]（配置快照）
 * 后停止剩余角色，并把未运行角色写进 [skippedRoleIds]、超预算原因写进 [reason]。
 * 下一轮重新计数，不挪用其他轮预算（每轮一条独立记录）。
 *
 * `committed_role_ids` / `skipped_role_ids` 以 JSON 数组字符串落 TEXT，
 * 转换见 [StringListConverter]（容错解析，脏数据退化为空列表而非崩溃）。
 */
@Entity(
    tableName = "group_runs",
    indices = [
        // 幂等基石：同一群同一轮次只允许一条运行记录
        Index(value = ["conversation_id", "round_id"], unique = true),
        // 运行日志 UI：按群倒序列出历史
        Index(value = ["conversation_id", "started_at"]),
        // 崩溃/强杀后清理僵尸 RUNNING 记录
        Index(value = ["status"]),
    ],
)
data class GroupRunEntity(
    /** run token 本身；必须先持久化才允许执行本轮。 */
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    /** 轮次 id，来自 `GroupChat.roundIdFor(triggerMessageId)`，重试复用。 */
    @ColumnInfo("round_id")
    val roundId: String,
    @ColumnInfo("status")
    val status: String,
    /** 本轮已用 prompt + completion 累计。 */
    @ColumnInfo("spent_tokens")
    val spentTokens: Int = 0,
    /** 本轮预算上限快照（`token_budget_per_round`），下一轮不沿用。 */
    @ColumnInfo("limit_tokens")
    val limitTokens: Int = 0,
    /** 因预算/取消/失败未运行的角色（契约要求的「未运行角色」）。 */
    @ColumnInfo("skipped_role_ids")
    val skippedRoleIds: List<String> = emptyList(),
    /** 超预算原因 / 失败原因 / 取消原因。 */
    @ColumnInfo("reason")
    val reason: String? = null,
    /** 已成功提交的角色；重试时据此跳过，避免重复消息。 */
    @ColumnInfo("committed_role_ids")
    val committedRoleIds: List<String> = emptyList(),
    /** 失败节点信息（哪个角色、什么错）。 */
    @ColumnInfo("error_message")
    val errorMessage: String? = null,
    @ColumnInfo("started_at")
    val startedAt: Long,
    @ColumnInfo("ended_at")
    val endedAt: Long? = null,
) {
    companion object {
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMMITTED = "COMMITTED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_TIMEOUT = "TIMEOUT"

        val ALL_STATUSES: Set<String> = setOf(
            STATUS_RUNNING, STATUS_COMMITTED, STATUS_FAILED,
            STATUS_CANCELLED, STATUS_TIMEOUT,
        )

        /** 已结束（不可再改 committed 集合）的状态。 */
        val TERMINAL_STATUSES: Set<String> = setOf(
            STATUS_COMMITTED, STATUS_FAILED, STATUS_CANCELLED, STATUS_TIMEOUT,
        )

        fun isTerminal(status: String): Boolean = status in TERMINAL_STATUSES

        /** 进程被杀后可能残留的僵尸状态，用于启动时清理。 */
        fun isStaleRunning(status: String): Boolean = status == STATUS_RUNNING

        const val REASON_TOKEN_BUDGET_EXCEEDED = "token_budget_exceeded"
        const val REASON_CANCELLED = "cancelled"
        const val REASON_TIMEOUT = "timeout"
        const val REASON_ROLE_FAILED = "role_failed"
        const val REASON_NO_SPEAKER = "no_speaker"
    }
}

/**
 * `List<String>` ↔ JSON 数组字符串转换（`committed_role_ids` / `skipped_role_ids`）。
 *
 * 纯 JVM 实现（只用 kotlinx.serialization），因此可在单元测试里直接验证往返。
 * 反序列化对脏数据容错：空串/空白/非法 JSON 一律退化为空列表，
 * 避免旧库或人工改库后打开即崩。
 */
object StringListConverter {
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        JsonInstant.encodeToString(stringListSerializer, value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { JsonInstant.decodeFromString(stringListSerializer, value) }
            .getOrDefault(emptyList())
    }
}
