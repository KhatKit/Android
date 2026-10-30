package heizige.kk.khatkit.app.core.ui.guards

/**
 * 源码护栏核心逻辑（纯函数，便于自检）。
 *
 * 规则：**不允许新增**三个待迁出库（kedge / miuix / khromia）的 import；
 * 存量文件必须逐路径登记在白名单里。
 * 注意：本文件刻意不出现完整的禁用语包字面量，避免自己把 §10 的 grep 计数抬高。
 * P4/P5 每删一批就把路径从白名单删掉，P7 白名单清空 —— 这是 C 方案唯一的防回退机制。
 */
object LegacyImportGuard {

    /** 目标迁移要清掉的三个库的包前缀。 */
    val FORBIDDEN_PREFIXES: List<String> = listOf(
        "heizige.kk." + "kedge",
        "top.yukonga." + "miuix",
        "heizige.kk." + "khromia",
    )

    private val IMPORT_LINE = Regex("""(?m)^\s*import\s+([A-Za-z0-9_.\x60]+)""")

    /** 抽取一个 Kotlin 源码里的全部 import 全限定名。 */
    fun importsOf(source: String): List<String> =
        IMPORT_LINE.findAll(source).map { it.groupValues[1] }.toList()

    /** 返回该源码命中的禁用库包前缀（去重，按出现顺序）。 */
    fun forbiddenHits(source: String): List<String> {
        val hits = mutableListOf<String>()
        for (fqn in importsOf(source)) {
            for (prefix in FORBIDDEN_PREFIXES) {
                if (fqn == prefix || fqn.startsWith("$prefix.")) {
                    if (prefix !in hits) hits += prefix
                }
            }
        }
        return hits
    }

    /**
     * 违规 = 命中了禁用 import 且不在白名单里。
     * 返回可读条目：`<path> -> <命中前缀...>`，已排序，便于直接贴进失败信息。
     */
    fun findViolations(sources: Map<String, String>, whitelist: Set<String>): List<String> =
        sources.entries
            .filter { it.key !in whitelist }
            .mapNotNull { (path, text) ->
                val hits = forbiddenHits(text)
                if (hits.isEmpty()) null else "$path -> ${hits.joinToString()}"
            }
            .sorted()

    /**
     * 白名单过期项 = 白名单里登记了、但现在要么文件不存在、要么已经不含禁用 import。
     * P4/P5 删完必须回头删白名单，否则这里会红。
     */
    fun findStaleWhitelist(sources: Map<String, String>, whitelist: Set<String>): List<String> =
        whitelist.filter { path ->
            val text = sources[path]
            text == null || forbiddenHits(text).isEmpty()
        }.sorted()
}
