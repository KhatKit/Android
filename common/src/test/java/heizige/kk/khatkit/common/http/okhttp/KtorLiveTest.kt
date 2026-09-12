package heizige.kk.khatkit.common.http.okhttp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import heizige.kk.khatkit.common.http.SseEvent
import heizige.kk.khatkit.common.http.sseFlow
import heizige.kk.khatkit.common.http.okhttp.sse.EventSource
import heizige.kk.khatkit.common.http.okhttp.sse.EventSourceListener
import heizige.kk.khatkit.common.http.okhttp.sse.EventSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 真实 API 的 live 冒烟测试：验证 Ktor 兼容层（JSON / SSE / sseFlow）。
 *
 * 需要环境变量 KHATKIT_LIVE_BASE_URL / KHATKIT_LIVE_API_KEY（可选 KHATKIT_LIVE_MODEL），
 * 未提供时整体跳过，不影响常规 CI。
 */
class KtorLiveTest {

    private val baseUrl: String = property("KHATKIT_LIVE_BASE_URL").trimEnd('/')
    private val apiKey: String = property("KHATKIT_LIVE_API_KEY")
    private val model: String = property("KHATKIT_LIVE_MODEL").ifBlank { "deepseek-flash" }

    private fun property(key: String): String =
        System.getProperty(key) ?: System.getenv(key).orEmpty()

    private fun enabled() = assumeTrue(baseUrl.isNotBlank() && apiKey.isNotBlank())

    private fun client() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): Pair<String, String> = "Authorization" to "Bearer $apiKey"

    private fun chatPayload(stream: Boolean) = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", "只回复两个字：你好")
                }
            )
        }
        put("stream", stream)
        put("max_tokens", 128)
    }

    @Test
    fun listModels() = runBlocking {
        enabled()
        val response = client().newCall(
            Request.Builder()
                .url("$baseUrl/models")
                .addHeader(authHeader().first, authHeader().second)
                .build()
        ).await()

        assertEquals(200, response.code)
        val body = response.body.string()
        assertTrue("models response should contain data", body.contains("\"data\""))
    }

    @Test
    fun chatCompletionNonStreaming() = runBlocking {
        enabled()
        val response = client().newCall(
            Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader(authHeader().first, authHeader().second)
                .post(chatPayload(stream = false).toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).await()

        assertEquals(200, response.code)
        val message = Json.parseToJsonElement(response.body.string())
            .jsonObject["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!
            .jsonObject
        val text = buildString {
            append(message["content"]?.jsonPrimitive?.contentOrNull.orEmpty())
            append(message["reasoning_content"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
        assertTrue("response should contain text", text.isNotBlank())
    }

    @Test
    fun chatCompletionStreamingViaSse() = runBlocking {
        enabled()
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader(authHeader().first, authHeader().second)
            .post(chatPayload(stream = true).toString().toRequestBody("application/json".toMediaType()))
            .build()

        val events = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                events += data
                if (data.contains("[DONE]")) done.complete(Unit)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                done.completeExceptionally(t ?: RuntimeException("SSE failed: HTTP ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                done.complete(Unit)
            }
        }

        EventSources.createFactory(client()).newEventSource(request, listener)
        withTimeout(60_000) { done.await() }

        assertTrue("should receive SSE chunks", events.isNotEmpty())
        assertTrue("should receive content deltas", events.any { it.contains("content") })
    }

    @Test
    fun geminiStreamingViaSse() = runBlocking {
        val geminiBaseUrl = property("KHATKIT_LIVE_GEMINI_BASE_URL").trimEnd('/')
        val geminiKey = property("KHATKIT_LIVE_GEMINI_API_KEY")
        val geminiModel = property("KHATKIT_LIVE_GEMINI_MODEL").ifBlank { "gemini-3-flash-preview" }
        assumeTrue(geminiBaseUrl.isNotBlank() && geminiKey.isNotBlank())

        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", "只回复两个字：你好") }) }
                    }
                )
            }
        }
        val request = Request.Builder()
            .url("$geminiBaseUrl/v1beta/models/$geminiModel:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", geminiKey)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val received = mutableListOf<String>()
        withTimeout(60_000) {
            client().sseFlow(request)
                .takeWhile { event ->
                    when (event) {
                        is SseEvent.Event -> {
                            received += event.data
                            !event.data.contains("\"text\"")
                        }

                        is SseEvent.Failure -> error("SSE failure: ${event.throwable}")
                        else -> true
                    }
                }
                .toList()
        }
        assertTrue(
            "gemini SSE should deliver candidates",
            received.any { it.contains("\"candidates\"") },
        )
    }

    @Test
    fun chatCompletionStreamingViaSseFlow() = runBlocking {
        enabled()
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader(authHeader().first, authHeader().second)
            .post(chatPayload(stream = true).toString().toRequestBody("application/json".toMediaType()))
            .build()

        val received = mutableListOf<String>()
        withTimeout(60_000) {
            client().sseFlow(request)
                .takeWhile { event ->
                    when (event) {
                        is SseEvent.Event -> {
                            received += event.data
                            !event.data.contains("[DONE]")
                        }

                        is SseEvent.Failure -> error("SSE failure: ${event.throwable}")
                        else -> true
                    }
                }
                .toList()
        }
        assertTrue("sseFlow should receive chunks", received.isNotEmpty())
    }
}
