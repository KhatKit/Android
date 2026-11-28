package heizige.kk.khatkit.app.core.data.automation
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
