package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.ai.tavern.GroupImportReport
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernGroupChatDocument
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupConfigError
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta

/**
 * 酒馆群聊文件（`.jsonl`）**回导**的判定层——C1 契约缺口 5「导出/恢复」的恢复侧。
 *
 * ## 补的是哪个缺口
 *
 * 导出侧早就有生产入口（[GroupTavernExportCard] → `TavernChatCodec.exportGroupJsonl`），
 * 其 UI 文案写着「可在 SillyTavern 打开并回导」。但回导那一步在 `app/src/main` 里
 * **零调用方**：`TavernChatCodec.importGroup` 只有 `app/src/test` 与 `app/src/androidTest`
 * 在调。也就是「导得出、导不回」，而导出卡片自己承诺的回导点不到。
 *
 * 本文件是那个入口的**判定部分**（纯函数，不碰 IO、不碰 `Context`），UI 在
 * [TavernGroupImportCard]，JVM 测试在 `GroupTavernImportTest`。
 *
 * ## ① 判定口径必须读 `GroupImportReport`，不能只看 `config == null`
 *
 * [TavernChatCodec.importGroup] 的 KDoc（`TavernChatCodec.kt:281-291`）写死了这条：
 *
 * > `config == null` 有**四种**原因，**必须和 `importReport` 一起读**，报告是唯一能把它们
 * > 分开的东西……**调用方不能只看 `config == null` 就当失败，要看 `GroupImportReport`**。
 *
 * 四个分支在本文件的 [resolveTavernGroupImport] 里一一对应，`when` **不带 `else`**：
 * 以后 codec 加第五种报告类型，这里编译不过，而不是悄悄按旧语义把新分支当 `Clean` 处理。
 *
 * | 报告 | 本文件的判定 | 是不是错误 |
 * |---|---|---|
 * | [GroupImportReport.Clean] | 配置原样通过，落库 | 否 |
 * | [GroupImportReport.Normalized] | 配置越界但已归一化，落库**并把 `fixes` 逐条显示** | 否 |
 * | [GroupImportReport.NoConfig] | 文件里没有配置块（纯酒馆群聊文件），只落消息、沿用当前群配置 | **否** |
 * | [GroupImportReport.Rejected] | 密钥黑名单 / 配置非法 / 超限，**绝不落库** | 是 |
 *
 * ⚠️ `NoConfig` 那行是最容易写错的一条：纯酒馆群聊文件**本来就没有** KhatKit 的配置块，
 * 把它当成「导入失败」会让一份完全正常的文件弹错误框。所以它走 [TavernGroupImportOutcome.Accepted]
 * 且 `config = null`（调用方沿用会话里已有的群配置，见 [applyTavernGroupImport]），
 * `isError = false`。
 *
 * ## ② 落库前那一道 `GroupChat.validate`
 *
 * [TavernChatCodec.importGroup] 自己会跑 [GroupChat.findForbiddenKeys] 与
 * [GroupChat.screenImportedConfig]，但那**不够**：[GroupChat.screenImportedConfig] 内部调的
 * 是 `validate(config)`，`conversationId` 传 null，于是
 * 「`memory_space_id` 必须等于派生值 `group:<conversationId>:role:<roleId>`」那条**够不着**
 * （`GroupChat.kt:466` 的 `conversationId != null &&` 前置条件）。
 *
 * 也就是说一份从别的会话导出的、带着**外来记忆空间键**（或全局 / 助手空间键）的配置能穿过
 * screening。本文件在落库前再跑一遍带 `conversationId` 的 [GroupChat.validate]：
 *
 * - 判定实现**只有这一份**（`GroupChat.validate`），不自写第二套规则；理由与
 *   `GroupChat.kt:459`「判定口径收敛在 `schemaVersionErrors`，`importShare` 与这里共用
 *   同一份实现」同源，`GroupChatPage.kt:338` 的 UI 保存闸门也是同一句。
 * - **绕不过**：闸门就在 [resolveTavernGroupImport] 里、构造 [TavernGroupImportOutcome.Accepted]
 *   的**同一条语句流**上；[TavernGroupImportOutcome.Rejected] 与
 *   [TavernGroupImportOutcome.Unsupported] 都不带任何可落库的载荷，而落库函数
 *   [applyTavernGroupImport] 的入参类型就是 [TavernGroupImportOutcome.Accepted]——
 *   拿着被拒的文档去写库在类型上就做不到。
 *
 * 硬约束「记忆键必须是 `group:<conversationId>:role:<roleId>`，失败绝不回退全局/助手空间」
 * 就是这一道闸门在管（见 `GroupTavernImportTest` 的 `a foreign memory space key is refused`）。
 *
 * ## ③ 库存消息完整，导入路径不做任何过滤
 *
 * 落进 `messageNodes` 的是 [TavernGroupImportOutcome.Accepted.nodes]，逐条来自
 * `document.messages`，**一条不过滤、不改写**。视角过滤只发生在发给模型的副本上
 * （生成侧 `GroupPerspectiveTransformer`），库存消息保持全量。
 *
 * 四个契约字段 `role_id` / `round_id` / `turn_kind` / `mention_role_ids` 由
 * `TavernChatCodec.groupMessageOf` 写回 [MessageNode] 的消息上，本文件原样带走，
 * 因此 `exportGroup → importGroup` 之后逐条相等（`TavernChatCodec.kt:256-268` 的契约）。
 *
 * ## ④ 不复用 `GroupChat.importShare`
 *
 * [GroupChat.importShare] 的五道闸门解的是**分享载荷 JSON**（`kind == QR_KIND` 的那一种），
 * 而这里读的是**酒馆群聊文件**（JSONL / JSON 数组）。文件入口调它会必然卡在闸门 2
 * 「不是 KhatKit 群聊分享载荷」上。**判定函数**（`validate` / `findForbiddenKeys` /
 * `screenImportedConfig` / `schemaVersionErrors`）才复用，实现各归各位。
 */

/**
 * 酒馆群聊文件导入的判定结论。三个分支与 UI 的三种呈现一一对应：
 * [Accepted] 落库、[Rejected] 拒收并逐条显示字段级错误、[Unsupported] 根本不是这类文件。
 *
 * `headline` / `detailLines` / `isError` 是**展示口径的唯一来源**：UI 的结果视图与 Toast
 * 都读它们，文案不在 Composable 里另写一份，所以 JVM 测试能直接断言文案
 * （例如「`Normalized` 的 `fixes` 里有 fix 描述」）。
 */
internal sealed interface TavernGroupImportOutcome {
    /** 面向用户的一句话，Toast 与结果视图共用同一份。 */
    val headline: String

    /** 逐条明细：修正项（`Normalized` / 卡片去重）或字段级错误（`Rejected`）。空列表表示没有。 */
    val detailLines: List<String>

    /** 结果视图里这一行是否按错误配色渲染。[NoConfig] 恒为 false。 */
    val isError: Boolean

    /**
     * 可以落库。[config] 为 **null 只出现在 [GroupImportReport.NoConfig]** 分支：
     * 那份文件没有群配置块，沿用会话里已有的群配置（[applyTavernGroupImport] 负责这件事）。
     *
     * @param nodes 逐条完整的库存消息，导入路径不做任何过滤。
     */
    data class Accepted(
        val config: GroupConfig?,
        val nodes: List<MessageNode>,
        val cards: List<RoleCardMeta>,
        val report: GroupImportReport,
        val groupName: String,
        override val headline: String,
        override val detailLines: List<String>,
        override val isError: Boolean = false,
    ) : TavernGroupImportOutcome

    /**
     * 被拒收，**绝不落库**。密钥黑名单命中 / 配置结构性非法 / 预算越界 / 版本未知 /
     * `config` 解不出来，都在这里。字段级错误逐条显示（`field` 已是契约 snake_case 键，
     * 直接展示，不做键名映射）。
     */
    data class Rejected(
        val reason: String,
        val fieldErrors: List<GroupConfigError>,
        override val headline: String,
        override val detailLines: List<String>,
        override val isError: Boolean = true,
    ) : TavernGroupImportOutcome

    /**
     * 不是酒馆群聊文件，或根本解析不出来（空文本、非法 UTF-8、非 JSONL）。
     *
     * 与 [Rejected] 分开是因为**成因不同**：前者是「这份文件被判定不能落库」，
     * 后者是「这压根不是走这条路的文件」。两者都不落库，但话术必须不一样，否则用户
     * 粘错一段文字会被告知「群配置校验未通过」。
     */
    data class Unsupported(
        val reason: String,
        override val headline: String,
        override val detailLines: List<String> = emptyList(),
        override val isError: Boolean = true,
    ) : TavernGroupImportOutcome
}

/**
 * 群聊文件回导的判定入口。[raw] 是整份文件的文本（SAF 读回来的字节解码结果或粘贴的文本）。
 *
 * @param conversationId 当前会话 id。只用于 [GroupChat.validate] 的记忆空间键核对；
 *   它决定「记忆键必须是 `group:<conversationId>:role:<roleId>`」这条能不能真正生效。
 *
 * ## 为什么不自己再扫一遍密钥黑名单
 *
 * [TavernChatCodec.importGroup] 已经对整个原始 `khatkit_group` 载荷跑了
 * [GroupChat.findForbiddenKeys]，且是**先扫后解**（扫解完的对象会漏）。这里再扫一遍是纯重复；
 * 本函数新增的那道闸门是它没覆盖的那条——带 `conversationId` 的 [GroupChat.validate]。
 */
internal fun resolveTavernGroupImport(
    raw: String,
    conversationId: String,
): TavernGroupImportOutcome {
    if (raw.isBlank()) {
        return TavernGroupImportOutcome.Unsupported(
            reason = "内容为空",
            headline = "酒馆群聊文件导入失败：内容为空",
        )
    }
    // importGroup 内部对 import(raw) 整个包了 runCatching：解析失败 / 非法 UTF-8 /
    // 非 JSONL 都只会回 null，不会把异常抛到 UI。
    val document = TavernChatCodec.importGroup(raw)
        ?: return TavernGroupImportOutcome.Unsupported(
            reason = "不是酒馆群聊文件（缺 khatkit_group 块，或内容不是合法 JSON/JSONL）",
            headline = "酒馆群聊文件导入失败：不是酒馆群聊文件",
        )

    // ⚠️ when 不带 else：GroupImportReport 加第五个分支时这里编译不过，
    // 不会有人悄悄按旧语义把新分支当 Clean 处理。
    return when (val report = document.importReport) {
        is GroupImportReport.Rejected -> TavernGroupImportOutcome.Rejected(
            reason = report.reason,
            fieldErrors = report.fieldErrors,
            headline = "酒馆群聊文件导入被拒：${report.reason}",
            detailLines = report.fieldErrors.map { errorLine(it.field, it.message) },
        )

        // ⚠️ 不是错误。纯酒馆群聊文件本来就没有 khatkit_group.config 块，
        // 「没有」与「坏掉」必须分开，否则一份完全正常的文件会被报成导入失败。
        GroupImportReport.NoConfig -> TavernGroupImportOutcome.Accepted(
            config = null,
            nodes = document.messages.map { it.node },
            cards = document.cards,
            report = report,
            groupName = document.groupName,
            headline = "酒馆群聊文件已导入 ${document.messages.size} 条消息" +
                "（这份文件没有群配置块，沿用当前群配置）",
            detailLines = document.cardFixes.map(::fixLine),
            isError = false,
        )

        GroupImportReport.Clean -> accepted(
            document = document,
            report = report,
            fixes = emptyList(),
            conversationId = conversationId,
        )

        is GroupImportReport.Normalized -> accepted(
            document = document,
            report = report,
            // 归一化不是无损的：revision / tie_policy 被改成了缺省值，改前改后的字面值
            // 必须显式展示给用户（GroupChat.GroupConfigFix 的 KDoc 正是这么要求的）。
            fixes = report.fixes,
            conversationId = conversationId,
        )
    }
}

/**
 * `Clean` / `Normalized` 的共同收尾：落库前再过一遍 [GroupChat.validate]（带 `conversationId`）。
 *
 * 这一道**不是**重复 screening：screening 内部是 `validate(config)`、`conversationId` 为 null，
 * 记忆空间键那条够不着（见本文件 KDoc 的 ②）。
 */
private fun accepted(
    document: TavernGroupChatDocument,
    report: GroupImportReport,
    fixes: List<GroupChat.GroupConfigFix>,
    conversationId: String,
): TavernGroupImportOutcome {
    // Clean / Normalized 按 codec 的实现必然带 config；真为 null 也不猜，兜成拒收。
    val config = document.config
        ?: return TavernGroupImportOutcome.Rejected(
            reason = "群聊文件里的群配置缺失",
            fieldErrors = listOf(GroupConfigError("config", "缺少或无法解析 roles")),
            headline = "酒馆群聊文件导入被拒：群聊文件里的群配置缺失",
            detailLines = listOf(errorLine("config", "缺少或无法解析 roles")),
        )

    // ★ 落库前的闸门。判定实现只有 GroupChat.validate 这一份，不另写一套规则。
    val errors = GroupChat.validate(config, conversationId)
    if (errors.isNotEmpty()) {
        return TavernGroupImportOutcome.Rejected(
            reason = "群配置校验未通过",
            fieldErrors = errors,
            headline = "酒馆群聊文件导入被拒：群配置校验未通过",
            detailLines = errors.map { errorLine(it.field, it.message) },
        )
    }

    val normalizedNote = if (fixes.isEmpty()) "" else "（群配置已归一化 ${fixes.size} 处）"
    return TavernGroupImportOutcome.Accepted(
        config = config,
        nodes = document.messages.map { it.node },
        cards = document.cards,
        report = report,
        groupName = document.groupName,
        headline = "酒馆群聊文件已导入 ${document.messages.size} 条消息" +
            " · ${config.roles.size} 个角色$normalizedNote",
        // 归一化的修正项 + 角色卡去重记录，两者都是「这次导入改动了什么」，一并显示。
        detailLines = fixes.map(::fixLine) + document.cardFixes.map(::fixLine),
        isError = false,
    )
}

/**
 * 一条 `field: message` 明细。文案与群配置面板那条 `GroupConfigErrorLine` 同源，
 * 但**本路径不调那个 composable**：那是 `importShare` 的展示，这条是酒馆文件路径的展示，
 * 两者标题与前置条件不同，合并会让「扫码导入」与「酒馆文件回导」的话术串味。
 */
internal fun errorLine(field: String, message: String): String = "· $field：$message"

/**
 * 一条修正记录的展示文案。**改前改后的字面值都在**，不写成一句「已修正」——
 * 悄悄改掉用户看得见的字段而只留一句「已修正」等于把改写伪装成无损。
 */
internal fun fixLine(fix: GroupChat.GroupConfigFix): String {
    val change = if (fix.original == fix.corrected) fix.original else "${fix.original} → ${fix.corrected}"
    return "· ${fix.field}：${fix.message}（$change）"
}

/**
 * 把 [TavernGroupImportOutcome.Accepted] 写进会话。**落库的构造点只有这一个。**
 *
 * - `type` / `groupConfig`：[config] 为 null（`NoConfig`）时**保留会话里已有的群配置**，
 *   不拿 null 覆盖——那会把一个还能用的群抹成 `group_config` 为空。
 * - `messageNodes`：[nodes] 逐条**追加**，一条不过滤（见本文件 KDoc 的 ③）。
 * - `groupCards`：随这次导入整体替换（含空列表），与分享/扫码那条导入路径同一语义
 *   （`Conversation.groupCards` 的 KDoc：「非 null = 导入带来的卡片，整体替换」）。
 *   导出方向不读它，三条导出路径仍然现场生成（`GroupRoleCards.kt`）。
 *
 * 入参类型就是 [TavernGroupImportOutcome.Accepted]：**被拒收的分支根本没有可落库的载荷**，
 * 闸门绕不过不靠自觉，靠类型。
 */
internal fun Conversation.applyTavernGroupImport(
    accepted: TavernGroupImportOutcome.Accepted,
): Conversation = copy(
    type = GroupChat.TYPE_GROUP,
    groupConfig = accepted.config ?: groupConfig,
    groupCards = accepted.cards,
    messageNodes = messageNodes + accepted.nodes,
)
