package heizige.kk.khatkit.app.core.data.automation

import kotlinx.serialization.Serializable

/**
 * A4 轨迹模型：一次动作记录（截图帧 + 动作 + 目标 id + 结果），可回放审计。
 *
 * JSONL 每行一个 [TraceStep]，字段名即落盘 wire 名，重命名类型不改字段。
 */
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
