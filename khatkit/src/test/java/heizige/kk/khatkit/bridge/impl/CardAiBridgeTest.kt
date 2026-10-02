package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.AiBridge
import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.BridgeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CardAiBridgeTest {

    private val approvals = mutableListOf<Triple<String, String, String>>()
    private val calls = mutableListOf<AiChatRequest>()

    private fun bridge(
        permissions: Map<String, String> = emptyMap(),
        gate: ApprovalGate? = ApprovalGate { title, detail, category ->
            approvals += Triple(title, detail, category)
            true
        },
        deadlineAt: Long = 0L,
        engine: AiEngine = AiEngine { request -> calls += request; "已回复" },
    ): AiBridge = CardAiBridge(engine).forRun(
        BridgeContext(
            cardName = "翻译卡片",
            engine = "lua",
            quotaMb = 0,
            permissions = permissions,
            approvalGate = gate,
            deadlineAt = deadlineAt,
        ),
    )

    private fun AiBridge.ask(
        prompt: String = "hi",
        system: String = "",
        provider: String = "",
        model: String = "",
        imagePaths: List<String> = emptyList(),
        maxTokens: Int = 0,
        temperature: Double = 0.0,
        timeoutSeconds: Int = 60,
    ): String = chat(prompt, system, provider, model, imagePaths, maxTokens, temperature, timeoutSeconds)

    @Test
    fun `allow skips the gate and passes normalized params`() {
        val result = bridge(permissions = mapOf("ai.chat" to "allow")).ask(
            prompt = "翻译：hello",
            system = "你是翻译",
            provider = " OpenAI ",
            model = " gpt-4o ",
            imagePaths = listOf("/a.png"),
            maxTokens = 256,
            temperature = 0.3,
            timeoutSeconds = 30,
        )

        assertEquals("已回复", result)
        assertTrue(approvals.isEmpty())
        val request = calls.single()
        assertEquals("翻译卡片", request.cardName)
        assertEquals("翻译：hello", request.prompt)
        assertEquals("你是翻译", request.system)
        assertEquals("OpenAI", request.provider)
        assertEquals("gpt-4o", request.model)
        assertEquals(listOf("/a.png"), request.imagePaths)
        assertEquals(256, request.maxTokens)
        assertEquals(0.3, request.temperature, 1e-6)
        assertEquals(30_000L, request.timeoutMs)
    }

    @Test
    fun `undeclared defaults to ask with actionable detail`() {
        bridge().ask(model = "gpt-4o")

        val (title, detail, category) = approvals.single()
        assertEquals("卡片调用模型：翻译卡片", title)
        assertTrue(detail.contains("翻译卡片"))
        assertTrue(detail.contains("gpt-4o"))
        assertEquals("ai_invoke", category)
        assertEquals(1, calls.size)
    }

    @Test
    fun `deny rejects without asking and without calling the engine`() {
        val error = errorOf { bridge(permissions = mapOf("ai.chat" to "deny")).ask() }
        assertTrue(error.contains("拒绝"))
        assertTrue(approvals.isEmpty())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `missing gate treats ask as denied`() {
        val error = errorOf { bridge(gate = null).ask() }
        assertTrue(error.contains("拒绝"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `complete goes through the same policy with empty system`() {
        val result = bridge(permissions = mapOf("ai.chat" to "allow")).complete("补全", 64)
        assertEquals("已回复", result)
        val request = calls.single()
        assertEquals("补全", request.prompt)
        assertEquals("", request.system)
        assertEquals(64, request.maxTokens)
        assertEquals(120, request.timeoutSeconds)
    }

    @Test
    fun `ai_complete declaration wins over ai chat`() {
        val denied = errorOf {
            bridge(permissions = mapOf("ai.chat" to "allow", "ai.complete" to "deny")).complete("补全", 0)
        }
        assertTrue(denied.contains("拒绝"))

        val allowed = bridge(permissions = mapOf("ai.chat" to "deny", "ai.complete" to "allow")).complete("补全", 0)
        assertEquals("已回复", allowed)
        assertTrue(approvals.isEmpty())
    }

    @Test
    fun `blank prompt is rejected before asking`() {
        val error = errorOf { bridge().ask(prompt = "  ") }
        assertTrue(error.contains("prompt"))
        assertTrue(approvals.isEmpty())
    }

    @Test
    fun `timeout is clamped by the remaining run budget`() {
        bridge(permissions = mapOf("ai.chat" to "allow"), deadlineAt = System.currentTimeMillis() + 5_000)
            .ask(timeoutSeconds = 600)

        val timeoutMs = calls.single().timeoutMs
        assertTrue("应取剩余预算：$timeoutMs", timeoutMs in 1..5_000)
    }

    @Test
    fun `expired deadline stops the call`() {
        val error = errorOf {
            bridge(permissions = mapOf("ai.chat" to "allow"), deadlineAt = System.currentTimeMillis() - 1).ask()
        }
        assertTrue(error.contains("超时"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `unbound bridge reports the missing provider configuration`() {
        val error = errorOf { CardAiBridge(AiEngine { "x" }).ask() }
        assertTrue(error.contains("尚未接入"))
    }

    private fun errorOf(block: () -> Unit): String = try {
        block()
        fail("应当抛中文错误")
        ""
    } catch (e: Exception) {
        e.message.orEmpty()
    }
}
