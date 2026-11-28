package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.feature.settings.data.DonationDataSource
import heizige.kk.khatkit.app.feature.settings.data.isDonationUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DonationRepositoryTest {
    private val base = "https://hub.example"
    private val valid = """{"profile":{"name":"KhatKit","projectUrl":"https://github.com/KhatKit/Android","websiteUrl":"https://heizige.top"},"channels":[{"id":"afdian","label":"Afdian","url":"https://afdian.com/a/test"}],"acknowledgements":[],"acknowledgementTotal":0}"""

    @Test fun failureKeepsLastGoodCacheAndDoesNotBecomeEmptySuccess() = runBlocking {
        val cache = mutableMapOf<String, String>()
        var networkFails = false
        val source = DonationDataSource(cache::get, { key, value -> cache[key] = value }, {
            if (networkFails) throw IOException("offline") else valid
        })
        assertEquals(1, source.refresh(base).channels.size)
        networkFails = true
        try { source.refresh(base); fail("Expected network failure") } catch (_: IOException) { }
        assertEquals(1, source.cached(base)?.channels?.size)
    }

    @Test fun cacheIsIsolatedByHubAndExpiresAfterOneDay() = runBlocking {
        val cache = mutableMapOf<String, String>()
        var time = 1_000L
        val source = DonationDataSource(cache::get, { key, value -> cache[key] = value }, { valid }, { time })
        source.refresh(base)
        assertNotNull(source.cached(base))
        assertNull(source.cached("https://another.example"))
        time += 86_400_001L
        assertNull(source.cached(base))
    }

    @Test fun malformedResponseDoesNotOverwriteGoodCache() = runBlocking {
        val cache = mutableMapOf<String, String>()
        var response = valid
        val source = DonationDataSource(cache::get, { key, value -> cache[key] = value }, { response })
        source.refresh(base)
        for (malformed in listOf("{}", """{"profile":{}}""", """{"profile":{"name":"KhatKit","projectUrl":"https://github.com/KhatKit/Android","websiteUrl":"https://heizige.top"}}""")) {
            response = malformed
            try { source.refresh(base); fail("Incomplete response must fail") } catch (_: kotlinx.serialization.SerializationException) { }
            assertEquals(1, source.cached(base)?.channels?.size)
        }
    }

    @Test fun unsafeDestinationsAreRejected() {
        listOf("javascript:alert(1)", "data:text/html,test", "https://user:pass@example.com", "https://example.com/\n").forEach {
            assertFalse(it, isDonationUrl(it, payment = true))
        }
        assertFalse(isDonationUrl("wxp://caseSensitive"))
        assertTrue(isDonationUrl("wxp://caseSensitive", payment = true))
        assertTrue(isDonationUrl("alipays://platformapi/startapp?appId=1", payment = true))
    }

    @Test fun paginationPreservesCursorAndValidatesServerResponse() = runBlocking {
        var requested = ""
        val source = DonationDataSource({ null }, { _, _ -> }, { url ->
            requested = url
            """{"items":[{"id":"s-2","name":"Consenting supporter"}],"total":51,"limit":50,"offset":50}"""
        })
        assertEquals("s-2", source.supporters(base, 50).items.single().id)
        assertEquals("$base/api/sponsors/acknowledgements?limit=50&offset=50", requested)
        try { source.supporters(base, 0); fail("Mismatched cursor must fail") } catch (_: IllegalArgumentException) { }
    }

    @Test fun cancellationPropagatesAndDoesNotWriteCache() = runBlocking {
        var writes = 0
        val source = DonationDataSource({ null }, { _, _ -> writes++ }, { throw CancellationException("cancelled") })
        try { source.refresh(base); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(0, writes)
    }

    @Test fun cacheWriteFailureDoesNotHideSuccessfulResponse() = runBlocking {
        val source = DonationDataSource({ null }, { _, _ -> throw IOException("disk full") }, { valid })
        assertEquals(1, source.refresh(base).channels.size)
    }

    @Test fun explicitEmptyResponseIsAValidLoadedResult() = runBlocking {
        val source = DonationDataSource({ "invalid cache" }, { _, _ -> }, { """{"profile":{"name":"KhatKit","projectUrl":"https://github.com/KhatKit/Android","websiteUrl":"https://heizige.top"},"channels":[],"acknowledgements":[],"acknowledgementTotal":0}""" })
        assertNull(source.cached(base))
        assertEquals(0, source.refresh(base).acknowledgementTotal)
    }
}
