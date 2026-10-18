package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.card.CardStrings

/** Resolve display strings before the image-access/DSL validation boundary. */
internal class LocalizedUiBridge(
    private val delegate: UiBridge,
    private val strings: CardStrings,
) : UiBridge by delegate {
    @Suppress("UNCHECKED_CAST")
    private fun map(value: Map<String, Any?>): Map<String, Any?> =
        strings.resolveValue(value) as Map<String, Any?>

    override fun sheet(title: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?) =
        delegate.sheet(strings.resolve(title), actions.map(::map), options?.let(::map))

    override fun webSheet(title: String, url: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?) =
        delegate.webSheet(strings.resolve(title), url, actions.map(::map), options?.let(::map))

    override fun form(title: String, items: Any, options: Map<String, Any?>?) =
        delegate.form(strings.resolve(title), strings.resolveValue(items) ?: items, options?.let(::map))

    override fun screen(title: String, root: Map<String, Any?>, options: Map<String, Any?>?) =
        delegate.screen(strings.resolve(title), map(root), options?.let(::map))

    override fun confirm(title: String, message: String, danger: Boolean) =
        delegate.confirm(strings.resolve(title), strings.resolve(message), danger)

    override fun progress(ratio: Float, label: String) = delegate.progress(ratio, strings.resolve(label))

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) = delegate.show(map(card), options?.let(::map))

    override fun automationStatus(label: String, detail: String) =
        delegate.automationStatus(strings.resolve(label), strings.resolve(detail))
}
