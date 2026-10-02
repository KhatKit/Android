package heizige.kk.khatkit.mediapicker.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import heizige.kk.khatkit.mediapicker.domain.Album
import heizige.kk.khatkit.mediapicker.domain.MediaOrder
import heizige.kk.khatkit.mediapicker.domain.OrderType
import kotlinx.coroutines.coroutineScope

/** `RELATIVE_PATH` 是 API 29+，旧版本用 `DATA` 顶替。 */
internal fun relativePathOrDataColumn(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.MediaColumns.RELATIVE_PATH
    } else {
        MediaStore.MediaColumns.DATA
    }

/**
 * MediaStore 查询描述。minSdk 26，可以无条件用 `Bundle` + `QUERY_ARG_*` 传 selection/sort。
 */
sealed class Query(
    var projection: Array<String>,
    var bundle: Bundle? = null,
) {
    class MediaQuery : Query(projection = mediaProjection())

    class PhotoQuery : Query(
        projection = mediaProjection(),
        bundle = defaultBundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.MIME_TYPE} like ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("image%"))
        },
    )

    class VideoQuery : Query(
        projection = videoProjection(),
        bundle = defaultBundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.MIME_TYPE} like ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("video%"))
        },
    )

    class AlbumQuery : Query(
        projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            relativePathOrDataColumn(),
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        ),
    )

    /** MediaStore 认不出的格式（jxl/qoi…）只能从 `MediaStore.Files` 里捞。 */
    class FileQuery(fileExtensions: List<String>) : Query(
        projection = mediaProjection(),
        bundle = defaultBundle().apply {
            if (fileExtensions.isEmpty()) return@apply
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                fileExtensions.joinToString(" or ") { "${MediaStore.MediaColumns.DATA} like ?" },
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, fileExtensions.toTypedArray())
        },
    )

    fun copy(
        projection: Array<String> = this.projection,
        bundle: Bundle? = this.bundle,
    ): Query {
        this.projection = projection
        this.bundle = bundle
        return this
    }

    internal fun asAlbumQuery(): Query = copy(
        bundle = (bundle ?: Bundle()).apply {
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
            )
        },
    )

    companion object {
        /**
         * 每份查询都要自己的一份 [Bundle]：`Bundle.apply` 会就地改写，
         * 共用同一个实例的话，先构造的 `PhotoQuery` 会把 `mime_type like 'image%'`
         * 漏给后面构造的 `MediaQuery`。
         */
        fun defaultBundle(): Bundle = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
            )
            putInt(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
        }

        private fun mediaProjection(): Array<String> = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            relativePathOrDataColumn(),
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        ).distinct().toTypedArray()

        private fun videoProjection(): Array<String> = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            relativePathOrDataColumn(),
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        ).distinct().toTypedArray()
    }
}

/**
 * 扫一遍全表，按 `BUCKET_ID` 聚成相册：取每个相册最新的一张做封面，累加 `count`。
 */
suspend fun ContentResolver.getAlbums(
    mediaQuery: Query = Query.AlbumQuery(),
    fileQuery: Query = Query.AlbumQuery(),
    mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending),
): List<Album> = coroutineScope {
    val albums = mutableListOf<Album>()

    queryMergedCursors(mediaQuery.asAlbumQuery(), fileQuery.asAlbumQuery()).use { cursor ->
        val bucketIdIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val labelIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val relativeIndex = cursor.getColumnIndex(relativePathOrDataColumn())
        val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

        while (cursor.moveToNext()) {
            if (bucketIdIndex < 0 || idIndex < 0) continue
            try {
                val bucketId = cursor.getLong(bucketIdIndex)
                val id = cursor.getLong(idIndex)
                val label = (if (labelIndex >= 0) cursor.getString(labelIndex) else null)
                    ?: Build.MODEL
                val thumbnailPath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                val relativePath = if (relativeIndex >= 0) cursor.getString(relativeIndex).orEmpty() else ""
                val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                val contentUri =
                    if (mimeType.contains("image")) mediaStoreImagesUri() else mediaStoreVideosUri()

                val album = Album(
                    id = bucketId,
                    label = label,
                    uri = ContentUris.withAppendedId(contentUri, id).toString(),
                    pathToThumbnail = thumbnailPath,
                    relativePath = relativePath,
                    timestamp = date,
                    count = 1,
                )

                val index = albums.indexOfFirst { it.id == bucketId }
                if (index < 0) {
                    albums.add(album)
                } else {
                    val current = albums[index]
                    albums[index] = if (album.timestamp > current.timestamp) {
                        album.copy(count = current.count + 1)
                    } else {
                        current.copy(count = current.count + 1)
                    }
                }
            } catch (_: Exception) {
                // 单行解析失败就跳过，不能因为一个坏相册丢掉整张列表
            }
        }
    }

    mediaOrder.sortAlbums(albums)
}
