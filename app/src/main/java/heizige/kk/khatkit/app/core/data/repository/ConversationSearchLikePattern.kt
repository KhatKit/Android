package heizige.kk.khatkit.app.core.data.repository

/**
 * 会话标题「包含匹配」用的 LIKE 转义字符。
 *
 * ## 为什么选 `~`
 *
 * 转义字符必须同时躲开三类坑：
 *
 * 1. **不能是 LIKE 通配符自己**（`%` / `_`）。`ESCAPE '%'` 会让 `title LIKE '%' || :searchText
 *    || '%'` 里那两个包夹用的 `%` 也变成「转义符」，于是模式串开头就吃掉一个字符、结尾那个
 *    `%` 悬空；整条查询不是「语义变了」而是「语义彻底坏掉」。`_` 同理。
 * 2. **不能是反斜杠 `\`**。它不是 LIKE 的转义符，但它是 SQL/正则/JSON/Kotlin 四层里最常用的
 *    转义字符，选它会让同一条 SQL 在四层里各有一套「反斜杠要写几遍」的规矩：Kotlin 字面量里
 *    要 `\\`，任何一层日志里又要再翻一倍；而且 MySQL 系的读者看到 `\%` 会下意识按 MySQL 的
 *    语义理解，SQLite 这里完全不是那回事。转义**字符**的可读性直接被这个字符毁掉。
 * 3. **不能是标题里常见的标点**（`-` `.` `,` `:` `/` `+` `=` `@` `#` `!` `|` `^` `&`
 *    `(` `)` `[` `]` `{` `}` `<` `>` `?` `*` `;` `"` `'`）。它们本身在 SQLite 的 LIKE
 *    里都是普通字面量，转义**功能上**没问题，但会让「模型对比 / API 调用 / a-b 测试」
 *    这类标题的搜索模式串被撑成两倍长，日志与 explain 里的模式串也就没法看了；`"` `'`
 *    还额外要在 SQL 字面量里翻倍。
 *
 * `~` 同时躲开这三类：它不是通配符、不是任何一层的转义约定、在中文与英文会话标题里
 * 几乎不出现（要出现也得是 `~/path` 这类路径，而那种标题本来就在搜自己）。
 * SQLite 要求 `ESCAPE` 的表达式求值后**恰好 1 个字符**，`~` 是单字符，满足。
 *
 * 附带的自洽性检查：它对 ASCII 大小写折叠免疫（`~` 不是字母），所以转义不影响 G8 那条
 * 「LIKE 对 ASCII 大小写不敏感、对非 ASCII 不折叠」的既有行为。
 */
internal const val CONVERSATION_LIKE_ESCAPE_CHAR: Char = '~'

/**
 * 拼进 `@Query` 的 ESCAPE 子句（**首部带一个空格**，尾部不带 —— 后面的 SQL 片段自己带
 * 那个分隔空格，折叠出来才是单个空格而不是两个）。
 *
 * 与 [CONVERSATION_LIKE_ESCAPE_CHAR] 是两个平行的 `const`：SQL 里只能写字面量，而
 * Room/KSP 认的也只是编译期常量表达式，所以宁可让「SQL 片段」是一段**纯字面量**
 * （绝对能被 Room 折叠），再由 `ConversationSearchLikePatternTest` 断言它与字符常量
 * 逐字节一致 —— 两份常量漂移会当场变红，而不是等到某条查询的搜索结果莫名其妙。
 */
internal const val CONVERSATION_LIKE_ESCAPE_SQL: String = " ESCAPE '~'"

/**
 * 把用户输入的搜索词转成「可安全拼进 LIKE 模式串」的**字面量片段**。
 *
 * 纯函数、不碰 Room / Repository / Android，所以能在 JVM 单测里逐个字符钉死。
 * 必须在**进 DAO 之前**调用：`ConversationRepository` 是所有搜索路径的唯一收敛点，
 * 抽屉（`ChatDrawerViewModel`）与 HTTP API（`ConversationRoutes.kt`）传进来的都是
 * **未转义的原始用户输入**，由它统一转义，任何调用方都不需要、也不应该自己转。
 *
 * 语义：只做「让 `%` / `_` / 转义字符本身变成字面量」，**不改变「包含匹配」这件事**
 * —— 包夹用的 `'%' ... '%'` 由 SQL 负责，仍然是首尾通配。
 *
 * - 空串 → 空串；纯空白 → 原样（**空搜索词 / 纯空白的行为逐字不变**，抽屉那条
 *   「空词等于没搜」仍然只由 `planConversationListQuery` 判定，SQL 层照旧不拦）。
 * - 反斜杠、单引号等**不是** LIKE 特殊字符，一律原样透传（单引号由 Room 的参数绑定
 *   处理，不进 SQL 字面量，所以不需要也不应该在这里处理）。
 * - 顺序上转义字符**最先**处理，而且只在一次遍历里做：因此不存在「先转义 `%` 再把
 *   新插入的 `~` 又转义一遍」这种二次转义（那会把 `a%b` 变成 `a~~~%b` 之类的乱码）。
 * - 非 ASCII（含中文、emoji 的代理对）逐 `Char` 原样透传，不做任何大小写或规范化。
 *
 * 幂等性**不成立**也**不需要**：转义后的串是给 SQL 用的中间表示，不是能再喂一次的用户输入。
 */
internal fun escapeConversationLikePattern(rawSearchText: String): String {
    val escape = CONVERSATION_LIKE_ESCAPE_CHAR
    val out = StringBuilder(rawSearchText.length + 8)
    for (ch in rawSearchText) {
        if (ch == escape || ch == '%' || ch == '_') out.append(escape)
        out.append(ch)
    }
    return out.toString()
}
