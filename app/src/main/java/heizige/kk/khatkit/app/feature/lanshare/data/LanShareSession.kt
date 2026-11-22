package heizige.kk.khatkit.app.feature.lanshare.data

import heizige.kk.khatkit.app.feature.lanshare.helper.lanAttachmentHeader
import heizige.kk.khatkit.app.feature.lanshare.helper.lanHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val LAN_TEXT_LIMIT = 4_096
private const val MAX_FORM_BYTES = LAN_TEXT_LIMIT * 12 + 32

internal class LanShareSession(
    private val directory: LanShareDirectoryRepository,
    root: LanShareDocument?,
    private val readText: () -> String,
    private val receiveText: (String) -> Unit,
    private val usable: () -> Boolean,
) {
    val path = "/share/${UUID.randomUUID()}"
    private val documents = LinkedHashMap<String, LanShareDocument>()
    private val rootId: String? = root?.let(::add)

    private fun add(doc: LanShareDocument): String = synchronized(documents) {
        val id = UUID.randomUUID().toString()
        // Keep issued links bounded even if a client repeatedly refreshes large directories.
        if (documents.size >= 4_096) documents.keys.first { it != rootId }.let(documents::remove)
        documents[id] = doc
        id
    }

    fun mount(router: Route) = with(router) {
        route(path) {
            get {
                if (!call.prepare()) return@get
                call.home()
            }
            post("/text") {
                if (!call.prepare()) return@post
                val incoming = call.boundedText()
                if (incoming == null) call.respond(HttpStatusCode.BadRequest)
                else if (!usable()) call.respond(HttpStatusCode.NotFound)
                else {
                    receiveText(incoming)
                    call.respondRedirect(path)
                }
            }
            get("/document/{id}") {
                if (!call.prepare()) return@get
                val document = synchronized(documents) { documents[call.pathParameters["id"]] }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                if (document.directory) call.browse(document) else call.download(document)
            }
        }
    }

    private suspend fun ApplicationCall.prepare(): Boolean {
        if (!usable()) {
            respond(HttpStatusCode.NotFound)
            return false
        }
        response.header(HttpHeaders.CacheControl, "no-store")
        response.header("Referrer-Policy", "no-referrer")
        response.header("X-Content-Type-Options", "nosniff")
        response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
        return true
    }

    private suspend fun ApplicationCall.home() = respondText(page("局域网共享", buildString {
        rootId?.let { append("<p><a href=\"$path/document/$it\">打开共享目录</a></p>") }
        append("<h2>手机分享的文本</h2><pre>${readText().lanHtml()}</pre>")
        append("<form action=\"$path/text\" method=\"post\"><label for=\"message\">发送文本到手机</label>")
        append("<textarea id=\"message\" name=\"text\" maxlength=\"$LAN_TEXT_LIMIT\" required></textarea><button>发送</button></form>")
    }), ContentType.Text.Html)

    private suspend fun ApplicationCall.browse(folder: LanShareDocument) {
        val entries = try {
            directory.children(folder)
        } catch (_: SecurityException) {
            return respond(HttpStatusCode.Forbidden)
        } catch (_: IllegalStateException) {
            return respond(HttpStatusCode.NotFound)
        }
        respondText(page(folder.name.ifBlank { "共享目录" }, buildString {
            append("<p><a href=\"$path\">返回共享首页</a></p><ul>")
            for (entry in entries) {
                val id = add(entry)
                append("<li><a href=\"$path/document/$id\">${entry.name.lanHtml()}${if (entry.directory) "/" else ""}</a></li>")
            }
            append("</ul>")
            if (entries.size == LanShareDirectoryRepository.MAX_ENTRIES) append("<p>目录最多显示 500 项。</p>")
        }), ContentType.Text.Html)
    }

    private suspend fun ApplicationCall.download(document: LanShareDocument) {
        val stream = try {
            directory.open(document)
        } catch (_: java.io.FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            return respond(HttpStatusCode.Forbidden)
        } ?: return respond(HttpStatusCode.NotFound)
        try {
            response.header(HttpHeaders.ContentDisposition, document.name.lanAttachmentHeader())
            respondOutputStream(ContentType.Application.OctetStream) {
                withContext(Dispatchers.IO) { stream.copyTo(this@respondOutputStream) }
            }
        } finally {
            stream.close()
        }
    }

    private suspend fun ApplicationCall.boundedText(): String? = withTimeoutOrNull(5_000) {
        val channel = receiveChannel()
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(2_048)
        while (true) {
            val size = channel.readAvailable(buffer, 0, buffer.size)
            if (size < 0) break
            if (bytes.size() + size > MAX_FORM_BYTES) return@withTimeoutOrNull null
            bytes.write(buffer, 0, size)
        }
        runCatching { parseQueryString(bytes.toString(Charsets.UTF_8.name()), limit = 2)["text"] }
            .getOrNull()?.takeIf { it.isNotBlank() && it.length <= LAN_TEXT_LIMIT }
    }

    private fun page(title: String, body: String): String = """<!doctype html>
        <html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${title.lanHtml()}</title><style>
        :root{color-scheme:light dark}body{font:16px system-ui,sans-serif;max-width:42rem;margin:2rem auto;padding:0 1rem}
        h1{font-size:1.55rem}h2{font-size:1.1rem}li{padding:.45rem 0;overflow-wrap:anywhere}
        textarea{box-sizing:border-box;width:100%;min-height:7rem;margin:.45rem 0;font:inherit;padding:.55rem}
        pre{white-space:pre-wrap;overflow-wrap:anywhere;min-height:1.5em}button{padding:.45rem .9rem;font:inherit}
        </style><main><h1>${title.lanHtml()}</h1>$body</main></html>""".trimIndent()
}
