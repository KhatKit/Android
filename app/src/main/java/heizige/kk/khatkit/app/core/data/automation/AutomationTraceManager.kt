package heizige.kk.khatkit.app.core.data.automation


import heizige.kk.khatkit.app.core.data.automation.AutomationTraceRepository
import heizige.kk.khatkit.app.core.data.automation.TraceMeta
import heizige.kk.khatkit.app.core.data.automation.TraceRecord
import heizige.kk.khatkit.app.core.data.automation.TraceStep
import heizige.kk.khatkit.app.core.data.automation.UiActionDto
import java.io.File

/**
 * A4 轨迹记录流程：begin / record / finish 持运行生命周期，落盘交给
 * [AutomationTraceRepository]（JSONL + index.json 格式与字段不变）。回放见 TraceReplayHelper。
 */
class AutomationTraceManager(
    baseDir: File,
    private val repository: AutomationTraceRepository = AutomationTraceRepository(baseDir),
) {
    private var currentId: String? = null
    private var currentLabel: String = ""
    private var currentStartedAt: Long = 0L
    private var currentSeq = 0

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
        repository.appendStep(id, step)
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
        val index = repository.loadIndex().filterNot { it.id == id } + meta
        repository.writeIndex(index)
        currentId = null
        currentSeq = 0
        return meta
    }

    /** 轨迹清单（按开始时间倒序）。 */
    fun list(): List<TraceMeta> = repository.loadIndex().sortedByDescending { it.startedAt }

    /** 读取完整轨迹（含步骤）。 */
    fun load(id: String): TraceRecord? {
        val steps = repository.readSteps(id) ?: return null
        val meta = repository.loadIndex().firstOrNull { it.id == id }
            ?: TraceMeta(id = id, label = "", startedAt = 0L, stepCount = steps.size)
        return TraceRecord(meta = meta, steps = steps)
    }

    /** 删除轨迹（连同清单项，不删截图文件本身）。 */
    fun delete(id: String): Boolean {
        val removed = repository.deleteTrace(id)
        val index = repository.loadIndex().filterNot { it.id == id }
        repository.writeIndex(index)
        return removed
    }
}
