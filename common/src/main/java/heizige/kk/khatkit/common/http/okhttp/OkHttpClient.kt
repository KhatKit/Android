package heizige.kk.khatkit.common.http.okhttp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * Ktor(CIO) 实现的 OkHttp 兼容客户端。
 *
 * - 代理按请求解析：代理变化时重建底层 Ktor client（对应 OkHttp 的动态 ProxySelector）
 * - 拦截器支持同步 Chain，挂在阻塞执行路径上
 * - SSE/WebSocket 走 Ktor 原生实现
 */
class OkHttpClient private constructor(internal val config: Config) {

    /** 默认客户端（测试与轻量调用用）。 */
    constructor() : this(Config())

    class Config {
        var connectTimeoutMillis: Long = 10_000
        var readTimeoutMillis: Long = 10_000
        var writeTimeoutMillis: Long = 10_000
        var callTimeoutMillis: Long = 0
        var followRedirects: Boolean = true
        var followSslRedirects: Boolean = true
        var retryOnConnectionFailure: Boolean = true
        var proxy: Proxy? = null
        var proxySelector: ProxySelector? = null
        var proxyAuthenticator: Authenticator? = null
        var interceptors: List<Interceptor> = emptyList()
        var networkInterceptors: List<Interceptor> = emptyList()
    }

    class Builder internal constructor(private val config: Config) {
        constructor() : this(Config())

        fun connectTimeout(timeout: Long, unit: TimeUnit) =
            apply { config.connectTimeoutMillis = unit.toMillis(timeout) }

        fun readTimeout(timeout: Long, unit: TimeUnit) =
            apply { config.readTimeoutMillis = unit.toMillis(timeout) }

        fun writeTimeout(timeout: Long, unit: TimeUnit) =
            apply { config.writeTimeoutMillis = unit.toMillis(timeout) }

        fun callTimeout(timeout: Long, unit: TimeUnit) =
            apply { config.callTimeoutMillis = unit.toMillis(timeout) }

        fun followRedirects(follow: Boolean) = apply { config.followRedirects = follow }
        fun followSslRedirects(follow: Boolean) = apply { config.followSslRedirects = follow }
        fun retryOnConnectionFailure(retry: Boolean) = apply { config.retryOnConnectionFailure = retry }
        fun proxy(proxy: Proxy?) = apply { config.proxy = proxy }
        fun proxySelector(selector: ProxySelector?) = apply { config.proxySelector = selector }
        fun proxyAuthenticator(authenticator: Authenticator?) = apply { config.proxyAuthenticator = authenticator }

        fun addInterceptor(interceptor: Interceptor) =
            apply { config.interceptors = config.interceptors + interceptor }

        fun addNetworkInterceptor(interceptor: Interceptor) =
            apply { config.networkInterceptors = config.networkInterceptors + interceptor }

        fun build(): OkHttpClient = OkHttpClient(config)
    }

    val connectionPool = ConnectionPool()

    @Volatile
    private var cachedProxy: Proxy? = null

    @Volatile
    private var cachedClient: HttpClient? = null
    private val clientLock = Any()

    init {
        connectionPool.evict = {
            synchronized(clientLock) {
                cachedClient?.close()
                cachedClient = null
                cachedProxy = null
            }
        }
    }

    internal fun proxyFor(url: HttpUrl): Proxy? {
        config.proxySelector?.let { selector ->
            val selected = runCatching { selector.select(java.net.URI(url.toString())) }.getOrNull()
            selected?.firstOrNull()?.takeIf { it != Proxy.NO_PROXY }?.let { return it }
            return null
        }
        return config.proxy
    }

    internal fun httpFor(url: HttpUrl): HttpClient {
        val proxy = proxyFor(url)
        synchronized(clientLock) {
            val current = cachedClient
            if (current != null && cachedProxy == proxy) return current
            current?.close()
            val created = buildKtor(proxy)
            cachedClient = created
            cachedProxy = proxy
            return created
        }
    }

    private fun buildKtor(proxy: Proxy?): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis.takeIf { it > 0 }
            socketTimeoutMillis = config.readTimeoutMillis.takeIf { it > 0 }
            requestTimeoutMillis = null
        }
        engine {
            this.proxy = proxy
        }
    }

    fun newBuilder(): Builder = Builder(config)

    fun newCall(request: Request): Call = Call(this, request)

    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
        KtorWebSocket(this, request, listener).also { it.connect() }

    internal fun runInterceptorChain(request: Request): Response {
        val interceptors = config.interceptors + config.networkInterceptors
        return InterceptorChain(this, request, 0, interceptors).proceed(request)
    }

    internal suspend fun sendRequest(initial: Request): Response {
        var request = initial
        var attempts = 0
        while (true) {
            val response = sendOnce(request)
            val proxyAuth = config.proxyAuthenticator
            if (response.code == 407 && proxyAuth != null && attempts < 2) {
                val retried = proxyAuth.authenticate(Route(proxyFor(request.url)), response) ?: return response
                request = retried
                attempts++
                continue
            }
            return response
        }
    }

    private suspend fun sendOnce(request: Request): Response {
        val http = httpFor(request.url)
        val response: HttpResponse = http.request(request.url.toString()) {
            method = HttpMethod.parse(request.method)
            request.headers.names().forEach { name ->
                request.headers.values(name).forEach { value -> header(name, value) }
            }
            request.body?.let { setBody(it.toOutgoingContent()) }
            timeout {
                requestTimeoutMillis = config.callTimeoutMillis.takeIf { it > 0 }
                connectTimeoutMillis = config.connectTimeoutMillis.takeIf { it > 0 }
                socketTimeoutMillis = config.readTimeoutMillis.takeIf { it > 0 }
            }
        }
        val bytes = response.readRawBytes()
        return Response(
            request = request,
            code = response.status.value,
            message = response.status.description,
            headers = response.headers.toCompatHeaders(),
            body = ByteArrayResponseBody(bytes, response.contentType()?.toString()?.let { MediaType.parse(it) }),
        )
    }

    private class InterceptorChain(
        private val client: OkHttpClient,
        override val request: Request,
        private val index: Int,
        private val interceptors: List<Interceptor>,
    ) : Interceptor.Chain {
        override fun proceed(request: Request): Response {
            if (index >= interceptors.size) {
                return runBlocking { client.sendRequest(request) }
            }
            return interceptors[index].intercept(InterceptorChain(client, request, index + 1, interceptors))
        }
    }
}

internal fun io.ktor.http.Headers.toCompatHeaders(): Headers {
    val builder = Headers.Builder()
    entries().forEach { entry ->
        entry.value.forEach { value -> builder.add(entry.key, value) }
    }
    return builder.build()
}

class Call internal constructor(
    private val client: OkHttpClient,
    private val request: Request,
) {
    @Volatile
    private var canceled = false
    private val timeout = Timeout()

    fun request(): Request = request
    fun isCanceled(): Boolean = canceled || timeout.cancelled
    fun timeout(): Timeout = timeout

    fun cancel() {
        canceled = true
    }

    fun execute(): Response {
        if (isCanceled()) throw IOException("Canceled")
        return client.runInterceptorChain(request)
    }

    suspend fun await(): Response = withContext(Dispatchers.IO) { execute() }

    fun enqueue(responseCallback: Callback) {
        Thread({
            try {
                responseCallback.onResponse(this, execute())
            } catch (e: IOException) {
                responseCallback.onFailure(this, e)
            } catch (e: Throwable) {
                responseCallback.onFailure(this, IOException(e))
            }
        }, "ktor-http-call").start()
    }
}
