package heizige.kk.khatkit.hub

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * 卡片 Hub 的客户端（设计文档第 9 节）。
 *
 * 网络层用 Ktor（CIO 引擎），刻意不依赖 OkHttp。
 *
 * 千万级规模注意（v1 不必实现全部，但接口要留）：
 *   - 索引分片：/index/shard/{tag}/{page}.json，不拉全量
 *   - 卡片懒加载：AI 选中后才下载对应包
 *   - 共享 lib 独立缓存，不进卡片包
 */
class HubClient(
    private val baseUrl: String,
    engine: HttpClientEngine = CIO.create(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    private fun resolve(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** 语义召回：服务端 embedding + 硬过滤，返回 top-K 候选喂给 AI。 */
    suspend fun search(
        query: String,
        capabilities: Set<String> = emptySet(),
        limit: Int = 20,
    ): CardIndex {
        val response = client.post(resolve("/api/khatkit/search")) {
            contentType(ContentType.Application.Json)
            setBody(KhatKitSearchRequest(query, capabilities.toList(), limit))
        }
        return response.body()
    }

    suspend fun fetchLibIndex(): LibIndex =
        client.get(resolve("/api/khatkit/libs/index.json")).body()

    /** 下载卡片包到 destDir，校验 sha256。 */
    suspend fun downloadCard(entry: CardIndexEntry, destDir: File): File =
        download(resolve(entry.url), File(destDir, "${entry.name}-${entry.version}.zip"), entry.hash)

    suspend fun downloadLib(lib: LibIndexEntry, destDir: File): File =
        download(resolve(lib.url), File(destDir, "${lib.name}-${lib.version}"), lib.hash)

    private suspend fun download(url: String, target: File, expectedHash: String): File =
        withContext(Dispatchers.IO) {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                error("下载失败 ${response.status.value}: $url")
            }
            val bytes = response.readBytes()
            if (expectedHash.isNotBlank()) {
                val actual = sha256(bytes)
                require(actual.equals(expectedHash, ignoreCase = true)) {
                    "卡片包 hash 不匹配：期望 $expectedHash，实际 $actual"
                }
            }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun close() = client.close()
}
