package heizige.kk.khatkit.ai.provider.providers.openai

import com.sun.net.httpserver.HttpServer
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.stream.SseEvent
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.StreamChunkHandler
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.util.HttpException
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.sse.EventSources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * responses API 路径「非 2xx + 空 body / 乱码 body 被静默当成零产出成功」的回归测试。
 *
 * 与 `ChatCompletionsStreamFailureTest` 同源：`KtorEventSource` 对非 2xx **固定**以
 * `t = null` 回调 `onFailure`，错误信息只能来自响应体；响应体为空或无法解析时
 * `exception` 保持 null，原样 `close(null)` 会让 `callbackFlow` 按正常完成收场。
 *
 * 这些用例用真实的本地 HTTP 服务 + 真实的 [EventSources]（KtorEventSource）+
 * 真实的监听器（[responseApiStreamListener]）驱动，断言最终传给 `closeFlow` 的值。
 */
class ResponseApiStreamFailureTest {

    private lateinit var server: HttpServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    /** 在本地服务注册一个固定状态码/响应体的上下文。 */
    private fun serve(path: String, code: Int, body: String = "") {
        server.createContext(path) { exchange ->
            exchange.responseHeaders.add("Connection", "close")
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(code, -1)
            } else {
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
    }

    /**
     * 用真实的 [EventSources]（KtorEventSource）驱动真实的 [responseApiStreamListener]，
     * 返回最终传给 `closeFlow` 的异常。返回 null 表示这次失败被当作正常完成。
     */
    private fun driveListener(path: String): Throwable? = runBlocking {
        val closed = CompletableDeferred<Throwable?>()
        val listener = responseApiStreamListener(
            decoder = ResponseApiStreamDecoder(),
            sendChunks = { },
            closeFlow = { closed.complete(it) },
        )
        val request = Request.Builder()
            .url("http://127.0.0.1:${server.address.port}$path")
            .build()
        EventSources.createFactory(client).newEventSource(request, listener)
        withTimeout(10_000) { closed.await() }
    }

    @Test
    fun `non 2xx with blank body must close with non null exception`() {
        serve("/blank-429", 429)
        val failure = driveListener("/blank-429")

        assertNotNull("非 2xx + 空 body 绝不能以 close(null) 静默收场", failure)
    }

    @Test
    fun `non 2xx blank body fallback message must carry http status code`() {
        serve("/blank-502", 502)
        val failure = driveListener("/blank-502")

        assertNotNull("非 2xx + 空 body 必须产生异常", failure)
        val message = failure!!.message.orEmpty()
        assertTrue("兜底异常信息应含 HTTP 字样，实际：$message", message.contains("HTTP 502"))
    }

    @Test
    fun `non 2xx with json body must keep existing parsed detail`() {
        serve("/json-500", 500, """{"error":{"message":"upstream exploded"}}""")
        val failure = driveListener("/json-500")

        assertNotNull("非 2xx + 非空 body 也必须产生异常", failure)
        assertTrue(
            "既有解析结果应保持为 HttpException，实际：${failure!!::class.java.name}",
            failure is HttpException,
        )
        assertEquals("既有 ErrorParser 解析出的 detail 必须原样保留", "upstream exploded", failure.message)
        assertFalse(
            "非空 body 的错误信息不应被兜底串覆盖，实际：${failure.message}",
            failure.message.orEmpty().contains("HTTP 500"),
        )
    }

    @Test
    fun `non 2xx with unparseable body must still close with non null exception`() {
        serve("/garbled-500", 500, "<html>bad gateway</html>")
        val failure = driveListener("/garbled-500")

        assertNotNull("非 2xx + 乱码 body 也绝不能以 close(null) 静默收场", failure)
        assertTrue(
            "乱码 body 的兜底异常应含 HTTP 状态码，实际：${failure!!.message}",
            failure.message.orEmpty().contains("HTTP 500"),
        )
    }

    @Test
    fun `2xx blank stream must stay a normal completion`() {
        serve("/ok-empty", 200)
        val failure = driveListener("/ok-empty")

        assertNull("2xx 空流必须保持现状语义（正常完成，而非报错），实际异常：$failure", failure)
    }

    @Test
    fun `2xx sse stream still yields text chunks`() {
        val decoder = ResponseApiStreamDecoder()
        val handler = StreamChunkHandler(Model())
        var messages = listOf(UIMessage.user("hi"))
        val chunks = mutableListOf<StreamChunk>()

        for (event in rawResponseFrames()) {
            val result = decoder.accept(event)
            chunks += result.chunks
            result.chunks.forEach { messages = handler.handle(messages, it) }
            if (result.completed) break
        }

        assertTrue("2xx 正常流应产出 chunk，实际 ${chunks.size} 个", chunks.isNotEmpty())
        val text = messages.last().parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { it.text }
        assertEquals("2xx 正常流的正文必须逐字保留", "你好", text)
    }

    /** 对齐真实 responses 流的 raw 帧：输出文本 delta → 文本 done → completed。 */
    private fun rawResponseFrames(): List<SseEvent> = listOf(
        SseEvent(data = """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"content_index":0,"delta":"你好"}"""),
        SseEvent(data = """{"type":"response.output_text.done","item_id":"msg_1","output_index":0,"content_index":0}"""),
        SseEvent(data = """{"type":"response.completed","response":{"id":"resp_1","model":"test-model","status":"completed"}}"""),
    )
}
