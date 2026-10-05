package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoundBudget
import heizige.kk.khatkit.app.core.data.model.RoundPlan
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.model.VoteBallot
import heizige.kk.khatkit.app.core.data.model.VoteOutcome

/**
 * C1-R 群聊轮次执行内核：**纯判定**，不含执行器、不读密钥、不写库。
 *
 * 契约要求（「上下文过滤必须发生在 prompt 组装层」「协作内核只接收不可变消息快照并返回
 * 运行日志」）决定了这里的形态：所有函数都只吃不可变入参、返回不可变结果；
 * 落库、抢占 run token、发起模型调用都由 [ChatManager] 这个执行层负责。
 *
 * 权威状态在 `group_runs`（`GroupRunDAO`），本文件的 [RoundState] 只是它的一一映射快照。
 * 进程内的 `groupTurns` 已被彻底移除：待发言角色每次都由
 * `plan - committedRoleIds` 现算，因此不存在「内存态与库不一致导致重复发言」的窗口，
 * 也满足契约「不同角色不得共享可变 prompt buffer」——本文件不持有任何可变集合。
 *
 * 判定分四段，与契约条款对应：
 * 1. [claimRound]：同一 `(conversationId, roundId)` 只允许一个运行实例 + 重试跳过已提交 turn。
 * 2. [advance] / [fail] / [cancelRound] / [timeoutRound]：失败语义与预算口径（prompt+completion）。
 * 3. [resolveVote] / [voteSummaryMessage] / [tieBreakInstruction]：投票与平票裁决。
 * 4. [viewerMessages] / [memoriesForViewer] / [memoryQuery]：工具、检索与记忆注入的同源上下文。
 */
object GroupTurnCoordinator {

    /**
     * 投票未能得出结论时写入 `group_runs.reason` 的原因字面量。
     *
     * `GroupRunEntity` 已有 `REASON_TOKEN_BUDGET_EXCEEDED` / `REASON_CANCELLED` /
     * `REASON_TIMEOUT` / `REASON_ROLE_FAILED` / `REASON_NO_SPEAKER`，但没有「平票未决」
     * 这一类（契约新增的失败语义）。`reason` 是自由 TEXT 列，DAO 也不校验取值，
     * 因此这里自带字面量；等 `core/data/db` 的 owner 认可后再并入 `GroupRunEntity.REASON_*`。
     */
    const val REASON_VOTE_NO_DECISION = "vote_no_decision"

    /** 裁决指令的 `turnKind`。与议长正常汇总区分开，便于回收时精确删除。 */
    private const val TURN_TIE_BREAK = GroupChat.TURN_CHAIR

    // ------------------------------------------------------------------
    // 运行态快照
    // ------------------------------------------------------------------

    /**
     * 一轮运行的不可变快照，字段与 `group_runs` 一一对应。
     *
     * `runToken` 在一轮内**不可变**：`GroupRunDAO` 的所有 UPDATE 都按
     * `(conversation_id, round_id)` 定位且没有改写 `run_token` 的语句，因此本轮第一次
     * 抢占时写入的令牌就是这一轮的执行血缘标识；僵尸回收与续跑都沿用它，
     * 「本进程是否正在驱动这一轮」由调用方传入的 `activeRunToken` 判定。
     */
    data class RoundState(
        val conversationId: String,
        val roundId: String,
        val runToken: String,
        val status: String,
        /** 本轮已用 prompt + completion 累计。 */
        val spentTokens: Int,
        /** 本轮上限快照；下一轮重新计数，不挪用其他轮。 */
        val tokenLimit: Int,
        val committedRoleIds: List<String>,
        val skippedRoleIds: List<String>,
        val reason: String,
        val errorMessage: String,
        val startedAt: Long,
        val updatedAt: Long,
        val endedAt: Long?,
    ) {
        /** 同一活跃 run 的下一步：只推进时间戳与上限快照，不动不可变字段。 */
        internal fun progressed(tokenLimit: Int, now: Long): RoundState =
            copy(tokenLimit = tokenLimit, updatedAt = now)

        /**
         * 续跑/僵尸回收：清掉上一段的终态语义，重新变回 RUNNING。
         * [skippedRoleIds] 一并清空——预算调高后这些角色本轮还要再跑。
         */
        internal fun reclaimed(tokenLimit: Int, now: Long): RoundState = copy(
            status = GroupRunEntity.STATUS_RUNNING,
            tokenLimit = tokenLimit,
            skippedRoleIds = emptyList(),
            reason = "",
            errorMessage = "",
            endedAt = null,
            updatedAt = now,
        )

        /** 收尾：写终态 + 结束时刻 + 原因 + 错误信息。 */
        internal fun terminal(status: String, reason: String, errorMessage: String, now: Long): RoundState =
            copy(
                status = status,
                reason = reason,
                errorMessage = errorMessage,
                endedAt = now,
                updatedAt = now,
            )
    }

    /** [RoundState] → 实体。写库时用。 */
    fun toEntity(state: RoundState): GroupRunEntity = GroupRunEntity(
        conversationId = state.conversationId,
        roundId = state.roundId,
        runToken = state.runToken,
        status = state.status,
        startedAt = state.startedAt,
        spentTokens = state.spentTokens,
        tokenLimit = state.tokenLimit,
        skippedRoleIds = state.skippedRoleIds,
        committedRoleIds = state.committedRoleIds,
        reason = state.reason,
        errorMessage = state.errorMessage,
        updatedAt = state.updatedAt,
        endedAt = state.endedAt,
    )

    /** 实体 → 快照。读库时用。 */
    fun fromEntity(entity: GroupRunEntity): RoundState = RoundState(
        conversationId = entity.conversationId,
        roundId = entity.roundId,
        runToken = entity.runToken,
        status = entity.status,
        spentTokens = entity.spentTokens,
        tokenLimit = entity.tokenLimit,
        committedRoleIds = entity.committedRoleIds,
        skippedRoleIds = entity.skippedRoleIds,
        reason = entity.reason,
        errorMessage = entity.errorMessage,
        startedAt = entity.startedAt,
        updatedAt = entity.updatedAt,
        endedAt = entity.endedAt,
    )

    // ------------------------------------------------------------------
    // 1. 幂等：抢占 / 续跑 / 拒绝
    // ------------------------------------------------------------------

    /** [Claim.Rejected] 的原因码。 */
    enum class RejectCode {
        /** 本进程已有活跃实例，或别的进程已抢占本轮：不得重复跑。 */
        ALREADY_RUNNING,

        /** 本轮已完成（跨进程/重启后依然判定「这轮已跑过」）。 */
        ALREADY_COMPLETED,

        /** 本轮没有任何可发言角色（@ 规则把所有人都过滤掉了）。 */
        NO_SPEAKER,
    }

    sealed interface Claim {
        /** 抢到执行权：需要把 [state] 以 RUNNING 落库（新建行或把既有行推回 RUNNING）。 */
        data class Acquired(val state: RoundState, val freshRow: Boolean) : Claim

        /** 同一活跃 run 的下一步：run token 不变，只换发言者。 */
        data class Continued(val state: RoundState) : Claim

        /** 不许执行。[state] 非空时是拒绝依据（写运行日志用）。 */
        data class Rejected(val state: RoundState?, val code: RejectCode) : Claim
    }

    /**
     * 产出归属判定结果，见 [checkCommitAdmission]。
     *
     * 与 [Claim] 分开是因为判据不同：[Claim] 回答「这一轮现在能不能开跑」，
     * 本类型回答「这一份已经跑完的产出能不能记进那一轮」。两者都遵守同一条纪律：
     * 被拒时执行层**一个副作用都不做**就放弃。
     */
    sealed interface CommitAdmission {
        /** 归属正确：[row] 就是本次提交该落库的那一行。 */
        data class Admitted(val row: RoundState) : CommitAdmission

        /**
         * 归属不对，拒收。[row] 是查到的那一行（null = 根本没有这一行），
         * 只用于写运行日志，**不得**拿它去落库。
         */
        data class Denied(val row: RoundState?, val detail: String) : CommitAdmission
    }

    /**
     * 轮次准入判定。**调用方必须先把 [Claim.Acquired] / [Claim.Continued] 的落库做完，
     * 才允许发起模型调用**（契约「run token 必须持久化后才可执行」）。
     *
     * @param existing `group_runs` 里这一轮现有的行（null = 从没跑过）
     * @param expectedRunToken 非空表示「本进程正在驱动这一轮、现在要下一位发言者」
     *   （由执行层显式透传，而不是从全局状态反查，这样重复触发无法伪装成续跑）
     * @param activeRunToken 本进程当前正在驱动的轮次令牌；非空且与 `existing.runToken`
     *   不同时判定为真并发重复触发
     */
    fun claimRound(
        conversationId: String,
        plan: RoundPlan,
        tokenLimit: Int,
        existing: RoundState?,
        expectedRunToken: String?,
        activeRunToken: String?,
        newRunToken: String,
        now: Long,
    ): Claim {
        val runToken = existing?.runToken ?: newRunToken
        if (plan.plan.isEmpty()) {
            // 运行日志要能解释「为什么这一轮什么也没发生」，所以仍然落一行终态。
            return Claim.Rejected(
                state = RoundState(
                    conversationId = conversationId,
                    roundId = plan.roundId,
                    runToken = runToken,
                    status = GroupRunEntity.STATUS_FAILED,
                    spentTokens = existing?.spentTokens ?: 0,
                    tokenLimit = tokenLimit,
                    committedRoleIds = existing?.committedRoleIds.orEmpty(),
                    skippedRoleIds = existing?.skippedRoleIds.orEmpty(),
                    reason = GroupRunEntity.REASON_NO_SPEAKER,
                    errorMessage = "",
                    startedAt = existing?.startedAt ?: now,
                    updatedAt = now,
                    endedAt = now,
                ),
                code = RejectCode.NO_SPEAKER,
            )
        }
        if (existing == null) {
            return Claim.Acquired(
                state = RoundState(
                    conversationId = conversationId,
                    roundId = plan.roundId,
                    runToken = newRunToken,
                    status = GroupRunEntity.STATUS_RUNNING,
                    spentTokens = 0,
                    tokenLimit = tokenLimit,
                    committedRoleIds = emptyList(),
                    skippedRoleIds = emptyList(),
                    reason = "",
                    errorMessage = "",
                    startedAt = now,
                    updatedAt = now,
                    endedAt = null,
                ),
                freshRow = true,
            )
        }
        if (existing.status == GroupRunEntity.STATUS_RUNNING) {
            // 同一个活跃 run 继续走下一位发言者。
            if (expectedRunToken != null && existing.runToken == expectedRunToken) {
                return Claim.Continued(existing.progressed(tokenLimit, now))
            }
            // 本进程已经在跑这一群：重复触发直接拒掉，绝不并行跑第二轮。
            if (activeRunToken != null) {
                return Claim.Rejected(existing, RejectCode.ALREADY_RUNNING)
            }
            // 没有活跃实例的 RUNNING 行 = 上一个进程被强杀留下的僵尸，回收后从失败/中断处续跑。
            return Claim.Acquired(existing.reclaimed(tokenLimit, now), freshRow = false)
        }
        if (existing.status == GroupRunEntity.STATUS_COMPLETED) {
            return Claim.Rejected(existing, RejectCode.ALREADY_COMPLETED)
        }
        // FAILED / CANCELLED / TIMEOUT / BUDGET_STOPPED：沿用同一 round_id 续跑，
        // committedRoleIds 里已完成的角色被 pendingSpeakers 跳过（预算调高后可继续）。
        return Claim.Acquired(existing.reclaimed(tokenLimit, now), freshRow = false)
    }

    /** 本轮下一个该发言的角色；null 表示全员已发言。已提交角色一律跳过。 */
    fun nextStep(plan: RoundPlan, committedRoleIds: Collection<String>): SpeakerStep? =
        GroupChat.pendingSpeakers(plan.plan, committedRoleIds.toSet()).firstOrNull()

    // ------------------------------------------------------------------
    // 2. 推进 / 失败 / 取消 / 超时
    // ------------------------------------------------------------------

    sealed interface Advance {
        /** 还有角色要发言，[next] 一定非空。 */
        data class More(val state: RoundState, val next: SpeakerStep) : Advance

        /** 达到本轮预算：剩余角色全部停跑，四项数据已齐。 */
        data class BudgetStopped(val state: RoundState, val stop: RoundBudget.Stop) : Advance

        /** 全员发言完成，等收尾（vote 还要计票）。 */
        data class Finished(val state: RoundState) : Advance

        /**
         * **不推进**：这次提交被拒，一个字节都不许改。
         *
         * 两种来源，同一个后果：
         * 1. [advance] 收到的 [state] 已是终态（轮次被取消 / 超时 / 判死）；
         * 2. 执行层判定产出不属于本轮（残留 job，见 [checkCommitAdmission]），
         *    此时 [state] 为 null —— `group_runs` 里连这一行都没有。
         *
         * [state] 是被拒时的**原状**（终态那条路径下逐字等于入参），调用方据此知道
         * 「库里现在是什么」，而不会误以为自己推进过。
         */
        data class Halted(val state: RoundState?, val detail: String) : Advance
    }

    /**
     * 一次发言成功提交后的推进判定。
     *
     * 预算口径是契约点名的 **prompt + completion 累计**（[usage] = `prompt to completion`），
     * 不是 `TokenUsage.totalTokens`，也不是只算最后一条消息：每完成一个角色就把该角色
     * 真实产生的用量加进本轮累计，再交给 [GroupChat.budgetDecision] 判停。
     *
     * **终态 state 一律不推进。** 轮次被取消 / 超时 / 用户发新消息被判死（执行层
     * `abandonDanglingGroupRuns`）之后，仍在飞的旧生成任务跑完时照样会进这里；
     * 它手里的 [state] 已是终态，
     * 若继续推进就会把已作废的角色塞进 `committedRoleIds`、把已用额度继续累加，
     * 甚至一路走到 `More` → 续跑下一位 / `Finished` → `completeGroupRound` 给这一轮收尾。
     * 所以这里先判终态并返回 [Advance.Halted]：这是显式防护，不是靠「`plan.roundId`
     * 会变成新轮，`findByRound` 查不到 → return null」那种巧合式的安全。
     *
     * 注意这**不**影响取消 / 失败后的续跑：[claimRound] 会先把那一行
     * `reclaimed` 回 RUNNING（同一 `round_id`、同一 `run_token`）才允许发起模型调用，
     * 所以续跑那一步提交时 [state.status] 仍是 `RUNNING`，照常走下面的三分支。
     */
    fun advance(
        state: RoundState,
        finishedRoleId: String,
        usage: Pair<Int, Int>,
        plan: RoundPlan,
        tokenLimit: Int,
        now: Long,
    ): Advance {
        if (GroupRunEntity.isTerminal(state.status)) {
            return Advance.Halted(
                state = state,
                detail = "轮次已终止（status=${state.status}），不再推进",
            )
        }
        val committed = (state.committedRoleIds + finishedRoleId).distinct()
        val spent = state.spentTokens + GroupChat.roundTotalTokens(listOf(usage))
        val pending = GroupChat.pendingSpeakers(plan.plan, committed.toSet())
        val progressed = state.copy(
            spentTokens = spent,
            tokenLimit = tokenLimit,
            committedRoleIds = committed,
            updatedAt = now,
        )
        val decision = GroupChat.budgetDecision(
            spent = spent,
            limit = tokenLimit,
            remainingRoleIds = pending.map { it.role.id },
        )
        return when {
            // budgetDecision 只在「还有剩余角色」时才会 Stop，所以四项数据此时必然齐备。
            decision is RoundBudget.Stop -> Advance.BudgetStopped(
                state = progressed.terminal(
                    status = GroupRunEntity.STATUS_BUDGET_STOPPED,
                    reason = decision.reason,
                    errorMessage = "",
                    now = now,
                ).copy(
                    spentTokens = decision.spent,
                    tokenLimit = decision.limit,
                    skippedRoleIds = decision.skippedRoleIds,
                ),
                stop = decision,
            )

            pending.isEmpty() -> Advance.Finished(progressed)
            else -> Advance.More(progressed, pending.first())
        }
    }

    /**
     * 产出归属判定（纯判定，不读库不写库）。
     *
     * 回答的是**归属**问题，不是生死问题：执行层拿一个 `SpeakerStep` 跑完模型之后，
     * 手上只有「现算的 `plan`」，而 `plan` 可能是**别的**那一轮——用户取消一轮、或
     * 用户发新消息把上一轮判死之后，仍在飞的旧生成任务跑完时，
     * `ChatManager.onSuccess` 里现算的 `plan` 已经是新轮（触发消息变了）。
     * 若直接拿 `plan.roundId` 去盖戳，这份上一轮本该作废的产出就被记到新轮名下，
     * 新轮的 `committed_role_ids` 里混进上一轮的账。
     *
     * 判据是 **run token**：[jobRunToken] 是本次生成在 [claimRound] 抢占时拿到的令牌，
     * 由执行层在**发起模型调用之前**捕获（不能事后从全局状态反查——判死路径已经把
     * 进程内镜像清掉了，反查到的会是新轮的令牌，恰好放行）。
     * [GroupRunDAO] 的所有 UPDATE 都按 `(conversation_id, round_id)` 定位且没有改写
     * `run_token` 的语句，所以「同一行的令牌」在一轮内不变，续跑 / 回收都沿用它。
     *
     * ⚠️ 这一层**故意不判终态**：终态由 [advance] 独占判定（问题一的显式防护就在那里，
     * 不在这里重复一遍）。分工是「`checkCommitAdmission` 认身份（这是谁的产出），
     * `advance` 判生死（这一轮还活着吗）」。同一行同一令牌但已终态时归属是**对的**
     * ——产出确实属于那一轮，盖戳不写错；由 [advance] 拦住不推进。
     */
    fun checkCommitAdmission(
        jobRunToken: String?,
        row: RoundState?,
        stampRoundId: String,
    ): CommitAdmission = when {
        // 血缘不明 = 拒收。宁可漏一次提交，也不让一份来路不明的产出盖到某一轮名下。
        jobRunToken.isNullOrEmpty() -> CommitAdmission.Denied(
            row = row,
            detail = "本次生成没有可核对的 run token（血缘不明），拒收",
        )

        row == null -> CommitAdmission.Denied(
            row = null,
            detail = "group_runs 里没有 round=$stampRoundId 这一行，产出无处可记",
        )

        row.roundId != stampRoundId -> CommitAdmission.Denied(
            row = row,
            detail = "产出行属于 ${row.roundId}，与盖戳目标 $stampRoundId 不符",
        )

        row.runToken != jobRunToken -> CommitAdmission.Denied(
            row = row,
            detail = "本次生成的 run token=$jobRunToken 不是该轮令牌 ${row.runToken}，" +
                "这是判死之后残留的旧 job",
        )

        else -> CommitAdmission.Admitted(row)
    }

    /**
     * 失败收尾（单角色失败 / 超时）的归属判定（纯判定，不读库不写库）。
     *
     * 与 [checkCommitAdmission] **共用同一套身份判据** —— 这里直接调它，不另立一套：
     * 血缘不明、库里没这一行、`round_id` 不符、`run token` 不符，一律拒收。
     * [jobRunToken] 同样必须是**发起模型调用之前**从 [claimRound] 抢占结果捕获的那个令牌；
     * `ChatManager.failGroupTurn` 里的 `plan` 与提交路一样是**现算**的
     * （[roundPlanFor] 取最后一条 USER 消息，判死之后它属于新轮），所以两条路
     * 必须用同一把尺子量「这份收尾属于哪一轮」。
     *
     * 在身份之外只补一条：**终态**。这一条是失败路独有的，理由是提交路有 [advance] 兜底而
     * 失败路没有 ——
     * - 提交路：[checkCommitAdmission] 刻意**只认身份、不判生死**（同一行同一令牌但已终态时，
     *   产出确实属于那一轮，盖戳不写错），生死由 [advance] 独占判定，返回 [Advance.Halted]。
     * - 失败路：**没有** [advance] 这一层。[fail] / [timeoutRound] 是无条件写终态的纯函数，
     *   执行层拿到 state 就直接写错误节点 + 落库 + 回收平票脚手架 + 清进程内镜像。
     *   所以「轮次已终态」在这条路上无人拦截：残留的旧 job 一旦超时或报错，就会把
     *   用户取消 / 已被判死的那一轮改写成 TIMEOUT / FAILED，并顺手抹掉同会话里
     *   （可能属于新轮的）进程内镜像。这一条补的就是那个缺口。
     *
     * 返回类型复用 [CommitAdmission]：两条路回答的是同一个问题（「这次收尾能不能记进那一轮」），
     * [CommitAdmission.Admitted.row] 就是本次该写终态的那一行；被拒时
     * [CommitAdmission.Denied.row] 只用于写运行日志，**不得**拿它落库。
     */
    fun checkFailureAdmission(
        jobRunToken: String?,
        row: RoundState?,
        targetRoundId: String,
    ): CommitAdmission {
        val identity = checkCommitAdmission(jobRunToken, row, targetRoundId)
        if (identity is CommitAdmission.Denied) return identity
        val target = (identity as CommitAdmission.Admitted).row
        return if (GroupRunEntity.isTerminal(target.status)) {
            CommitAdmission.Denied(
                row = target,
                detail = "轮次已终止（status=${target.status}），残留的失败收尾不再改写它的终态",
            )
        } else {
            identity
        }
    }

    /**
     * 单角色失败：本轮就此停止（不伪造回复）。
     *
     * 已完成角色留在 `committedRoleIds` 里（输出保留），失败角色**不**进 committed，
     * 因此再次触发同一 `round_id` 时 [GroupChat.pendingSpeakers] 会从失败角色继续。
     * [remainingRoleIds] 是失败角色之后本轮未运行的名单，写进运行日志。
     */
    fun fail(
        state: RoundState,
        failedRoleId: String,
        remainingRoleIds: List<String>,
        errorMessage: String,
        now: Long,
    ): RoundState = state.terminal(
        status = GroupRunEntity.STATUS_FAILED,
        reason = GroupRunEntity.REASON_ROLE_FAILED,
        errorMessage = errorMessage,
        now = now,
    ).copy(
        skippedRoleIds = remainingRoleIds,
        updatedAt = now,
    )

    /** 用户取消：只写运行日志，不写未生成的消息。 */
    fun cancelRound(state: RoundState, now: Long): RoundState = state.terminal(
        status = GroupRunEntity.STATUS_CANCELLED,
        reason = GroupRunEntity.REASON_CANCELLED,
        errorMessage = "",
        now = now,
    )

    /** 超时：与取消同语义，只写运行日志。 */
    fun timeoutRound(state: RoundState, errorMessage: String, now: Long): RoundState = state.terminal(
        status = GroupRunEntity.STATUS_TIMEOUT,
        reason = GroupRunEntity.REASON_TIMEOUT,
        errorMessage = errorMessage,
        now = now,
    )

    /** 全员发言完成且无需计票时的正常收尾。 */
    fun completeRound(state: RoundState, now: Long): RoundState = state.terminal(
        status = GroupRunEntity.STATUS_COMPLETED,
        reason = "",
        errorMessage = "",
        now = now,
    )

    /** 失败节点：只标记失败，不含任何模型生成的正文（契约「不伪造回复」）。 */
    fun errorNode(state: RoundState, config: GroupConfig, failedRoleId: String, detail: String): UIMessage =
        UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text(
                    buildString {
                        append('[')
                        append(displayNameOf(config, failedRoleId))
                        append("] 本轮生成失败：")
                        append(detail)
                    },
                ),
            ),
            roleId = failedRoleId,
            roundId = state.roundId,
            turnKind = GroupChat.TURN_ERROR,
        )

    // ------------------------------------------------------------------
    // 3. 投票与平票裁决
    // ------------------------------------------------------------------

    /** 模式要求的议长：显式 `chair_role_id` 优先，其次 `chair = true` 的成员。 */
    fun chairRoleIdOf(config: GroupConfig): String? {
        val ids = config.roles.map { it.id }
        return config.chairRoleId?.takeIf { it in ids }
            ?: config.roles.firstOrNull { it.chair }?.id
    }

    /**
     * 候选集外的票一律丢弃；只收本轮、结构化、角色身份合法的票。
     *
     * `turnKind != TURN_ERROR` 与 [ChatManager.roundOutputPresent] 同一口径（负向判据，
     * `turnKind` 可空时 null-safe：`null` 仍算产出）：失败节点不是发言。正文里拼的是
     * `errorDetailOf` 的 `detail`，provider 会把上游原始响应体塞进异常消息，那里面完全可能
     * 有一行能过 `parseBallot` 前缀的 `VOTE:`；而失败角色**不进** `committedRoleIds`
     * （见 [fail]），重试同一 `round_id` 时陈旧失败节点仍留在轮次消息里，会顶替它那一轮的一票。
     */
    fun collectBallots(
        config: GroupConfig,
        candidates: List<String>,
        roundMessages: List<UIMessage>,
    ): List<VoteBallot> {
        if (candidates.isEmpty()) return emptyList()
        val roleIds = config.roles.map { it.id }.toSet()
        return roundMessages
            .filter {
                it.role == MessageRole.ASSISTANT &&
                    it.roleId in roleIds &&
                    it.turnKind != GroupChat.TURN_ERROR
            }
            .mapNotNull { message ->
                message.roleId?.let { GroupChat.parseBallot(message.toText(), it, candidates) }
            }
    }

    sealed interface VoteResolution {
        /** 多数决成功：写入 `turn_kind = vote_summary` 的摘要。 */
        data class Decided(val state: RoundState, val summary: UIMessage) : VoteResolution

        /** 平票未决（`TIE_FAIL` 默认策略 / 交议长但议长没给出有效裁决）：不写摘要。 */
        data class Undecided(val state: RoundState, val detail: String) : VoteResolution

        /** `TIE_CHAIR`：平票，交给议长裁决。本轮保持 RUNNING，议长裁决完再收尾。 */
        data class NeedsChairTieBreak(
            val chairRoleId: String,
            val tiedCandidates: List<String>,
            val tally: Map<String, Int>,
            val ballots: List<VoteBallot>,
            val instruction: UIMessage,
        ) : VoteResolution
    }

    /**
     * 计票与收尾。
     *
     * 候选集唯一来源是 [GroupChat.newRound] 算出的 [RoundPlan.candidates]（执行层透传），
     * 不从自由文本二次猜测；候选集外的票由 [collectBallots] 里的 [GroupChat.parseBallot] 判为无票。
     *
     * [chairAlreadyDecided] 为 true 表示议长已经就本轮平票表过态：这时**只数议长自己那一票**
     * （限定在平票候选内、`TIE_FAIL` 口径），因此裁决必然收敛，不会二次平票死循环。
     */
    fun resolveVote(
        state: RoundState,
        config: GroupConfig,
        plan: RoundPlan,
        roundMessages: List<UIMessage>,
        chairAlreadyDecided: Boolean,
        now: Long,
    ): VoteResolution {
        val candidates = plan.candidates
        val ballots = collectBallots(config, candidates, roundMessages)
        val outcome = GroupChat.tally(ballots, candidates, config.tiePolicy)
        if (outcome is VoteOutcome.Decided) {
            return VoteResolution.Decided(
                state = completeRound(state, now),
                summary = voteSummaryMessage(state, config, outcome),
            )
        }
        if (outcome is VoteOutcome.ChairDecides) {
            val chair = chairRoleIdOf(config)
            val tied = outcome.candidates
            return when {
                // 平票策略是 `chair` 但群里没有议长：没人能裁决，按失败处理而不是静默挂起。
                chair == null -> undecided(state, "平票但未指定议长，无法裁决", now)

                // 议长还没裁决：保持 RUNNING，跑一轮议长（`chairRound = true`，议长能看到全部票）。
                !chairAlreadyDecided -> VoteResolution.NeedsChairTieBreak(
                    chairRoleId = chair,
                    tiedCandidates = tied,
                    tally = outcome.tally,
                    ballots = outcome.ballots,
                    instruction = tieBreakInstruction(state, config, chair, tied, outcome.tally),
                )

                // 议长裁决过：只数议长自己那一票（限定平票候选内），保证收敛。
                else -> {
                    val chairBallots = collectBallots(config, tied, roundMessages)
                        .filter { it.roleId == chair }
                    when (val final = GroupChat.tally(chairBallots, tied, GroupChat.TIE_FAIL)) {
                        is VoteOutcome.Decided -> VoteResolution.Decided(
                            state = completeRound(state, now),
                            summary = voteSummaryMessage(state, config, final),
                        )

                        else -> undecided(state, "议长裁决未能给出平票候选内的有效票", now)
                    }
                }
            }
        }
        val detail = when (outcome) {
            is VoteOutcome.Invalid -> outcome.reason
            is VoteOutcome.Tie -> "平票：" + outcome.candidates.joinToString()
            // Decided 已在上面返回；VoteOutcome 是 sealed，这里只是让编译器知道穷尽了。
            is VoteOutcome.Decided -> ""
            is VoteOutcome.ChairDecides -> ""
        }
        return undecided(state, detail, now)
    }

    private fun undecided(state: RoundState, detail: String, now: Long): VoteResolution.Undecided =
        VoteResolution.Undecided(
            state = state.terminal(
                status = GroupRunEntity.STATUS_FAILED,
                reason = REASON_VOTE_NO_DECISION,
                errorMessage = detail,
                now = now,
            ),
            detail = detail,
        )

    /**
     * 多数决摘要。`role_id` 用 [GroupChat.SUMMARY_ID]（各视角都看得见的合成节点），
     * 正文用**候选显示名**（`GroupRole.name`）而不是裸 id，票数行同时带 id 便于追溯。
     */
    fun voteSummaryMessage(
        state: RoundState,
        config: GroupConfig,
        outcome: VoteOutcome.Decided,
    ): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Text(
                buildString {
                    append("本轮投票结果：")
                    append(candidateLabel(config, outcome.winner, withId = true))
                    append('\n')
                    append("票数：")
                    append(
                        outcome.tally.entries
                            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                            .joinToString(" / ") { entry ->
                                "${candidateLabel(config, entry.key, withId = true)} ${entry.value}"
                            },
                    )
                },
            ),
        ),
        roleId = GroupChat.SUMMARY_ID,
        roundId = state.roundId,
        turnKind = GroupChat.TURN_VOTE_SUMMARY,
    )

    /**
     * `TIE_CHAIR` 的裁决指令。
     *
     * 用 `SYSTEM` + `isSynthetic` 而不是伪造一条 `USER` 消息是有原因的：
     * [GroupChat.visibleMessages] 的 `roundStart` 取「最后一条 USER 消息」的位置，
     * 插一条 USER 会把 `chairRound` 的可见范围截断到它之后，议长反而看不到其他人的票。
     * 裁决完由 [withoutTieBreakInstruction] 删掉，不留在对话里。
     */
    fun tieBreakInstruction(
        state: RoundState,
        config: GroupConfig,
        chairRoleId: String,
        tiedCandidates: List<String>,
        tally: Map<String, Int>,
    ): UIMessage = UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(
            UIMessagePart.Text(
                buildString {
                    append("你是本群议长（")
                    append(displayNameOf(config, chairRoleId))
                    append("），本轮投票出现平票，由你裁决。\n")
                    append("平票候选：")
                    append(
                        tiedCandidates.joinToString { candidateLabel(config, it, withId = true) },
                    )
                    append('\n')
                    append("当前票数：")
                    append(
                        tally.entries.sortedBy { it.key }.joinToString(" / ") { entry ->
                            "${candidateLabel(config, entry.key, withId = true)} ${entry.value}"
                        },
                    )
                    append('\n')
                    append("请只输出一行 `")
                    append(GroupChat.BALLOT_PREFIX)
                    append(" <候选id>` 作为最终裁决，候选 id 必须取自上面的平票名单。")
                },
            ),
        ),
        roleId = chairRoleId,
        roundId = state.roundId,
        turnKind = TURN_TIE_BREAK,
        isSynthetic = true,
    )

    /** 议长是否已经就本轮平票表过态（其 `turn_kind = chair` 的助手回复）。 */
    fun chairAlreadyDecided(roundMessages: List<UIMessage>, chairRoleId: String): Boolean =
        roundMessages.any {
            it.role == MessageRole.ASSISTANT &&
                it.roleId == chairRoleId &&
                it.turnKind == TURN_TIE_BREAK
        }

    /** 回收裁决指令脚手架：它是给议长看的提示，不属于对话内容。 */
    fun withoutTieBreakInstruction(nodes: List<MessageNode>): List<MessageNode> = nodes
        .map { node ->
            node.copy(
                messages = node.messages.filterNot { it.role == MessageRole.SYSTEM && it.turnKind == TURN_TIE_BREAK },
            )
        }
        .filter { it.messages.isNotEmpty() }

    // ------------------------------------------------------------------
    // 4. 同源上下文：模型、工具、检索、记忆注入共用一份 viewer 过滤结果
    // ------------------------------------------------------------------

    /**
     * 本轮计划：触发消息取「最后一条 USER 消息」，因此整轮期间稳定，重试必然落回同一 `round_id`。
     */
    fun roundPlanFor(config: GroupConfig, messages: List<UIMessage>): RoundPlan? {
        val trigger = messages.lastOrNull { it.role == MessageRole.USER } ?: return null
        return GroupChat.newRound(
            triggerMessageId = trigger.id.toString(),
            config = config,
            mentionRoleIds = trigger.mentionRoleIds,
            userText = trigger.toText(),
        )
    }

    /** 本轮消息 = 触发消息之后的部分。收票、判议长裁决状态都只看这一段。 */
    fun roundMessages(messages: List<UIMessage>): List<UIMessage> {
        val lastUser = messages.indexOfLast { it.role == MessageRole.USER }
        return if (lastUser < 0) messages else messages.drop(lastUser + 1)
    }

    /**
     * **发给模型的消息集合的唯一入口。**
     *
     * 契约要求过滤发生在 prompt 组装层、且「工具调用、检索与记忆注入均使用同一 viewer
     * 过滤结果」。执行层把本函数的结果直接当 `messages` 传给生成管线，下游的
     * `tool.systemPrompt(model, messages)`、`limitContext`、记忆检索 query 就自动拿到同一份，
     * 不再依赖 inputTransformers 兜底（transformer 保留作双保险，重复过滤幂等）。
     */
    fun viewerMessages(
        config: GroupConfig,
        allMessages: List<UIMessage>,
        step: SpeakerStep,
    ): List<UIMessage> = GroupChat.buildContext(
        viewerRoleId = step.role.id,
        messages = allMessages,
        config = config,
        predecessorId = step.predecessorId,
        chairRound = step.chairRound,
    )

    /** 记忆检索 query 也只从 viewer 可见的消息里取，避免检索词本身就带越权内容。 */
    fun memoryQuery(viewerMessages: List<UIMessage>): String = viewerMessages
        .takeLast(6)
        .filter { it.role == MessageRole.USER }
        .joinToString("\n") { it.toText() }
        .takeLast(4_000)

    /**
     * 检索结果再过一遍 viewer 口径（契约「检索结果再次经过 viewer 过滤」）。
     *
     * 判定复用 [GroupChat.filterMemoryForViewer]：能定位来源的记忆，其来源消息必须对
     * 当前角色可见；定位不到来源的保留（可能是用户显式写入）。群记忆额外按
     * `roleId` 收紧——别的角色写的记忆不进本角色提示词。
     */
    fun memoriesForViewer(
        viewerRoleId: String,
        viewerMessageIds: Set<String>,
        memories: List<AssistantMemory>,
    ): List<AssistantMemory> {
        if (memories.isEmpty()) return memories
        val byKey = memories.associateBy { it.id.toString() }
        val visibleKeys = GroupChat.filterMemoryForViewer(
            viewerId = viewerRoleId,
            viewerMessageIds = viewerMessageIds,
            sourceMessageIdOf = { byKey[it]?.sourceMessageId },
            candidateMessageIds = byKey.keys.toList(),
        ).toSet()
        return memories.filter { it.id.toString() in visibleKeys || it.roleId == viewerRoleId }
    }

    /** 角色显示名，缺省回落到 id。 */
    fun displayNameOf(config: GroupConfig, roleId: String): String =
        config.roles.firstOrNull { it.id == roleId }?.name?.takeIf { it.isNotBlank() } ?: roleId

    private fun candidateLabel(config: GroupConfig, candidateId: String, withId: Boolean): String {
        val name = config.roles.firstOrNull { it.id == candidateId }?.name?.takeIf { it.isNotBlank() }
        return when {
            name == null || !withId -> candidateId
            name == candidateId -> name
            else -> "$name（$candidateId）"
        }
    }

    /** 取一条助手消息的 `(prompt, completion)` 用量；不是助手消息或没有用量时返回 null。 */
    fun usageOf(message: UIMessage?): Pair<Int, Int>? {
        val usage = message?.usage ?: return null
        if (message.role != MessageRole.ASSISTANT) return null
        return usage.promptTokens to usage.completionTokens
    }

    /** `turn_kind`：议长汇总用 `chair`，其余发言用 `speaker`。 */
    fun turnKindOf(step: SpeakerStep): String =
        if (step.chairRound) GroupChat.TURN_CHAIR else GroupChat.TURN_SPEAKER
}