package heizige.kk.khatkit.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.toMediaType
import heizige.kk.khatkit.common.http.okhttp.toRequestBody
import heizige.kk.khatkit.search.SearchResult.SearchResultItem
import heizige.kk.khatkit.search.SearchRepository.Companion.httpClient
import heizige.kk.khatkit.search.SearchRepository.Companion.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

object DeepSeekSearchRepository : SearchRepository<SearchOptions.DeepSeekOptions> {
    private const val BASE_URL = "https://api.deepseek.com/v1/tools"

    override val name: String = "DeepSeek"

    @Composable
    override fun Description() {
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri("https://platform.deepseek.com/api_keys") }) {
            Text(stringResource(heizige.kk.khatkit.search.R.string.click_to_get_api_key))
        }
    }

    override fun parameters(options: SearchOptions.DeepSeekOptions): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", 128)
                    put("description", "Search query; supports site:, -site: and quoted phrases")
                })
                put("count", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 50)
                    put("description", "Number of results, default 10")
                })
                put("ori_question", buildJsonObject {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", 128)
                    put("description", "The user's original question")
                })
                put("localization", buildJsonObject {
                    put("type", "string")
                    put("description", "IP address used to prefer localized results")
                })
                put("need_content", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Include full page content in search results")
                })
            },
            required = listOf("query"),
        )

    override fun scrapingParameters(options: SearchOptions.DeepSeekOptions): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "HTTP or HTTPS URL to fetch as Markdown")
                })
            },
            required = listOf("url"),
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchOptions.DeepSeekOptions,
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 128 }
                ?: error("query must contain 1 to 128 characters")
            val body = buildSearchRequestBody(params, commonOptions.resultSize, query)
            val response = post("search", body, serviceOptions.apiKey)
            val data = json.decodeFromString<DeepSeekSearchResponse>(response)
            SearchResult(
                items = data.results.map {
                    SearchResultItem(
                        title = it.title,
                        url = it.url,
                        text = it.summary.ifBlank { it.content.ifBlank { it.snippet } },
                        publishedDate = it.date,
                    )
                },
            )
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchOptions.DeepSeekOptions,
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = params["url"]?.jsonPrimitive?.content?.trim()
                ?.takeIf(::isValidFetchUrl)
                ?: error("url must be an HTTP/HTTPS URL without credentials")
            val response = post(
                "fetch",
                buildJsonObject { put("url", url) },
                serviceOptions.apiKey,
            )
            val data = json.decodeFromString<DeepSeekFetchResponse>(response)
            ScrapedResult(
                urls = listOf(
                    ScrapedResultUrl(
                        url = url,
                        content = data.content,
                        metadata = ScrapedResultMetadata(title = data.title),
                    )
                )
            )
        }
    }

    internal fun buildSearchRequestBody(
        params: JsonObject,
        resultSize: Int,
        query: String = params["query"]?.jsonPrimitive?.content?.trim().orEmpty(),
    ): JsonObject = buildJsonObject {
        put("query", query)
        put("count", params["count"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 50) ?: resultSize.coerceIn(1, 50))
        params["ori_question"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 128 }
            ?.let { put("ori_question", it) }
        params["localization"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { put("localization", it) }
        params["need_content"]?.jsonPrimitive?.booleanOrNull
            ?.let { put("need_content", it) }
    }

    private suspend fun post(path: String, body: JsonObject, apiKey: String): String {
        require(apiKey.isNotBlank()) { "DeepSeek API key is required" }
        val request = Request.Builder()
            .url("$BASE_URL/$path")
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            error("DeepSeek $path failed #${response.code}: ${response.body.string().take(500)}")
        }
        return response.body.string()
    }

    internal fun isValidFetchUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.lowercase() in setOf("http", "https") &&
            uri.userInfo.isNullOrBlank() &&
            uri.host != null
    }.getOrDefault(false)

    @Serializable
    data class DeepSeekSearchResponse(
        val results: List<DeepSeekSearchResult> = emptyList(),
    )

    @Serializable
    data class DeepSeekSearchResult(
        val title: String = "",
        val url: String = "",
        val snippet: String = "",
        val summary: String = "",
        val content: String = "",
        @SerialName("date") val date: String? = null,
    )

    @Serializable
    data class DeepSeekFetchResponse(
        val title: String = "",
        val content: String = "",
    )
}
