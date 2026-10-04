package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import kotlin.uuid.Uuid

/**
 * 群成员 → 角色卡元数据（[RoleCardMeta]）的**唯一**一份实现。
 *
 * 两条导出路径都要它：群聊页的二维码 / 文本分享载荷（`GroupChat.encodeQr`）与酒馆群聊导出
 * （`TavernChatCodec.exportGroupJsonl` 的顶层 `khatkit_group.cards`）。C1 之前这两条路径
 * 各自抄了一份同样十来行的构造（`GroupChatPage.roleCards` 与 `GroupExportCard.groupExportRoleCards`），
 * 口径当时一致但没有任何东西保证它们继续一致——改一处忘另一处，两种导出就会给出不同的
 * persona，而用户没法从结果里看出是哪一份过期了。现在只有这一份。
 *
 * ## 逐字段口径（写在这里，两条路径共用）
 *
 * - `role_id` / `assistant_id` / `card_id`：直接来自群配置的 [GroupRole]，不加工。
 *   `card_id` 只是**原样透传**的 Tavern 引用：全仓没有任何地方把 `cardId` 解析成角色卡实体，
 *   真正能拿到的「真实角色卡」只有 `assistantId` 指向的助手。
 * - `name`：优先 `role.name`，为空时退回它绑定的助手的 `name`，再为空就是空串。
 * - `persona`：取 `role.assistantId` 指向的助手的 `systemPrompt`。解析方式
 *   （`Uuid.parse(role.assistantId)` → `settings.getAssistantById`）与 `ChatManager` 发言时
 *   挑角色助手完全一致（见 `ChatManager.takeGroupTurn` 那段），所以导出的 persona 就是该角色
 *   真正会用的那段系统提示词。
 * - `avatar_ref`：恒为 null。助手头像存的是 `Avatar`（本地文件 URI 或远程 URL），不是可移植
 *   引用，写进载荷只会让对方拿到一个指不到东西的路径。
 *
 * ## 降级行为（不得改成「取不到就不导出」）
 *
 * `assistantId` 不是合法 Uuid、或者助手已被删除时，**照样返回一张卡**：`name` 退成
 * `role.name`、`persona` 是空串。缺一张角色卡不该把整个二维码 / 导出入口禁掉——那会让
 * 「群成员和轮次信息」也一起导不出去，损失远大于少一张 persona。
 *
 * 角色数量**一对一**：`roles` 有几条就返回几条，空 `roles` 返回空列表，不做去重、不排序、
 * 不丢弃（顺序即群配置顺序，两种载荷的消费者都按下标对应角色）。
 *
 * 纯函数：不碰 Compose / 数据库，可在 JVM 单测里直接跑（`GroupTavernExportTest` 已覆盖
 * 上面每一条降级分支）。
 */
internal fun groupExportRoleCards(roles: List<GroupRole>, settings: Settings): List<RoleCardMeta> =
    roles.map { role ->
        val assistant = runCatching { Uuid.parse(role.assistantId) }.getOrNull()
            ?.let { settings.getAssistantById(it) }
        RoleCardMeta(
            roleId = role.id,
            name = role.name.ifBlank { assistant?.name.orEmpty() },
            assistantId = role.assistantId,
            cardId = role.cardId,
            persona = assistant?.systemPrompt.orEmpty(),
        )
    }
