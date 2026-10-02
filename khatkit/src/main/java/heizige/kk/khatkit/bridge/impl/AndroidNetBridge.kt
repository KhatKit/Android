package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.NetBridge
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
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI

/** Android 网络 bridge；白名单在每次请求前检查。 */
class AndroidNetBridge(
    private val httpClient: HttpClient,
    private val allow: Set<String> = emptySet(),
) : NetBridge {
    fun httpClient(): HttpClient = httpClient
    override fun get(url: String, headers: Map<String, Any?>): String = runBlocking {
        checkHost(url)
        httpClient.get(url) { applyHeaders(headers) }.bodyAsText()
    }

    override fun post(url: String, body: String, headers: Map<String, Any?>): String = runBlocking {
        checkHost(url)
        httpClient.post(url) {
            applyHeaders(headers)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
    }

    override fun put(url: String, body: String, headers: Map<String, Any?>): String = runBlocking {
        checkHost(url)
        httpClient.put(url) {
            applyHeaders(headers)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
    }

    override fun delete(url: String, headers: Map<String, Any?>): String = runBlocking {
        checkHost(url)
        httpClient.delete(url) { applyHeaders(headers) }.bodyAsText()
    }

    override fun multipart(
        url: String,
        fields: Map<String, Any?>,
        fileField: String?,
        filePath: String?,
        headers: Map<String, Any?>,
        saveBinary: Boolean,
    ): String = runBlocking {
        checkHost(url)
        val response = httpClient.post(url) {
            applyHeaders(headers)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        fields.forEach { (name, value) -> append(name, value?.toString().orEmpty()) }
                        if (!fileField.isNullOrBlank() && !filePath.isNullOrBlank()) {
                            val file = File(filePath)
                            require(file.exists()) { "文件不存在：$filePath" }
                            append(
                                fileField,
                                file.readBytes(),
                                Headers.build {
                                    append(HttpHeaders.ContentType, mimeFor(file.name))
                                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                },
                            )
                        }
                    },
                ),
            )
        }
        if (!saveBinary) return@runBlocking response.bodyAsText()
        response.bodyAsText()
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
        checkHost(url)
        val bytes = httpClient.get(url) { applyHeaders(headers) }.readRawBytes()
        File(outputPath).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }.absolutePath
    }

    override fun head(url: String, headers: Map<String, Any?>): Map<String, Any?> = runBlocking {
        checkHost(url)
        val response = httpClient.head(url) { applyHeaders(headers) }
        mapOf(
            "status" to response.status.value,
            "headers" to response.headers.entries().associate { it.key to it.value.joinToString(",") },
            "contentType" to response.headers["Content-Type"],
            "length" to response.headers["Content-Length"]?.toLongOrNull(),
        )
    }

    private fun checkHost(url: String) {
        val host = URI(url).host?.lowercase()
            ?: throw IllegalArgumentException("网络地址缺少有效域名：$url")
        if (allow.any { it == "*" || host == it || host.endsWith(".$it") }) return
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
}
