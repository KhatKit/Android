package heizige.kk.khatkit.bridge

import com.sun.net.httpserver.HttpServer
import heizige.kk.khatkit.bridge.impl.AndroidNetBridge
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class AndroidNetRequestTest {
    private fun withServer(block: (HttpServer, HttpClient, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        HttpClient(CIO) { expectSuccess = true }.use { client ->
            try {
                block(server, client, "http://127.0.0.1:${server.address.port}")
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun preservesRepeatedCookiesBodyAndErrorStatus() = withServer { server, client, base ->
        server.createContext("/api") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("""{"pwd_id":"share"}""", exchange.requestBody.reader().readText())
            assertEquals("session=one", exchange.requestHeaders.getFirst("Cookie"))
            exchange.responseHeaders.add("Set-Cookie", "__pugs=guest; Path=/; HttpOnly")
            exchange.responseHeaders.add("Set-Cookie", "__puus=refreshed; Path=/")
            val bytes = """{"code":31001}""".toByteArray()
            exchange.sendResponseHeaders(403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val bridge = AndroidNetBridge(client, setOf("127.0.0.1"))
        val result = bridge.request("$base/api", "POST", """{"pwd_id":"share"}""", mapOf("Cookie" to "session=one"))
        assertEquals(403, result["status"])
        assertEquals("""{"code":31001}""", result["body"])
        val headers = result["headers"] as Map<*, *>
        assertEquals(2, (headers["set-cookie"] as List<*>).size)
    }

    @Test
    fun doesNotFollowRedirectsOrForwardCredentials() = withServer { server, client, base ->
        val targetCalls = AtomicInteger()
        server.createContext("/redirect") {
            it.responseHeaders.add("Location", "$base/target")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.createContext("/target") {
            targetCalls.incrementAndGet()
            it.sendResponseHeaders(200, -1)
            it.close()
        }
        val result = AndroidNetBridge(client, setOf("127.0.0.1")).request(
            "$base/redirect", "GET", "", mapOf("Cookie" to "private=value"),
        )
        assertEquals(302, result["status"])
        assertEquals(0, targetCalls.get())
    }

    @Test
    fun rejectsOversizedResponsesAndKeepsParentClientUsable() = withServer { server, client, base ->
        server.createContext("/large") {
            val bytes = ByteArray(192 * 1024 + 1) { 65 }
            it.sendResponseHeaders(200, bytes.size.toLong())
            it.responseBody.use { out -> out.write(bytes) }
        }
        server.createContext("/small") {
            it.sendResponseHeaders(200, 2)
            it.responseBody.use { out -> out.write("ok".toByteArray()) }
        }
        val bridge = AndroidNetBridge(client, setOf("127.0.0.1"))
        assertThrows(IllegalArgumentException::class.java) {
            bridge.request("$base/large", "GET", "", emptyMap())
        }
        assertEquals("ok", bridge.request("$base/small", "GET", "", emptyMap())["body"])
    }

    @Test
    fun scopedRequestUsesCardAllowlistInsteadOfGlobalAllowlist() = withServer { _, client, base ->
        val scoped = ScopedNetBridge(
            BridgeContext("yunx", "wasm", 5, networkAllow = emptySet()),
            AndroidNetBridge(client, setOf("*")),
        )
        assertThrows(SecurityException::class.java) {
            scoped.request(base, "GET", "", emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidNetBridge(client, setOf("*")).request("file:///tmp/private", "GET", "", emptyMap())
        }
    }
}
