package heizige.kk.khatkit.app.feature.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A4 轨迹落盘：每次动作记录（截图帧 + 动作 + 目标 id + 结果），可回放审计。
 *
 * JSONL 存储：`<baseDir>/<traceId>.jsonl`，每行一个 [TraceStep]；
 * `index.json` 维护轨迹清单。回放见 [TracePlayer]。
 */
class AutomationTracer(
    private val baseDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    @Serializable
    data class TraceStep(
        val seq: Int,
        val timestamp: Long,
        val action: UiActionDto,
        val screenshotBefore: String? = null,
        val result: String = "",
        val success: Boolean = true,
        val packageName: String? = null,
        val resolutionMethod: String? = null,
    )

    @Serializable
    data class UiActionDto(
        val type: String,
        val packageName: String = "",
        val viewId: String = "",
        val text: String = "",
        val desc: String = "",
        val x: Int = 0,
        val y: Int = 0,
        val x2: Int = 0,
        val y2: Int = 0,
        val direction: String = "",
        val inputText: String = "",
        val durationMs: Long = 0L,
        val globalAction: String = "",
        val bbox: BBoxDto? = null,
        val origin: String = "agent",
    )

    @Serializable
    data class BBoxDto(val left: Int, val top: Int, val right: Int, val bottom: Int)

    @Serializable
    data class TraceMeta(
        val id: String,
        val label: String,
        val startedAt: Long,
        val finishedAt: Long? = null,
        val stepCount: Int = 0,
    )

    @Serializable
    data class TraceRecord(
        val meta: TraceMeta,
        val steps: List<TraceStep>,
    )

    private val indexFile = File(baseDir, "index.json")
    private var currentId: String? = null
    private var currentLabel: String = ""
    private var currentStartedAt: Long = 0L
    private var currentSeq = 0

    init {
        baseDir.mkdirs()
    }

    /** 开始一段轨迹，返回 traceId。已有未结束轨迹时先收尾。 */
    @Synchronized
    fun begin(label: String, nowMillis: Long = System.currentTimeMillis()): String {
        finish()
        val id = "trace_${nowMillis}"
        currentId = id
        currentLabel = label
        currentStartedAt = nowMillis
        currentSeq = 0
        return id
    }

    /** 记录一步；未 begin 时自动开启匿名轨迹。 */
    @Synchronized
    fun record(
        action: UiActionDto,
        screenshotBefore: String? = null,
        result: String = "",
        success: Boolean = true,
        packageName: String? = null,
        resolutionMethod: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val id = currentId ?: begin("auto", nowMillis)
        val step = TraceStep(
            seq = currentSeq++,
            timestamp = nowMillis,
            action = action,
            screenshotBefore = screenshotBefore,
            result = result,
            success = success,
            packageName = packageName,
            resolutionMethod = resolutionMethod,
        )
        File(baseDir, "$id.jsonl").appendText(json.encodeToString(TraceStep.serializer(), step) + "\n")
    }

    /** 收尾当前轨迹，写入清单。 */
    @Synchronized
    fun finish(nowMillis: Long = System.currentTimeMillis()): TraceMeta? {
        val id = currentId ?: return null
        val meta = TraceMeta(
            id = id,
            label = currentLabel,
            startedAt = currentStartedAt,
            finishedAt = nowMillis,
            stepCount = currentSeq,
        )
        val index = loadIndex().filterNot { it.id == id } + meta
        indexFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TraceMeta.serializer()), index))
        currentId = null
        currentSeq = 0
        return meta
    }

    /** 轨迹清单（按开始时间倒序）。 */
    fun list(): List<TraceMeta> = loadIndex().sortedByDescending { it.startedAt }

    /** 读取完整轨迹（含步骤）。 */
    fun load(id: String): TraceRecord? {
        val file = File(baseDir, "$id.jsonl")
        if (!file.exists()) return null
        val steps = file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString(TraceStep.serializer(), it) }.getOrNull() }
        val meta = loadIndex().firstOrNull { it.id == id }
            ?: TraceMeta(id = id, label = "", startedAt = 0L, stepCount = steps.size)
        return TraceRecord(meta = meta, steps = steps)
    }

    /** 删除轨迹（连同截图引用，不删截图文件本身）。 */
    fun delete(id: String): Boolean {
        val removed = File(baseDir, "$id.jsonl").delete()
        val index = loadIndex().filterNot { it.id == id }
        indexFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TraceMeta.serializer()), index))
        return removed
    }

    private fun loadIndex(): List<TraceMeta> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(TraceMeta.serializer()),
                indexFile.readText(),
            )
        }.getOrDefault(emptyList())
    }
}
