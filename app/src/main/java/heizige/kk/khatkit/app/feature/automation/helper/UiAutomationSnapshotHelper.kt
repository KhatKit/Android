package heizige.kk.khatkit.app.feature.automation.helper

import heizige.kk.khatkit.app.core.data.automation.UiAutomationBounds
import heizige.kk.khatkit.app.core.data.automation.UiAutomationNode
import heizige.kk.khatkit.app.core.data.automation.UiAutomationSnapshot
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder

/**
 * 无障碍树的快照采集适配：把 bridge 的 dumpWindow/currentPackage 投影成
 * [UiAutomationSnapshot]（模型与 wire 字段留在 data）。
 *
 * 依赖 [AccessibilityBridgeHolder] 与平台调用，属 Helper；上限常量不变。
 */
object UiAutomationSnapshotHelper {
    private const val MAX_TEXT = 256
    private const val MAX_NODES = 500

    fun capture(): UiAutomationSnapshot? {
        val bridge = AccessibilityBridgeHolder.current() ?: return null
        val nodes = bridge.dumpWindow().take(MAX_NODES).mapIndexed { index, raw ->
            UiAutomationNode(
                index = index,
                depth = raw.intValue("depth"),
                className = raw.stringValue("className"),
                viewId = raw.stringValue("viewId"),
                text = raw.stringValue("text")?.take(MAX_TEXT),
                description = raw.stringValue("desc")?.take(MAX_TEXT),
                bounds = raw.boundsValue(),
                clickable = raw.booleanValue("clickable"),
                editable = raw.booleanValue("editable"),
                scrollable = raw.booleanValue("scrollable"),
            )
        }
        return UiAutomationSnapshot(
            packageName = bridge.currentPackage(),
            nodes = nodes,
        )
    }

    internal fun Map<String, Any?>.stringValue(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }

    internal fun Map<String, Any?>.intValue(key: String): Int =
        (this[key] as? Number)?.toInt() ?: 0

    internal fun Map<String, Any?>.booleanValue(key: String): Boolean =
        this[key] as? Boolean ?: false

    internal fun Map<String, Any?>.boundsValue(): UiAutomationBounds? {
        val bounds = this["bounds"] as? Map<*, *> ?: return null
        fun number(key: String): Int = (bounds[key] as? Number)?.toInt() ?: 0
        return UiAutomationBounds(
            left = number("left"),
            top = number("top"),
            right = number("right"),
            bottom = number("bottom"),
        )
    }
}
