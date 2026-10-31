package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge

/**
 * 小组件外框：`header` = 宿主标题行 + 内边距（旧行为）；`plain` = 只有内边距；
 * `full` = 内容铺满整格，背景/留白都由卡片自己画。
 */
enum class WidgetFrame { HEADER, PLAIN, FULL }

/** 网格里至少 [minWidth] × [minHeight] 格时才换成这一版内容。 */
data class CardWidgetLayout(
    val minWidth: Int,
    val minHeight: Int,
    val root: UiNode,
    val frame: WidgetFrame,
)

/** Inline content and the script-owned action for the host's refresh icon. */
data class CardWidgetContent(
    val root: UiNode,
    val refreshAction: String = "",
    val frame: WidgetFrame = WidgetFrame.HEADER,
    val layouts: List<CardWidgetLayout> = emptyList(),
) {
    /**
     * 当前格数下用哪一版：放得下的版本里取面积最大的（同面积取更宽的）；
     * 一个都放不下就退回 [root]。纯函数，拖动边框时每一格都重新挑一次。
     */
    fun pick(width: Int, height: Int): CardWidgetLayout =
        layouts.filter { it.minWidth <= width && it.minHeight <= height }
            .maxWithOrNull(compareBy<CardWidgetLayout>({ it.minWidth * it.minHeight }, { it.minWidth }))
            ?: CardWidgetLayout(0, 0, root, frame)
}

/** A run-local UI sink. Background entries must not steal focus with modal UI. */
class CardWidgetBridge(
    private val cancelled: () -> Boolean = { false },
) : UiBridge {
    @Volatile var content: UiNode? = null
        private set
    @Volatile var refreshAction: String = ""
        private set
    @Volatile var frame: WidgetFrame = WidgetFrame.HEADER
        private set
    @Volatile var layouts: List<CardWidgetLayout> = emptyList()
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
        val root = tree(card)
        val action = options?.get("refresh_action")
        require(action == null || action is String && action.isNotBlank() && action.length <= 128) {
            "WIDGET_REFRESH_ACTION: expected a nonblank event string of at most 128 characters"
        }
        val parsed = layouts(options?.get("layouts"))
        require(root.walk().count() + parsed.sumOf { it.root.walk().count() } <= MAX_TOTAL_NODES) {
            "WIDGET_TOO_LARGE: at most $MAX_TOTAL_NODES nodes across all layouts"
        }
        content = root
        refreshAction = action as? String ?: ""
        frame = frame(options?.get("frame"), "WIDGET_FRAME")
        layouts = parsed
    }

    private fun tree(raw: Any?): UiNode {
        val node = raw as? Map<*, *> ?: error("WIDGET_LAYOUT: expected a ui node")
        val root = UiNodeParser.parse(node)
        require(root.walk().count() <= MAX_NODES) { "WIDGET_TOO_LARGE: at most $MAX_NODES nodes" }
        require(root.walk().none { it.type in setOf("FilePicker", "DirPicker") }) {
            "WIDGET_PICKER_UNSUPPORTED: open the card to pick files"
        }
        // The host owns the frame; a card inside that frame would duplicate its chrome.
        return if (root.type == "Card") root.copy(type = "Column", props = emptyMap()) else root
    }

    private fun layouts(raw: Any?): List<CardWidgetLayout> {
        // Rust 把空 Lua 表编码成 {}，和 NodeTreeParser 同一口径当空数组。
        val list = when {
            raw == null -> return emptyList()
            raw is List<*> -> raw
            raw is Map<*, *> && raw.isEmpty() -> return emptyList()
            else -> error("WIDGET_LAYOUTS: expected an array")
        }
        require(list.size <= MAX_LAYOUTS) { "WIDGET_LAYOUTS: at most $MAX_LAYOUTS layouts" }
        return list.mapIndexed { index, item ->
            val spec = item as? Map<*, *> ?: error("WIDGET_LAYOUTS[$index]: expected object")
            spec.keys.firstOrNull { it !in setOf("min_w", "min_h", "tree", "frame") }?.let {
                error("WIDGET_LAYOUTS[$index].$it: unknown property")
            }
            CardWidgetLayout(
                minWidth = cells(spec["min_w"], "WIDGET_LAYOUTS[$index].min_w"),
                minHeight = cells(spec["min_h"], "WIDGET_LAYOUTS[$index].min_h"),
                root = tree(spec["tree"]),
                frame = frame(spec["frame"], "WIDGET_LAYOUTS[$index].frame"),
            )
        }
    }

    private fun cells(raw: Any?, where: String): Int {
        val n = (raw as? Number)?.toDouble()
        require(n != null && n % 1 == 0.0 && n in 1.0..MAX_CELLS) { "$where: expected an integer 1..${MAX_CELLS.toInt()}" }
        return n.toInt()
    }

    private fun frame(raw: Any?, where: String): WidgetFrame = when (raw) {
        null, "header" -> WidgetFrame.HEADER
        "plain" -> WidgetFrame.PLAIN
        "full" -> WidgetFrame.FULL
        else -> error("$where: expected header|plain|full")
    }

    private companion object {
        const val MAX_NODES = 64
        const val MAX_TOTAL_NODES = 256
        const val MAX_LAYOUTS = 6
        const val MAX_CELLS = 6.0
    }
}
