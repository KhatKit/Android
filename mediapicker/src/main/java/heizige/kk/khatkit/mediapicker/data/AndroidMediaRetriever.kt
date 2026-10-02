package heizige.kk.khatkit.mediapicker.data

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.Album
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.domain.MediaOrder
import heizige.kk.khatkit.mediapicker.domain.MediaRetriever
import heizige.kk.khatkit.mediapicker.domain.OrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * [MediaRetriever] 的 MediaStore 实现。
 *
 * 每个查询都挂在 [contentFlowObserver] 上：首次订阅发一次数据，之后相册有变化
 * 自动重查。查询本身跑在 [Dispatchers.IO]。
 */
class AndroidMediaRetriever(context: Context) : MediaRetriever {

    private val appContext = context.applicationContext

    override fun getAlbumsWithType(allowedMedia: AllowedMedia): Flow<Result<List<Album>>> =
        appContext.retrieveAlbums { resolver ->
            val query = Query.AlbumQuery().copy(
                bundle = selectionBundle {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.MediaColumns.MIME_TYPE} like ?",
                    )
                    putStringArray(
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        arrayOf(allowedMedia.mimePattern()),
                    )
                },
            )
            val fileQuery = Query.AlbumQuery().copy(
                bundle = extensionSelectionBundle(getSupportedFileSequence(allowedMedia).toList()),
            )
            resolver.getAlbums(
                mediaQuery = query,
                fileQuery = fileQuery,
                mediaOrder = MediaOrder.Date(OrderType.Descending),
            )
        }

    override fun mediaFlowWithType(
        albumId: Long,
        allowedMedia: AllowedMedia,
    ): Flow<Result<List<Media>>> = if (albumId != ALL_ALBUM_ID) {
        getMediaByAlbumIdWithType(albumId, allowedMedia)
    } else {
        getMediaByType(allowedMedia)
    }.flowOn(Dispatchers.IO).conflate()

    override fun getMediaByAlbumIdWithType(
        albumId: Long,
        allowedMedia: AllowedMedia,
    ): Flow<Result<List<Media>>> = appContext.retrieveMedia { resolver ->
        val mimeType = allowedMedia.mimePattern()
        val extensions = getSupportedFileSequence(allowedMedia).toList()
        val query = Query.MediaQuery().copy(
            bundle = selectionBundle {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.BUCKET_ID} = ? and (" +
                        "${MediaStore.MediaColumns.MIME_TYPE} like ? OR " +
                        "${MediaStore.MediaColumns.DATA} LIKE ? OR " +
                        "${MediaStore.MediaColumns.DATA} LIKE ?)",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(albumId.toString(), mimeType, "%.jxl", "%.qoi"),
                )
            },
        )
        // 扩展名补捞（jxl/qoi 这类 MediaStore 不认识的格式）也要按相册过滤，
        // 但扩展名列表为空时不能拼出 "BUCKET_ID = ? and ()" 这种坏 SQL。
        val fileQuery = Query.MediaQuery().copy(
            bundle = extensionSelectionBundle(extensions, albumId = albumId),
        )
        resolver.getMedia(mediaQuery = query, fileQuery = fileQuery)
    }

    override fun getMediaByType(allowedMedia: AllowedMedia): Flow<Result<List<Media>>> =
        appContext.retrieveMedia { resolver ->
            val mediaQuery = when (allowedMedia) {
                is AllowedMedia.Photos -> Query.PhotoQuery()
                AllowedMedia.Videos -> Query.VideoQuery()
                AllowedMedia.Both -> Query.MediaQuery()
            }
            val fileQuery = Query.FileQuery(getSupportedFileSequence(allowedMedia).toList())
            resolver.getMedia(mediaQuery = mediaQuery, fileQuery = fileQuery)
        }

    private fun AllowedMedia.mimePattern(): String = when (this) {
        is AllowedMedia.Photos -> "image%"
        AllowedMedia.Videos -> "video%"
        AllowedMedia.Both -> "%/%"
    }

    private fun Context.retrieveMedia(
        dataBody: suspend (ContentResolver) -> List<Media>,
    ): Flow<Result<List<Media>>> = contentFlowObserver()
        .map { runSuspendCatching { dataBody(contentResolver) } }
        .flowOn(Dispatchers.IO)
        .conflate()

    private fun Context.retrieveAlbums(
        dataBody: suspend (ContentResolver) -> List<Album>,
    ): Flow<Result<List<Album>>> = contentFlowObserver()
        .map { runSuspendCatching { dataBody(contentResolver) } }
        .flowOn(Dispatchers.IO)
        .conflate()
}

/** 带默认排序的 selection bundle。 */
private fun selectionBundle(block: Bundle.() -> Unit): Bundle =
    Query.defaultBundle().apply(block)

/**
 * 按扩展名补捞的 selection bundle。
 *
 * 列表为空（例如只查视频时没有任何额外格式）时**不带 selection**：
 * 空 `or` 串会拼出 `BUCKET_ID = ? and ()` 这种非法 SQL，MediaProvider 只会报
 * 一个无从下手的错。
 */
private fun extensionSelectionBundle(
    extensions: List<String>,
    albumId: Long? = null,
): Bundle = Query.defaultBundle().apply {
    if (extensions.isEmpty()) return@apply
    val extensionClause = extensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DATA} LIKE ?" }
    if (albumId == null) {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, extensionClause)
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, extensions.toTypedArray())
    } else {
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.MediaColumns.BUCKET_ID} = ? and ($extensionClause)",
        )
        putStringArray(
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            arrayOf(albumId.toString(), *extensions.toTypedArray()),
        )
    }
}
