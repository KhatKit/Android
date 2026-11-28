package heizige.kk.khatkit.app.feature.settings.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DonationRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val provider: KhatKitToolProvider,
) {
    private val cache = context.getSharedPreferences("khatkit_sponsors", Context.MODE_PRIVATE)
    private val source = DonationDataSource(
        readCache = { cache.getString(it, null) },
        writeCache = { key, value -> cache.edit().putString(key, value).apply() },
        fetch = { url ->
            val response = client.get(url)
            check(response.status.isSuccess()) { "Sponsor endpoint returned HTTP "+response.status.value }
            response.bodyAsText().also { require(it.length <= 1_048_576) }
        },
    )

    internal fun baseUrl(): String = provider.hubBaseUrl.trim().trimEnd('/')
    internal suspend fun cached(base: String): DonationSnapshot? = withContext(Dispatchers.IO) { source.cached(base) }
    internal suspend fun refresh(base: String): DonationSnapshot = withContext(Dispatchers.IO) { source.refresh(base) }
    internal suspend fun supporters(base: String, offset: Int): DonationSupporterPage = withContext(Dispatchers.IO) { source.supporters(base, offset) }

    private companion object {
        val client by lazy {
            HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 8_000
                    socketTimeoutMillis = 8_000
                }
            }
        }
    }
}

/** Transport and cache boundaries are injectable so failure behaviour is tested without Android. */
internal class DonationDataSource(
    private val readCache: (String) -> String?,
    private val writeCache: (String, String) -> Unit,
    private val fetch: suspend (String) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun cached(base: String): DonationSnapshot? = try {
        val entry = readCache(cacheKey(base))?.let { json.decodeFromString<CacheEntry>(it) }
        entry?.takeIf { now() - it.savedAt in 0..86_400_000L }?.snapshot?.validated()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    suspend fun refresh(base: String): DonationSnapshot {
        val snapshot = json.decodeFromString<DonationSnapshot>(fetch(endpoint(base, "/api/sponsors"))).validated()
        // A local cache write failure must not discard a successful server response.
        try {
            writeCache(cacheKey(base), json.encodeToString(CacheEntry(now(), snapshot)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next visit can fetch again.
        }
        return snapshot
    }

    suspend fun supporters(base: String, offset: Int, limit: Int = 50): DonationSupporterPage {
        require(offset >= 0 && limit in 1..100)
        return json.decodeFromString<DonationSupporterPage>(
            fetch(endpoint(base, "/api/sponsors/acknowledgements?limit=$limit&offset=$offset")),
        ).also {
            require(it.offset == offset && it.limit == limit && it.total >= 0 && it.items.size <= limit)
            require(it.items.isNotEmpty() || offset >= it.total)
            require(it.items.isEmpty() || it.total.toLong() >= offset.toLong() + it.items.size)
            validateSupporters(it.items)
        }
    }

    private fun endpoint(base: String, path: String): String {
        require(isDonationUrl(base))
        val uri = java.net.URI(base)
        require(uri.rawQuery == null && uri.rawFragment == null)
        return base.trimEnd('/') + path
    }

    private fun cacheKey(base: String): String = MessageDigest.getInstance("SHA-256")
        .digest(base.trimEnd('/').toByteArray()).joinToString("") { "%02x".format(it) }

    @Serializable
    private data class CacheEntry(val savedAt: Long, val snapshot: DonationSnapshot)
}
