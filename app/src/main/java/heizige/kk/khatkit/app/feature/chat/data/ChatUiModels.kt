package heizige.kk.khatkit.app.feature.chat.data

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult

data class ConversationSizeInfo(
    val nodeCount: Int,
    val lastAssistantInputTokens: Int,
    val exceedNodeCountThreshold: Boolean,
    val exceedInputTokenThreshold: Boolean,
    val showWarning: Boolean
)

internal val DefaultSizeInfo = ConversationSizeInfo(
    nodeCount = 0,
    lastAssistantInputTokens = 0,
    exceedNodeCountThreshold = false,
    exceedInputTokenThreshold = false,
    showWarning = false
)

/**
 * 「重命名当前聊天」这一行的可用性。
 *
 * @param title 当前标题（弹窗初始值）。
 * @param canRename 会话已落库**且**至少有一条消息。旧行为（顶栏长按标题）在空会话上给的是
 *   「请先聊天再编辑标题吧」的提示而不是把入口藏掉，这里用 [canRename] 表达同一件事。
 */
data class RolePanelRenameTarget(
    val title: String,
    val canRename: Boolean,
)

/**
 * 抽屉搜索态的一次快照（消息正文检索 + 标题降级）。
 *
 * - [keyword]：本次命中对应的关键字（UI 用它判断「这份状态是不是当前输入框的」，避免
 *   防抖窗口内把上一轮结果当成本轮结果渲染）
 * - [searching]：查询进行中（UI 显示「正在检索…」而不是闪一下空态）
 * - [hits]：FTS 命中（[MessageSearchResult] 带 nodeId / conversationId / 标题 / 片段 / 时间），
 *   最多 [DRAWER_SEARCH_HIT_LIMIT] 条（与 FTS 查询的 LIMIT 对齐）
 * - [titleFallback]：FTS 没命中时的标题兜底（索引未建 / 老数据没进索引时不至于一条都没有）。
 *   这是**降级路**，UI 用一个可见的分节标题明确标注「以下为标题匹配」，不与消息命中混淆。
 */
data class DrawerMessageSearchState(
    val keyword: String = "",
    val searching: Boolean = false,
    val hits: List<MessageSearchResult> = emptyList(),
    val titleFallback: List<Conversation> = emptyList(),
)
