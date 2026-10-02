package heizige.kk.khatkit.mediapicker.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.MergeCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.domain.MediaOrder
import heizige.kk.khatkit.mediapicker.domain.OrderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import java.io.File

private val observedUris: Array<Uri> = arrayOf(
    mediaStoreImagesUri(),
    mediaStoreVideosUri(),
    mediaStoreFilesUri(),
)

/**
 * 包一层 `Result` 但**不吞掉协程取消**。
 *
 * MediaStore 查询里排着 `ensureActive()`（见 [MediaOrder.sortMedia]），用普通
 * `runCatching` 会把 `CancellationException` 变成 `Result.failure`，协程照常往下跑，
 * 于是切相册时旧 job 还能把一帧空结果写进状态。进出各 `ensureActive()` 一次。
 */
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
    currentCoroutineContext().ensureActive()
    return try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
}

/**
 * MediaStore 变更监听：订阅即发一次 `true`（首次加载），
 * 之后每次 provider 变更发 `false`，UI 侧据此重查。
 *
 * 变更很密集（相册批量导入会连发几十次），所以这里 `conflate` + 1s 节流。
 * 回调可能在 binder 线程上，所以只 `trySend`，不在回调里直接跑查询。
 */
fun Context.contentFlowObserver(
    uris: Array<Uri> = observedUris,
): Flow<Boolean> = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(false)
        }
    }
    uris.forEach { contentResolver.registerContentObserver(it, true, observer) }
    trySend(true)
    awaitClose { contentResolver.unregisterContentObserver(observer) }
}.conflate().onEach { if (!it) delay(1000) }

suspend fun ContentResolver.getMedia(
    mediaQuery: Query = Query.MediaQuery(),
    fileQuery: Query = Query.MediaQuery(),
    mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending),
): List<Media> = coroutineScope {
    val media = mutableListOf<Media>()
    queryMergedCursors(mediaQuery, fileQuery).use { cursor ->
        while (cursor.moveToNext()) {
            // 单行解析失败不能中断整表：厂商定制列缺失时这里是最常见的失败点。
            // 只 catch Exception —— OOM 这类 Error 必须往上抛。
            try {
                media.add(cursor.getMediaFromCursor())
            } catch (_: Exception) {
                // 跳过这一行
            }
        }
    }
    mediaOrder.sortMedia(media)
}

/**
 * 图片库 / 文件库 / 视频库三路合并 —— 图片与视频要各查各的，
 * 混在一个 cursor 里按同一条排序键排会乱。
 *
 * `ContentResolver.query` 是可空的，被拒绝或某些定制 ROM 会返回 null。
 * `MergeCursor` 只在 `getCount`/`onMove` 上做了 null 保护，`getColumnIndex` 仍会
 * 落到第一个子 cursor（可能是 null）上取列名，直接 NPE 或让所有列索引变成 -1。
 * 所以先把 null 剔掉，全空就抛出去让上层显示「重试」。
 *
 * 中途抛异常时也要把已经打开的 cursor 关掉，不能泄漏。
 */
internal fun ContentResolver.queryMergedCursors(
    mediaQuery: Query,
    fileQuery: Query,
): Cursor {
    val opened = mutableListOf<Cursor>()
    try {
        listOf(
            mediaStoreImagesUri() to mediaQuery,
            mediaStoreFilesUri() to fileQuery,
            mediaStoreVideosUri() to mediaQuery,
        ).forEach { (uri, query) ->
            query(uri, query.projection, query.bundle, null)?.let(opened::add)
        }
    } catch (error: Exception) {
        opened.forEach { runCatching { it.close() } }
        throw error
    }
    check(opened.isNotEmpty()) { "MediaStore returned no readable cursor" }
    return MergeCursor(opened.toTypedArray())
}

fun Cursor.getMediaFromCursor(): Media {
    fun string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    fun long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    fun positiveInt(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index).takeIf { it > 0 } else null
    }

    val id = long(MediaStore.MediaColumns._ID) ?: 0L
    val path = string(MediaStore.MediaColumns.DATA).orEmpty()
    val relativePath = string(relativePathOrDataColumn()).orEmpty()
    val title = string(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty()
    val albumId = long(MediaStore.MediaColumns.BUCKET_ID) ?: 0L
    val albumLabel = string(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) ?: Build.MODEL
    val modified = long(MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L

    val (mimeType, contentUri) = SUPPORTED_FILES[File(path).extension.lowercase()]?.let { mime ->
        Pair(mime, mediaStoreFilesUri())
    } ?: run {
        val mime = string(MediaStore.MediaColumns.MIME_TYPE).orEmpty()
        Pair(
            mime,
            if (mime.contains("image")) mediaStoreImagesUri() else mediaStoreVideosUri(),
        )
    }

    return Media(
        id = id,
        label = title,
        uri = ContentUris.withAppendedId(contentUri, id).toString(),
        path = path,
        relativePath = relativePath,
        albumID = albumId,
        albumLabel = albumLabel,
        timestamp = modified,
        takenTimestamp = long(MediaStore.MediaColumns.DATE_TAKEN),
        mimeType = mimeType,
        width = positiveInt(MediaStore.MediaColumns.WIDTH),
        height = positiveInt(MediaStore.MediaColumns.HEIGHT),
        size = long(MediaStore.MediaColumns.SIZE),
    )
}
