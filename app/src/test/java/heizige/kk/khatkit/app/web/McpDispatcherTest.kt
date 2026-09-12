package heizige.kk.khatkit.web

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpDispatcherTest {

    private class FakeHost : McpToolHost {
        override suspend fun listTools(): List<McpToolDescriptor> = listOf(
            McpToolDescriptor(
                name = "khatkit__demo",
                description = "demo card",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                },
            )
        )

        override suspend fun callTool(name: String, arguments: JsonObject): McpToolResult =
            McpToolResult("ok:$name")
    }

    private fun reply(raw: String, host: McpToolHost? = FakeHost()): JsonObject {
        val outcome = runBlocking { McpDispatcher(host).dispatch(raw) }
        return (outcome as McpDispatcher.Outcome.Reply).payload
    }

    @Test
    fun initializeEchoesProtocolVersion() {
        val payload = reply(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}"""
        )
        val result = payload["result"]!!.jsonObject
        assertEquals("2025-03-26", result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("khatkit", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolsListReturnsCards() {
        val payload = reply("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        val tools = payload["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertEquals("khatkit__demo", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolsCallReturnsContent() {
        val payload = reply(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"khatkit__demo","arguments":{}}}"""
        )
        val result = payload["result"]!!.jsonObject
        assertEquals(false, result["isError"]!!.jsonPrimitive.content.toBoolean())
        val text = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("ok:khatkit__demo", text)
    }

    @Test
    fun unknownMethodReturnsError() {
        val payload = reply("""{"jsonrpc":"2.0","id":4,"method":"resources/list"}""")
        assertEquals(-32601, payload["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun notificationIsAccepted() {
        val outcome = runBlocking {
            McpDispatcher(FakeHost()).dispatch("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }
        assertTrue(outcome is McpDispatcher.Outcome.Accepted)
    }

    @Test
    fun parseErrorReturnsJsonRpcError() {
        val payload = reply("not-json")
        assertEquals(-32700, payload["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun missingHostReturnsUnavailable() {
        val payload = reply("""{"jsonrpc":"2.0","id":5,"method":"tools/list"}""", host = null)
        assertEquals(-32000, payload["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    }
}
