package heizige.kk.khatkit.bridge.impl

import android.content.Context
import heizige.kk.khatkit.bridge.DownloadBridge
import heizige.kk.khatkit.bridge.DownloadHandle
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * download bridge 的宿主实现（设计文档 7.3）。
 *
 * 长任务模型：`enqueue` 立即返回 handle；下载跑在 App 级协程里，脚本退出后继续；
 * 断点续传用 HTTP Range；进度/速度按秒滚动；“策略层是天花板”由调用方在构造时
 * 注入（并发、是否仅 WiFi 等由设置页写入，脚本不能覆盖）。
 */
class DownloadManagerImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rootDir: File,
    private val http: HttpClient,
    private val maxConcurrent: Int = 3,
    private val userAgent: String = "KhatKit/1.0",
) : DownloadBridge {

    @Serializable
    private data class PersistedRecord(
        val id: String,
        val url: String,
        val name: String,
        val target: String,
        val headers: Map<String, String> = emptyMap(),
        val state: String = STATE_QUEUED,
        val bytes: Long = 0,
        val total: Long = -1,
    )

    private inner class Record(
        val id: String,
        val url: String,
        val name: String,
        val target: String,
        val headers: Map<String, String>,
        @Volatile var state: String = STATE_QUEUED,
        @Volatile var bytes: Long = 0,
        @Volatile var total: Long = -1,
        @Volatile var error: String? = null,
        @Volatile var speedBps: Long = 0,
        @Volatile var job: Job? = null,
    ) {
        val completion = CompletableDeferred<Unit>()
    }

    private val tasks = LinkedHashMap<String, Record>()

    private val _taskFlow = MutableStateFlow<List<DownloadTaskInfo>>(emptyList())

    override fun observeTasks(): StateFlow<List<DownloadTaskInfo>> = _taskFlow.asStateFlow()

    private fun publishTasks() {
        val list = synchronized(tasks) {
            tasks.values.map { record ->
                DownloadTaskInfo(
                    id = record.id,
                    name = record.name,
                    url = record.url,
                    state = record.state,
                    bytes = record.bytes,
                    total = record.total,
                    speedBps = record.speedBps,
                    file = record.fileOrNull(),
                    error = record.error,
                )
            }
        }
        _taskFlow.value = list
    }
    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))
    private val persistMutex = Mutex()
    private val indexFile = File(rootDir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        rootDir.mkdirs()
        loadPersisted()
    }

    override fun enqueue(task: Map<String, Any?>): DownloadHandle {
        val url = task["url"]?.toString()?.takeIf { it.isNotBlank() }
            ?: error("下载任务缺少 url")
        val name = task["name"]?.toString()?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        val headers = (task["headers"] as? Map<*, *>)
            ?.entries
            ?.associate { it.key.toString() to it.value.toString() }
            ?: emptyMap()
        val record = synchronized(tasks) {
            val existing = tasks.values.firstOrNull { it.url == url && it.name == name && it.state == STATE_DONE }
            if (existing != null) {
                existing
            } else {
                val record = Record(
                    id = UUID.randomUUID().toString().take(8),
                    url = url,
                    name = name.replace('/', '_'),
                    target = File(rootDir, "files/${name.replace('/', '_')}").absolutePath,
                    headers = headers,
                )
                record.bytes = File(record.target + ".part").takeIf { it.exists() }?.length() ?: 0
                tasks[record.id] = record
                record
            }
        }
        if (record.state != STATE_DONE) launch(record)
        persist()
        publishTasks()
        return Handle(record)
    }

    override fun list(state: String?): List<DownloadHandle> = synchronized(tasks) {
        tasks.values
            .filter { state == null || it.state == state }
            .map { Handle(it) }
    }

    override fun query(id: String): DownloadHandle? = synchronized(tasks) {
        tasks[id]?.let { Handle(it) }
    }

    override fun start(task: Map<String, Any?>): String = enqueue(task).id

    override fun status(id: String, waitSeconds: Int): Map<String, Any?> {
        val record = synchronized(tasks) { tasks[id] }
            ?: return mapOf("id" to id, "state" to "missing")
        if (waitSeconds > 0) {
            runBlocking {
                withTimeoutOrNull(waitSeconds.coerceAtLeast(0) * 1000L) { record.completion.await() }
            }
        }
        return synchronized(tasks) {
            mapOf(
                "id" to record.id,
                "name" to record.name,
                "state" to record.state,
                "progress" to progressOf(record),
                "speed" to record.speedBps,
                "file" to record.fileOrNull(),
                "error" to record.error,
            )
        }
    }

    override fun remove(id: String): Boolean {
        val record = synchronized(tasks) { tasks[id] } ?: return false
        record.state = STATE_CANCELLED
        record.job?.cancel()
        runCatching {
            File(record.target).delete()
            File("${record.target}.part").delete()
        }
        synchronized(tasks) {
            tasks.remove(id)
        }
        persist()
        publishTasks()
        return true
    }

    override fun pause(id: String): Boolean = query(id)?.let { it.pause(); true } ?: false

    override fun resume(id: String): Boolean = query(id)?.let { it.resume(); true } ?: false

    override fun cancel(id: String): Boolean = query(id)?.let { it.cancel(); true } ?: false

    private fun progressOf(record: Record): Double {
        val total = record.total
        return if (total > 0) (record.bytes.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
    }

    private fun Record.fileOrNull(): String? =
        File(target).takeIf { state == STATE_DONE && it.exists() }?.absolutePath

    private fun launch(record: Record) {
        record.state = STATE_QUEUED
        record.job = scope.launch(Dispatchers.IO) {
            semaphore.withPermit {
                download(record)
            }
        }
        refreshNotification()
    }

    /** 同步前台服务通知：有活动任务就保持前台，全部结束就撤。 */
    private fun refreshNotification() {
        val active = synchronized(tasks) {
            tasks.values.filter { it.state == STATE_QUEUED || it.state == STATE_RUNNING }
        }
        DownloadStatus.activeCount = active.size
        DownloadStatus.label = active.firstOrNull()?.let { record ->
            val total = record.total
            val percent = if (total > 0) (record.bytes * 100 / total).toInt() else 0
            "${record.name} $percent%"
        } ?: ""

        if (active.isEmpty()) {
            KhatKitDownloadService.stop(context)
        } else {
            KhatKitDownloadService.start(context)
            KhatKitDownloadService.update(context)
        }
        publishTasks()
    }

    private suspend fun download(record: Record) {
        val part = File("${record.target}.part")
        val target = File(record.target)
        target.parentFile?.mkdirs()
        try {
            record.state = STATE_RUNNING
            persist()

            var offset = if (part.exists()) part.length() else 0L
            record.bytes = offset
            http.prepareGet(record.url) {
                record.headers.forEach { (key, value) -> header(key, value) }
                header(HttpHeaders.UserAgent, userAgent)
                if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
            }.execute { response ->
                if (response.status == HttpStatusCode.PartialContent) {
                    record.total = parseRangeTotal(response) ?: -1
                } else {
                    offset = 0
                    part.delete()
                    record.total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1
                }
                record.bytes = offset

                FileOutputStream(part, offset > 0).use { output ->
                    response.bodyAsChannel().toInputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var lastTickNanos = System.nanoTime()
                        var lastBytes = record.bytes
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            record.bytes += read
                            val now = System.nanoTime()
                            val seconds = (now - lastTickNanos) / 1_000_000_000.0
                            if (seconds >= 1.0) {
                                record.speedBps = ((record.bytes - lastBytes) / seconds).toLong()
                                lastTickNanos = now
                                lastBytes = record.bytes
                                refreshNotification()
                            }
                        }
                    }
                }
            }

            if (part.renameTo(target)) {
                record.bytes = target.length()
                record.speedBps = 0
                record.state = STATE_DONE
            } else {
                record.error = "无法写入目标文件：${target.absolutePath}"
                record.state = STATE_FAILED
            }
        } catch (e: CancellationException) {
            // pause/cancel 主动取消：状态已在调用点设置，保留断点
            throw e
        } catch (e: Throwable) {
            record.error = e.message ?: e.toString()
            record.state = STATE_FAILED
        } finally {
            if (record.state == STATE_DONE || record.state == STATE_FAILED || record.state == STATE_CANCELLED) {
                record.completion.complete(Unit)
            }
            persist()
            refreshNotification()
        }
    }

    private fun parseRangeTotal(response: HttpResponse): Long? {
        val contentRange = response.headers[HttpHeaders.ContentRange] ?: return null
        return contentRange.substringAfterLast('/', "").trim().toLongOrNull()
    }

    private fun persist() {
        val snapshot = synchronized(tasks) {
            tasks.values.map { record ->
                PersistedRecord(
                    id = record.id,
                    url = record.url,
                    name = record.name,
                    target = record.target,
                    headers = record.headers,
                    state = record.state,
                    bytes = record.bytes,
                    total = record.total,
                )
            }
        }
        scope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                runCatching {
                    indexFile.parentFile?.mkdirs()
                    indexFile.writeText(json.encodeToString(snapshot))
                }
            }
        }
    }

    private fun loadPersisted() {
        if (!indexFile.exists()) return
        val records = runCatching {
            json.decodeFromString<List<PersistedRecord>>(indexFile.readText())
        }.getOrDefault(emptyList())
        synchronized(tasks) {
            records.forEach { persisted ->
                val record = Record(
                    id = persisted.id,
                    url = persisted.url,
                    name = persisted.name,
                    target = persisted.target,
                    headers = persisted.headers,
                    // 进程重启后 running 任务视为可续传的 paused
                    state = if (persisted.state == STATE_RUNNING) STATE_PAUSED else persisted.state,
                    bytes = persisted.bytes,
                    total = persisted.total,
                )
                if (record.state == STATE_DONE) record.completion.complete(Unit)
                tasks[record.id] = record
            }
        }
        publishTasks()
    }

    private inner class Handle(private val record: Record) : DownloadHandle {
        override val id: String get() = record.id

        override fun await(seconds: Int): String = runBlocking {
            if (record.state == STATE_DONE) return@runBlocking STATE_DONE
            withTimeoutOrNull(seconds.coerceAtLeast(0) * 1000L) { record.completion.await() }
            record.state
        }

        override fun progress(): Float {
            val total = record.total
            return if (total > 0) (record.bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        }

        override fun speed(): Long = record.speedBps

        override fun pause() {
            if (record.state == STATE_RUNNING || record.state == STATE_QUEUED) {
                record.state = STATE_PAUSED
                record.job?.cancel()
                persist()
                refreshNotification()
            }
        }

        override fun resume() {
            if (record.state == STATE_PAUSED || record.state == STATE_FAILED) {
                record.error = null
                launch(record)
                persist()
            }
        }

        override fun cancel() {
            record.state = STATE_CANCELLED
            record.job?.cancel()
            File("${record.target}.part").delete()
            record.completion.complete(Unit)
            persist()
            refreshNotification()
        }

        override fun file(): String? =
            File(record.target).takeIf { record.state == STATE_DONE && it.exists() }?.absolutePath

        override fun toString(): String = "Download(${record.id}, ${record.name}, ${record.state})"
    }

    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
        const val STATE_DONE = "done"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELLED = "cancelled"
    }
}
