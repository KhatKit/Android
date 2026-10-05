package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.SpeakerStep

/**
 * **标题 / 摘要类后台生成**在群聊会话上的 viewer 口径。
 *
 * 契约（`docs/beyond-operit-client-changes.md`）明写：「上下文过滤必须发生在 prompt 组装层……
 * 工具调用、检索与记忆注入均使用同一 viewer 过滤结果，禁止在 UI 层『隐藏但仍发送』」。
 * 聊天轮次那一半早就接上了（`ChatManager.handleMessageComplete` 里的 `viewerMessages` +
 * `viewerScopedTools` + `GroupPerspectiveTransformer`），但**标题与摘要这一半漏了**：
 * `ChatManager.generateTitle` / `compressConversation` 直接把 `conversation.currentMessages`
 * 送进 TITLE / SUMMARY 模型。
 *
 * ## 为什么这是真漏洞而不是理论问题
 *
 * `generateTitle` 不只由「长按 → 重新生成标题」触发，它还在**每一轮群聊结束时自动跑**
 * （`ChatManager.handleMessageComplete` 的 `onSuccess` 末尾 `generateTitle(...)`，单聊与群聊
 * 走的是同一行）。也就是说**只要群里跑完一轮，其他角色最近 4 条发言就进了 TITLE 模型**，
 * 用户什么菜单都不用点。抽屉里的群聊筛选项 + 会话列表的「群」徽标保证群会话会出现在列表里，
 * 长按菜单（`ConversationList`）又不判会话 type，所以手动那条路同样可达。
 *
 * ## 过滤口径与聊天轮次共用同一份实现
 *
 * 这里**不新写一套过滤**：[summaryViewerMessages] 直接转调
 * [GroupTurnCoordinator.viewerMessages]，与工具 `systemPrompt`、记忆检索 query 是同一个函数、
 * 同一份 `GroupChat.visibleMessages` 判定。两处必然漂移的风险因此不存在。
 *
 * ## ⚠️ 待产品确认：标题/摘要按**谁的**视角取
 *
 * 聊天轮次的 viewer 是确定的（本轮发言角色，由 `GroupChat.plan` 算出来）；标题/摘要**没有轮次**，
 * 「该按谁看」是一个产品口径问题，当前实现按下述规则取值：
 *
 * | `mode` | viewer | 理由 |
 * |---|---|---|
 * | `roundtable` | 议长（`chairRoleId`），且 `chairRound = true` | 议长在 `GroupChat.plan` 里本来就是唯一被放开「本轮全部输出」的角色，它看全量不算越权 |
 * | `pipeline` / `vote` / 未知 mode | `roles` 列表第一个 | 名单顺序即发言顺序（`GroupChat.plan` 的 `selected.mapIndexed`），取第一位等于「按第一个发言者的视角」 |
 *
 * **这条规则是判断，不是实证**：契约只规定了「必须过滤」，没规定「按谁过滤」。
 * 另一种同样自洽的口径是「议长优先存在于任何模式」或「用户点的是哪个角色就按谁」，
 * 结论会不同。改动请只动 [summaryViewerStep]，并同步改 `SummaryViewerScopeTest`。
 *
 * ## 退化口径一律 fail-closed
 *
 * - `config == null`（非群聊）→ 原样返回入参列表，**同一个 List 实例**，单聊路径逐字不变。
 * - 群聊但 `roles` 为空 / 议长 id 不在名单里 → [summaryViewerStep] 返回 `null`，
 *   [summaryViewerMessages] 返回**空列表**，绝不回退成「不过滤」。
 *
 * fail-closed 的代价是这类群聊会话拿不到标题/摘要（模型收到空内容）；换来的是
 * 「配置坏了就把内容全发出去」这种形状不可能出现。老数据里 `roles` 为空的群会话理论上不存在
 * （导入与 UI 保存都过 `GroupChat.validate` 的「至少需要一个成员」），这是兜底而不是常态。
 */
internal object SummaryViewerScope {

    /**
     * 标题/摘要的 viewer 视角。`null` = 定不出 viewer（配置残缺），调用方必须 fail-closed。
     *
     * 口径与理由见类 KDoc的「⚠️ 待产品确认」一节。
     */
    fun step(config: GroupConfig?): SpeakerStep? {
        if (config == null) return null
        val chair = config.chairRoleId?.let { chairId ->
            config.roles.firstOrNull { it.id == chairId }
        }
        val role = when {
            // roundtable：议长。`chairRound = true` 与 `GroupChat.plan` 给议长那一步的取值一致，
            // 所以标题摘要看到的集合与议长本人发言时看到的集合同源，不会出现「摘要里有他
            // 发言时看不到的东西」这种自相矛盾。
            config.mode == GroupChat.MODE_ROUNDTABLE && chair != null -> chair
            else -> config.roles.firstOrNull()
        } ?: return null
        return SpeakerStep(
            role = role,
            chairRound = config.mode == GroupChat.MODE_ROUNDTABLE && chair != null,
        )
    }

    /**
     * 送给 TITLE / SUMMARY 模型的上下文。非群聊时**原样返回 [Conversation.currentMessages]**。
     */
    fun messages(conversation: Conversation): List<UIMessage> {
        if (!isGroupConversation(conversation)) return conversation.currentMessages
        val config = conversation.groupConfig ?: return conversation.currentMessages
        val viewerStep = step(config) ?: return emptyList()
        return GroupTurnCoordinator.viewerMessages(config, conversation.currentMessages, viewerStep)
    }
}