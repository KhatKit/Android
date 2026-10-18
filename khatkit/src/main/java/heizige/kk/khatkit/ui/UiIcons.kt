package heizige.kk.khatkit.ui

/**
 * Card DSL 里 `Icon.name` / `Button.icon` 允许的图标名。
 *
 * 名字用 Material 图标的 snake_case 形式（`arrow_back`），渲染侧在
 * [khatkit-ui 的 UiIconVectors] 里映射成 ImageVector。校验放在运行时模块，
 * `ui_kit.lua` 里维护同一份名单，由 `UiIconsTest` 保证两边一致。
 */
object UiIcons {
    val names: Set<String> = linkedSetOf(
        "account_circle", "add", "analytics", "arrow_back", "arrow_downward", "arrow_forward",
        "arrow_upward", "bolt", "block", "calendar_month", "cancel", "celebration", "check",
        "check_circle", "close", "delete", "edit", "email", "error", "event", "expand_more",
        "favorite", "favorite_border", "history", "hourglass_top", "info", "insights", "link",
        "lock", "more_vert", "notifications", "open_in_new", "paid", "people", "person",
        "psychology", "refresh", "remove", "rocket_launch", "schedule", "search", "settings",
        "share", "star", "star_border", "sync", "thumb_down", "thumb_up", "timer", "trending_down",
        "trending_up", "verified", "visibility", "warning",
    )

    fun isValid(name: String): Boolean = name in names
}
