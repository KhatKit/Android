package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.ui.UiNodeParser

/** Validate and authorize images on the script thread, before publishing UI state. */
internal class UiImageAccessBridge(
    private val delegate: UiBridge,
    private val authorize: (String) -> String,
) : UiBridge by delegate {
    private fun checked(root: Map<String, Any?>): Map<String, Any?> {
        if (!root.containsKey("__ui")) return root
        val node = UiNodeParser.parse(root)
        fun encode(node: heizige.kk.khatkit.ui.UiNode): Map<String, Any?> {
            val props = node.props.toMutableMap()
            if (node.type == "Image") props["src"] = authorize(props["src"] as? String ?: error("Image.src required"))
            return mapOf("__ui" to node.type, "props" to props, "children" to node.children.map(::encode))
        }
        return encode(node)
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(title: String, items: Any, options: Map<String, Any?>?): Map<String, Any?>? =
        delegate.form(title, if (items is Map<*, *>) checked(items as Map<String, Any?>) else items, options)

    override fun screen(title: String, root: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?>? =
        delegate.screen(title, checked(root), options)

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) = delegate.show(checked(card), options)
}
