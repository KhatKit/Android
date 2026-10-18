package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge

/** A run-local UI sink. Background entries must not steal focus with modal UI. */
class CardWidgetBridge(
    private val cancelled: () -> Boolean = { false },
) : UiBridge {
    @Volatile var content: UiNode? = null
        private set

    private fun modal(): Nothing = error("WIDGET_MODAL_UNSUPPORTED: use ui.show(node) for inline widgets")

    override fun sheet(title: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? = modal()
    override fun webSheet(title: String, url: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? = modal()
    override fun form(title: String, items: Any, options: Map<String, Any?>?): Map<String, Any?>? = modal()
    override fun screen(title: String, root: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?>? = modal()
    override fun confirm(title: String, message: String, danger: Boolean): Boolean = modal()
    override fun progress(ratio: Float, label: String) = Unit
    override fun isCancelled(): Boolean = cancelled()

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) {
        val root = UiNodeParser.parse(card)
        require(root.walk().count() <= 64) { "WIDGET_TOO_LARGE: at most 64 nodes" }
        require(root.walk().none { it.type in setOf("FilePicker", "DirPicker") }) {
            "WIDGET_PICKER_UNSUPPORTED: open the card to pick files"
        }
        // The host owns the frame; a card inside that frame would duplicate its chrome.
        content = if (root.type == "Card") root.copy(type = "Column", props = emptyMap()) else root
    }
}
