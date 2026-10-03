package heizige.kk.khatkit.app.feature.automation

import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import kotlinx.serialization.Serializable

/**
 * Stable, bounded representation of the current accessibility tree for an
 * agent or visual UI annotator. The bridge remains the only action boundary.
 */
@Serializable
data class UiAutomationSnapshot(
    val packageName: String?,
    val nodes: List<UiAutomationNode>,
)

@Serializable
data class UiAutomationNode(
    val index: Int,
    val depth: Int,
    val className: String?,
    val viewId: String?,
    val text: String?,
    val description: String?,
    val bounds: UiAutomationBounds?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
)

@Serializable
data class UiAutomationBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object UiAutomationSnapshotProvider {
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
        fun number(key: String): Int = (bounds[key] as? Number)?.toInt() ?: return 0
        return UiAutomationBounds(
            left = number("left"),
            top = number("top"),
            right = number("right"),
            bottom = number("bottom"),
        )
    }
}
