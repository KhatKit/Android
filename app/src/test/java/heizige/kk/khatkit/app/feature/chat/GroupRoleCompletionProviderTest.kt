package heizige.kk.khatkit.app.feature.chat

import androidx.compose.ui.text.TextRange
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.ui.components.ai.completion.ChatCompletionContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GroupRoleCompletionProvider] 的匹配口径。
 *
 * 重点钉死两件事：**触发不误触**（邮箱 / 选区 / query 含空白都不弹），
 * 以及 **过滤不是伪 @**（`john` 匹配不上 `Alice Johnson`）。
 */
class GroupRoleCompletionProviderTest {

    private val alice = GroupRole("a", "Alice", "asst-a")
    private val bob = GroupRole("b", "Bob", "asst-b")
    private val cara = GroupRole("c", "Cara", "asst-c", chair = true)
    private val roles = listOf(alice, bob, cara)

    private fun provider(roles: List<GroupRole>? = this.roles) = GroupRoleCompletionProvider {
        roles?.let { GroupConfig(roles = it) }
    }

    private fun context(text: String, cursor: Int = text.length) =
        ChatCompletionContext(text = text, selection = TextRange(cursor))

    /** 跑一次 provider，返回候选 label；null 表示**不触发**（空列表被 provider 归一成 null）。 */
    private suspend fun labels(
        text: String,
        cursor: Int = text.length,
        roles: List<GroupRole>? = this.roles,
    ): List<String>? = provider(roles).complete(context(text, cursor))?.items?.map { it.label }

    @Test
    fun `bare at sign lists every role in roster order`() = runBlocking {
        // 用例 1：光标前是 @ 且 @ 到光标之间无空白 → 全部角色候选
        val result = provider().complete(context("@"))
        assertNotNull(result)
        assertEquals(listOf("Alice", "Bob", "Cara"), result!!.items.map { it.label })
        // 替换范围必须正好覆盖那个孤零零的 @，否则插入会把光标前的字吃掉
        assertEquals(TextRange(0, 1), result.replacementRange)
        assertEquals("group_roles", result.providerId)
        // 插入文本固定是 "@<整名> "：尾随空格让用户能接着打字
        assertEquals(listOf("@Alice ", "@Bob ", "@Cara "), result.items.map { it.insertText })
    }

    @Test
    fun `query filters candidates by anchored name prefix`() = runBlocking {
        // 用例 2：@Al 只给 Alice（前缀过滤，忽略大小写）
        assertEquals(listOf("Alice"), labels("@Al"))
        assertEquals(listOf("Alice"), labels("@al"))
        // 名单里没有以 op 开头的 → 空候选被归一成 null（不弹空列表）
        assertNull(labels("@op"))
        // 前缀锚定：不是前缀的词一律不命中
        assertNull(labels("@lic"))
        // role_id 也是可匹配面：@b 命中 name=Bob，也命中 id=b
        assertEquals(listOf("Bob"), labels("@b"))
    }

    @Test
    fun `whitespace inside the query disables the popup`() = runBlocking {
        // 用例 3：@ 与光标之间有空白 → 不触发。
        // 已知的设计后果（已写进 provider 的 KDoc）：多词角色名没法用弹窗补全，
        // 因为空格会终结 mention token。点成员头像仍然能插入完整的多词名。
        assertNull(labels("@Alice world"))
        assertNull(labels("hello @Alice world", cursor = 14))
        // 敲完名字顺手补一个空格 → 光标前是空格，也���触发
        assertNull(labels("@Alice "))
        // 刚敲下 @ 后面跟一个空格 → 不触发
        assertNull(labels("@ "))
    }

    @Test
    fun `email shaped at sign does not trigger`() = runBlocking {
        // 用例 4：a@b.com → @ 前是字母 'a'，不是 mention 边界字符 → 判定为邮箱，不触发
        assertNull(labels("a@b.com"))
        assertNull(labels("写给 foo@bar", cursor = 10))
        assertNull(labels("mail me at a@b", cursor = 12))
        // 对照组：行首的 @ 照常触发（证明上面拒的是邮箱形态，不是 @ 本身）
        assertEquals(listOf("Bob"), labels("@b"))
        assertEquals(listOf("Alice", "Bob", "Cara"), labels("@", cursor = 1))
    }

    @Test
    fun `mention is only recognised in the token left of the cursor`() = runBlocking {
        // 用例 5：@ 不在光标前时按设计判定。
        // 设计（与 WorkspaceCompletionProvider 同源，已写进 provider 的 KDoc「触发条件」）：
        //   @ 必须是 [0,cursor) 里最后一个 @，且落在行首或 mention 边界字符之后，
        //   @ 到光标之间不含空白；**光标之后的内容一概不看**。
        // ① 光标停在 @ 之前 → 光标前没有 @，不触发
        assertNull(labels("@Alice", cursor = 0))
        assertNull(labels("hi @Ali", cursor = 3))
        // ② @ 在文本中间但光标越过了一整个 token（query 含空白）→ 不触发
        assertNull(labels("@Alice world", cursor = 12))
        // ③ 光标刚落在 @ 之后（query 还是空的）→ 触发，给全员
        assertEquals(listOf("Alice", "Bob", "Cara"), labels("@Ali", cursor = 1))
        // ④ @ 在句子中间、后面已经写了别的字，但光标还在这个 token 内 → 照常触发
        assertEquals(listOf("Alice"), labels("hello @Ali", cursor = 10))
        // ⑤ 光标后的内容不影响：同一个 @Al 放在末尾也触发
        assertEquals(listOf("Alice"), labels("@Al", cursor = 3))
    }

    @Test
    fun `whole word boundary rejects inner word of a multi word name`() = runBlocking {
        // 用例 6：防伪 @ 的核心断言。
        // 候选名 "Alice Johnson"：@Alice 命中；@john / @John / @ohn / @son / @lic 一个都不命中。
        val jones = GroupRole("j", "Alice Johnson", "asst-j")
        val short = GroupRole("s", "Son", "asst-s")
        val group = listOf(jones, short)

        // 敲第一个词就给出**整名**候选，插进去正好是合法 mention
        assertEquals(listOf("Alice Johnson"), labels("@Alice", roles = group))
        // 名字里的第二个词不是名字的前缀 → 一个都不命中（这正是伪 @ 会误判的那一类）
        assertNull(labels("@john", roles = group))
        assertNull(labels("@John", roles = group))
        assertNull(labels("@ohn", roles = group))
        assertNull(labels("@lic", roles = group))
        // @son 只命中真名叫 Son 的那个角色，**不**顺带唤醒 Alice Johnson：
        // 伪 @（name.contains(query)）在这里会把 "Alice Johnson" 也唤醒。
        assertEquals(listOf("Son"), labels("@son", roles = group))
        // 空格会终结 mention token，所以多词名没法用弹窗补到中间那个词
        assertNull(labels("@Alice Jo", roles = group))
        // 前缀锚定对空格免疫：多词名的第一个词之后是词边界 → 完全命中（最高分）
        val item = provider(group).complete(context("@Alice"))!!.items.single()
        assertEquals("@Alice Johnson ", item.insertText)
        // 与真正决定路由的 parseMentions 同口径：插进去的整名必须能被它识别
        assertEquals(listOf("j"), GroupChat.parseMentions(item.insertText, group))
    }

    @Test
    fun `null config or empty roles returns null instead of throwing`() = runBlocking {
        // 用例 7：config == null / roles 为空 → 返回 null，不崩
        assertNull(provider(roles = null).complete(context("@")))
        assertNull(provider(roles = emptyList()).complete(context("@Al")))
        // 群配置加载出来之前（config 还是 null）不得抛异常
        assertNull(GroupRoleCompletionProvider { null }.complete(context("@A")))
        // 有选区时不触发（选区下谈「光标前是什么」没有意义）
        assertNull(
            provider().complete(
                ChatCompletionContext(text = "@Alice", selection = TextRange(0, 6))
            )
        )
    }

    @Test
    fun `chair candidate is distinguishable by detail and outranks the rest`() = runBlocking {
        // 用例 8：议长候选有可测的区分依据（排序 + detail 两条）。
        // ① detail 用 GroupSpeakerResolver.BADGE_CHAIR 常量标注议长，非议长不带
        val all = provider().complete(context("@"))!!
        val chairItem = all.items.single { it.label == "Cara" }
        val plainItem = all.items.single { it.label == "Alice" }
        assertEquals("c · ${GroupSpeakerResolver.BADGE_CHAIR}", chairItem.detail)
        assertEquals("a", plainItem.detail)

        // ② query 为空时严格按名单顺序（此时用户没在挑人，议长不插队）
        assertEquals(listOf("Alice", "Bob", "Cara"), all.items.map { it.label })

        // ③ query 非空时议长加权生效：两个同名角色，只有议长那条 detail 带徽章且排最前
        val plain = GroupRole("a1", "Alpha", "asst-1")
        val chair = GroupRole("a2", "Alpha", "asst-2", chair = true)
        val tie = provider(listOf(plain, chair)).complete(context("@Al"))!!
        assertEquals(listOf("Alpha", "Alpha"), tie.items.map { it.label })
        assertEquals("a2 · ${GroupSpeakerResolver.BADGE_CHAIR}", tie.items.first().detail)
        assertEquals("a1", tie.items.last().detail)
        assertTrue(
            "议长 sortScore 必须高于普通角色",
            tie.items.first().sortScore > tie.items.last().sortScore,
        )

        // ④ 名字为空时退回 role.id，候选不消失（与 roleCards 的降级口径一致）
        val nameless = GroupRole("c", "", "asst-c", chair = true)
        val fallback = provider(listOf(nameless)).complete(context("@c"))!!.items.single()
        assertEquals("c", fallback.label)
        assertEquals("@c ", fallback.insertText)
        assertEquals("c · ${GroupSpeakerResolver.BADGE_CHAIR}", fallback.detail)
    }
}
