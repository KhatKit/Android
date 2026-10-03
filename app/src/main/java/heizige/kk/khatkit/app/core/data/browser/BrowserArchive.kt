package heizige.kk.khatkit.app.core.data.browser

import android.content.Context
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

interface BrowserArchive {
    suspend fun record(url: String, title: String?, now: Long = System.currentTimeMillis())
    suspend fun history(): List<BrowserHistoryEntry>
    suspend fun clearHistory()
    suspend fun removeHistory(urls: List<String>) {
        error("Selective history deletion is unavailable")
    }
    suspend fun bookmarks(): List<BrowserBookmark>
    suspend fun bookmark(url: String, title: String?, now: Long = System.currentTimeMillis())
    suspend fun removeBookmark(url: String): Boolean
}

@Serializable
@Entity(tableName = "browser_bookmarks")
data class BrowserBookmark(
    @PrimaryKey val url: String,
    val title: String?,
    val createdAt: Long,
)

@Entity(tableName = "browser_history", indices = [Index("visitedAt")])
data class BrowserVisit(
    @PrimaryKey val url: String,
    val title: String?,
    val visitedAt: Long,
)

@Entity(tableName = "browser_archive_metadata")
data class BrowserArchiveMetadata(@PrimaryKey val key: String, val value: String)

@Dao
interface BrowserArchiveDao {
    @Upsert
    suspend fun record(visit: BrowserVisit)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importVisit(visit: BrowserVisit)

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC, url ASC")
    suspend fun history(): List<BrowserVisit>

    @Query("DELETE FROM browser_history WHERE url NOT IN (SELECT url FROM browser_history ORDER BY visitedAt DESC, url ASC LIMIT :limit)")
    suspend fun trimHistory(limit: Int)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()

    @Query("DELETE FROM browser_history WHERE url IN (:urls)")
    suspend fun removeHistory(urls: List<String>)

    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC, url ASC")
    suspend fun bookmarks(): List<BrowserBookmark>

    @Query("SELECT * FROM browser_bookmarks WHERE url = :url")
    suspend fun bookmark(url: String): BrowserBookmark?

    @Upsert
    suspend fun bookmark(bookmark: BrowserBookmark)

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun removeBookmark(url: String): Int

    @Query("SELECT value FROM browser_archive_metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?

    @Upsert
    suspend fun metadata(entry: BrowserArchiveMetadata)
}

@Database(
    entities = [BrowserVisit::class, BrowserBookmark::class, BrowserArchiveMetadata::class],
    version = 1,
    exportSchema = true,
)
abstract class BrowserArchiveDatabase : RoomDatabase() {
    abstract fun archive(): BrowserArchiveDao
}

/** A dedicated database avoids coupling browser retention to conversation migrations. */
class RoomBrowserArchive(
    private val database: BrowserArchiveDatabase,
    private val legacyHistory: File?,
    private val maxHistory: Int = 200,
) : BrowserArchive {
    init { require(maxHistory > 0) }

    private suspend fun <T> transaction(block: suspend (BrowserArchiveDao) -> T): T =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val dao = database.archive()
                if (dao.metadata(LEGACY_IMPORT) == null) {
                    // Leave the source intact even when corrupt. A bad legacy file must not
                    // prevent new visits/bookmarks from being saved or history from being cleared.
                    val entries = readLegacyHistory()
                    entries.orEmpty().sortedByDescending { it.visitedAt }.forEach {
                        if (runCatching { BrowserSessionManager.validateBrowserUrl(it.url) }.isFailure) {
                            return@forEach
                        }
                        dao.importVisit(BrowserVisit(it.url, it.title, it.visitedAt))
                    }
                    dao.trimHistory(maxHistory)
                    dao.metadata(BrowserArchiveMetadata(LEGACY_IMPORT, if (entries == null) "failed" else "complete"))
                }
                block(dao)
            }
        }

    private fun readLegacyHistory(): List<BrowserHistoryEntry>? = try {
        legacyHistory?.takeIf { it.isFile }?.let {
            require(it.length() <= 4 * 1024 * 1024) { "Legacy browser history is too large" }
            Json.decodeFromString<List<BrowserHistoryEntry>>(it.readText())
        }.orEmpty()
    } catch (_: IllegalArgumentException) {
        Log.w("BrowserArchive", "Legacy history could not be decoded; original file preserved")
        null
    } catch (_: IOException) {
        Log.w("BrowserArchive", "Legacy history could not be read; original file preserved")
        null
    }

    override suspend fun record(url: String, title: String?, now: Long) {
        BrowserSessionManager.validateBrowserUrl(url)
        transaction {
            it.record(BrowserVisit(url, title, now))
            it.trimHistory(maxHistory)
        }
    }

    override suspend fun history(): List<BrowserHistoryEntry> = transaction { dao ->
        dao.history().map { BrowserHistoryEntry(it.url, it.title, it.visitedAt) }
    }

    override suspend fun clearHistory() = transaction { it.clearHistory() }

    override suspend fun removeHistory(urls: List<String>) = transaction { it.removeHistory(urls) }

    override suspend fun bookmarks(): List<BrowserBookmark> = transaction { it.bookmarks() }

    override suspend fun bookmark(url: String, title: String?, now: Long) {
        BrowserSessionManager.validateBrowserUrl(url)
        transaction { dao ->
            val createdAt = dao.bookmark(url)?.createdAt ?: now
            dao.bookmark(BrowserBookmark(url, title, createdAt))
        }
    }

    override suspend fun removeBookmark(url: String): Boolean = transaction { it.removeBookmark(url) > 0 }

    companion object {
        private const val LEGACY_IMPORT = "json_history_import_v1"
        @Volatile private var instance: RoomBrowserArchive? = null

        fun get(context: Context): RoomBrowserArchive = instance ?: synchronized(this) {
            instance ?: RoomBrowserArchive(
                Room.databaseBuilder(
                    context.applicationContext, BrowserArchiveDatabase::class.java, "browser-archive.db",
                ).build(),
                File(context.applicationContext.filesDir, "browser/history.json"),
            ).also { instance = it }
        }
    }
}
