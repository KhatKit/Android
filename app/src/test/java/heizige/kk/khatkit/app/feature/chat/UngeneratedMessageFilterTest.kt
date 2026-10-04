package heizige.kk.khatkit.app.feature.chat

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.ServerToolStatus
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.ToolApprovalState
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 契约条款「取消/超时不得写入未生成的消息」。
 *
 * 覆盖两层：纯函数（[dropUngeneratedAssistantMessages]）的判据，以及真实落库路径
 * （[ConversationSession.finishGeneration]，取消与超时都从这里出去）。
 */
class UngeneratedMessageFilterTest {

    // ---- 纯函数：完全没产出 → 丢弃 ----

    @Test
    fun `empty assistant placeholder from generation is dropped`() {
        val placeholder = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        val kept = listOf(MessageNode.of(UIMessage.user("hi")), MessageNode.of(placeholder))
            .dropUngeneratedAssistantMessages(emptySet())

        assertEquals(1, kept.size)
        assertEquals("hi", kept.single().currentMessage.toText())
    }

    @Test
    fun `assistant message whose parts are all blank is dropped`() {
        val blank = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text(""),
                UIMessagePart.Reasoning("   "),
                UIMessagePart.Image(""),
                UIMessagePart.Document("", fileName = ""),
            ),
        )
        val kept = listOf(MessageNode.of(blank)).dropUngeneratedAssistantMessages(emptySet())
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `node left without any message is removed instead of kept as an empty shell`() {
        // Conversation.currentMessages 按 selectIndex 取消息，空节点会直接越界。
        val nodes = listOf(
            MessageNode.of(UIMessage.user("hi")),
            MessageNode.of(UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())),
        )
        val kept = nodes.dropUngeneratedAssistantMessages(emptySet())

        assertEquals(1, kept.size)
        assertEquals(listOf("hi"), kept.flatMap { it.messages }.map { it.toText() })
    }

    @Test
    fun `selectIndex follows the surviving branch after a placeholder is dropped`() {
        val oldReply = UIMessage.assistant("old reply")
        val placeholder = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        val node = MessageNode(messages = listOf(oldReply, placeholder), selectIndex = 1)

        val kept = listOf(node).dropUngeneratedAssistantMessages(emptySet())

        assertEquals(1, kept.size)
        assertEquals(listOf(oldReply), kept.single().messages)
        assertEquals(0, kept.single().selectIndex)
        assertEquals("old reply", kept.single().currentMessage.toText())
    }

    // ---- 纯函数：有产出 → 保留 ----

    @Test
    fun `assistant message with a text part is kept`() {
        val reply = UIMessage.assistant("partial reply")
        val kept = listOf(MessageNode.of(reply)).dropUngeneratedAssistantMessages(emptySet())

        assertEquals(listOf(reply), kept.single().messages)
    }

    @Test
    fun `assistant message with only a reasoning part is kept`() {
        val reasoningOnly = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Reasoning("thinking so far")),
        )
        val kept = listOf(MessageNode.of(reasoningOnly)).dropUngeneratedAssistantMessages(emptySet())

        assertEquals(listOf(reasoningOnly), kept.single().messages)
    }

    @Test
    fun `assistant message with only a tool call is kept`() {
        // Tool：模型已经发出工具调用，哪怕 output 还空着（等待审批或被用户取消）也算产出。
        val toolCallOnly = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Tool(toolCallId = "c1", toolName = "edit_file", input = "{}")),
        )
        assertTrue(toolCallOnly.parts.hasGeneratedOutput())
        assertEquals(
            listOf(toolCallOnly),
            listOf(MessageNode.of(toolCallOnly)).dropUngeneratedAssistantMessages(emptySet()).single().messages,
        )

        // ServerTool：服务端执行的工具调用同理。
        val serverToolOnly = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ServerTool(
                    toolCallId = "c2",
                    toolName = "web_search",
                    status = ServerToolStatus.IN_PROGRESS,
                )
            ),
        )
        assertEquals(
            listOf(serverToolOnly),
            listOf(MessageNode.of(serverToolOnly)).dropUngeneratedAssistantMessages(emptySet()).single().messages,
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy tool call result and search parts count as generated output`() {
        // isEmptyUIMessage() 的 when 没覆盖这三类，会落到 else -> true（视为空）；
        // 过滤必须把它们翻回「有产出」，否则只带工具调用的消息会被误删。
        val legacyToolCall = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "c1",
                    toolName = "search",
                    arguments = "{}",
                    approvalState = ToolApprovalState.Auto,
                )
            ),
        )
        assertTrue(legacyToolCall.parts.hasGeneratedOutput())
        assertFalse(legacyToolCall.isUngeneratedAssistantMessage())

        val toolResult = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "c1",
                    toolName = "search",
                    content = JsonPrimitive("ok"),
                    arguments = JsonPrimitive("{}"),
                )
            ),
        )
        assertTrue(toolResult.parts.hasGeneratedOutput())

        val search = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Search))
        assertTrue(search.parts.hasGeneratedOutput())
        assertEquals(
            listOf(search),
            listOf(MessageNode.of(search)).dropUngeneratedAssistantMessages(emptySet()).single().messages,
        )
    }

    @Test
    fun `only assistant messages are eligible for dropping`() {
        // 空的用户/system/tool 消息不是生成产物，删掉就是数据丢失。
        val nodes = listOf(
            MessageNode.of(UIMessage(role = MessageRole.USER, parts = emptyList())),
            MessageNode.of(UIMessage(role = MessageRole.SYSTEM, parts = emptyList())),
            MessageNode.of(UIMessage(role = MessageRole.TOOL, parts = emptyList())),
        )
        assertEquals(nodes, nodes.dropUngeneratedAssistantMessages(emptySet()))
    }

    // ---- 纯函数：存量不被清洗 & 幂等 ----

    @Test
    fun `legacy empty assistant messages already in the conversation are not cleaned`() {
        val legacyEmpty = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        val nodes = listOf(
            MessageNode.of(legacyEmpty),
            MessageNode.of(UIMessage.user("hi")),
            MessageNode.of(UIMessage.assistant("real reply")),
        )
        val existing = nodes.flatMap { it.messages }.map { it.id }.toSet()

        val kept = nodes.dropUngeneratedAssistantMessages(existing)

        assertEquals(nodes, kept)
        assertTrue(kept.any { it.messages.contains(legacyEmpty) })
    }

    @Test
    fun `filter is idempotent when applied twice`() {
        val legacyEmpty = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        val nodes = listOf(
            MessageNode.of(legacyEmpty),
            MessageNode.of(UIMessage.user("hi")),
            MessageNode.of(UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())),
            MessageNode.of(UIMessage.assistant("partial reply")),
        )
        val existing = setOf(legacyEmpty.id)

        val once = nodes.dropUngeneratedAssistantMessages(existing)
        val twice = once.dropUngeneratedAssistantMessages(existing)

        assertEquals(once, twice)
        // 存量空消息仍在，本次新增的空占位在同一个位置被丢掉
        assertEquals(3, twice.size)
        assertEquals(listOf("", "hi", "partial reply"), twice.flatMap { it.messages }.map { it.toText() })
    }

    // ---- 落库路径：finishGeneration ----

    @Test
    fun `cancelling before the first chunk persists nothing for the placeholder`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id).updateCurrentMessages(listOf(UIMessage.user("hi")))
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        // GenerationLoop 预建的占位消息：一个 token 都还没产出。
        session.updateConversation(
            session.state.value.updateCurrentMessages(
                listOf(UIMessage.user("hi"), UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()))
            )
        )

        var savedCount = 0
        session.finishGeneration { savedCount++; persisted = it }

        assertEquals(1, savedCount)
        assertEquals(listOf("hi"), persisted.currentMessages.map { it.toText() })
        assertEquals(persisted, session.state.value)
        // 内存态与落库一致，UI 不会留下空气泡
        assertEquals(1, session.state.value.messageNodes.size)
    }

    @Test
    fun `cancelling after partial output keeps what was received`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id)
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        val partial = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning("thinking so far", finishedAt = null),
                UIMessagePart.Text("partial reply"),
            ),
        )
        session.updateConversation(session.state.value.updateCurrentMessages(listOf(partial)))

        session.finishGeneration { persisted = it }

        assertEquals(listOf(partial.id), persisted.currentMessages.map { it.id })
        val parts = persisted.currentMessages.single().parts
        assertEquals("thinking so far", (parts[0] as UIMessagePart.Reasoning).reasoning)
        assertNotNull((parts[0] as UIMessagePart.Reasoning).finishedAt)
        assertEquals("partial reply", (parts[1] as UIMessagePart.Text).text)
    }

    @Test
    fun `timeout thrown out of the stream drops the placeholder and keeps the history`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id)
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        val failure = java.io.IOException("read timed out")
        val result = runCatching {
            flow<Conversation> {
                // 超时前 GenerationLoop 已经把占位消息写进状态
                throw failure
            }.onCompletion {
                session.updateConversation(
                    session.state.value.updateCurrentMessages(
                        listOf(
                            UIMessage.user("hi"),
                            UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()),
                        )
                    )
                )
                session.finishGeneration { persisted = it }
            }.collect { }
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf("hi"), persisted.currentMessages.map { it.toText() })
    }

    @Test
    fun `a placeholder emptied by an unrelated generation still cannot touch stored history`() = runBlocking {
        val id = Uuid.random()
        // 老会话里已经存了一条空气泡（本次修复之前落库的遗留数据）。
        val legacyEmpty = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        val question = UIMessage.user("hi")
        var persisted = Conversation.ofId(id).updateCurrentMessages(listOf(legacyEmpty, question))
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        // 一次完全无关的生成：用户又发了一条，助手正常回了一句话。
        val newReply = UIMessage.assistant("brand new reply")
        session.updateConversation(
            session.state.value.updateCurrentMessages(listOf(legacyEmpty, question, newReply))
        )

        session.finishGeneration { persisted = it }

        assertEquals(
            listOf(legacyEmpty.id, question.id, newReply.id),
            persisted.currentMessages.map { it.id },
        )
        assertTrue(persisted.currentMessages.any { it.id == legacyEmpty.id })
    }

    @Test
    fun `a new empty placeholder in the next generation is dropped again`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id)
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        val reply = UIMessage.assistant("first reply")
        session.updateConversation(session.state.value.updateCurrentMessages(listOf(reply)))
        session.finishGeneration { persisted = it }

        // 第二轮生成：占位消息是新的，必须被判掉，而上一轮已落库的回复不能被牵连。
        val secondPlaceholder = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
        session.updateConversation(
            session.state.value.updateCurrentMessages(
                persisted.currentMessages + UIMessage.user("again") + secondPlaceholder
            )
        )
        session.finishGeneration { persisted = it }

        assertEquals(
            listOf("first reply", "again"),
            persisted.currentMessages.map { it.toText() },
        )
    }

    @Test
    fun `direct chat without generation output keeps every message`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id, assistantId = Uuid.random())
        val history = listOf(
            UIMessage.user("hi"),
            UIMessage.assistant("hello"),
        )
        persisted = persisted.updateCurrentMessages(history)
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }

        session.finishGeneration { persisted = it }

        // 单聊（type = direct）：无内容可丢时必须原样落库
        assertEquals(GroupChat.TYPE_DIRECT, persisted.type)
        assertEquals(history, persisted.currentMessages)
        assertEquals(history.map { it.id }, persisted.currentMessages.map { it.id })
    }

    @Test
    fun `group chat rounds keep their ids and only drop the ungenerated placeholder`() = runBlocking {
        val id = Uuid.random()
        var persisted = Conversation.ofId(id).copy(type = GroupChat.TYPE_GROUP)
        val speaker = UIMessage.assistant("speaker line").copy(
            roleId = "role-1",
            roundId = "round-7",
            turnKind = "speaker",
        )
        val placeholder = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()).copy(
            roundId = "round-7",
        )
        val session = ConversationSession(id, Conversation.ofId(id), this, {})
        session.initialize { persisted }
        session.updateConversation(
            session.state.value.updateCurrentMessages(listOf(speaker, placeholder))
        )

        session.finishGeneration { persisted = it }

        val kept = persisted.currentMessages
        assertEquals(1, kept.size)
        assertEquals(speaker, kept.single())
        assertEquals("role-1", kept.single().roleId)
        assertEquals("round-7", kept.single().roundId)
        assertEquals("speaker", kept.single().turnKind)
    }
}