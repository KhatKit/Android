package heizige.kk.khatkit.common.http.okhttp.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

/**
 * Codex（chatgpt.com/backend-api/codex）的事件流不带 Content-Type，上游为此内置了自己的 SSE 实现。
 * 本仓传输层不校验 Content-Type，解析结果由本用例锁定形状：event 名 + data 原样交给上层 decoder。
 */
class ServerSentEventReaderTest {

    private data class Frame(val id: String?, val type: String?, val data: String)

    private fun parse(raw: String): List<Frame> {
        val frames = mutableListOf<Frame>()
        ServerSentEventReader.read(
            reader = BufferedReader(StringReader(raw)),
            isCancelled = { false },
            onDispatch = { id, type, data -> frames += Frame(id, type, data) },
        )
        return frames
    }

    @Test
    fun `parses codex response frames without content type`() {
        // 摘自真实 /backend-api/codex/responses 响应（省略 response 对象里的细节）
        val raw = """
            event: response.created
            data: {"type":"response.created","sequence_number":0,"response":{"id":"resp_1","status":"in_progress"}}

            event: response.output_item.added
            data: {"type":"response.output_item.added","sequence_number":2,"output_index":0,"item":{"type":"message","id":"msg_1"}}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","sequence_number":3,"item_id":"msg_1","delta":"你好"}

            event: response.completed
            data: {"type":"response.completed","sequence_number":9,"response":{"id":"resp_1","status":"completed"}}

        """.trimIndent() + "\n"

        val frames = parse(raw)
        assertEquals(4, frames.size)
        assertEquals("response.created", frames[0].type)
        assertTrue(frames[0].data.contains("\"resp_1\""))
        assertEquals("response.output_text.delta", frames[2].type)
        assertTrue(frames[2].data.contains("\"你好\""))
        assertEquals("response.completed", frames[3].type)
    }

    @Test
    fun `keeps literal null data for upper layer tolerance`() {
        // 部分中转会返回 data: null，上层（ResponseApiStreamDecoder）负责跳过而不是崩溃
        val frames = parse("data: null\n\n")
        assertEquals(1, frames.size)
        assertEquals("null", frames[0].data)
        assertEquals(null, frames[0].type)
    }

    @Test
    fun `joins multiline data and ignores comments plus retry`() {
        val raw = """: ping
            retry: 3000
            id: 42
            event: response.output_text.delta
            data: line-1
            data: line-2

        """.trimIndent() + "\n"

        val frames = parse(raw)
        assertEquals(1, frames.size)
        assertEquals("42", frames[0].id)
        assertEquals("response.output_text.delta", frames[0].type)
        assertEquals("line-1\nline-2", frames[0].data)
    }

    @Test
    fun `flushes trailing frame without blank line`() {
        val frames = parse("event: response.completed\ndata: {}\n")
        assertEquals(1, frames.size)
        assertEquals("response.completed", frames[0].type)
        assertEquals("{}", frames[0].data)
    }
}
