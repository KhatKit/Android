package heizige.kk.khatkit.app.core.data.automation

import heizige.kk.khatkit.app.core.data.automation.TraceMeta

import heizige.kk.khatkit.app.core.data.automation.TraceStep

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 轨迹落盘读写：`<baseDir>/<traceId>.jsonl`（每行一个 [TraceStep]）+ `index.json` 清单。
 *
 * 只做文件/JSON 读写，不持运行生命周期；生命周期在
 * [heizige.kk.khatkit.app.core.data.automation.AutomationTraceManager]。
 */
class AutomationTraceRepository(
    private val baseDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val indexFile = File(baseDir, "index.json")

    init {
        baseDir.mkdirs()
    }

    /** 追加一步到该轨迹的 JSONL 文件。 */
    fun appendStep(id: String, step: TraceStep) {
        File(baseDir, "$id.jsonl").appendText(json.encodeToString(TraceStep.serializer(), step) + "\n")
    }

    /** 读取该轨迹的全部步骤；文件不存在返回 null。 */
    fun readSteps(id: String): List<TraceStep>? {
        val file = File(baseDir, "$id.jsonl")
        if (!file.exists()) return null
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString(TraceStep.serializer(), it) }.getOrNull() }
    }

    /** 轨迹清单（未排序，读取失败按空清单）。 */
    fun loadIndex(): List<TraceMeta> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(TraceMeta.serializer()), indexFile.readText())
        }.getOrDefault(emptyList())
    }

    /** 覆盖写清单。 */
    fun writeIndex(index: List<TraceMeta>) {
        indexFile.writeText(json.encodeToString(ListSerializer(TraceMeta.serializer()), index))
    }

    /** 删除轨迹 JSONL（不删截图文件）。 */
    fun deleteTrace(id: String): Boolean = File(baseDir, "$id.jsonl").delete()
}
