package heizige.kk.khatkit.app.feature.automation

import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.record.UiAction
import heizige.kk.khatkit.record.UiBBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A4 轨迹回放：把 [AutomationTracer.TraceRecord] 的动作序列按序重放。
 *
 * 与录制/Agent 生成同构：步骤是 [UiAction]，选择器优先级 viewId > text > desc > 坐标/bbox 中心。
 * 回放走无障碍桥（与 device_act 同一执行面），不重复触发审批（用户主动回放视为一次性授权）。
 */
object TracePlayer {

    data class ReplayResult(
        val totalSteps: Int,
        val executedSteps: Int,
        val failures: List<String>,
    ) {
        val success: Boolean get() = failures.isEmpty()
    }

    /** 回放间隔下限，避免动作过快界面未刷新。 */
    const val MIN_GAP_MS = 400L

    suspend fun replay(
        record: AutomationTracer.TraceRecord,
        onStep: (suspend (index: Int, AutomationTracer.TraceStep, String) -> Unit)? = null,
        gapMs: Long = MIN_GAP_MS,
    ): ReplayResult = withContext(Dispatchers.IO) {
        val bridge = AccessibilityBridgeHolder.current()
            ?: return@withContext ReplayResult(0, 0, listOf("无障碍服务未开启"))
        val failures = mutableListOf<String>()
        var executed = 0
        record.steps.forEachIndexed { index, step ->
            if (AutomationBus.isCancelRequested()) {
                failures += "步骤 ${index + 1}：用户取消"
                return@forEachIndexed
            }
            val message = executeStep(bridge, step)
            executed++
            onStep?.invoke(index, step, message)
            if (message.startsWith("失败")) failures += "步骤 ${index + 1}：$message"
            if (gapMs > 0 && index < record.steps.lastIndex) delay(gapMs)
        }
        ReplayResult(record.steps.size, executed, failures)
    }

    internal suspend fun executeStep(bridge: heizige.kk.khatkit.bridge.impl.AccessibilityBridgeImpl, step: AutomationTracer.TraceStep): String {
        val a = step.action
        val type = runCatching { UiAction.Type.valueOf(a.type) }.getOrNull()
            ?: return "失败：未知动作 ${a.type}"
        return runCatching {
            when (type) {
                UiAction.Type.CLICK -> {
                    val ok = when {
                        a.viewId.isNotBlank() -> bridge.click(mapOf("viewId" to a.viewId))
                        a.text.isNotBlank() -> bridge.click(mapOf("text" to a.text))
                        a.desc.isNotBlank() -> bridge.click(mapOf("desc" to a.desc))
                        a.bbox != null -> bridge.tap(
                            (a.bbox.left + a.bbox.right) / 2f,
                            (a.bbox.top + a.bbox.bottom) / 2f,
                        )
                        else -> bridge.tap(a.x.toFloat(), a.y.toFloat())
                    }
                    if (ok) "已点击：${a.text.ifBlank { a.viewId.ifBlank { "(${a.x},${a.y})" } }}" else "失败：点击未命中"
                }

                UiAction.Type.SET_TEXT -> {
                    val query = if (a.viewId.isNotBlank()) mapOf<String, Any?>("viewId" to a.viewId)
                    else mapOf<String, Any?>("editable" to true)
                    if (bridge.setText(query, a.inputText)) "已输入：${a.inputText}" else "失败：未找到输入框"
                }

                UiAction.Type.SWIPE -> {
                    if (bridge.swipe(a.x.toFloat(), a.y.toFloat(), a.x2.toFloat(), a.y2.toFloat(), a.durationMs.coerceAtLeast(200L)))
                        "已滑动" else "失败：滑动未执行"
                }

                UiAction.Type.PRESS -> {
                    if (bridge.press(a.x.toFloat(), a.y.toFloat(), a.durationMs.coerceAtLeast(300L)))
                        "已长按" else "失败：长按未执行"
                }

                UiAction.Type.OPEN_APP -> {
                    if (bridge.openApp(a.packageName)) "已打开：${a.packageName}" else "失败：打开 ${a.packageName}"
                }

                UiAction.Type.WAIT -> {
                    delay(600)
                    "已等待"
                }

                UiAction.Type.GLOBAL -> {
                    if (bridge.globalAction(a.globalAction)) "已执行：${a.globalAction}" else "失败：${a.globalAction}"
                }
            }
        }.getOrElse { "失败：${it.message ?: it.javaClass.simpleName}" }
    }

    /** TraceStep → UiAction（回放与审计共用）。 */
    fun toUiAction(step: AutomationTracer.TraceStep): UiAction {
        val a = step.action
        return UiAction(
            type = runCatching { UiAction.Type.valueOf(a.type) }.getOrDefault(UiAction.Type.WAIT),
            atMillis = step.timestamp,
            packageName = a.packageName,
            viewId = a.viewId,
            text = a.text,
            desc = a.desc,
            x = a.x,
            y = a.y,
            x2 = a.x2,
            y2 = a.y2,
            direction = a.direction,
            inputText = a.inputText,
            durationMs = a.durationMs,
            globalAction = a.globalAction,
            bbox = a.bbox?.let { UiBBox(it.left, it.top, it.right, it.bottom) },
            origin = a.origin,
        )
    }

    /** UiAction → TraceStep 的动作 DTO。 */
    fun toActionDto(action: UiAction): AutomationTracer.UiActionDto =
        AutomationTracer.UiActionDto(
            type = action.type.name,
            packageName = action.packageName,
            viewId = action.viewId,
            text = action.text,
            desc = action.desc,
            x = action.x,
            y = action.y,
            x2 = action.x2,
            y2 = action.y2,
            direction = action.direction,
            inputText = action.inputText,
            durationMs = action.durationMs,
            globalAction = action.globalAction,
            bbox = action.bbox?.let { AutomationTracer.BBoxDto(it.left, it.top, it.right, it.bottom) },
            origin = action.origin,
        )
}
