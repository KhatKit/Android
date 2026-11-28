package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.feature.chat.data.GroupSpeakerIdentity



/**
 * 群聊消息 → 说话者身份。纯逻辑：不碰 Compose / Hilt / 数据库，因此可用 JVM 单测钉死。
 *
 * ## 为什么单独一个类
 *
 * C1 之前气泡只按 `message.role == USER` 二分，群聊里 3 个角色 + 议长裁决 + 投票小结 + 错误节点
 * 复用同一个气泡后长得一模一样：同左对齐、同气泡色、无名字无头像。这个类把「这条消息是谁说的、
 * 它是什么性质」补进气泡层，供 [ChatMessage] 与群聊页共用。
 *
 * ## 口径（刻意收窄，理由写在这里以免以后被"顺手优化"掉）
 *
 * - **说话者只看 `roleId`**，绝不看 `mentionRoleIds`：后者是这条消息 @ 了谁，一条消息可以 @ 多个角色，
 *   拿它当说话者会把「谁说的」显示成被点名的人。
 * - **错误只看 `turnKind`**：`ChatManager.voteFailureNode` 把失败摘要也写成
 *   `role_id = SUMMARY_ID`，`GroupTurnCoordinator` 的发言失败节点则写成出错角色的 id。
 *   两种都得算错误，所以判据是 `turn_kind` 而不是 roleId。
 * - **议长只看 `turnKind == chair`**：那是议长**裁决**发言（平票裁决）。
 *   群里被配成 `chair = true` 的角色正常发言时 `turn_kind` 是 `speaker`，那是普通发言，不加徽章。
 * - **角色名只有一份**：真实角色一律走 [GroupTurnCoordinator.displayNameOf]，不再另写一份
 *   `roles.firstOrNull { it.id == roleId }?.name`。
 *
 * ## 降级行为
 *
 * [resolve] 对任何"数据不一致"都返回可用的结果，绝不抛异常、绝不给空名：
 * - `config == null` 但 `roleId != null`（旧数据、群配置被清）：显示名退回裸 `roleId`，
 *   `assistantId` 为 null，`degraded = true`。调用方拿它渲染就是「名字等于 id」的普通发言，
 *   不会崩、也不会凭空多出一个角色。
 * - `config` 在但 `roleId` 不在 `roles` 里（角色被删 / id 写错）：同上走 displayNameOf 的 id 回落，
 *   `degraded = true`。
 */
object GroupSpeakerResolver {

    /**
     * 群小结（`role_id == __summary__`）的显示名。
     *
     * 合成节点不在 `config.roles` 里，[GroupTurnCoordinator.displayNameOf] 对它只会回落到裸
     * `"__summary__"`——那是契约 id，不是人话，直接显示出来等于把内部标识漏给用户。
     * 所以这里给它一个人类可读名，并且**只此一份**：群聊页与酒馆导出都从这里取
     * （`TavernChatCodec` 与 `GroupChatPage` 都改成调用本常量，不再各写一个 "多数决"）。
     */
    const val SUMMARY_DISPLAY_NAME = "多数决"

    /** 议长平票裁决发言的徽章。 */
    const val BADGE_CHAIR = "议长"

    /** 投票小结（`turn_kind = vote_summary`）的徽章。 */
    const val BADGE_SUMMARY = "投票小结"

    /** 错误节点（`turn_kind = error`）的徽章。 */
    const val BADGE_ERROR = "错误"

    /**
     * 解一条消息的说话者身份。
     *
     * @param message 气泡要渲染的那条消息（取 `node.currentMessage`）。
     * @param config 群聊的群配置；单聊传 null。
     */
    fun resolve(message: UIMessage, config: GroupConfig?): GroupSpeakerIdentity {
        val roleId = message.roleId
        if (roleId == null) return GroupSpeakerIdentity.NotGroup

        val turnKind = message.turnKind
        val isError = turnKind == GroupChat.TURN_ERROR
        val isChair = turnKind == GroupChat.TURN_CHAIR
        // 合成节点没有角色，也没有发言角色可挂；但它也可能是一条失败摘要（见类注释）。
        val isSummary = roleId == GroupChat.SUMMARY_ID
        val role = config?.roles?.firstOrNull { it.id == roleId }
        val displayName = when {
            isSummary -> SUMMARY_DISPLAY_NAME
            config != null -> GroupTurnCoordinator.displayNameOf(config, roleId)
            // 没有群配置可查：退到裸 id，见 KDoc 的「降级行为」。
            else -> roleId
        }
        return GroupSpeakerIdentity.Group(
            roleId = roleId,
            displayName = displayName,
            assistantId = role?.assistantId?.takeIf { it.isNotBlank() },
            turnKind = turnKind,
            isSummary = isSummary,
            isError = isError,
            isChair = isChair,
            badge = badgeOf(isError = isError, isSummary = isSummary, isChair = isChair),
            // 有配置也查不到这个角色，和没配置一样是「查不到」。
            degraded = role == null,
        )
    }

    /** 徽章文本。错误优先于小结优先于议长，一条消息最多一枚。 */
    private fun badgeOf(isError: Boolean, isSummary: Boolean, isChair: Boolean): String? = when {
        isError -> BADGE_ERROR
        isSummary -> BADGE_SUMMARY
        isChair -> BADGE_CHAIR
        else -> null
    }
}
