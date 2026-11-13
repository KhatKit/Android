// SPDX-License-Identifier: AGPL-3.0-or-later
package heizige.kk.khatkit.app.feature.yunx

import com.yunx.app.data.download.DashMuxer
import com.yunx.app.data.download.HlsDownloader
import com.yunx.app.data.network.HttpClients
import heizige.kk.khatkit.bridge.impl.MediaDownloadHandler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File

/** Invoked by KhatKit's existing queue, notification, cancellation and persistence lifecycle. */
class YunxMediaDownloadHandler : MediaDownloadHandler {
    override suspend fun download(
        url: String, headers: Map<String, String>, hls: Boolean,
        audioUrl: String, audioHeaders: Map<String, String>, target: File,
        onBytes: suspend (Long) -> Unit,
    ) {
        if (hls) {
            check(HlsDownloader.download(url, headers, target, onBytes)) {
                "HLS 下载失败（加密流或特殊分段格式暂不支持）"
            }
            currentCoroutineContext().ensureActive()
            return
        }
        val video = File(target.path + ".video")
        val audio = File(target.path + ".audio")
        try {
            fetch(url, headers, video, onBytes)
            fetch(audioUrl, audioHeaders, audio, onBytes)
            currentCoroutineContext().ensureActive()
            check(DashMuxer.mux(video, audio, target)) { "音视频合轨失败，请重新下载或选择其他清晰度" }
            currentCoroutineContext().ensureActive()
        } finally {
            video.delete()
            audio.delete()
        }
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, target: File, onBytes: suspend (Long) -> Unit) {
        val initial = url.toHttpUrl()
        require(initial.isHttps) { "媒体下载需要 HTTPS" }
        var current = initial
        val client = HttpClients.apiClient().newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        repeat(6) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(current).apply {
                headers.forEach { (key, value) ->
                    val sensitive = key.lowercase() in setOf("cookie", "authorization", "proxy-authorization")
                    if (!sensitive || (current.host == initial.host && current.port == initial.port)) header(key, value)
                }
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    current = current.resolve(response.header("Location") ?: error("媒体重定向缺少地址"))
                        ?.takeIf { it.isHttps } ?: error("媒体重定向地址无效")
                } else {
                    check(response.isSuccessful) { "媒体下载失败：HTTP ${response.code}" }
                    target.outputStream().use { out ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                onBytes(read.toLong())
                            }
                        }
                    }
                    return
                }
            }
        }
        error("媒体重定向次数过多")
    }
}
