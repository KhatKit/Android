package heizige.kk.khatkit.app.core.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 全仓每个 `PrimaryBottomSheet(...)` 调用点都必须显式给 `painter =` 或 `imageVector =`。
 *
 * ## 为什么要有这条
 *
 * 项目转发层 `core/ui/components/ui/PrimaryBottomSheet.kt` 在 `KedgeStyle.MD3Exp`
 * 分支上按「给了哪个」委派给 Khromia 的两个重载；当 `painter == null` 时落到第二个重载，
 * 参数写成 `requireNotNull(imageVector) { "PrimaryBottomSheet 需要 painter 或 imageVector 之一" }`。
 * 于是**两个都不传 = 运行时 IllegalArgumentException**，而且只在 MD3Exp 风格下炸
 * （Miuix 分支把 nullable 的 `imageVector` 直接透传给 Kedge，不炸）。
 *
 * 这个 bug 逃过 CI 的原因很具体：CI 只跑编译 + JVM 单测，**一行 Compose 没上过屏**。
 * Kotlin 侧完全合法 —— `painter`/`imageVector` 都有 `= null` 默认值，编译器不可能报错；
 * 异常只在真正的组合函数跑到那一行时才抛。真机实测点群聊右上角「群配置」必崩：
 * `IllegalArgumentException: PrimaryBottomSheet 需要 painter 或 imageVector 之一`，
 * 栈顶 `PrimaryBottomSheet.kt` → `GroupChatPage.kt:GroupConfigSheet`。
 *
 * 仓库 testImplementation 只有 junit，没有 Robolectric / compose-ui-test，组合函数跑不起来，
 * 所以这里断言的是**源码文本**。能钉住的是「调用点有没有把图标参数写出来」，
 * 这恰好就是本次崩的唯一变量。
 *
 * ## 为什么不是改组件语义（给个默认图标兜底）
 *
 * 兜底看着更省事，但**会把编译器能抓的错降级成肉眼才看得出的错**：漏传的地方不再炸，
 * 弹层顶上就默默挂一个和内容无关的默认图标，语义错了但没有任何信号。`require` 是在
 * 说「图标是这个弹层语义的一部分，不是装饰」，把它关掉等于放弃这条语义。
 * 更实际的是：两个参数的默认值本来就是 `= null`（允许不传只是为了让 Miuix 分支能过），
 * 漏传在源码层面就长得和「故意不传」一模一样，没有任何静态信号能再区分二者。
 * 所以保 `require`，改为在源码层把每个调用点都扫一遍。
 *
 * ## 扫的方式（比正则更严的两处）
 *
 * 1. **先剥注释与字符串字面量再解析。** 参数列表里写注释是很自然的（本次修复就加了
 *    四行说明为什么必须给图标）。若直接在原文上匹配 `painter\s*=` / `imageVector\s*=`
 *    会把注释里出现的这两个词当成真的传参 —— 假阴性，且恰好发生在最需要护栏的那种
 *    「调用点带注释」的写法上。
 * 2. **只认顶层实参。** 用括号配平切出本次调用的实参，再按深度 0 的逗号切分，
 *    只在每个顶层片段上匹配 `名字 =`。否则嵌套 lambda / 子调用里恰好出现
 *    `imageVector =`（比如 content 里又开了一张 sheet）就会替外层顶缸，同样是假阴性。
 *
 * 断言同时统计「本次扫描实际覆盖了多少个调用点」，防止以后有人把路径写窄、
 * 或者把某个文件挪出扫描范围，护栏静默地什么都不再检查。
 */
class PrimaryBottomSheetIconSourceGuardTest {

    private companion object {
        /** 转发层自己的声明所在文件：不检查（它是 `require` 的作者，不是调用点）。 */
        const val DECL_FILE = "src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt"

        /**
         * 扫的源码根。测试跑在 `app` 模块里，工作目录是 `app/`，所以本模块是
         * `src/main/java`、兄弟模块 `khatkit-ui` 要走 `../`。
         *
         * `khatkit-ui` 也得扫：那边虽直接 import Khromia 的组件，但同属
         * 「PrimaryBottomSheet 调用点」，一起守住，免得以后有人换 import 时踩同一个坑。
         */
        val SOURCE_ROOTS = listOf("src/main/java", "../khatkit-ui/src/main/java")

        /** 调用点里出现这两个名字之一才算传了图标。 */
        val ICON_ARG_NAMES = setOf("painter", "imageVector")

        /**
         * 匹配函数调用。用 `(?<![\w.])` 前视排除 `KedgePrimaryBottomSheet(` /
         * `KhromiaPrimaryBottomSheet(` 这类转发层内部对下游组件的委派调用 ——
         * 那是组件内部实现，不是「业务调用点漏传图标」。
         */
        val CALL_REGEX = Regex("(?<![\\w.])PrimaryBottomSheet\\s*\\(")
    }

    /** 把注释与字符串字面量替换成等长空白（保留换行，行号才不会漂）。 */
    private fun stripCommentsAndStrings(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        val n = src.length
        fun blank(from: Int, to: Int) {
            for (k in from until minOf(to, n)) if (src[k] != '\n') out.setCharAt(k, ' ')
        }
        while (i < n) {
            when {
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                    blank(i, end); i = end
                }
                src.startsWith("/*", i) -> {
                    // Kotlin 块注释可嵌套
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

    /** 从左括号起做括号配平，切出这一次调用的实参文本。 */
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

    /** 按深度 0 的逗号切分实参（嵌套的括号/lambda 内部逗号不算）。 */
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
        val argText: String,
    )

    /** 全仓所有 `PrimaryBottomSheet(` 调用点（不含转发层声明文件）。 */
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
                val raw = file.readText()
                val clean = stripCommentsAndStrings(raw)
                for (m in CALL_REGEX.findAll(clean)) {
                    val openParen = m.range.last
                    val args = callArgs(clean, openParen)
                    val names = splitTopLevelArgs(args)
                        .mapNotNull { part ->
                            NAMED_ARG_REGEX.find(part)?.groupValues?.get(1)
                        }
                        .toSet()
                    sites += CallSite(
                        file = rel,
                        // 行号在 clean 上算：strip 保持等长且保留换行，与 raw 行号一致
                        line = clean.substring(0, m.range.first).count { ch -> ch == '\n' } + 1,
                        namedArgs = names,
                        argText = args,
                    )
                }
            }
        }
        return sites
    }

    private val NAMED_ARG_REGEX = Regex("^\\s*([A-Za-z_]\\w*)\\s*=")

    /**
     * 主断言：每个调用点都带了 `painter =` 或 `imageVector =`。
     *
     * 漏一个就是真机必崩（且只在 MD3Exp 风格下崩，见类注释），所以这里一次性把所有
     * 漏传点的「文件:行号 + 实际传了哪些具名参数」列出来，避免只报第一个。
     */
    @Test
    fun everyPrimaryBottomSheetCallSite_passesAnIcon() {
        val sites = allCallSites()
        assertTrue("没扫到任何 PrimaryBottomSheet 调用点，扫描范围失效了", sites.isNotEmpty())

        val offenders = sites.filterNot { it.namedArgs.any { n -> n in ICON_ARG_NAMES } }

        assertEquals(
            buildString {
                appendLine("以下 PrimaryBottomSheet 调用点既没传 painter = 也没传 imageVector =，")
                appendLine("在 KedgeStyle.MD3Exp 下会走到 requireNotNull(imageVector) 直接抛异常：")
                offenders.forEach {
                    appendLine("  ${it.file}:${it.line}  实际传了 ${it.namedArgs.sorted()}")
                }
            },
            0,
            offenders.size,
        )
    }

    /**
     * 护栏自身不能腐化：把扫描覆盖面钉住。
     *
     * 数字来自 2026-10-06 这次修复后的真实调用点数（转发层声明文件不算）。
     * 以后**新增**调用点时这条会红 —— 那不是要求回改历史数字，而是提醒把新的
     * 调用点也纳入 `SOURCE_ROOTS` 的覆盖审查（正常情况下新增调用点会先撞上一条断言，
     * 改完带齐图标后调高这个数字并写清新增在哪即可）。
     */
    @Test
    fun callSiteScan_stillCoversTheWholeRepo() {
        val sites = allCallSites()
        assertEquals(
            "扫描到的 PrimaryBottomSheet 调用点数量变了：" +
                "新增调用点请确认它在扫描范围内，然后更新本数字并说明新增位置",
            65,
            sites.size,
        )
        assertTrue(
            "群聊页的 GroupConfigSheet 是本次真机崩的那个点，扫描必须覆盖到它",
            sites.any { it.file.endsWith("feature/chat/GroupChatPage.kt") },
        )
    }

    /**
     * 本次崩溃点的定点回归：群配置面板必须显式带上图标，且用的是顶栏「群配置」
     * 按钮同款 `tune` —— 触发按钮与它打开的弹层同图标，语义闭合。
     */
    @Test
    fun groupConfigSheet_passesTune() {
        val site = allCallSites().firstOrNull {
            it.file.endsWith("feature/chat/GroupChatPage.kt")
        } ?: error("没找到 GroupChatPage.kt 里的 PrimaryBottomSheet 调用点")
        assertTrue(
            "GroupConfigSheet 必须显式传 imageVector = tune（真机实测必崩点）",
            Regex("\\bimageVector\\s*=\\s*tune\\b").containsMatchIn(site.argText),
        )
    }
}