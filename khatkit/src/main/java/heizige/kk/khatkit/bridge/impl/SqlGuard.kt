package heizige.kk.khatkit.bridge.impl

/**
 * `store.sql` 的准入检查：只允许卡片在自己库文件里跑单条 SQL。
 *
 * 反射派发进来的 SQL 是第三方代码，SQLite 本身没有「只碰一个文件」的概念，
 * 所以在执行前挡住能跨库 / 跨文件的语句：
 *
 * - `ATTACH` / `DETACH`：能挂上宿主主库（如聊天数据库）读写，必须挡住；
 * - `VACUUM`（含 `VACUUM INTO '任意路径'`）：能往任意可写路径落文件，必须挡住；
 * - `LOAD_EXTENSION`：加载原生扩展。
 *
 * 一次调用只接受一条语句（尾随分号除外），避免批量执行被当成注入通道。
 */
object SqlGuard {

    /** 关键词按「整词」匹配，避免 `my_attach_column` 之类被误伤。 */
    private val BLOCKED = setOf("ATTACH", "DETACH", "VACUUM", "LOAD_EXTENSION")

    /** 字符串 / 标识符字面量：先摘掉再判断，否则 `SELECT 'attach'` 会误判。 */
    private val LITERAL = Regex("""'(?:[^']|'')*'|"(?:[^"]|"")*"|`(?:[^`]|``)*`|\[[^]]*]""")

    /** 注释：注释里的 `;` / 关键字都不是语句的一部分。 */
    private val COMMENT = Regex("""--[^\n]*|/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    private val TOKEN = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** @throws IllegalArgumentException 带中文说明。 */
    fun check(sql: String) {
        require(sql.isNotBlank()) { "store.sql 的语句不能为空" }
        val stripped = COMMENT.replace(LITERAL.replace(sql, " "), " ")
        val body = stripped.trim()
        val semicolons = body.count { it == ';' }
        require(semicolons == 0 || (semicolons == 1 && body.endsWith(";"))) {
            "store.sql 一次只允许执行一条语句：请拆成多次调用"
        }
        val blocked = TOKEN.findAll(stripped)
            .map { it.value.uppercase() }
            .firstOrNull { it in BLOCKED }
        require(blocked == null) {
            "store.sql 不允许执行 $blocked：卡片只能操作自己的库文件（databases/cards/<卡片名>.db）"
        }
    }

    /** 语句是否返回结果集（决定走 query 还是 execute 路径）。 */
    fun returnsRows(sql: String): Boolean {
        val head = TOKEN.findAll(COMMENT.replace(LITERAL.replace(sql, " "), " "))
            .firstOrNull()?.value?.uppercase()
        return head in ROW_KEYWORDS
    }

    private val ROW_KEYWORDS = setOf("SELECT", "WITH", "PRAGMA", "EXPLAIN", "VALUES")
}
