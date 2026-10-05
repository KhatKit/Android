package heizige.kk.khatkit.app.core.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `PrimaryBottomSheet(...)` 调用点不得同时「让弹层滚」和「内容自己滚」——那会套两层滚动容器，
 * 真机必崩 `IllegalStateException: Vertically scrollable component was measured with an
 * infinity maximum height constraints`。
 *
 * ## 为什么要有这条
 *
 * 转发层 `core/ui/components/ui/PrimaryBottomSheet.kt` 把 `scrollable` 原样传给 Khromia，
 * 而 Khromia `BasicBottomSheet`（`components/BottomSheet.kt:353`）是这么用的：
 *
 * ```kotlin
 * Column(modifier = Modifier.weight(1f, fill = false)
 *     .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
 *     currentContent()
 * }
 * ```
 *
 * 也就是说 **`scrollable = true`（默认值）时，弹层已经给内容套了一层 `verticalScroll`**。
 * 而 `verticalScroll` 内部是拿 `maxHeight = Infinity` 去量自己内容的，于是：
 *
 * ```
 * BasicBottomSheet 的 verticalScroll          <- 已有一层滚动
 *   └─ PrimaryBottomSheet 的 Column(fillMaxWidth)
 *        └─ 调用点自己的 Column(Modifier.verticalScroll(...))   <- 第二层滚动，被量到 Infinity
 * ```
 *
 * 内层那个 `ScrollNode` 一上来就撞上「infinity maximum height constraints」并抛异常。
 * 这也是真机栈里那个 `ScrollNode.measure` 套 `ScrollNode.measure` 的形状。
 *
 * 全仓约定是**二选一**，62 个调用点里没有一个同时干两件事：
 *
 * - 交给弹层滚：`scrollable` 保持默认 `true`，内容里不出现任何滚动容器（本测试的主体规则）；
 * - 自己滚：`scrollable = false` + 内容自带 `verticalScroll` / `LazyColumn`（19 处，全是这个配对）。
 *
 * 群聊「群配置」面板原本是唯一同时干两件事的调用点：`GroupConfigSheet` 自己带
 * `verticalScroll` 却漏了 `scrollable = false`，点顶栏「群配置」必崩。
 * 同一个面板在同一轮里还漏传了图标参数（`PrimaryBottomSheetIconSourceGuardTest` 守着那条），
 * 两个 `require`/不变量都是同一种形态的错：**默认值 + 组合函数，只有上过屏才炸**。
 *
 * ## 为什么源码扫而不是 Robolectric
 *
 * 仓库 testImplementation 只有 junit，组合函数在 JVM 上跑不起来
 * （见 `PrimaryBottomSheetIconSourceGuardTest` 的类注释）。
 * 所以断言源码文本。能钉住的是「调用点有没有把两层滚动容器叠在一起」，
 * 这恰好就是本次崩的唯一变量。
 *
 * ## 这条规则覆盖不到的一处（诚实说明）
 *
 * 内容被转交给别的组合函数时（例如 `khatkit-ui/KhatKitSheet.kt` 把 `content` 透传给
 * 上层、由调用方决定滚不滚），源码扫看不出那层到底滚不滚。文本扫在这里只能做到
 * 「不制造新的违规点」，判不了「透传进来的 content 是不是已经在滚」。
 * 这正是下面 `groupConfigSheet_...` 那条定点回归存在的理由：把真机炸过的这个点
 * 的**具体形态**（`scrollable = false` + 恰好一个 `verticalScroll`）单独钉住。
 *
 * ## 扫的方式
 *
 * 1. **先剥注释与字符串字面量**再解析，保留换行让行号不漂（调用点加注释是常态）。
 * 2. **必须把尾随 lambda 也切进来。** `content` 是最后一个参数时写法是
 *    `PrimaryBottomSheet(...) { _ -> ... }`，大括号在**右括号外面**。
 *    只按括号配平切实参会完全看不到内容体，本次崩溃点就会漏检（写这条护栏时先踩过）。
 * 3. **只认顶层具名实参**：`content = { ... }` 形式同样要算进去。
 */
class BottomSheetScrollSourceGuardTest {

    private companion object {
        /** 转发层自己的声明所在文件：不检查（它不产内容体，是转发实现）。 */
        const val DECL_FILE = "src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt"

        /** 扫的源码根；工作目录是 `app/`，兄弟模块 `khatkit-ui` 走 `../`。 */
        val SOURCE_ROOTS = listOf("src/main/java", "../khatkit-ui/src/main/java")

        /** 内容体里出现这些就算「内容自己也在滚」。 */
        val SELF_SCROLL_REGEX =
            Regex("\\.verticalScroll\\s*\\(|\\bLazyColumn\\s*\\(|\\bLazyVerticalGrid\\s*\\(|\\bLazyColumnForEach\\s*\\(")

        /** 排除转发层内部对下游组件的委派调用。 */
        val CALL_REGEX = Regex("(?<![\\w.])PrimaryBottomSheet\\s*\\(")

        val NAMED_ARG_REGEX = Regex("^\\s*([A-Za-z_]\\w*)\\s*=")
    }

    /** 把注释与字符串字面量替换成等长空白（保留换行）。 */
    private fun stripCommentsAndStrings(src: String): String {
        val out = StringBuilder(src)
        val n = src.length
        fun blank(from: Int, to: Int) {
            for (k in from until minOf(to, n)) if (src[k] != '\n') out.setCharAt(k, ' ')
        }
        var i = 0
        while (i < n) {
            when {
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                    blank(i, end); i = end
                }
                src.startsWith("/*", i) -> {
                    var depth = 1
                    var j = i + 2
                    while (j < n && depth > 0) {
                        when {
                            src.startsWith("/*", j) -> { depth++; j += 2 }
                            src.startsWith("*/", j) -> { depth--; j += 2 }
                            else -> j++
                        }
                    }
                    blank(i, j); i = j
                }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                    blank(i, end); i = end
                }
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < n) {
                        when {
                            src[j] == '\\' -> j += 2
                            src[j] == '"' -> { j++; break }
                            src[j] == '\n' -> break
                            else -> j++
                        }
                    }
                    blank(i, j); i = j
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** 从左括号起配平，切出实参文本。 */
    private fun callArgs(src: String, openParen: Int): String {
        var depth = 0
        var j = openParen
        while (j < src.length) {
            when (src[j]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openParen + 1, j)
                }
            }
            j++
        }
        return ""
    }

    /** 从左花括号起配平，切出 lambda 体文本。 */
    private fun blockBody(src: String, openBrace: Int): String {
        var depth = 0
        var j = openBrace
        while (j < src.length) {
            when (src[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openBrace + 1, j)
                }
            }
            j++
        }
        return ""
    }

    /** 按深度 0 的逗号切分实参。 */
    private fun splitTopLevelArgs(args: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        for (c in args) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ',' && depth == 0) {
                parts += cur.toString(); cur.setLength(0)
            } else {
                cur.append(c)
            }
        }
        if (cur.isNotBlank()) parts += cur.toString()
        return parts
    }

    private data class CallSite(
        val file: String,
        val line: Int,
        val namedArgs: Set<String>,
        /** 实参文本 + 尾随 lambda 体（内容真正写在哪两者都要看到）。 */
        val contentText: String,
        /** 内容体是否自带纵向滚动容器。 */
        val selfScrolls: Boolean,
        /** 是否显式 `scrollable = false`。 */
        val scrollDisabled: Boolean,
    )

    private fun allCallSites(): List<CallSite> {
        val sites = mutableListOf<CallSite>()
        for (root in SOURCE_ROOTS) {
            val dir = File(root)
            assertTrue("源码目录不存在：$root（测试的工作目录应是 app 模块）", dir.isDirectory)
            val files = dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
                .toList()
            for (file in files) {
                val rel = file.path.replace('\\', '/')
                if (rel.endsWith(DECL_FILE)) continue
                val clean = stripCommentsAndStrings(file.readText())
                for (m in CALL_REGEX.findAll(clean)) {
                    val args = callArgs(clean, m.range.last)
                    val names = splitTopLevelArgs(args)
                        .mapNotNull { part -> NAMED_ARG_REGEX.find(part)?.groupValues?.get(1) }
                        .toSet()

                    // 尾随 lambda：右括号之后紧跟的 `{ ... }`，必须一起切进来。
                    // 起点是 `)` 的下一位 = openParen + 1 + args.length + 1。
                    var contentText = args
                    var k = m.range.last + args.length + 2
                    while (k < clean.length && clean[k].isWhitespace()) k++
                    if (k < clean.length && clean[k] == '{') contentText += blockBody(clean, k)

                    sites += CallSite(
                        file = rel,
                        line = clean.substring(0, m.range.first).count { ch -> ch == '\n' } + 1,
                        namedArgs = names,
                        contentText = contentText,
                        selfScrolls = SELF_SCROLL_REGEX.containsMatchIn(contentText),
                        scrollDisabled = Regex("\\bscrollable\\s*=\\s*false\\b").containsMatchIn(args),
                    )
                }
            }
        }
        return sites
    }

    /**
     * 主断言：不得既有弹层自带滚动（没显式 `scrollable = false`）、
     * 又有内容自带纵向滚动容器。两者同时成立就是套了两层滚动，真机必崩。
     */
    @Test
    fun noCallSite_nestsItsOwnVerticalScrollInsideAScrollableSheet() {
        val sites = allCallSites()
        assertTrue("没扫到任何 PrimaryBottomSheet 调用点，扫描范围失效了", sites.isNotEmpty())

        val offenders = sites.filter { !it.scrollDisabled && it.selfScrolls }

        assertEquals(
            buildString {
                appendLine("以下 PrimaryBottomSheet 调用点同时有「弹层滚动」(scrollable 未传 false)")
                appendLine("和「内容自己 verticalScroll / LazyColumn」，滚动容器套了两层，真机会抛")
                appendLine("IllegalStateException: Vertically scrollable component was measured with an")
                appendLine("infinity maximum height constraints。只能二选一：")
                appendLine("  - 交给弹层滚：保持默认 scrollable = true，内容里删掉自己的滚动容器；")
                appendLine("  - 自己滚：加 scrollable = false，内容保留自己的 verticalScroll/LazyColumn。")
                offenders.forEach { appendLine("  ${it.file}:${it.line}") }
            },
            0,
            offenders.size,
        )
    }

    /**
     * 护栏自身不能腐化：覆盖面与规则有效性一起钉住。
     *
     * 顺带钉一条**反向**事实——必须真的存在「自己滚」的调用点，否则说明
     * `SELF_SCROLL_REGEX` 写坏了（比如漏转义导致永远匹配不上），
     * 主断言就会因为「谁都不违规」而恒绿。
     */
    @Test
    fun scan_stillFindsSelfScrollingCallSites() {
        val sites = allCallSites()
        assertEquals(
            "扫描到的 PrimaryBottomSheet 调用点数量变了：" +
                "新增调用点请确认它在扫描范围内，然后更新本数字并说明新增位置",
            65,
            sites.size,
        )
        val selfScrolling = sites.filter { it.selfScrolls }
        assertTrue(
            "一个「内容自带滚动」的调用点都没扫到，SELF_SCROLL_REGEX 失效了，" +
                "主断言会变成永远绿的空检查",
            selfScrolling.isNotEmpty(),
        )
        assertTrue(
            "所有自己滚的调用点都应该配 scrollable = false（当前 ${selfScrolling.size} 处）",
            selfScrolling.all { it.scrollDisabled },
        )
        assertTrue(
            "群聊页的 GroupConfigSheet 是本次真机崩的那个点，扫描必须覆盖到它",
            sites.any { it.file.endsWith("feature/chat/GroupChatPage.kt") },
        )
    }

    /**
     * 本次崩溃点的定点回归：群配置面板必须显式 `scrollable = false`，
     * 且内容里**恰好一个** `verticalScroll`。
     *
     * 为什么单独钉这一条：主规则只看「有没有叠两层」，判不出「滚动权归谁」；
     * 而这个面板的正确答案就是它自己滚（内容很长），钉住具体形态才防得住
     * 「顺手把 scrollable 改回 true」或「顺手再套一层 LazyColumn」。
     */
    @Test
    fun groupConfigSheet_scrollsItselfAndOnlyItself() {
        val site = allCallSites().firstOrNull {
            it.file.endsWith("feature/chat/GroupChatPage.kt")
        } ?: error("没找到 GroupChatPage.kt 里的 PrimaryBottomSheet 调用点")

        assertTrue(
            "GroupConfigSheet 必须显式传 scrollable = false（真机实测滚动必崩点）：" +
                "Khromia BasicBottomSheet 在 scrollable = true 时已经套了一层 verticalScroll",
            site.scrollDisabled,
        )
        val scrolls = SELF_SCROLL_REGEX.findAll(site.contentText).count()
        assertEquals(
            "GroupConfigSheet 内应恰好有 1 个纵向滚动容器（自己那层），实际 $scrolls 个",
            1,
            scrolls,
        )
    }
}