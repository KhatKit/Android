package heizige.kk.khatkit.ui

/** Theme roles shared by text/icon colors and Modifier.background. Hex colors remain supported. */
object UiColors {
    val roles = setOf(
        "surface", "on_surface", "on_surface_variant", "surface_container",
        "surface_container_high", "surface_container_highest", "outline_variant",
        "primary", "on_primary", "error", "error_container", "on_error_container",
    )
    private val hex = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    fun isValid(value: String): Boolean = value in roles || hex.matches(value)
}
