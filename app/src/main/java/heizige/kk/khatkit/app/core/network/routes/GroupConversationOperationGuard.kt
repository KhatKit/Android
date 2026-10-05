package heizige.kk.khatkit.app.core.network.routes

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.network.ConflictException
import heizige.kk.khatkit.app.feature.chat.isGroupConversation

/**
 * 群聊会话上被门禁挡下的会话操作。
 *
 * 这五个操作都不是群聊感知的：它们绕过群轮次内核直接改会话的消息节点，而群聊轮次的账面
 * （`group_runs` 里的 `committed_role_ids` / `spent_tokens`）只由 `ChatManager.takeGroupTurn` 维护。
 * 一旦外来写入让「账面说已提交」与「会话里还在」对不上，`roundOutputPresent` 就会返回 false，
 * `takeGroupTurn` 随即 `deleteByRound` 删掉整轮运行日志、按 `freshRow=true` 重新抢占，
 * 于是整轮作废、全员重跑、`spent_tokens` 归零 —— 本轮预算被无声重置。
 *
 * UI 侧早就用 `if (!groupChat)` 挡了这五个入口（`ChatMessageActions.kt`），但 HTTP 侧没挡；
 * `WebApiModule` 的 `jwtEnabled` 还可以关，关掉后整个 `/api/` 前缀没有任何鉴权。
 */
internal enum class ConversationOperation {
    EditMessage,
    Fork,
    DeleteMessage,
    SelectNode,
    Regenerate,
}

/**
 * 群聊会话上该操作的拒绝原因；`null` = 放行。
 *
 * 判定直接用 [isGroupConversation]（`group_config` 非空**且** `type == GROUP`），与轮次内核、
 * UI 入口同一口径，**不在这里另立一套**：老数据可能残留 `group_config` 却已被改回单聊，
 * 这时必须当单聊放行，否则一次普通调用会误伤同 id 的群聊行。
 *
 * 文案要回答两件事：**为什么被拒**、**外部调用方该换哪个 API**。
 * 静默 ignore 或静默改数据都会让调用方以为成功了，所以这里一律明确拒绝。
 */
internal fun groupConversationOperationRejection(
    conversation: Conversation,
    operation: ConversationOperation,
): String? {
    if (!isGroupConversation(conversation)) return null
    // 四个操作共用同一句「为什么」：破坏的是本轮轮次账目。
    val ledgerDamage = "会让群运行日志记录的「本轮已提交角色 / 已用 token」与实际产出对不上，" +
        "续跑时整轮被判定产出缺失并作废重来（预算归零）"
    return when (operation) {
        ConversationOperation.EditMessage ->
            "群聊会话不支持编辑消息：编辑会追加一条不属于任何角色的消息，$ledgerDamage。" +
                "要追加内容请改用 POST /api/conversations/{id}/messages，让群聊按自己的轮次继续。"

        ConversationOperation.DeleteMessage ->
            "群聊会话不支持删除单条消息：删掉本轮已提交角色的产出后，$ledgerDamage。" +
                "要清空整个会话请改用 DELETE /api/conversations/{id}。"

        ConversationOperation.SelectNode ->
            "群聊会话不支持切换分支：切换 selectIndex 会换掉 currentMessages 里整段本轮产出，$ledgerDamage。" +
                "要读取另一分支的内容请改用 GET /api/conversations/{id}。"

        ConversationOperation.Regenerate ->
            "群聊会话不支持重新生成：重新生成会用 role_id 不同的一条新回复顶掉旧回复，$ledgerDamage。" +
                "要开新一轮讨论请改用 POST /api/conversations/{id}/messages。"

        // fork 与另外四个不同：它**不写源会话、不碰群运行日志**，破坏不了账目。
        // 它的问题是另一码事——`createForkConversation` 既不复制 `group_config` 也不复制 `type`，
        // 产物是一条单聊会话，而响应里只回 `conversationId`，调用方无从分辨，
        // 等于静默换了一种会话类型。仍然拒绝，但理由不谎报成「账目错位」。
        ConversationOperation.Fork ->
            "群聊会话不支持 fork：fork 不会带上 group_config，产物是一条单聊会话，" +
                "而响应里只返回 conversationId，调用方无从分辨，等于静默换了会话类型。" +
                "要留存群聊内容请改用 GET /api/conversations/{id} 读取快照自行落地。"
    }
}

/**
 * 门禁本体。群聊会话 → 抛 [ConflictException]（409）；非群聊 → 原样放行，行为逐字不变。
 *
 * 用 409 而不是 400：请求本身不畸形（400 在本仓库表示「参数没填对」），
 * 挡住它的是**资源当前状态**——这正是 `FolderRoutes` 拒绝删除「有会话在生成中的文件夹」
 * 用的同一范式。调用方原样重试永远不会成功，409 才不会诱导它进重试循环。
 *
 * ⚠️ 调用点必须排在 `chatService.initializeConversation(uuid)` **之后**：
 * `getConversationFlow` 走 `ConversationSessionManager.getOrCreate`，未加载的会话拿到的是
 * `Conversation.ofId(...)` 这个空单聊占位（`group_config = null`）。排在 initialize 之前
 * 会读到占位对象、判成非群聊、直接放行，门禁形同虚设。
 */
internal fun requireConversationOperationAllowed(
    conversation: Conversation,
    operation: ConversationOperation,
) {
    val reason = groupConversationOperationRejection(conversation, operation) ?: return
    throw ConflictException(reason)
}