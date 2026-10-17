package heizige.kk.khatkit.search

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.util.KeyRoulette
import heizige.kk.khatkit.common.http.okhttp.Call
import heizige.kk.khatkit.common.http.okhttp.Callback
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Response
import heizige.kk.khatkit.common.http.okhttp.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.uuid.Uuid

interface SearchRepository<T : SearchOptions> {
    val name: String

    fun parameters(options: T): InputSchema?

    fun scrapingParameters(options: T): InputSchema?

    @Composable
    fun Description()

    suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<SearchResult>

    suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<ScrapedResult>

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : SearchOptions> get(options: T): SearchRepository<T> {
            return when (options) {
                is SearchOptions.TavilyOptions -> TavilySearchRepository
                is SearchOptions.ExaOptions -> ExaSearchRepository
                is SearchOptions.ZhipuOptions -> ZhipuSearchRepository
                is SearchOptions.DoubaoOptions -> DoubaoSearchRepository
                is SearchOptions.BingLocalOptions -> BingSearchRepository
                is SearchOptions.SearXNGOptions -> SearXNGRepository
                is SearchOptions.LinkUpOptions -> LinkUpRepository
                is SearchOptions.BraveOptions -> BraveSearchRepository
                is SearchOptions.MetasoOptions -> MetasoSearchRepository
                is SearchOptions.OllamaOptions -> OllamaSearchRepository
                is SearchOptions.PerplexityOptions -> PerplexitySearchRepository
                is SearchOptions.FirecrawlOptions -> FirecrawlSearchRepository
                is SearchOptions.JinaOptions -> JinaSearchRepository
                is SearchOptions.BochaOptions -> BochaSearchRepository
                is SearchOptions.KhatKitOptions -> KhatKitSearchRepository
                is SearchOptions.GrokOptions -> GrokSearchRepository
                is SearchOptions.TinyfishOptions -> TinyfishSearchRepository
                is SearchOptions.SerperOptions -> SerperSearchRepository
                is SearchOptions.CustomJsOptions -> CustomJsSearchRepository
                is SearchOptions.DeepSeekOptions -> DeepSeekSearchRepository
            } as SearchRepository<T>
        }

        @Volatile
        internal var httpClient: OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @Volatile
        internal var keyRoulette: KeyRoulette = KeyRoulette.default()

        fun init(client: OkHttpClient, context: Context? = null) {
            httpClient = client
            keyRoulette = if (context != null) KeyRoulette.lru(context) else KeyRoulette.default()
        }

        internal val json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}

@Serializable
data class SearchCommonOptions(
    val resultSize: Int = 10
)

@Serializable
data class SearchResult(
    val answer: String? = null,
    val items: List<SearchResultItem>,
    val images: List<String> = emptyList(),
    /** Local time at which this result was retrieved; not a publication date. */
    val retrievedAt: String? = null,
) {
    @Serializable
    data class SearchResultItem(
        val title: String,
        val url: String,
        val text: String,
        /** Provider-supplied publication date. Null means that no date was supplied. */
        val publishedDate: String? = null,
        val highlights: List<String> = emptyList(),
    )
}

@Serializable
data class ScrapedResult(
    val urls: List<ScrapedResultUrl>,
    /** Local time at which this result was retrieved; not a publication date. */
    val retrievedAt: String? = null,
)

@Serializable
data class ScrapedResultUrl(
    val url: String,
    val content: String,
    val metadata: ScrapedResultMetadata? = null,
)

@Serializable
data class ScrapedResultMetadata(
    val title: String? = null,
    val description: String? = null,
    val language: String? = null,
    /** Provider-supplied publication date. Null means that no date was supplied. */
    val publishedDate: String? = null,
)

@Serializable
sealed class SearchOptions {
    abstract val id: Uuid

    open val displayName: String
        get() = TYPES[this::class] ?: "Unknown"

    companion object {
        val DEFAULT = BingLocalOptions()

        val TYPES = mapOf(
            BingLocalOptions::class to "Bing",
            KhatKitOptions::class to "KhatKit",
            ZhipuOptions::class to "智谱",
            DoubaoOptions::class to "豆包",
            TavilyOptions::class to "Tavily",
            ExaOptions::class to "Exa",
            SearXNGOptions::class to "SearXNG",
            LinkUpOptions::class to "LinkUp",
            BraveOptions::class to "Brave",
            MetasoOptions::class to "秘塔",
            OllamaOptions::class to "Ollama",
            PerplexityOptions::class to "Perplexity",
            FirecrawlOptions::class to "Firecrawl",
            JinaOptions::class to "Jina",
            BochaOptions::class to "博查",
            GrokOptions::class to "Grok",
            TinyfishOptions::class to "Tinyfish",
            SerperOptions::class to "Serper",
            CustomJsOptions::class to "Custom JS",
            DeepSeekOptions::class to "DeepSeek",
        )
    }

    @Serializable
    @SerialName("bing_local")
    class BingLocalOptions(
        override val id: Uuid = Uuid.random()
    ) : SearchOptions()

    @Serializable
    @SerialName("zhipu")
    data class ZhipuOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("doubao")
    data class DoubaoOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val mode: DoubaoSearchMode = DoubaoSearchMode.CUSTOM,
    ) : SearchOptions()

    @Serializable
    @SerialName("tavily")
    data class TavilyOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "advanced",
    ) : SearchOptions()

    @Serializable
    @SerialName("exa")
    data class ExaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("searxng")
    data class SearXNGOptions(
        override val id: Uuid = Uuid.random(),
        val url: String = "",
        val engines: String = "",
        val language: String = "",
        val username: String = "",
        val password: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("linkup")
    data class LinkUpOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "standard",
    ) : SearchOptions()

    @Serializable
    @SerialName("brave")
    data class BraveOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("metaso")
    data class MetasoOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("ollama")
    data class OllamaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("perplexity")
    data class PerplexityOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val maxTokens: Int? = null,
        val maxTokensPerPage: Int? = null,
    ) : SearchOptions()

    @Serializable
    @SerialName("firecrawl")
    data class FirecrawlOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("jina")
    data class JinaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val searchUrl: String = "https://s.jina.ai/",
        val scrapeUrl: String = "https://r.jina.ai/",
    ) : SearchOptions()

    @Serializable
    @SerialName("bocha")
    data class BochaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val summary: Boolean = true,
    ) : SearchOptions()

    @Serializable
    @SerialName("rikkahub")
    data class KhatKitOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "standard",
    ) : SearchOptions()

    @Serializable
    @SerialName("grok")
    data class GrokOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val model: String = "grok-4-1-fast-non-reasoning",
        val customUrl: String = "https://api.x.ai/v1/responses",
        val systemPrompt: String = "You are a helpful search assistant. Search the web to find accurate and up-to-date information for the user's query. Provide a comprehensive answer with citations.",
    ) : SearchOptions()

    @Serializable
    @SerialName("tinyfish")
    data class TinyfishOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("serper")
    data class SerperOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()

    @Serializable
    @SerialName("custom_js")
    data class CustomJsOptions(
        override val id: Uuid = Uuid.random(),
        val name: String = "",
        val searchScript: String = DEFAULT_SEARCH_SCRIPT,
        val scrapeScript: String = "",
    ) : SearchOptions() {
        override val displayName: String
            get() = name.ifBlank { "Custom JS" }
        companion object {
            const val DEFAULT_SCRAPE_SCRIPT = """// Implement scrape(urls) function
// urls is an array of URL strings
// Use fetch(url, options?) for HTTP requests
// fetch() returns { status, ok, text(), json() }
// Return { urls: [{ url, content, metadata?: { title?, description?, language? } }] }

function scrape(urls) {
  return {
    urls: urls.map(function(url) {
      const res = fetch(url);
      const body = res.text();
      return { url: url, content: body };
    })
  };
}"""

            const val DEFAULT_SEARCH_SCRIPT = """// Implement search(query, resultSize) function
// Use fetch(url, options?) for HTTP requests
// fetch() returns { status, ok, text(), json() }
// Return { items: [{ title, url, text }], answer?: string }

function search(query, resultSize) {
  const encoded = encodeURIComponent(query);
  const res = fetch("https://example.com/search?q=" + encoded + "&limit=" + resultSize);
  const data = res.json();
  return {
    items: data.results.map(function(r) {
      return { title: r.title, url: r.url, text: r.snippet };
    })
  };
}"""
        }
    }

    @Serializable
    @SerialName("deepseek")
    data class DeepSeekOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchOptions()
}

@Serializable
enum class DoubaoSearchMode {
    @SerialName("global")
    GLOBAL,

    @SerialName("custom")
    CUSTOM,
}

internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { cause, _, _ ->
                    response.closeQuietly()
                }
            }
        })
    }
}
