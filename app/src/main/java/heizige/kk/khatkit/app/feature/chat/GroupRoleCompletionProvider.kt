package heizige.kk.khatkit.app.feature.chat

import androidx.compose.ui.text.TextRange
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionContext
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionItem
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionList
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionProvider

/**
 * 群聊输入框里的 `@角色` 选择器。
 *
 * 通过 [ChatScaffold] 的 `extraCompletionProviders` 挂进 [ChatInput] 已有的
 * `completionProviders` 插槽（`ChatInput.kt:876-925` 那套 provider 驱动逻辑），
 * 本类**不持有任何 ViewModel / Compose 状态**，因此可以用 JVM 单测把匹配口径钉死。
 *
 * ## 触发条件（三道门，全过才出候选；判定收敛在 [findMention]）
 *
 * 1. 无选区（`context.hasSelection` 为真直接不触发）——有选区时谈「光标前是什么」没有意义。
 * 2. `@` 必须是 `[0, cursor)` 里**最后一个** `@`，且落在行首或某个「mention 边界字符」
 *    （空白或 ``([{<"'``）之后。取「最后一个」是因为用户正在敲的 token 一定紧邻光标，
 *    取更靠前的 `@` 会拿另一段文本当 query。
 * 3. `@` 到光标之间**不含空白**。
 *
 * 第 2 条的后半截就是**邮箱防误触发**：`a@b.com` 里 `@` 前面是 `a`（字母，不是边界字符）⇒
 * 判定为邮箱/句柄里的 `@`，不弹角色候选。这与 `WorkspaceCompletionProvider.findWorkspaceMention`
 * 的 `isMentionBoundary()` 同一口径，两者在同一个输入框里共存也不会互相误触发。
 *
 * **光标之后的内容一概不看**：补全只关心光标前那个 token。
 *
 * ## 排序
 *
 * - query 为空（刚敲下 `@`）：全员候选，**严格按名单顺序**（契约 `roles[]` 顺序）。
 *   此时用户没在挑人，可预期的名单顺序比加权更重要，所以这一档不加议长加权。
 * - query 非空：按匹配分排（同分按 `role.id` 保证稳定），**议长加权排最前**。
 *   同时 `detail` 里带 [GroupSpeakerResolver.BADGE_CHAIR] 标注，两条区分依据都可测。
 * *
 * ## 候选过滤：整词边界 + 前缀锚定（**不是伪 @**）
 *
 * 这是本类最要紧的一段。对照反面教材：SillyTavern 与 RisuAI 的 @ 都是**伪 @**——
 * 拿 `name.contains(query)` 判命中。角色名 `Alice Johnson` 在伪 @ 下会被 `alice`、
 * `johnson`、甚至 `lic`、`ohn` 唤醒，而真正被点名时用户往往只打了一个片段，
 * 结果是「@ 了一堆人，谁也没被准确点名」。
 *
 * 本类的口径与 [heizige.kk.khatkit.app.core.data.model.GroupChat.parseMentions] 对齐
 * （后者是真正决定消息投给谁的判定，见 `GroupChat.mentioned`：整名 + 前后非词字符边界）。
 * 具体到「用户正在敲的半截 query」，判定分两层：
 *
 * - **锚定在名字开头**：`query` 必须是 `name`（或 `role.id`）的**前缀**。
 *   于是 `john` 匹配不上 `Alice Johnson`——它虽然是名字里的一个词，但不是前缀。
 * - **尾部整词边界**：`query` 恰好覆盖完 `name` 里的某个词时（即 `i + query.length`
 *   落在词的末尾），该位置必须后面真的是词边界；若后面还接着词字符，说明用户只打了
 *   一个更长词的前缀（`alic` 对 `Alice`），此时**继续匹配**（这是正常的输入中态），
 *   但排在完全命中之后。
 *
 * 换句话说：`alic` → 命中 Alice（输入中态，较低分）；`Alice` → 完全命中（高分）；
 * `john` / `ohn` / `lic` → 一个都不命中。整词边界的存在让 `Ali`（会命中 `Alice`）与
 * `Al` 在排序上可区分，也让「把 `@Al` 补全成 `@Alice `」这件事只可能补成合法的整名 mention。
 *
 * ## 插入文本
 *
 * `insertText` 固定是 `"@<name> "`（名字为空时退回 `role.id`，再不行就不给这个候选）：
 * 尾随空格让用户能接着打字，`GroupChat.parseMentions` 也只认 `@<整名>` 后接非词字符，
 * 插进去即可被 [heizige.kk.khatkit.app.core.data.model.GroupChat.parseMentions] 正确识别。
 *
 * ## 已知限制：多词角色名只能补到第一个词
 *
 * 触发条件第 3 条禁止 `@` 与光标之间出现空白，而空白正是多词名字的分隔符，所以
 * 「Alice Johnson」这种名字**没法用弹窗补到中间那个词**（`@Alice Jo` 不触发）。
 * 这不是缺陷而是规则的必然推论，且有另一条覆盖路径：敲 `@Alice` 会给出**整名**候选，
 * 一键插入 `@Alice Johnson `；点成员头像也会直接插入完整的多词名（见 `GroupMemberBar`）。
 * 两条路都能产出 `parseMentions` 认得的整名 mention。
 *
 * ## ⚠️ 与 workspace 文件补全共存时的已知坑
 *
 * [ChatInput] 合并多 provider 的实现是 `val primary = lists.firstOrNull()`，
 * **只认第一个非空列表的 `replacementRange`**，只有 `replacementRange` 相同的列表才会被
 * 合并进候选。因此：
 *
 * - 本 provider 必须由 [ChatScaffold] 追加在 `WorkspaceCompletionProvider` **之后**
 *   （`ChatScaffold` 里的 `workspace + extraCompletionProviders` 已保证这个顺序）；
 * - 两者触发条件重叠时（`@` 紧邻光标、前缀是空白、query 非空），两边都会给出列表，
 *   但 `replacementRange` 不同（workspace 的 `@` 是文件路径起点，本类的是 `@` 到光标），
 *   于是**只有 workspace 的候选会被显示**，本类的被丢弃。
 *   这不是 bug 而是可接受的降级：用户打 `/workspace/...` 时本来就不该看到角色候选。
 *   要让本类生效，query 必须不是 workspace 的有效前缀（workspace provider 返回 null）。
 */
class GroupRoleCompletionProvider(
    private val config: () -> GroupConfig?,
) : ChatCompletionProvider {

    override val id: String = "group_roles"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val mention = findMention(context.text, context.cursor) ?: return null
        // config 为 null（还没加载出群配置）或 roles 为空 → 不给候选，也不崩。
        val roles = config()?.roles.orEmpty()
        if (roles.isEmpty()) return null

        val items = roles
            .mapIndexed { index, role -> role to roleItem(role, mention.query, index) }
            .mapNotNull { (role, item) ->
                item?.let { role to it }
            }
            .sortedWith(
                compareByDescending<Pair<GroupRole, ChatCompletionItem>> { it.second.sortScore }
                    .thenBy { it.first.id }
            )
            .take(MAX_COMPLETION_ITEMS)
            .map { it.second }

        if (items.isEmpty()) return null
        return ChatCompletionList(
            providerId = id,
            replacementRange = mention.range,
            items = items,
        )
    }

    /**
     * 一个角色在当前 query 下的候选；不匹配返回 null。
     *
     * 纯函数（不碰 Compose / 数据库），所以匹配口径能被单测直接覆盖。
     */
    private fun roleItem(role: GroupRole, query: String, index: Int): ChatCompletionItem? {
        val name = role.name.ifBlank { role.id }
        if (name.isBlank()) return null
        val queryLower = query.lowercase()

        // 空前缀（刚敲下 @，还没输名字）→ 全员候选，**严格按名单顺序**排。
        // 这里刻意不加议长加权：此时用户没有在挑人，名单顺序（= 契约 roles[] 顺序，
        // 与 GroupChat.plan 的发言顺序同源）才是可预期的呈现。加权只在真的敲了 query、
        // 用户正在挑人的时候才生效。
        if (queryLower.isEmpty()) {
            return item(role, name, score = SCORE_NAME_PREFIX_BASE - index, chairBonus = 0)
        }

        val byName = matchScore(name, queryLower)
        val byId = matchScore(role.id, queryLower)
        // 名字与 id 都匹配不上才放弃；名字命中优先于 id 命中（分更高）。
        val score = when {
            byName != null && byId != null -> maxOf(byName, byId)
            byName != null -> byName
            else -> byId ?: return null
        }
        return item(role, name, score)
    }

    /**
     * 名字（或 id）与 query 的匹配分；不匹配返回 null。
     *
     * 锚定：只在偏移 0 处尝试匹配（`regionMatches(0, ...)`），因此 `john` 匹配不上
     * `Alice Johnson`。尾部整词边界：覆盖完一个词且后面还粘着词字符 ⇒ 降一级
     * （`alic` vs `Alice`），既不误判为不匹配、也排不到完全命中前面。
     */
    private fun matchScore(name: String, queryLower: String): Int? {
        if (name.isEmpty() || queryLower.isEmpty()) return null
        if (!name.regionMatches(0, queryLower, 0, queryLower.length, ignoreCase = true)) return null
        val end = queryLower.length
        val continuesWord = end < name.length && name[end].isWordChar()
        return if (continuesWord) SCORE_PREFIX_PARTIAL else SCORE_FULL_WORD
    }

    private fun item(role: GroupRole, name: String, score: Int, chairBonus: Int = CHAIR_BONUS): ChatCompletionItem {
        // 议长给可测的区分依据：detail 前缀固定用 GroupSpeakerResolver.BADGE_CHAIR
        // （"议长"），与气泡上的徽章同一份常量，不另写一个字面量。
        val detail = buildString {
            append(role.id)
            if (role.chair) {
                append(" · ")
                append(GroupSpeakerResolver.BADGE_CHAIR)
            }
        }
        return ChatCompletionItem(
            label = name,
            insertText = "@$name ",
            detail = detail,
            sortScore = score + if (role.chair) chairBonus else 0,
        )
    }

    /**
     * 光标前的 `@` mention。返回 null 表示不触发。
     *
     * [findMention] 的三道门（见类 K「触发条件」）：
     * - `start` = `[0, cursor)` 里**最后一个** `@`。必须是最后一个而不是任意一个：
     *   用户正在敲的 token 一定是紧邻光标的那段，取更靠前的 `@` 会拿另一段文本当 query。
     * - `start` 必须落在行首，或 `text[start - 1]` 是 mention 边界字符。
     *   这道门就是**邮箱防误触发**：`a@b.com` 的 `@` 前面是字母 `a`，判定为邮箱里的 `@`。
     * - `@` 与光标之间（`query`）不含空白。
     *
     * **光标之后的内容一概不看**：补全只关心光标前那个 token，用户在 `@Al` 后面继续打字
     * 时弹窗该不该留着是 [ChatInput] 的事。
     */
    private fun findMention(text: String, cursor: Int): Mention? {
        if (cursor <= 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf(MENTION_PREFIX)
        if (start < 0) return null
        if (start > 0 && !text[start - 1].isMentionBoundary()) return null
        val query = prefix.substring(start + 1)
        if (query.any { it.isWhitespace() }) return null
        return Mention(query = query, range = TextRange(start, cursor))
    }

    private data class Mention(val query: String, val range: TextRange)

    /** 与 [GroupChat.mentioned] 的 `[\p{L}\p{N}_]` 保持同一套「词字符」定义。 */
    private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'

    /** 与 `WorkspaceCompletionProvider.isMentionBoundary` 同一套边界字符。 */
    private fun Char.isMentionBoundary(): Boolean =
        isWhitespace() || this in "([{<\"'"

    private companion object {
        const val MENTION_PREFIX = '@'

        /** query 为空（刚敲下 @）时的基分，再按名单顺序递减，保证名单顺序稳定。 */
        const val SCORE_NAME_PREFIX_BASE = 300

        /** query 覆盖完一整个词（`Alice` 对 `Alice Johnson`）——最高分。 */
        const val SCORE_FULL_WORD = 1_000

        /** query 只是某个更长词的前缀（`alic` 对 `Alice`）——命中但排在完全命中之后。 */
        const val SCORE_PREFIX_PARTIAL = 500

        /**
         * 议长加权，只在 query 非空时生效（见类 KDoc「排序」）。
         * 取得比 [SCORE_FULL_WORD] 大，所以「议长 vs 同名同前缀的普通角色」时议长恒排最前。
         */
        const val CHAIR_BONUS = 2_000

        const val MAX_COMPLETION_ITEMS = 8
    }
}
