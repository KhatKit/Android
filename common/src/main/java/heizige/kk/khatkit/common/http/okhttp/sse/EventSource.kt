package heizige.kk.khatkit.common.http.okhttp.sse

import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import heizige.kk.khatkit.common.http.okhttp.ByteArrayResponseBody
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.Response
import heizige.kk.khatkit.common.http.okhttp.toCompatHeaders
import heizige.kk.khatkit.common.http.okhttp.toOutgoingContent
import java.io.BufferedReader

/** Ktor 实现的 SSE EventSource，兼容 okhttp-sse 的回调形状。 */
interface EventSource {
    fun request(): Request
    fun cancel()

    fun interface Factory {
        fun newEventSource(request: Request, listener: EventSourceListener): EventSource
    }
}

abstract class EventSourceListener {
    open fun onOpen(eventSource: EventSource, response: Response) {}
    open fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {}
    open fun onClosed(eventSource: EventSource) {}
    open fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {}
}

object EventSources {
    fun createFactory(client: OkHttpClient): EventSource.Factory = EventSource.Factory { request, listener ->
        val actualRequest = if (request.header("Accept") == null) {
            request.newBuilder().addHeader("Accept", "text/event-stream").build()
        } else {
            request
        }
        KtorEventSource(client, actualRequest, listener).also { it.connect() }
    }
}

internal class KtorEventSource(
    private val client: OkHttpClient,
    private val request: Request,
    private val listener: EventSourceListener,
) : EventSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var cancelled = false

    fun connect() {
        job = scope.launch { run() }
    }

    private suspend fun run() {
        try {
            val http = client.httpFor(request.url)
            http.prepareRequest {
                method = HttpMethod.parse(request.method)
                url(request.url.toString())
                request.headers.names().forEach { name ->
                    request.headers.values(name).forEach { value -> header(name, value) }
                }
                request.body?.let { setBody(it.toOutgoingContent()) }
                timeout {
                    requestTimeoutMillis = null
                    socketTimeoutMillis = null
                    connectTimeoutMillis = client.config.connectTimeoutMillis.takeIf { it > 0 }
                }
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    val text = runCatching {
                        response.bodyAsChannel().toInputStream().bufferedReader().readText()
                    }.getOrDefault("")
                    listener.onFailure(
                        this@KtorEventSource,
                        null,
                        Response(
                            request = request,
                            code = response.status.value,
                            message = response.status.description,
                            headers = response.headers.toCompatHeaders(),
                            body = ByteArrayResponseBody(text.toByteArray(Charsets.UTF_8), null),
                        ),
                    )
                    return@execute
                }

                listener.onOpen(
                    this@KtorEventSource,
                    Response(
                        request = request,
                        code = response.status.value,
                        message = response.status.description,
                        headers = response.headers.toCompatHeaders(),
                        body = ByteArrayResponseBody(ByteArray(0), null),
                    ),
                )

                readEvents(response.bodyAsChannel().toInputStream().bufferedReader())
                if (!cancelled) listener.onClosed(this@KtorEventSource)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!cancelled) listener.onFailure(this@KtorEventSource, e, null)
        }
    }

    private fun readEvents(reader: BufferedReader) {
        var lastId: String? = null
        var eventType: String? = null
        val data = StringBuilder()

        fun dispatch() {
            if (data.isEmpty()) return
            val payload = data.toString().removeSuffix("\n")
            listener.onEvent(this, lastId, eventType, payload)
            data.setLength(0)
            eventType = null
        }

        while (!cancelled) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> dispatch()
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> {
                    data.append(line.removePrefix("data:").removePrefix(" ")).append('\n')
                }

                line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                line.startsWith("id:") -> lastId = line.removePrefix("id:").trim()
                line.startsWith("retry:") -> Unit
            }
        }
        dispatch()
    }

    override fun request(): Request = request

    override fun cancel() {
        cancelled = true
        job?.cancel()
    }
}
