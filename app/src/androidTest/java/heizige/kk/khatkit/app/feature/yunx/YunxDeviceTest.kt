package heizige.kk.khatkit.app.feature.yunx

import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.bridge.impl.DownloadManagerImpl
import heizige.kk.khatkit.bridge.impl.MediaDownloadHandler
import heizige.kk.khatkit.yunx.YunxParserManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread

class YunxDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rejectsLookalikeHostsBeforeNetwork() {
        val parser = YunxParserManager(context)
        for (url in listOf("https://evil.test/?next=https://pan.quark.cn/s/abc",
            "https://pan.quark.cn@evil.test/s/abc", "https://pan.baidu.com.evil.test/s/1abc",
            "https://pan.quark.cn:1234/s/abc")) {
            val result = JSONObject(parser.call("detect", JSONObject().put("text", url).toString()))
            assertTrue(url, result.has("error"))
        }
        val good = JSONObject(parser.call("detect", """{"text":"https://pan.quark.cn/s/abc"}"""))
        assertEquals("quark", good.getString("provider"))
    }

    @Test fun hostQueuePreservesUaFallsBackAndRejectsHttpErrors() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        requests += lines.joinToString("\n")
                        val missing = lines.first().contains("/missing")
                        val range = if (lines.first().contains("/range")) {
                            lines.firstOrNull { it.startsWith("Range:", true) }?.substringAfter("bytes=")?.split("-")
                        } else null
                        val complete = "0123456789".repeat(13)
                        val body = if (range != null) complete.substring(range[0].toInt(), range[1].toInt() + 1)
                            else if (missing) "not found" else "YUNX_TEST_FILE"
                        socket.getOutputStream().write(
                            ("HTTP/1.1 ${if (range != null) "206 Partial Content" else if (missing) "404 Not Found" else "200 OK"}\r\n" +
                                (if (range != null) "Content-Range: bytes ${range[0]}-${range[1]}/${complete.length}\r\n" else "") +
                                "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                    }
                } catch (_: Exception) { if (!server.isClosed) throw AssertionError("test server failed") }
            }
        }
        val dir = File(context.cacheDir, "yunx-download-test-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = HttpClient(CIO)
        try {
            val queue = DownloadManagerImpl(context, scope, dir, client)
            val id = queue.start(mapOf("url" to "http://127.0.0.1:${server.localPort}/file",
                "name" to "test.txt", "threads" to 8, "headers" to mapOf("User-Agent" to "YunX-UA")))
            withTimeout(20_000) { while (queue.status(id, 0)["state"] !in listOf("done", "failed")) delay(100) }
            val result = queue.status(id, 0)
            assertEquals(result.toString(), "done", result["state"])
            assertEquals("YUNX_TEST_FILE", File(result["file"].toString()).readText())
            assertTrue(requests.any { it.contains("Range: bytes=0-0", true) })
            requests.forEach { assertTrue(it, it.contains("User-Agent: YunX-UA", true)); assertFalse(it, it.contains("KhatKit/1.0")) }
            val ranged = queue.start(mapOf("url" to "http://127.0.0.1:${server.localPort}/range",
                "name" to "ranged.txt", "threads" to 8))
            withTimeout(20_000) { while (queue.status(ranged, 0)["state"] !in listOf("done", "failed")) delay(100) }
            assertEquals("done", queue.status(ranged, 0)["state"])
            assertEquals("0123456789".repeat(13), File(queue.status(ranged, 0)["file"].toString()).readText())
            val failed = queue.start(mapOf("url" to "http://127.0.0.1:${server.localPort}/missing", "name" to "missing.txt"))
            withTimeout(20_000) { while (queue.status(failed, 0)["state"] !in listOf("done", "failed")) delay(100) }
            assertEquals("failed", queue.status(failed, 0)["state"])
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            client.close()
            server.close()
            worker.join(1000)
            dir.deleteRecursively()
        }
    }

    @Test fun mediaUsesSameHostTaskAndPersistsContainerMetadata() = runBlocking {
        val dir = File(context.cacheDir, "yunx-media-test-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = HttpClient(CIO)
        try {
            val handler = MediaDownloadHandler { _, _, hls, audio, audioHeaders, target, progress ->
                assertTrue(hls)
                assertEquals("https://example.org/audio", audio)
                assertEquals("example", audioHeaders["Referer"])
                target.writeText("MEDIA")
                progress(5)
            }
            val queue = DownloadManagerImpl(context, scope, dir, client, mediaDownloadHandler = handler)
            val id = queue.start(mapOf("url" to "https://example.org/video", "name" to "media.mp4",
                "hls" to true, "audio_url" to "https://example.org/audio", "audio_headers" to mapOf("Referer" to "example")))
            withTimeout(20_000) { while (queue.status(id, 0)["state"] !in listOf("done", "failed")) delay(100) }
            assertEquals("done", queue.status(id, 0)["state"])
            assertEquals(1, queue.list(null).size)
            withTimeout(5_000) {
                while (!File(dir, "index.json").let { it.exists() && it.readText().contains("\"state\":\"done\"") }) delay(100)
            }
            val persisted = File(dir, "index.json").readText()
            assertTrue(persisted.contains("\"hls\":true"))
            assertTrue(persisted.contains("https://example.org/audio"))
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            client.close()
            dir.deleteRecursively()
        }
    }
}
