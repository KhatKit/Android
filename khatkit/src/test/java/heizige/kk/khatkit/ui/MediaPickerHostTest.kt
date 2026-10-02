package heizige.kk.khatkit.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPickerHostTest {

    @Test
    fun `options default to single media type without declaration`() {
        val options = PickMediaOptions.from(null)
        assertEquals(PickMediaKind.IMAGE, options.kind)
        assertTrue(options.multiple)
        assertEquals(PickMediaOptions.DEFAULT_MAX, options.max)
        assertEquals(PickMediaOptions(), PickMediaOptions.from(emptyMap()))
    }

    @Test
    fun `media multiple and max parsed from loose types`() {
        val options = PickMediaOptions.from(mapOf("media" to "Video", "multiple" to 1, "max" to 3))
        assertEquals(PickMediaKind.VIDEO, options.kind)
        assertTrue(options.multiple)
        assertEquals(3, options.max)
        assertEquals(PickMediaKind.ANY, PickMediaOptions.from(mapOf("media" to "any")).kind)
        assertEquals(PickMediaKind.IMAGE, PickMediaOptions.from(mapOf("media" to "gif")).kind)
    }

    @Test
    fun `max clamped and forced to one when not multiple`() {
        assertEquals(PickMediaOptions.MAX_COUNT, PickMediaOptions.from(mapOf("max" to 9999)).max)
        assertEquals(1, PickMediaOptions.from(mapOf("max" to 0)).max)
        assertEquals(1, PickMediaOptions.from(mapOf("multiple" to false, "max" to 8)).max)
        assertEquals(1, PickMediaOptions.from(mapOf("multiple" to 0)).max)
    }

    @Test
    fun `pick blocks until host submits and returns imported paths`() = runBlocking {
        val imported = mutableListOf<Pair<String, List<String>>>()
        val host = MediaPickerHost(importToCache = { card, uris ->
            imported += card to uris
            uris.map { "/cache/$card/$it" }
        })

        val paths = async(Dispatchers.Default) {
            host.pick("快递卡片", mapOf("media" to "video", "multiple" to false))
        }

        val request = withTimeout(2_000) { host.request.filterNotNull().first() }
        assertEquals("快递卡片", request.cardName)
        assertEquals(PickMediaKind.VIDEO, request.options.kind)
        assertEquals(1, request.options.max)

        host.submit(listOf("content://media/1"))

        assertEquals(listOf("/cache/快递卡片/content://media/1"), withTimeout(2_000) { paths.await() })
        assertEquals("快递卡片" to listOf("content://media/1"), imported.single())
        assertNull(host.request.value)
    }

    @Test
    fun `dismiss returns empty list and clears request`() = runBlocking {
        val host = MediaPickerHost(importToCache = { _, uris -> uris })
        val job = launch(Dispatchers.Default) { host.pick("卡片", null) }
        withTimeout(2_000) { host.request.filterNotNull().first() }
        host.dismiss()
        withTimeout(2_000) { job.join() }
        assertNull(host.request.value)
    }

    @Test
    fun `timeout returns empty list instead of blocking forever`() {
        val host = MediaPickerHost(importToCache = { _, uris -> uris }, timeoutMillis = 50)
        assertEquals(emptyList<String>(), host.pick("卡片", null))
        assertNull(host.request.value)
    }

    @Test
    fun `forCard view imports into the bound card directory`() = runBlocking {
        val seen = mutableListOf<String>()
        val host = MediaPickerHost(importToCache = { card, _ -> seen += card; emptyList() })
        val scoped = host.forCard("天气卡片")

        val job = launch(Dispatchers.Default) { scoped.pickMedia(mapOf("media" to "image")) }
        withTimeout(2_000) { host.request.filterNotNull().first() }
        host.submit(listOf("content://1"))
        withTimeout(2_000) { job.join() }

        assertEquals(listOf("天气卡片"), seen)
    }
}