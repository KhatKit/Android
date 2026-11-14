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
        "favorite", "favorite_border", "folded_hands", "history", "hourglass_top", "info", "insights", "link",
        "lock", "more_vert", "notifications", "open_in_new", "paid", "people", "person",
        "psychology", "public", "refresh", "remove", "rocket_launch", "schedule", "search", "settings",
        "share", "star", "star_border", "sync", "thumb_down", "thumb_up", "timer", "trending_down",
        "trending_up", "verified", "visibility", "warning",
        // Material Symbols Rounded 补齐（khatkit-ui 的 UiIconsSymbols.kt 提供矢量）
        "casino", "emoji_events",
        // Material Symbols Outlined（khatkit-ui 的 UiIconsSymbolsOutlined.kt，线性风格同 24dp 规格）
        "text_fields", "subject", "notes", "translate", "format_list_numbered", "data_object",
        // 云盘解析卡片用到的图标（material-icons-core/extended 直接提供）
        "add_circle", "chevron_left", "chevron_right", "content_paste", "clear_history",
        "folder", "folder_open", "insert_drive_file", "key", "verified_user",
        // 厂商品牌 logo（khatkit-ui 的 UiIconsBrands.kt，Simple Icons 路径）
        "brand_baidu", "brand_xhs", "brand_kuaishou", "brand_weibo", "brand_douyin",
        "brand_bilibili", "brand_pixiv", "brand_github", "brand_quark", "brand_uc",
        "brand_xunlei", "brand_c139",
    )

    fun isValid(name: String): Boolean = name in names
}
