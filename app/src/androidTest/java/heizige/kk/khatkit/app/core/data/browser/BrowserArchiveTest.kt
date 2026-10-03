package heizige.kk.khatkit.app.core.data.browser

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BrowserArchiveTest {
    @Test
    fun corruptLegacyFileDoesNotBlockNewDataOrReappearAfterClear() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacy = File(context.cacheDir, "browser-corrupt-${UUID.randomUUID()}.json")
        val database = Room.inMemoryDatabaseBuilder(context, BrowserArchiveDatabase::class.java).build()
        try {
            legacy.writeText("{broken")
            val archive = RoomBrowserArchive(database, legacy)
            archive.record("https://new.example", "New", 1)
            archive.bookmark("https://new.example", "Saved", 2)
            assertEquals("https://new.example", archive.history().single().url)
            assertEquals("Saved", archive.bookmarks().single().title)
            assertEquals("{broken", legacy.readText())
            assertEquals("failed", database.archive().metadata("json_history_import_v1"))
            archive.clearHistory()
            BrowserHistoryStore(legacy).record("https://old.example", "Old", 0)
            assertTrue(archive.history().isEmpty())
        } finally {
            database.close()
            legacy.delete()
        }
    }

    @Test
    fun migrationSkipsUnsafeUrlsAndKeepsNewestDuplicate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacy = File(context.cacheDir, "browser-migration-${UUID.randomUUID()}.json")
        val database = Room.inMemoryDatabaseBuilder(context, BrowserArchiveDatabase::class.java).build()
        try {
            legacy.writeText("""[
                {"url":"https://one.example","title":"Old","visitedAt":1},
                {"url":"file:///private","title":"Invalid","visitedAt":9},
                {"url":"https://one.example","title":"New","visitedAt":3}
            ]""")
            val archive = RoomBrowserArchive(database, legacy)
            assertEquals(BrowserHistoryEntry("https://one.example", "New", 3), archive.history().single())
        } finally {
            database.close()
            legacy.delete()
        }
    }

    @Test
    fun importsOnceAndPreservesBookmarksAcrossDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "browser-archive-test-${UUID.randomUUID()}.db"
        val legacy = File(context.cacheDir, "$name.json")
        BrowserHistoryStore(legacy).apply {
            record("https://one.example", "One", 1)
            record("https://two.example", "Two", 2)
        }
        fun open() = Room.databaseBuilder(context, BrowserArchiveDatabase::class.java, name).build()
        var database = open()
        try {
            var archive = RoomBrowserArchive(database, legacy, maxHistory = 2)
            assertEquals(listOf("https://two.example", "https://one.example"), archive.history().map { it.url })
            archive.bookmark("https://one.example", "Keep me", 3)
            archive.bookmark("https://one.example", "Updated", 4)
            archive.record("https://three.example", "Three", 5)
            assertEquals(listOf("https://three.example", "https://two.example"), archive.history().map { it.url })
            database.close()
            database = open()
            archive = RoomBrowserArchive(database, legacy, maxHistory = 2)
            assertEquals("Updated", archive.bookmarks().single().title)
            assertEquals(3L, archive.bookmarks().single().createdAt)
            archive.clearHistory()
            database.close()
            database = open()
            archive = RoomBrowserArchive(database, legacy, maxHistory = 2)
            assertTrue(archive.history().isEmpty()) // Old JSON must not reappear after clearing.
            assertTrue(legacy.isFile) // Migration preserves the recoverable source.
            assertEquals(1, archive.bookmarks().size)
            assertTrue(archive.removeBookmark("https://one.example"))
            assertFalse(archive.removeBookmark("https://one.example"))
        } finally {
            database.close()
            context.deleteDatabase(name)
            legacy.delete()
        }
    }

    @Test
    fun concurrentVisitsRemainUniqueAndBounded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, BrowserArchiveDatabase::class.java).build()
        try {
            val archive = RoomBrowserArchive(database, null, maxHistory = 10)
            coroutineScope {
                (0..29).map { index -> async {
                    archive.record("https://site-$index.example", "Site $index", index.toLong())
                } }.awaitAll()
            }
            assertEquals((29 downTo 20).map { "https://site-$it.example" }, archive.history().map { it.url })
            archive.record("https://site-29.example", "Changed", 100)
            assertEquals(10, archive.history().size)
            assertEquals("Changed", archive.history().first().title)
        } finally {
            database.close()
        }
    }
}
