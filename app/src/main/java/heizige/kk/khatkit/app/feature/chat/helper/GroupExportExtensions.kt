package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.feature.chat.data.GROUP_EXPORT_EXTENSION

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.feature.chat.data.GROUP_EXPORT_FALLBACK_NAME
import heizige.kk.khatkit.app.feature.chat.data.GROUP_EXPORT_FALLBACK_USER
import heizige.kk.khatkit.app.feature.chat.data.GroupExportSelection



/**
 * 把导出面板给的 `List<UIMessage>` 反查成 [MessageNode] 列表——[TavernChatCodec.exportGroupJsonl]
 * 要的是节点（要拿到 `swipes` / `swipe_id` 分支），面板手上只有消息。
 *
 * 反查口径只有一份：[Conversation.getMessageNodeByMessage]（按 `UIMessage` 的数据类相等
 * 在 `messageNodes` 里找命中的节点）。
 *
 * 三条规则：
 * 1. **反查不到就跳过并计数**，绝不静默丢，也不整批失败——选 20 条里 18 条在会话里仍然要导。
 * 2. **按节点 id 去重**。同一个节点的多个 swipe 都在选中集合里时，
 *    `getMessageNodeByMessage` 会把同一个节点返回多次；不去重的话 JSONL 里会出现两条
 *    完全相同的行，往返导入后变成两条重复消息。面板另外两条导出（Markdown / PNG）是直接遍历
 *    `selectedMessages` 的，所以那边不会出现这个形状；群聊导出按节点写行，必须自己去重。
 * 3. 保持 `selectedMessages` 的原始顺序，不重排、不排序——用户选的就是他要的顺序。
 */
internal fun resolveGroupExportNodes(
    conversation: Conversation,
    selectedMessages: List<UIMessage>,
): GroupExportSelection {
    val nodes = ArrayList<MessageNode>(selectedMessages.size)
    val seen = HashSet<Uuid>(selectedMessages.size)
    var skipped = 0
    selectedMessages.forEach { message ->
        val node = conversation.getMessageNodeByMessage(message)
        if (node == null || !seen.add(node.id)) {
            skipped++
        } else {
            nodes.add(node)
        }
    }
    return GroupExportSelection(nodes = nodes, skipped = skipped)
}

/**
 * 导出文件名。与 `ConversationExport.kt` 里 Markdown / PNG 两条导出同一口径
 * （`chat-export-` 前缀 + 同一份时间戳格式），所以同一次导出里的三种文件在下载目录里排在一起。
 *
 * 刻意**不**把群名拼进文件名：会话标题是用户自由输入，可能带 `/`、`:`、换行，落进文件名
 * 要么建文件失败、要么在系统分享面板里显示成一串路径分隔符。群名本身已经写进文件头部
 * （`character_name` 与 `khatkit_group.name`，见 [TavernChatCodec.exportGroup]），
 * 不靠文件名承载。
 */
internal fun groupExportFileName(now: LocalDateTime): String =
    "chat-export-${now.format(ExportTimeFormatter)}.$GROUP_EXPORT_EXTENSION"

/** 群名口径：会话标题，空标题回落到 [GROUP_EXPORT_FALLBACK_NAME]。 */
internal fun groupExportName(conversation: Conversation): String =
    conversation.title.ifBlank { GROUP_EXPORT_FALLBACK_NAME }

/**
 * 用户名口径：[Settings.displaySetting] 的 `userNickname`，空昵称回落到
 * [GROUP_EXPORT_FALLBACK_USER]。
 *
 * 字段名与兜底值都照抄酒馆侧的既有写法：`TavernMacroTransformer.kt:16` 与
 * `PromptInjectionTransformer.kt:34` 都是
 * `ctx.settings.displaySetting.userNickname.ifBlank { "User" }`。所以导出的
 * `user_name` 与宏展开时用的是同一个名字，导出到酒馆再导入回来不会换人。
 */
internal fun groupExportUserName(settings: Settings): String =
    settings.displaySetting.userNickname.ifBlank { GROUP_EXPORT_FALLBACK_USER }

/**
 * 群成员摊成角色卡元数据（[RoleCardMeta]）的实现**不在本文件**：二维码 / 文本分享与酒馆导出
 * 两条路径共用同一份，见 [groupExportRoleCards]（`GroupRoleCards.kt`）。
 */

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
