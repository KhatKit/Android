package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-M 纯 JVM 证据：抽取写入记忆时的 `role_id` / `source_message_id` 归属。
 *
 * 契约：「群消息写入记忆时带 `source_message_id` 与 `role_id`，检索结果再次经过
 * viewer 过滤」。
 *
 * 修复前 [MemoryExtractor] 的 `extractFromTurn` / `parseAndStore` 签名里没有 roleId，
 * 并且 `addMemory(assistantId = spaceId)` 用空间键冒充角色；`sourceMessageId` 一律取
 * 「窗口最后一条消息」，窗口里第 2 条消息的事实也被记成最后一条的来源——溯源字段是假的。
 *
 * ⚠️ 只能覆盖纯判定部分：[MemoryExtractor.parseAndStore] 需要 `MemoryRepository`（final
 * class，依赖 Room DAO）与 `ProviderManager`（Hilt 注入），本仓库没有 mockk / Robolectric，
 * 所以「真的落库、role_id 真在行里」只能靠 androidTest 覆盖。本文件断言的是落库前
 * 决定写什么的那一步（[MemoryAttribution] / [MemoryExtractor.parseFacts] /
 * [MemoryExtractor.buildExtractWindow]）。
 */
class MemoryAttributionTest {

    private val aliceSpace = GroupChat.memorySpaceId("conv-1", "alice")

    private fun message(text: String, role: MessageRole = MessageRole.USER) = UIMessage(
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
    )

    /** roleId 必须原样透传到写入（`memory_chunks.role_id`），空间键不受影响。 */
    @Test
    fun roleIdIsPassedThroughToTheWrite() {
        val write = MemoryAttribution.forExtractedFact(
            spaceId = aliceSpace,
            roleId = "alice",
            fact = MemoryExtractor.ExtractedFact(content = "张伟负责结算", sourceLine = 1),
            fallbackMessageId = "msg-9",
            messageIdOfLine = { "msg-$it" },
        )
        assertEquals("alice", write.roleId)
        assertEquals("group:conv-1:role:alice", write.spaceId)
        assertEquals("msg-1", write.sourceMessageId)
    }

    /** 单聊路径 roleId 为 null：存量单聊/助手记忆的行不变。 */
    @Test
    fun directChatWriteHasNoRoleId() {
        val write = MemoryAttribution.forExtractedFact(
            spaceId = "assistant-space",
            roleId = null,
            fact = MemoryExtractor.ExtractedFact(content = "用户偏好简短回答"),
            fallbackMessageId = "msg-1",
            messageIdOfLine = { "msg-$it" },
        )
        assertNull(write.roleId)
        assertEquals("assistant-space", write.spaceId)
        assertEquals("msg-1", write.sourceMessageId)
    }

    /** 模型报不出 source_line 时才落到调用方给的兜底消息（旧行为保留为兜底）。 */
    @Test
    fun missingSourceLineFallsBackToGivenMessage() {
        val write = MemoryAttribution.forExtractedFact(
            spaceId = aliceSpace,
            roleId = "alice",
            fact = MemoryExtractor.ExtractedFact(content = "事实"),
            fallbackMessageId = "msg-last",
            messageIdOfLine = { "msg-$it" },
        )
        assertEquals("msg-last", write.sourceMessageId)
    }

    /** source_line 越界（模型编了个不存在的行号）时也不能当成真实来源。 */
    @Test
    fun outOfRangeSourceLineFallsBackInsteadOfGuessing() {
        val write = MemoryAttribution.forExtractedFact(
            spaceId = aliceSpace,
            roleId = "alice",
            fact = MemoryExtractor.ExtractedFact(content = "事实", sourceLine = 99),
            fallbackMessageId = "msg-last",
            messageIdOfLine = { line -> if (line in 1..3) "msg-$line" else null },
        )
        assertEquals("msg-last", write.sourceMessageId)
    }

/** 兜底也没有 → null，按 filterMemoryForViewer 口径放行。 */
    @Test
    fun noResolvableSourceStaysNull() {
        val write = MemoryAttribution.forExtractedFact(
            spaceId = aliceSpace,
            roleId = "alice",
            fact = MemoryExtractor.ExtractedFact(content = "事实", sourceLine = 2),
            fallbackMessageId = null,
            messageIdOfLine = { null },
        )
        assertNull(write.sourceMessageId)
        assertEquals("alice", write.roleId)
    }

    /** 模型自报的 `source_line` 要能解析出来（旧实现根本没这个字段）。 */
    @Test
    fun parseFactsReadsSourceLine() {
        val facts = MemoryExtractor.parseFacts(
            """
            [{"content":"张伟负责结算","entity":"张伟","relation":"RESPONSIBLE","related_entity":"结算","source_line":2,"confidence":0.9}]
            """.trimIndent()
        )
        assertEquals(1, facts.size)
        assertEquals(2, facts[0].sourceLine)
        assertEquals("张伟负责结算", facts[0].content)
        assertEquals(0.9f, facts[0].confidence, 1e-6f)
    }

    /** 老模型不返回该字段时按 null 处理，不报错。 */
    @Test
    fun parseFactsWithoutSourceLineGivesNull() {
        val facts = MemoryExtractor.parseFacts("""[{"content":"用户偏好简短回答","confidence":0.7}]""")
        assertEquals(1, facts.size)
        assertNull(facts[0].sourceLine)
    }

    /** 提示词窗口带 `[n]` 行号，行号即 source_line 的取值空间。 */
    @Test
    fun extractWindowIsNumberedFromOne() {
        val window = MemoryExtractor.buildExtractWindow(
            listOf(
                message("我叫张伟"),
                message("你负责结算", MessageRole.ASSISTANT),
            )
        )
        val lines = window.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("[1] user: 我叫张伟"))
        assertTrue(lines[1].startsWith("[2] assistant: 你负责结算"))
    }

    /** 超长窗口从头部截断（保留最近的消息），行号仍与原窗口对齐。 */
    @Test
    fun extractWindowTruncatesFromTheHeadAndKeepsLineNumbers() {
        val window = MemoryExtractor.buildExtractWindow(
            (1..10).map { message("第 $it 条消息".repeat(400)) }
        )
        val kept = window.lines().filter { it.isNotBlank() }
        assertTrue("截断后应仍有内容", kept.isNotEmpty())
        assertTrue("最旧的行应被截掉：${kept.first().take(20)}", kept.none { it.startsWith("[1] ") || it.startsWith("[2] ") })
        assertTrue("最新一行应完整保留：${kept.last().take(20)}", kept.last().startsWith("[10] user: 第 10 条消息"))
    }
}