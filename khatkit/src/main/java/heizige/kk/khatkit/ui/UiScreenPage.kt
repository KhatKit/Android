package heizige.kk.khatkit.ui

/** A prevalidated page; each tree retains the ordinary 512-node limit. */
data class UiScreenPage(val root: UiNode, val loadMoreEvent: String? = null) {
    companion object {
        /** Internal UI-to-bridge envelope; never included in collected field values. */
        const val PAGE_VALUE_KEY = "__khatkit_page"

        fun parse(options: Map<String, Any?>?, navigation: UiSheetNavigation?): Map<String, UiScreenPage> {
            val raw = options?.get("pages") ?: return emptyMap()
            require(navigation != null) { "ui.screen.pages: nav is required" }
            val pages = raw as? Map<*, *> ?: error("ui.screen.pages: expected object")
            val ids = navigation.items.map { it.event }
            require(ids.distinct().size == ids.size && pages.keys == ids.toSet()) {
                "ui.screen.pages: keys must exactly match nav items"
            }
            require(navigation.selected == null || navigation.selected in pages) {
                "ui.screen.pages: selected page is unknown"
            }
            return ids.associateWith { id ->
                val page = pages[id] as? Map<*, *> ?: error("ui.screen.pages.$id: expected object")
                require(page.keys.all { it == "tree" || it == "load_more" }) {
                    "ui.screen.pages.$id: unknown property"
                }
                val tree = page["tree"] as? Map<*, *> ?: error("ui.screen.pages.$id.tree: expected node")
                val loadMore = page["load_more"]
                require(loadMore == null || loadMore is String && loadMore.isNotBlank()) {
                    "ui.screen.pages.$id.load_more: expected event string"
                }
                UiScreenPage(UiNodeParser.parse(tree), loadMore as? String)
            }
        }
    }
}
