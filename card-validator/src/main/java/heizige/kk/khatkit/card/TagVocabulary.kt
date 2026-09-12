package heizige.kk.khatkit.card

/**
 * 标签受控词表（设计文档 5.4）。
 *
 * 不用深层树：两个正交维度各选一个，场景可选一个。
 * AI 打标签是**选择题**（从枚举里选），不是自由生成。
 */
object TagVocabulary {
    val DOMAINS: Set<String> = linkedSetOf(
        "file",    // 文件
        "media",   // 图片/视频/音频
        "app",     // 应用管理
        "system",  // 系统设置
        "net",     // 网络请求
        "text",    // 文本处理
        "device",  // 设备控制（屏幕/传感器）
        "game",    // 游戏（画面识别/挂机/自动任务）
        "social",  // 社交通讯
        "data",    // 结构化数据（表格/数据库）
    )

    val ACTIONS: Set<String> = linkedSetOf(
        "read", "convert", "batch", "clean", "monitor", "control", "create",
    )

    val SCENES: Set<String> = linkedSetOf(
        "work", "dev", "daily", "privacy", "gaming",
    )

    /** 语义位总数：10 × 7 */
    const val SEMANTIC_SLOTS: Int = 70

    fun isValidDomain(value: String?): Boolean = value != null && value in DOMAINS
    fun isValidAction(value: String?): Boolean = value != null && value in ACTIONS
    fun isValidScene(value: String?): Boolean = value == null || value in SCENES
}
