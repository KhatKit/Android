package heizige.kk.khatkit.common.http.okhttp

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Ktor WebSocket 的 OkHttp 兼容接口。 */
interface WebSocket {
    fun request(): Request
    fun send(text: String): Boolean
    fun send(bytes: ByteArray): Boolean
    fun close(code: Int, reason: String?): Boolean
    fun cancel()

    /** 兼容 OkHttp：Ktor 实现同步写帧，队列恒为 0。 */
    fun queueSize(): Long = 0L
}

abstract class WebSocketListener {
    open fun onOpen(webSocket: WebSocket, response: Response) {}
    open fun onMessage(webSocket: WebSocket, text: String) {}
    open fun onMessage(webSocket: WebSocket, bytes: ByteArray) {}
    open fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
    open fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
    open fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
}

internal class KtorWebSocket(
    private val client: OkHttpClient,
    private val request: Request,
    private val listener: WebSocketListener,
) : WebSocket {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val session = CompletableDeferred<DefaultClientWebSocketSession>()

    fun connect() {
        job = scope.launch {
            try {
                val http = client.httpFor(request.url)
                val wsUrl = request.url.toString()
                    .replaceFirst("http://", "ws://")
                    .replaceFirst("https://", "wss://")
                val active = http.webSocketSession {
                    url(wsUrl)
                    request.headers.names().forEach { name ->
                        request.headers.values(name).forEach { value -> header(name, value) }
                    }
                }
                session.complete(active)
                listener.onOpen(
                    this@KtorWebSocket,
                    Response(request, 101, "Switching Protocols", Headers.of(), ByteArrayResponseBody(ByteArray(0), null)),
                )
                for (frame in active.incoming) {
                    when (frame) {
                        is Frame.Text -> listener.onMessage(this@KtorWebSocket, frame.readText())
                        is Frame.Binary -> listener.onMessage(this@KtorWebSocket, frame.data)
                        is Frame.Close -> {
                            val reason = frame.readReason()
                            listener.onClosed(
                                this@KtorWebSocket,
                                reason?.code?.toInt() ?: 1000,
                                reason?.message.orEmpty(),
                            )
                            break
                        }

                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                listener.onFailure(this@KtorWebSocket, e, null)
            }
        }
    }

    override fun request(): Request = request

    override fun send(text: String): Boolean = sendFrame(Frame.Text(text))

    override fun send(bytes: ByteArray): Boolean = sendFrame(Frame.Binary(true, bytes))

    private fun sendFrame(frame: Frame): Boolean {
        val active = session.getCompletedOrNull() ?: return false
        return runCatching {
            runBlocking { active.send(frame) }
            true
        }.getOrDefault(false)
    }

    override fun close(code: Int, reason: String?): Boolean {
        val active = session.getCompletedOrNull() ?: return false
        return runCatching {
            runBlocking { active.close(CloseReason(code.toShort(), reason.orEmpty())) }
            true
        }.getOrDefault(false)
    }

    override fun cancel() {
        job?.cancel()
    }
}

private fun <T> CompletableDeferred<T>.getCompletedOrNull(): T? =
    if (isCompleted) runCatching { getCompleted() }.getOrNull() else null
