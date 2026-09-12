package heizige.kk.khatkit.common.http.okhttp

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.HttpHeaders as KtorHttpHeaders
import io.ktor.http.Headers as KtorHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/**
 * Ktor 实现的 OkHttp 兼容层（仅覆盖本项目用到的 API 面）。
 *
 * 目的：让存量代码不改调用形状就能跑在 Ktor 上，逐步去掉 OkHttp 依赖。
 * 包名沿用 `okhttp` 命名以便机械替换 import；底层是 Ktor CIO，不是 OkHttp。
 */

// ══════════════ MediaType ══════════════

class MediaType private constructor(
    val type: String,
    val subtype: String,
    private val parameters: Map<String, String>,
) {
    fun charset(defaultValue: java.nio.charset.Charset? = null): java.nio.charset.Charset? {
        val value = parameters["charset"] ?: return defaultValue
        return runCatching { java.nio.charset.Charset.forName(value) }.getOrDefault(defaultValue)
    }

    override fun toString(): String = buildString {
        append(type).append('/').append(subtype)
        parameters.forEach { (key, value) -> append("; ").append(key).append('=').append(value) }
    }

    override fun equals(other: Any?): Boolean =
        other is MediaType && toString().equals(other.toString(), ignoreCase = true)

    override fun hashCode(): Int = toString().lowercase().hashCode()

    companion object {
        fun parse(string: String): MediaType? {
            val parts = string.split(';')
            val typeParts = parts.firstOrNull()?.trim()?.split('/') ?: return null
            if (typeParts.size != 2) return null
            val parameters = linkedMapOf<String, String>()
            parts.drop(1).forEach { raw ->
                val kv = raw.trim().split('=', limit = 2)
                if (kv.size == 2) parameters[kv[0].trim().lowercase()] = kv[1].trim().removeSurrounding("\"")
            }
            return MediaType(typeParts[0].trim().lowercase(), typeParts[1].trim().lowercase(), parameters)
        }

        fun get(type: String, subtype: String): MediaType = MediaType(type, subtype, emptyMap())
    }
}

fun String.toMediaType(): MediaType = MediaType.parse(this) ?: error("Invalid media type: $this")

// ══════════════ Headers ══════════════

class Headers private constructor(private val entries: List<Pair<String, String>>) {
    private val map: Map<String, List<String>> =
        entries.groupBy({ it.first.lowercase() }, { it.second })

    fun size(): Int = entries.size
    fun get(name: String): String? = map[name.lowercase()]?.firstOrNull()
    fun values(name: String): List<String> = map[name.lowercase()].orEmpty()
    fun names(): Set<String> = map.keys
    fun toMultimap(): Map<String, List<String>> = map

    fun newBuilder(): Builder = Builder().apply { entries.forEach { add(it.first, it.second) } }

    class Builder {
        private val entries = mutableListOf<Pair<String, String>>()
        fun add(name: String, value: String) = apply { entries.add(name to value) }
        fun set(name: String, value: String) = apply {
            entries.removeAll { it.first.equals(name, ignoreCase = true) }
            entries.add(name to value)
        }

        fun removeAll(name: String) = apply { entries.removeAll { it.first.equals(name, ignoreCase = true) } }
        fun build(): Headers = Headers(entries.toList())
    }

    companion object {
        fun of(vararg pairs: Pair<String, String>): Headers =
            Builder().apply { pairs.forEach { add(it.first, it.second) } }.build()

        fun builder(): Builder = Builder()
    }
}

// ══════════════ HttpUrl ══════════════

class HttpUrl private constructor(
    val scheme: String,
    val host: String,
    val port: Int,
    private val path: String,
    private val query: String?,
    private val fragment: String?,
    private val userInfo: String?,
) {
    val encodedPath: String get() = path

    fun queryParameter(name: String): String? = queryParameterValues(name).firstOrNull()

    fun queryParameterValues(name: String): List<String> {
        val raw = query ?: return emptyList()
        return raw.split('&').mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null
            val index = part.indexOf('=')
            val key = if (index >= 0) part.substring(0, index) else part
            if (java.net.URLDecoder.decode(key, "UTF-8") != name) return@mapNotNull null
            if (index >= 0) java.net.URLDecoder.decode(part.substring(index + 1), "UTF-8") else ""
        }
    }

    fun newBuilder(): Builder = Builder(scheme, host, port, path, query, fragment, userInfo)

    override fun toString(): String = buildString {
        append(scheme).append("://")
        userInfo?.takeIf { it.isNotEmpty() }?.let { append(it).append('@') }
        append(host)
        if (port != defaultPort(scheme)) append(':').append(port)
        append(path.ifEmpty { "/" })
        query?.takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }

    override fun equals(other: Any?): Boolean = other is HttpUrl && toString() == other.toString()
    override fun hashCode(): Int = toString().hashCode()

    class Builder(
        private var scheme: String,
        private var host: String,
        private var port: Int,
        private var path: String,
        private var query: String?,
        private var fragment: String?,
        private var userInfo: String?,
    ) {
        fun addQueryParameter(name: String, value: String?): Builder {
            val pair = if (value == null) name else "$name=${URLEncoder.encode(value, "UTF-8")}"
            query = if (query.isNullOrEmpty()) pair else "$query&$pair"
            return this
        }

        fun fragment(value: String?): Builder = apply { fragment = value }
        fun encodedPath(value: String): Builder = apply { path = value }
        fun build(): HttpUrl = HttpUrl(scheme, host, port, path, query, fragment, userInfo)
    }

    companion object {
        fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
            "https", "wss" -> 443
            "http", "ws" -> 80
            else -> -1
        }

        fun parse(url: String): HttpUrl? {
            val trimmed = url.trim()
            // 与 OkHttp 一致：必须显式带 scheme，否则视为非法
            if ("://" !in trimmed) return null
            return runCatching {
                val uri = URI(trimmed)
                val scheme = uri.scheme ?: return null
                val host = uri.host ?: return null
                HttpUrl(
                    scheme = scheme.lowercase(),
                    host = host,
                    port = if (uri.port != -1) uri.port else defaultPort(scheme),
                    path = uri.rawPath.orEmpty(),
                    query = uri.rawQuery,
                    fragment = uri.rawFragment,
                    userInfo = uri.userInfo,
                )
            }.getOrNull()
        }
    }
}

fun String.toHttpUrl(): HttpUrl = HttpUrl.parse(this) ?: error("Invalid URL: $this")
fun String.toHttpUrlOrNull(): HttpUrl? = HttpUrl.parse(this)

// ══════════════ RequestBody ══════════════

abstract class RequestBody {
    abstract fun contentType(): MediaType?
    abstract fun contentLength(): Long
    abstract fun bytes(): ByteArray
    internal open fun multipart(): MultipartBody? = null

    companion object {
        fun create(contentType: MediaType?, content: String): RequestBody =
            ByteArrayRequestBody(content.toByteArray(Charsets.UTF_8), contentType)

        fun create(contentType: MediaType?, content: ByteArray): RequestBody =
            ByteArrayRequestBody(content, contentType)

        fun create(contentType: MediaType?, file: File): RequestBody =
            FileRequestBody(file, contentType)

        val EMPTY: RequestBody = ByteArrayRequestBody(ByteArray(0), null)
    }
}

internal class ByteArrayRequestBody(private val content: ByteArray, private val type: MediaType?) : RequestBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = content.size.toLong()
    override fun bytes(): ByteArray = content
}

internal class FileRequestBody(private val file: File, private val type: MediaType?) : RequestBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = file.length()
    override fun bytes(): ByteArray = file.readBytes()
}

fun String.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody.create(contentType, this)
fun ByteArray.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody.create(contentType, this)
fun File.asRequestBody(contentType: MediaType? = null): RequestBody = RequestBody.create(contentType, this)

internal fun RequestBody.toOutgoingContent(): OutgoingContent {
    multipart()?.let { return it.toOutgoingContent() }
    return ByteArrayContent(bytes(), contentType()?.let { io.ktor.http.ContentType.parse(it.toString()) })
}

class FormBody private constructor(
    private val names: List<String>,
    private val values: List<String>,
) : RequestBody() {
    override fun contentType(): MediaType = MediaType.get("application", "x-www-form-urlencoded")
    override fun contentLength(): Long = encoded().length.toLong()
    override fun bytes(): ByteArray = encoded().toByteArray(Charsets.UTF_8)

    private fun encoded(): String = names.indices.joinToString("&") { index ->
        "${encode(names[index])}=${encode(values[index])}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    class Builder {
        private val names = mutableListOf<String>()
        private val values = mutableListOf<String>()
        fun add(name: String, value: String) = apply { names.add(name); values.add(value) }
        fun addEncoded(name: String, value: String) = add(name, value)
        fun build(): FormBody = FormBody(names.toList(), values.toList())
    }

    companion object {
        fun create(vararg pairs: Pair<String, String>): FormBody =
            Builder().apply { pairs.forEach { add(it.first, it.second) } }.build()
    }
}

class MultipartBody private constructor(
    internal val parts: List<Part>,
    private val type: MediaType?,
) : RequestBody() {
    sealed interface Part {
        val name: String
        data class StringPart(override val name: String, val value: String) : Part
        data class FilePart(override val name: String, val fileName: String?, val body: RequestBody) : Part
    }

    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = 0
    override fun bytes(): ByteArray = ByteArray(0)
    override fun multipart(): MultipartBody = this

    class Builder {
        private val parts = mutableListOf<Part>()
        private var type: MediaType? = FORM
        fun setType(type: MediaType) = apply { this.type = type }
        fun addFormDataPart(name: String, value: String) = apply { parts.add(Part.StringPart(name, value)) }
        fun addFormDataPart(name: String, filename: String?, body: RequestBody) = apply {
            parts.add(Part.FilePart(name, filename, body))
        }

        fun build(): MultipartBody = MultipartBody(parts.toList(), type)
    }

    companion object {
        val FORM: MediaType = MediaType.get("multipart", "form-data")
        val MIXED: MediaType = MediaType.get("multipart", "mixed")
        val ALTERNATIVE: MediaType = MediaType.get("multipart", "alternative")
        val DIGEST: MediaType = MediaType.get("multipart", "digest")
        val PARALLEL: MediaType = MediaType.get("multipart", "parallel")
    }
}

internal fun MultipartBody.toOutgoingContent(): OutgoingContent {
    val items = parts.map { part ->
        when (part) {
            is MultipartBody.Part.StringPart -> Triple(part.name, part.value.toByteArray(Charsets.UTF_8), null)
            is MultipartBody.Part.FilePart -> Triple(part.name, part.body.bytes(), part.fileName)
        }
    }
    return MultiPartFormDataContent(formData {
        items.forEach { (name, bytes, filename) ->
            if (filename == null) {
                append(name, bytes)
            } else {
                append(
                    name,
                    bytes,
                    KtorHeaders.build {
                        append(KtorHttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    },
                )
            }
        }
    })
}

// ══════════════ Request ══════════════

class Request private constructor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
) {
    fun header(name: String): String? = headers.get(name)
    fun newBuilder(): Builder = Builder(url, method, headers, body)

    class Builder {
        private var url: HttpUrl? = null
        private var method: String = "GET"
        private var headers: Headers = Headers.of()
        private var body: RequestBody? = null

        constructor()

        internal constructor(url: HttpUrl, method: String, headers: Headers, body: RequestBody?) {
            this.url = url
            this.method = method
            this.headers = headers
            this.body = body
        }

        fun url(url: String): Builder = apply { this.url = url.toHttpUrl() }
        fun url(url: HttpUrl): Builder = apply { this.url = url }
        fun header(name: String, value: String): Builder =
            apply { headers = headers.newBuilder().set(name, value).build() }

        fun addHeader(name: String, value: String): Builder =
            apply { headers = headers.newBuilder().add(name, value).build() }

        fun headers(headers: Headers): Builder = apply { this.headers = headers }
        fun get(): Builder = apply { method = "GET"; body = null }
        fun head(): Builder = apply { method = "HEAD"; body = null }
        fun post(body: RequestBody): Builder = apply { method = "POST"; this.body = body }
        fun put(body: RequestBody): Builder = apply { method = "PUT"; this.body = body }
        fun patch(body: RequestBody): Builder = apply { method = "PATCH"; this.body = body }
        fun delete(): Builder = apply { method = "DELETE"; body = null }
        fun delete(body: RequestBody?): Builder = apply { method = "DELETE"; this.body = body }
        fun method(method: String, body: RequestBody?): Builder = apply { this.method = method; this.body = body }
        fun build(): Request = Request(requireNotNull(url) { "url is required" }, method, headers, body)
    }
}

// ══════════════ Response ══════════════

abstract class ResponseBody : Closeable {
    abstract fun contentType(): MediaType?
    abstract fun contentLength(): Long
    abstract fun byteStream(): InputStream

    fun bytes(): ByteArray = byteStream().use { it.readBytes() }
    fun string(): String = bytes().toString(contentType()?.charset() ?: Charsets.UTF_8)
    override fun close() {}
}

internal class ByteArrayResponseBody(
    private val content: ByteArray,
    private val type: MediaType?,
) : ResponseBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = content.size.toLong()
    override fun byteStream(): InputStream = content.inputStream()
}

class Response internal constructor(
    val request: Request,
    val code: Int,
    val message: String,
    val headers: Headers,
    val body: ResponseBody,
) : Closeable {
    val isSuccessful: Boolean get() = code in 200..299
    fun header(name: String): String? = headers.get(name)
    override fun close() = body.close()
}

// ══════════════ Call / Callback / Interceptor ══════════════

class Timeout {
    @Volatile
    var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
    }
}

interface Callback {
    fun onFailure(call: Call, e: java.io.IOException)
    fun onResponse(call: Call, response: Response)
}

fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        val request: Request
        fun request(): Request = request
        fun proceed(request: Request): Response
    }
}

fun interface Authenticator {
    fun authenticate(route: Route?, response: Response): Request?
}

class Route(val proxy: Proxy?)

object Credentials {
    fun basic(username: String, password: String): String = basic(username, password, Charsets.UTF_8)

    fun basic(username: String, password: String, charset: java.nio.charset.Charset): String {
        val raw = "$username:$password".toByteArray(charset)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }
}

class ConnectionPool internal constructor() {
    internal var evict: (() -> Unit)? = null
    fun evictAll() {
        evict?.invoke()
    }
}


