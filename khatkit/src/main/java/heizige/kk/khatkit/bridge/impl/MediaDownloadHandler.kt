package heizige.kk.khatkit.bridge.impl

import java.io.File

/** Optional container handling within a normal host download task. */
fun interface MediaDownloadHandler {
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        hls: Boolean,
        audioUrl: String,
        audioHeaders: Map<String, String>,
        target: File,
        onBytes: suspend (Long) -> Unit,
    )
}
