package heizige.kk.khatkit.app.feature.chat.data

/**
 * 一条消息的「说话者身份」：这条消息是谁说的、它是什么性质。
 *
 * 气泡层（[heizige.kk.khatkit.app.core.ui.components.message.ChatMessage]）不认群聊语义，
 * 只认这个结果，因此「谁说的」这件事在这里一次算清、到处复用。
 */
sealed interface GroupSpeakerIdentity {

    /**
     * 单聊消息（`roleId == null`），包括群聊里用户自己那条没盖角色戳的消息。
     *
     * 调用方**必须**保持原有单聊渲染：不多显示名字、不多显示徽章、不查角色助手。
     */
    data object NotGroup : GroupSpeakerIdentity

    /**
     * 群聊消息。
     *
     * @property roleId 契约字段 `role_id`，原样保留（可能是 [GroupChat.SUMMARY_ID] 合成节点）。
     * @property displayName 给人看的名字。真实角色取 [GroupTurnCoordinator.displayNameOf]
     *   （唯一一份角色名口径），合成节点取 [GroupSpeakerResolver.SUMMARY_DISPLAY_NAME]。
     * @property assistantId 该角色的 `assistant_id`，用来取真实头像与助手名；取不到为 null。
     * @property turnKind 契约字段 `turn_kind`，原样保留。
     * @property isSummary 是否群小结（`role_id == __summary__` 的合成节点）。
     * @property isError 是否错误节点（只看 `turn_kind == error`，与 roleId 无关）。
     * @property isChair 是否议长裁决发言（只看 `turn_kind == chair`）。
     * @property badge 气泡上方那枚小徽章的文本；普通发言为 null。
     * @property degraded true 表示这条消息**没能**从群配置里查到角色（配置缺失、角色被删、
     *   `role_id` 指向不存在的角色），显示名与 assistantId 都退到了裸 id。见 [GroupSpeakerResolver]。
     */
    data class Group(
        val roleId: String,
        val displayName: String,
        val assistantId: String? = null,
        val turnKind: String? = null,
        val isSummary: Boolean = false,
        val isError: Boolean = false,
        val isChair: Boolean = false,
        val badge: String? = null,
        val degraded: Boolean = false,
    ) : GroupSpeakerIdentity
}
