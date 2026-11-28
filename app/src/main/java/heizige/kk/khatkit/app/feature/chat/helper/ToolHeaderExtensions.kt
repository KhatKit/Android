package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ui.UiIcons
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.app.Screen



internal fun UiRequest.toolHeaderIcon(): String? {
    val root = when (this) {
        is UiRequest.Screen -> root.takeUnless { options.push }
        is UiRequest.FormTree -> root
        is UiRequest.ShowTree -> root
        else -> null
    } ?: return null
    return root.toolHeaderIcon()
}

/** Card banners put their large standalone icon before the title, not inside a control. */
internal fun UiNode.toolHeaderIcon(): String? = walk()
    .takeWhile { it.type !in headerEndTypes }
    .firstNotNullOfOrNull { node ->
        (node.props["name"] as? String)?.takeIf {
            node.type == "Icon" && (node.props["size"] as? Number)?.toFloat()?.let { it >= 28f } == true &&
                it in UiIcons.names
        }
    }

private val headerEndTypes = setOf("Text", "Markdown", "Section", "Button", "IconButton", "TextField")
