package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.ui.UiNodeParser

/** Validate and authorize images on the script thread, before publishing UI state. */
internal class UiImageAccessBridge(
    private val delegate: UiBridge,
    private val authorize: (String) -> String,
    private val allowRemote: (String) -> Boolean = { false },
) : UiBridge by delegate {

    /**
     * `Image.src` 现在也接受 https URL（头像这类外链图）。远程图绕过 `net` 桥，
     * 所以这里按卡片 network.allow 再核一次域名，白名单外的地址直接拒绝。
     */
    private fun source(src: String): String {
        if (!src.startsWith("https://")) return authorize(src)
        require(allowRemote(src)) { "Image.src: 远程图片域名不在 network.allow 白名单内：$src" }
        return src
    }
    private fun checked(root: Map<String, Any?>): Map<String, Any?> {
        if (!root.containsKey("__ui")) return root
        val node = UiNodeParser.parse(root)
        fun encode(node: heizige.kk.khatkit.ui.UiNode): Map<String, Any?> {
            val props = node.props.toMutableMap()
            if (node.type == "Image") props["src"] = source(props["src"] as? String ?: error("Image.src required"))
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
