package heizige.kk.khatkit.ai.provider.providers.openai

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.TextGenerationResult
import heizige.kk.khatkit.ai.provider.stream.SseEvent
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.StreamChunkHandler
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.handleTextGenerationResult
import heizige.kk.khatkit.ai.util.json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「模型名必须是 wire 级证据」的回归测试。
 *
 * 背景：解码器早就能从 SSE 字节里取出 `model` 字段（[ChatCompletionsStreamDecoder]
 * 的 `responseModel` → `StreamChunk.Finish.model`），但合并层一度只用了
 * `finishedAt`，把 `Finish.model` 扔掉了。结果一条助手消息上留不下网关自报的模型名，
 * 验收「实际模型调用序列」时只能拿本地 `modelId`（配置 UUID）回查 provider 模型表反查，
 * 那不是 wire 级证据。
 *
 * 这些用例全部走真实链路：手写的 raw SSE 字节 → 真实的 [ChatCompletionsStreamDecoder]
 * → 真实的 [StreamChunkHandler] → [UIMessage.wireModelName]，中间没有替身。
 */
class WireModelNameProvenanceTest {
    /** 刻意刁钻的模型名字面量：带日期后缀，验证不做任何归一化。 */
    private val trickyWireModel = "deepseek-v4-flash-250528"

    /** 第二个刁钻字面量：大写、点号、斜杠、`-preview` 后缀。 */
    private val secondTrickyWireModel = "zhengyimeng/GLM-5.2-preview"

    /** 本地模型配置里的名字，刻意与 wire 上的完全不同。 */
    private val unrelatedLocalAlias = "local-cfg-alias-9f3c"

    // ---------------------------------------------------------------- 流式链路

    @Test
    fun `raw sse model literal should land on wireModelName verbatim`() {
        val stream = runStream(trickyWireModel, Model())

        // 解码器这一跳就已经是 wire 上的原样字符串了。
        assertEquals(trickyWireModel, stream.finish?.model)
        // 合并层这一跳没有把它改写成任何东西。
        assertEquals(trickyWireModel, stream.assistant.wireModelName)
        // 逐字相等：没有被 trim、小写化或按别名归一。
        assertEquals(trickyWireModel, stream.assistant.wireModelName?.trim())
    }

    @Test
    fun `wire model name should survive dotted slashed and uppercase literal unchanged`() {
        val stream = runStream(secondTrickyWireModel, Model())

        assertEquals(secondTrickyWireModel, stream.finish?.model)
        // 逐字断言，含斜杠、点号与大写全部原样保留。
        assertEquals("zhengyimeng/GLM-5.2-preview", stream.assistant.wireModelName)
    }

    /**
     * 反查隔离：即使本地模型配置的名字与 wire 上完全不同，落到消息上的也必须是 wire 那个。
     * 如果代码是拿 `modelId`(UUID) 回查本地模型表反查模型名，这里必然挂。
     */
    @Test
    fun `wire model name should not be derived from local model configuration`() {
        val localModel = Model(
            modelId = unrelatedLocalAlias,
            displayName = "本地别名·完全无关",
        )
        val stream = runStream(trickyWireModel, localModel)

        // 仍然逐字等于 SSE 里的字面量。
        assertEquals(trickyWireModel, stream.assistant.wireModelName)
        // 本地配置仍然只影响 modelId（UUID），两条路径互不串味。
        assertEquals(localModel.id, stream.assistant.modelId)
        // 明确排除「wire 模型名其实来自本地配置」的可能。
        assertNotEquals(localModel.id.toString(), stream.assistant.wireModelName)
        assertNotEquals(localModel.modelId, stream.assistant.wireModelName)
        assertNotEquals(localModel.displayName, stream.assistant.wireModelName)
    }

    @Test
    fun `sse frames without model field should leave wireModelName null`() {
        val stream = runStream(null, Model())

        // 帧里没有 model，解码器就没有可上报的名字。
        assertNull(stream.finish?.model)
        // 既不是空串，也没有回退到本地 modelId。
        assertNull(stream.assistant.wireModelName)
        // 流本身仍然是正常完成的，说明 null 表示「网关没报」而不是「流坏了」。
        assertEquals("stop", stream.finish?.finishReason)
        assertEquals("chatcmpl-wire-1", stream.finish?.responseId)
    }

    @Test
    fun `absent model in sse should not overwrite an existing wire model name`() {
        val seed = listOf(
            UIMessage.user("hi"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                wireModelName = secondTrickyWireModel,
            ),
        )
        val stream = runStream(null, Model(), seed)

        // 缺失的 model 不覆盖已有值（允许 null 传播，不静默清空）。
        assertNull(stream.finish?.model)
        assertEquals(secondTrickyWireModel, stream.assistant.wireModelName)
    }

    // ------------------------------------------------------------ 非流式对称链路

    @Test
    fun `non streaming result should set wireModelName symmetrically`() {
        val localModel = Model(modelId = unrelatedLocalAlias)
        val result = TextGenerationResult(
            id = "chatcmpl-nonstream-1",
            model = secondTrickyWireModel,
            message = UIMessage.assistant("你好"),
            finishReason = "stop",
        )
        val messages = listOf(UIMessage.user("hi")).handleTextGenerationResult(result, localModel)

        // 与流式路径对称：非流式也把 result.model 原样落盘。
        assertEquals(secondTrickyWireModel, messages.last().wireModelName)
        assertEquals("zhengyimeng/GLM-5.2-preview", messages.last().wireModelName)
        // 本地配置路径依然只写 modelId，两条路径不串味。
        assertEquals(localModel.id, messages.last().modelId)
        assertNotEquals(localModel.modelId, messages.last().wireModelName)
    }

    @Test
    fun `non streaming empty model string should not be treated as a valid model name`() {
        // ChatCompletionsAPI 的非流式解析用 `?: ""` 兜底，空串必须按缺失处理。
        val result = TextGenerationResult(
            id = "chatcmpl-nonstream-2",
            model = "",
            message = UIMessage.assistant("你好"),
            finishReason = "stop",
        )
        val messages = listOf(UIMessage.user("hi")).handleTextGenerationResult(result, Model())

        assertNull(messages.last().wireModelName)
    }

    // -------------------------------------------------------------- 持久化往返

    @Test
    fun `wireModelName should survive kotlinx serialization round trip`() {
        val original = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = emptyList(),
            wireModelName = secondTrickyWireModel,
        )
        val encoded = json.encodeToString(original)

        // 真的进了对话 JSON，而不是只活在内存里。
        assertTrue(encoded.contains("wireModelName"))
        assertEquals(
            secondTrickyWireModel,
            json.parseToJsonElement(encoded).jsonObject["wireModelName"].toString().trim('"'),
        )

        val decoded = json.decodeFromString<UIMessage>(encoded)
        assertEquals(secondTrickyWireModel, decoded.wireModelName)
    }

    @Test
    fun `legacy json without wireModelName key should decode to null without throwing`() {
        // 构造「旧版本序列化器写出来的数据」：当前编码结果里删掉 wireModelName 键。
        val currentJson = json.encodeToString(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                wireModelName = secondTrickyWireModel,
            )
        )
        val legacyJson = json.encodeToString(
            json.parseToJsonElement(currentJson).jsonObject.minus("wireModelName")
        )
        assertTrue(!legacyJson.contains("wireModelName"))

        // 旧数据不抛异常，字段为 null。
        val decoded = json.decodeFromString<UIMessage>(legacyJson)
        assertNull(decoded.wireModelName)
        assertEquals(MessageRole.ASSISTANT, decoded.role)
    }

    // -------------------------------------------------------------------- 脚手架

    private class DecodedStream(
        val assistant: UIMessage,
        val finish: StreamChunk.Finish?,
    )

    /**
     * 走完整链路：raw SSE 字节 → 真实解码器 → 真实合并层。
     *
     * @param wireModel 写进 SSE 帧顶层 `model` 字段的字面量；null 表示帧里不带该字段。
     */
    private fun runStream(
        wireModel: String?,
        model: Model,
        seed: List<UIMessage> = listOf(UIMessage.user("hi")),
    ): DecodedStream {
        val raw = rawSseBytes(
            // 首帧：role + 空 content，真实网关就是这样起头的。
            chunkFrame(wireModel, """{"role":"assistant","content":""}"""),
            // 中间帧：真正的文本增量。
            chunkFrame(wireModel, """{"content":"你好"}"""),
            // 末帧：空 delta + finish_reason。解码器在 [DONE] 时才吐出 Finish。
            chunkFrame(wireModel, "{}", finishReason = "stop"),
        )

        val decoder = ChatCompletionsStreamDecoder()
        val handler = StreamChunkHandler(model)
        var messages = seed
        var finish: StreamChunk.Finish? = null

        for (event in sseFrames(raw)) {
            val result = decoder.accept(event)
            for (chunk in result.chunks) {
                if (chunk is StreamChunk.Finish) finish = chunk
                messages = handler.handle(messages, chunk)
            }
            if (result.completed) break
        }

        return DecodedStream(messages.last(), finish)
    }

    /**
     * 拼出手写的 raw SSE 字节：每帧 `data: {json}\n\n`，末帧 `data: [DONE]\n\n`。
     * 对齐真实 OpenAI chat-completions 流格式。
     */
    private fun rawSseBytes(vararg frames: String): String =
        frames.joinToString(separator = "", postfix = "data: [DONE]\n\n") { "data: $it\n\n" }

    /** 拼出单条 choice 的 `chat.completion.chunk` 帧体（不含 `data:` 前缀）。 */
    private fun chunkFrame(
        wireModel: String?,
        delta: String,
        finishReason: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.append("""{"id":"chatcmpl-wire-1","object":"chat.completion.chunk","created":1700000000""")
        if (wireModel != null) sb.append(""","model":"""").append(wireModel).append('"')
        sb.append(""","choices":[{"index":0,"delta":""").append(delta)
        if (finishReason != null) sb.append(""","finish_reason":"""").append(finishReason).append('"')
        sb.append("}]}")
        return sb.toString()
    }

    /** 按 SSE 分帧规则把 raw 字节切成解码器能吃的 [SseEvent]。 */
    private fun sseFrames(raw: String): List<SseEvent> = raw
        .split("\n\n")
        .filter { it.isNotBlank() }
        .map { block ->
            val data = block.lineSequence()
                .filter { it.startsWith("data:") }
                .joinToString("\n") { it.removePrefix("data:").removePrefix(" ") }
            SseEvent(data = data)
        }
}