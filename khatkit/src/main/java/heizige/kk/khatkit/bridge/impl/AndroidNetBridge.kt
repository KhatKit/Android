package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.NetBridge
import heizige.kk.khatkit.bridge.CardApiObservation
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.prepareRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI

/** Android 网络 bridge；白名单在每次请求前检查。 */
class AndroidNetBridge(
    private val httpClient: HttpClient,
    private val allow: Set<String> = emptySet(),
    private val onObservation: ((CardApiObservation) -> Unit)? = null,
    private val card: String? = null,
) : NetBridge {
    internal fun forCard(name: String, allow: Set<String>): AndroidNetBridge =
        AndroidNetBridge(httpClient, allow, onObservation, name)

    override fun request(
        url: String,
        method: String,
        body: String,
        headers: Map<String, Any?>,
    ): Map<String, Any?> = runBlocking {
        val uri = URI(url)
        require(uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null) {
            "网络地址必须为不含用户信息的 HTTP(S) URL"
        }
        val host = checkHost(url)
        val verb = method.uppercase()
        require(verb in setOf("GET", "POST", "PUT", "DELETE", "HEAD")) { "不支持的网络方法：$method" }
        // A redirect must not leak credentials or escape the per-card allowlist.
        observe(host, verb) { record -> httpClient.config { followRedirects = false }.use { client ->
            client.prepareRequest(url) {
                this.method = HttpMethod.parse(verb)
                expectSuccess = false
                applyHeaders(headers)
                if (verb == "POST" || verb == "PUT") {
                    if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                        contentType(ContentType.Application.Json)
                    }
                    setBody(body)
                }
            }.execute { response ->
                record(response.status.value)
                val channel = response.bodyAsChannel()
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    require(output.size() + count <= 192 * 1024) { "net.request 响应超过 192 KiB，请缩小分页" }
                    output.write(buffer, 0, count)
                }
                mapOf(
                    "status" to response.status.value,
                    "headers" to response.headers.entries().associate { it.key.lowercase() to it.value },
                    "body" to output.toString(Charsets.UTF_8.name()),
                )
            }
        } }
    }

    override fun get(url: String, headers: Map<String, Any?>): String = runBlocking {
        observedResponse(url, "GET") { httpClient.get(url) { applyHeaders(headers) } }.bodyAsText()
    }

    override fun post(url: String, body: String, headers: Map<String, Any?>): String = runBlocking {
        observedResponse(url, "POST") { httpClient.post(url) {
            applyHeaders(headers)
            contentType(ContentType.Application.Json)
            setBody(body)
        } }.bodyAsText()
    }

    override fun put(url: String, body: String, headers: Map<String, Any?>): String = runBlocking {
        observedResponse(url, "PUT") { httpClient.put(url) {
            applyHeaders(headers)
            contentType(ContentType.Application.Json)
            setBody(body)
        } }.bodyAsText()
    }

    override fun delete(url: String, headers: Map<String, Any?>): String = runBlocking {
        observedResponse(url, "DELETE") { httpClient.delete(url) { applyHeaders(headers) } }.bodyAsText()
    }

    override fun multipart(
        url: String,
        fields: Map<String, Any?>,
        fileField: String?,
        filePath: String?,
        headers: Map<String, Any?>,
        saveBinary: Boolean,
    ): String = runBlocking {
        val host = checkHost(url)
        val payload = MultiPartFormDataContent(formData {
            fields.forEach { (name, value) -> append(name, value?.toString().orEmpty()) }
            if (!fileField.isNullOrBlank() && !filePath.isNullOrBlank()) {
                val file = File(filePath)
                require(file.exists()) { "文件不存在：$filePath" }
                append(fileField, file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, mimeFor(file.name))
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }
        })
        val response = observedResponse(host, "POST", checked = true) { httpClient.post(url) {
            applyHeaders(headers)
            setBody(payload)
        } }
        if (!saveBinary) return@runBlocking response.bodyAsText()
        val contentType = response.contentType()
        val isImage = contentType?.contentType?.equals("image", ignoreCase = true) == true
        if (!isImage) return@runBlocking response.bodyAsText()

        val bytes = response.readRawBytes()
        val extension = contentType.contentSubtype
            .lowercase()
            .let { subtype ->
                when (subtype) {
                    "jpeg", "jpg" -> "jpg"
                    "png", "webp", "gif", "bmp", "svg" -> subtype
                    else -> subtype.replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }
                }
            }
        val output = File(
            "/sdcard/Download",
            "khatkit_net_${safeEndpointName(url)}_${System.currentTimeMillis()}.$extension",
        )
        try {
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        } catch (e: SecurityException) {
            throw SecurityException(
                "图片响应无法保存到共享存储：${output.absolutePath}；请授予「所有文件访问」后重试",
                e,
            )
        }
        output.absolutePath
    }

    override fun streamText(
        url: String,
        method: String,
        body: String,
        headers: Map<String, Any?>,
        timeoutSeconds: Int,
    ): String = when (method.uppercase()) {
        "GET" -> get(url, headers)
        "POST" -> post(url, body, headers)
        "PUT" -> put(url, body, headers)
        "DELETE" -> delete(url, headers)
        else -> throw IllegalArgumentException("不支持的网络方法：$method")
    }

    override fun toFile(url: String, outputPath: String, headers: Map<String, Any?>): String = runBlocking {
        val bytes = observedResponse(url, "GET") { httpClient.get(url) { applyHeaders(headers) } }.readRawBytes()
        File(outputPath).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }.absolutePath
    }

    override fun head(url: String, headers: Map<String, Any?>): Map<String, Any?> = runBlocking {
        val response = observedResponse(url, "HEAD") { httpClient.head(url) { applyHeaders(headers) } }
        mapOf(
            "status" to response.status.value,
            "headers" to response.headers.entries().associate { it.key to it.value.joinToString(",") },
            "contentType" to response.headers["Content-Type"],
            "length" to response.headers["Content-Length"]?.toLongOrNull(),
        )
    }

    private suspend fun observedResponse(endpoint: String, method: String, checked: Boolean = false,
        request: suspend () -> HttpResponse): HttpResponse =
        observe(if (checked) endpoint else checkHost(endpoint), method) { record -> request().also { record(it.status.value) } }

    private suspend fun <T> observe(host: String, method: String, block: suspend ((Int) -> Unit) -> T): T {
        val name = card ?: return block {}
        val sink = onObservation ?: return block {}
        var responseReceived = false
        fun record(status: Int?) {
            responseReceived = true
            runCatching { sink(CardApiObservation(name, host, method, status)) }
        }
        return try {
            block(::record)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ResponseException) {
            if (!responseReceived) record(failure.response.status.value)
            throw failure
        } catch (failure: IOException) {
            if (!responseReceived) record(null)
            throw failure
        }
    }

    private fun checkHost(url: String): String {
        val host = URI(url).host?.trimEnd('.')?.lowercase()
            ?: throw IllegalArgumentException("网络地址缺少有效域名：$url")
        val allowed = allow.mapNotNull { raw ->
            val value = raw.trim().trimEnd('.').lowercase()
            if (value == "*") value else runCatching {
                if ("://" in value) URI(value).host else value
            }.getOrNull()?.trimEnd('.')?.takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains(':') }
        }
        if (allowed.any { it == "*" || host == it || host.endsWith(".$it") }) return host
        throw SecurityException("访问被卡片 network 白名单拒绝：$host；请在 card.json 的 network.allow 中声明该域名")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(headers: Map<String, Any?>) {
        headers.forEach { (key, value) -> value?.let { header(key, it.toString()) } }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun safeEndpointName(url: String): String {
        val name = url.substringAfterLast('/').substringBefore('?').removeSuffix(".php")
        return name.replace(Regex("[^A-Za-z0-9_]+"), "_").ifBlank { "api" }
    }
}
