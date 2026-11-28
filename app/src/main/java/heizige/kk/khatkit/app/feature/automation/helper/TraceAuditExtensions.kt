package heizige.kk.khatkit.app.feature.automation.helper

import android.content.Context
import heizige.kk.khatkit.app.core.data.automation.TraceRecord
import heizige.kk.khatkit.app.core.data.automation.UiActionDto
import heizige.kk.khatkit.record.localizedComment

/** 审计页的步骤投影（纯数据，无状态）。 */
data class TraceStepView(
    val index: Int,
    val title: String,
    val detail: String,
    val result: String,
    val success: Boolean,
    val screenshotPath: String?,
    val hit: String?,
)

/** 审计页步骤浏览游标（纯函数状态机）。 */
data class TraceBrowseState(
    val index: Int,
    val count: Int,
) {
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = count > 0 && index < count - 1

    fun previous(): TraceBrowseState = copy(index = (index - 1).coerceAtLeast(0))

    fun next(): TraceBrowseState = if (count == 0) this else copy(index = (index + 1).coerceAtMost(count - 1))
}

/** [TraceRecord] → 审计页可展示的步骤列表（UI 展示请用带 Context 的重载，文案走 localizedComment）。 */
fun TraceRecord.toStepViews(): List<TraceStepView> = steps.mapIndexed { index, step ->
    val action = step.action
    val target = action.viewId.ifBlank {
        action.text.ifBlank {
            action.desc.ifBlank {
                if (action.x != 0 || action.y != 0) "${action.x},${action.y}" else action.type
            }
        }
    }
    TraceStepView(
        index = index,
        // Legacy projection kept for protocol/source tests; audit page renders localizedComment(context).
        title = "${index + 1}. ${action.type} $target",
        detail = traceStepDetail(action),
        result = step.result,
        success = step.success,
        screenshotPath = step.screenshotBefore,
        hit = step.resolutionMethod,
    )
}

/** [TraceRecord] → 审计页展示步骤（当前 locale）：标题用 UiAction.localizedComment，详情仅技术 bbox。 */
fun TraceRecord.toStepViews(context: Context): List<TraceStepView> = steps.mapIndexed { index, step ->
    val action = TraceReplayHelper.toUiAction(step)
    TraceStepView(
        index = index,
        title = "${index + 1}. ${action.localizedComment(context)}",
        detail = traceStepDetail(step.action),
        result = step.result,
        success = step.success,
        screenshotPath = step.screenshotBefore,
        hit = step.resolutionMethod,
    )
}

private fun traceStepDetail(action: UiActionDto): String = buildString {
    if (action.bbox != null) {
        append("bbox ${action.bbox.left},${action.bbox.top}-${action.bbox.right},${action.bbox.bottom}")
    }
}
