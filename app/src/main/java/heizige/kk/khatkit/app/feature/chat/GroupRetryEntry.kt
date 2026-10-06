package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.GroupChat

/**
 * C1 —— 群聊「失败续跑」入口的**纯判定**。
 *
 * 契约 `docs/beyond-operit-client-changes.md:204`「重试使用同一 `round_id` 并跳过已提交
 * turn，避免重复消息」与 `:227-228`「重试沿用原 round 并跳过已提交 turn」在代码里的落点
 * 就是 [ChatManager.regenerateAtMessage]：它对**已存在的**消息触发、不 append 新 USER
 * 消息，于是 `GroupTurnCoordinator.roundPlanFor` 取的「最后一条 USER」不变 ⇒ `round_id`
 * 不变；`takeGroupTurn` 的 `pendingSpeakers(plan, committedRoleIds)` 会把已提交角色过滤掉。
 *
 * ## 为什么群聊不能直接放开「重新生成」
 *
 * `regenerateAtMessage` 对**助手消息**走 `handleMessageComplete(messageRange = 0..<nodeIndex)`
 * （`ChatManager.kt:638`）：模型上下文被截到「被点节点之前」，而产出经
 * `Conversation.updateCurrentMessages`（`Conversation.kt:74-106`）落在**同一个节点**里、
 * 成为一条新的候选分支并把 `selectIndex` 切过去。群聊里被点的角色通常**已提交**
 * （在 `committed_role_ids` 里），而 `stampGroupTurn` 给这份新产出盖的是
 * `pendingSpeakers` 里的**下一个**待发言角色，不是被点的角色。后果：
 *
 * 1. 新产出物理落在被点角色的节点里、把该节点选中的已提交发言「顶掉」；
 * 2. `group_runs.committed_role_ids` 不会被这次重新生成修订，仍声称被点角色已提交；
 * 3. `pendingSpeakers` 按 committed 过滤（`GroupChat.kt:902-903`），于是**那个输出已经
 *    消失的角色再也不会被轮到**；`roundOutputPresent`（`ChatManager.kt:2361-2373`）是
 *    `any` 判定，抓不到「个别角色产物被顶掉」；
 * 4. 若该轮已 `COMPLETED`，`claimRound` 直接 `Rejected(ALREADY_COMPLETED)`
 *    （`GroupTurnCoordinator.kt:261-263`）⇒ 点了是个静默 no-op。
 *
 * 所以群聊的「重新生成」保持整条关闭，另设一个**只指向失败节点**的续跑入口。
 *
 * ## 入口条件
 *
 * [canResume] 只认**失败角色的错误节点**，且必须是消息列表里**最后一个节点**：
 *
 * - `turnKind == GroupChat.TURN_ERROR`：`failGroupTurn` 写的错误节点（`GroupTurnCoordinator
 *   .errorNode:510-526`）。该角色**不在** `committedRoleIds` 里（`fail():469-483`），所以
 *   `pendingSpeakers` 必然包含它 ⇒ 续跑会补上它和其后未运行的角色、跳过已提交角色。
 * - 是最后一个节点：`regenerateAtMessage` 的 `nodeIndex` 就等于 `lastIndex`，
 *   `messageRange = 0..<lastIndex` 正好是「错误节点之前」的正确上下文，且新产出作为
 *   该节点的新候选分支落在**同一失败角色名下**（`role_id` / `round_id` 一致），账面对得上。
 *   一旦用户已发新 USER 消息，旧错误节点不再是最后一个 ⇒ 入口消失，避免用旧节点触发到
 *   新轮（判死之后残留的旧 job 那一类错位）。
 * - 排除 `GroupChat.SUMMARY_ID`：投票未决的失败摘要也是 `TURN_ERROR` + 最后一条，但它是
 *   合成节点（没有待发言角色），续跑只会是个 no-op，不该给入口。
 *
 * 本对象只做判定、不读库不写库，好让 JVM 单测穷举形状。
 */
object GroupRetryEntry {

    /**
     * 这条消息此刻是不是群聊「失败续跑」入口的合法目标。
     *
     * @param isGroup 当前会话是不是群聊（调用方用 `isGroupConversation` 的严格口径给出）。
     * @param isLastMessage 这条消息所在节点是不是 `messageNodes` 的最后一个节点。
     * @param message 该节点当前选中的消息。
     */
    fun canResume(
        isGroup: Boolean,
        isLastMessage: Boolean,
        message: UIMessage,
    ): Boolean = isGroup &&
        isLastMessage &&
        message.role == MessageRole.ASSISTANT &&
        message.turnKind == GroupChat.TURN_ERROR &&
        message.roleId != GroupChat.SUMMARY_ID
}
