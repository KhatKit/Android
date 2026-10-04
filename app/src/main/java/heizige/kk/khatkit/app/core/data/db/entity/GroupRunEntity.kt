package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverter
import heizige.kk.khatkit.app.core.util.JsonInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * C1-D 群聊轮次运行记录（run log / run token 持久化）。
 *
 * 契约依据（`docs/beyond-operit-client-changes.md` C1 实施契约）：
 * - 「同一 `conversationId + roundId` 的 run token 必须持久化后才可执行，重试沿用原 round
 *   并跳过已提交 turn」→ 本表**主键就是 `(conversation_id, round_id)`**，插入前必须先落库；
 *   重复插入由主键冲突直接拒绝，这是「同一群同一轮只允许一个运行实例」的数据库级保证。
 * - 「重试使用同一 `round_id` 并跳过已提交 turn，避免重复消息」→ [committedRoleIds]。
 * - 「超预算原因、已用/上限与未运行角色写入运行日志。下一轮重新计数，不挪用其他轮预算」
 *   → 每轮一行独立记录（不按会话累加），[spentTokens] / [tokenLimit] 是**本轮**快照，
 *   [skippedRoleIds] 记未运行角色，[reason] 记超预算原因。
 * - 「单角色失败记录错误节点并停止该轮（不伪造回复）；已完成输出保留，可从失败角色续跑」
 *   → [status] = [STATUS_FAILED] + [errorMessage]，已完成角色仍在 [committedRoleIds] 里，
 *   续跑据此跳过。
 * - 「取消/超时只写运行日志，不写虚构消息」→ [STATUS_CANCELLED] / [STATUS_TIMEOUT]。
 *
 * [runToken] 是「开始跑一轮」时生成的唯一令牌（UUID）。它与主键是**两个不同的东西**：
 * 主键回答「这一群这一轮是谁」，runToken 回答「这一次进程执行是谁」；进程被杀后重试会
 * 复用同一 `round_id` 但拿到**新的** runToken，据此把僵尸 RUNNING 记录与新尝试区分开。
 * [runToken] 上建 UNIQUE 索引，保证任何两次执行都不共用同一令牌。
 *
 * `committed_role_ids` / `skipped_role_ids` 以 JSON 数组字符串落 TEXT，
 * 转换见 [StringListConverter]（容错解析，脏数据退化为空列表而非崩溃）。
 */
@Entity(
    tableName = "group_runs",
    primaryKeys = ["conversation_id", "round_id"],
    indices = [
        // runToken 全局唯一：两次执行不得共用同一令牌
        Index(value = ["run_token"], unique = true),
        // 运行日志 UI / 「下一轮重新计数」：按群倒序列出历史
        Index(value = ["conversation_id", "started_at"]),
        // 崩溃/强杀后清理僵尸 RUNNING 记录
        Index(value = ["status"]),
    ],
)
data class GroupRunEntity(
    /** 群会话 id（`Conversation.id`）。主键第 1 段。 */
    @ColumnInfo("conversation_id")
    val conversationId: String,
    /** 轮次 id，来自 `GroupChat.roundIdFor(triggerMessageId)`，重试复用。主键第 2 段。 */
    @ColumnInfo("round_id")
    val roundId: String,
    /** 本次执行的唯一令牌；必须先持久化才允许发起模型调用。 */
    @ColumnInfo("run_token")
    val runToken: String,
    /** 见 companion object 的 `STATUS_*`。 */
    @ColumnInfo("status")
    val status: String,
    /** 本轮开始时刻（epoch millis）。 */
    @ColumnInfo("started_at")
    val startedAt: Long,
    /** 本轮**已用** prompt + completion 累计（不是最后一条消息的 totalTokens）。 */
    @ColumnInfo("spent_tokens", defaultValue = "0")
    val spentTokens: Int = 0,
    /** 本轮上限快照（`GroupConfig.tokenBudgetPerRound`）；0 表示不限。下一轮不沿用。 */
    @ColumnInfo("token_limit", defaultValue = "0")
    val tokenLimit: Int = 0,
    /** 因超预算/取消/失败**未运行**的角色（契约要求的「未运行角色」）。 */
    @ColumnInfo("skipped_role_ids", defaultValue = "''")
    val skippedRoleIds: List<String> = emptyList(),
    /** 已成功提交 turn 的角色；重试/续跑据此跳过，避免重复消息。 */
    @ColumnInfo("committed_role_ids", defaultValue = "''")
    val committedRoleIds: List<String> = emptyList(),
    /** 超预算原因 / 失败原因 / 取消原因，见 companion object 的 `REASON_*`。 */
    @ColumnInfo("reason", defaultValue = "''")
    val reason: String = "",
    /** 失败角色的错误信息（失败节点）。 */
    @ColumnInfo("error_message", defaultValue = "''")
    val errorMessage: String = "",
    /** 最后一次写入的时刻，运行日志排序/排查用。 */
    @ColumnInfo("updated_at")
    val updatedAt: Long = startedAt,
    /** 结束时刻；仍为 null 表示尚未收尾（含僵尸 RUNNING）。 */
    @ColumnInfo("ended_at")
    val endedAt: Long? = null,
) {
    companion object {
        /** 本轮正在跑。只有这一个状态允许继续追加 committed / 累加 spent。 */
        const val STATUS_RUNNING = "RUNNING"

        /** 本轮全部角色发言完成且全部提交成功。 */
        const val STATUS_COMPLETED = "COMPLETED"

        /** 单角色失败 → 停止该轮（不伪造回复）。已完成角色保留，可从失败角色续跑。 */
        const val STATUS_FAILED = "FAILED"

        /** 用户取消：只写运行日志，不写未生成的消息。 */
        const val STATUS_CANCELLED = "CANCELLED"

        /** 达到 [tokenLimit] 后停止剩余角色；已生成内容保留，未运行角色进 [skippedRoleIds]。 */
        const val STATUS_BUDGET_STOPPED = "BUDGET_STOPPED"

        /** 超时：同取消语义，只写运行日志。 */
        const val STATUS_TIMEOUT = "TIMEOUT"

        /**
         * 兼容别名：早期草稿用 `COMMITTED` 表示「本轮正常收尾」，现统一为 [STATUS_COMPLETED]。
         * 值相同（都是 `"COMPLETED"`），落库与 [isTerminal] 判定完全一致，仅为不让并行包编译失败。
         */
        const val STATUS_COMMITTED = STATUS_COMPLETED

        val ALL_STATUSES: Set<String> = setOf(
            STATUS_RUNNING, STATUS_COMPLETED, STATUS_FAILED,
            STATUS_CANCELLED, STATUS_BUDGET_STOPPED, STATUS_TIMEOUT,
        )

        /** 已结束（不可再改 committed 集合）的状态。 */
        val TERMINAL_STATUSES: Set<String> = setOf(
            STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED,
            STATUS_BUDGET_STOPPED, STATUS_TIMEOUT,
        )

        fun isTerminal(status: String): Boolean = status in TERMINAL_STATUSES

        /** 进程被杀后可能残留的僵尸状态，用于启动时清理。 */
        fun isStaleRunning(status: String): Boolean = status == STATUS_RUNNING

        /** 契约点名的超预算原因字面量（与 `GroupChat` 侧预算判定保持一致）。 */
        const val REASON_TOKEN_BUDGET_EXCEEDED = "token_budget_exceeded"
        const val REASON_CANCELLED = "cancelled"
        const val REASON_TIMEOUT = "timeout"

        /** 单角色失败：本轮在此停止，失败角色 id 记在这里，已完成角色留在 committedRoleIds。 */
        const val REASON_ROLE_FAILED = "role_failed"

        /** 本轮没有任何可发言角色（如全员被 @ 规则过滤掉）。 */
        const val REASON_NO_SPEAKER = "no_speaker"

        /** BUDGET_STOPPED 的固定原因；与 [REASON_TOKEN_BUDGET_EXCEEDED] 等价，保留旧名以兼容。 */
        const val REASON_BUDGET_EXCEEDED = REASON_TOKEN_BUDGET_EXCEEDED
    }
}

/**
 * `List<String>` ↔ JSON 数组字符串转换（`committed_role_ids` / `skipped_role_ids`）。
 *
 * 纯 JVM 实现（只用 kotlinx.serialization），因此可在单元测试里直接验证往返。
 * 反序列化对脏数据容错：空串/空白/非法 JSON 一律退化为空列表，
 * 避免旧库或人工改库后打开即崩。写出侧恒为合法 JSON 数组（空列表写 `[]`），
 * 与实体上的 `defaultValue = "''"` 不冲突（`''` 只在列缺省时出现，读回来同样是空列表）。
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
