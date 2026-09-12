package heizige.kk.khatkit.card

/**
 * 宿主实际渲染的 UI 组件白名单（设计文档 7.2）。
 *
 * 只收 KhatKitForm 真正实现的组件；
 * CI 用它对 card.json 的 `ui.*.widget` 做校验，未实现的不允许出现在卡片里。
 */
object UiWidgetVocabulary {
    val ALLOWED = setOf(
        "text", "input", "number", "switch", "slider", "select", "radio",
        "file_picker", "dir_picker",
        "button", "progress", "markdown", "divider", "custom",
    )

    val SOURCES = setOf("ai", "ui")
}
