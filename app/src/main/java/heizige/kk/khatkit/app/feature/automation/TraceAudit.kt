package heizige.kk.khatkit.app.feature.automation

data class TraceStepView(
    val index: Int,
    val title: String,
    val detail: String,
    val result: String,
    val success: Boolean,
    val screenshotPath: String?,
    val hit: String?,
)

data class TraceBrowseState(
    val index: Int,
    val count: Int,
) {
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = count > 0 && index < count - 1

    fun previous(): TraceBrowseState = copy(index = (index - 1).coerceAtLeast(0))

    fun next(): TraceBrowseState = if (count == 0) this else copy(index = (index + 1).coerceAtMost(count - 1))
}

fun AutomationTracer.TraceRecord.toStepViews(): List<TraceStepView> = steps.mapIndexed { index, step ->
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
        title = "${index + 1}. ${action.type} $target",
        detail = buildString {
            if (action.inputText.isNotBlank()) append("输入 ${action.inputText}")
            if (action.bbox != null) {
                if (isNotEmpty()) append(" · ")
                append("bbox ${action.bbox.left},${action.bbox.top}-${action.bbox.right},${action.bbox.bottom}")
            }
        },
        result = step.result,
        success = step.success,
        screenshotPath = step.screenshotBefore,
        hit = step.resolutionMethod,
    )
}
