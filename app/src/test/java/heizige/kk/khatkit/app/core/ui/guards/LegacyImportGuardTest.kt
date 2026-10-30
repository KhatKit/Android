package heizige.kk.khatkit.app.core.ui.guards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P1 源码护栏（reviewer F2）：拦住「新增 Kedge / Miuix / Khromia import」。
 *
 * 扫描范围：全仓每个模块的 `<module>/src/main` 下的 *.kt（排除 build/ 与 .git/）。
 * 白名单：`app/src/test/resources/legacy-import-whitelist.txt`（每行 `<lib>\t<仓库相对路径>`），
 * 由 §10 的 grep 命令实测生成，P4/P5 逐批删除，P7 清空。
 */
class LegacyImportGuardTest {

    private val root: File = System.getProperty("user.dir").let(::File).let { dir ->
        generateSequence(dir.absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("找不到仓库根（settings.gradle.kts）：从 ${dir.absolutePath} 向上没找到")
    }

    private fun relPath(file: File): String =
        file.absoluteFile.relativeTo(root).path.replace(File.separatorChar, '/')

    /** 全仓各模块 src/main 下的 Kotlin 源文件 → （仓库相对路径 → 源码文本）。 */
    private fun scanSources(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
            .forEach { module ->
                val srcMain = File(module, "src/main")
                if (!srcMain.isDirectory) return@forEach
                srcMain.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { result[relPath(it)] = it.readText() }
            }
        return result
    }

    private fun loadWhitelist(): Map<String, List<String>> {
        val text = javaClass.getResourceAsStream("/legacy-import-whitelist.txt")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("缺少测试资源 legacy-import-whitelist.txt")
        val map = linkedMapOf<String, List<String>>()
        text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val (lib, path) = line.split('\t', limit = 2)
                map[path] = (map[path] ?: emptyList()) + lib
            }
        return map
    }

    private fun whitelistPaths(): Set<String> = loadWhitelist().keys

    @Test
    fun noNewForbiddenImports() {
        val sources = scanSources()
        assertEquals("扫描到的源码文件数异常（应在 700+）", true, sources.size > 500)
        val violations = LegacyImportGuard.findViolations(sources, whitelistPaths())
        assertTrue(
            "出现新的 Kedge/Miuix/Khromia import（必须改用 mochi，或把该文件列入白名单并说明原因）：\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun whitelistHasNoStaleEntries() {
        val sources = scanSources()
        val stale = LegacyImportGuard.findStaleWhitelist(sources, whitelistPaths())
        assertTrue(
            "白名单有 ${stale.size} 条过期项（文件已删或已不含禁用 import，请从白名单移除）：\n" +
                stale.joinToString("\n"),
            stale.isEmpty(),
        )
    }

    /**
     * 白名单条数口径核对（生成时实测，误差 0）：
     *  - kedge 206（src/main 全仓）；其中 app/src/main + khatkit-ui/src/main 内 = 196（= §10 命令口径），
     *    另外 10 个在 mediapicker/src/main；
     *  - miuix 36、khromia 79（两者 src/main 全仓口径与 §10 命令一致）。
     * 白名单只收 src/main：androidTest 里的 2 个 kedge 引用不在护栏范围内（不参与 app 运行期依赖）。
     */
    @Test
    fun whitelistMatchesMeasuredInventory() {
        val whitelist = loadWhitelist()
        val byLib = whitelist.values.flatten().groupingBy { it }.eachCount()
        assertEquals("kedge 白名单条数", 169, byLib["kedge"] ?: 0)
        assertEquals("miuix 白名单条数", 11, byLib["miuix"] ?: 0)
        assertEquals("khromia 白名单条数", 71, byLib["khromia"] ?: 0)
        assertEquals("白名单条目总数（同文件可命中多个库）", 251, whitelist.values.sumOf { it.size })

        val kedgeRoots = whitelist.count { (path, libs) ->
            "kedge" in libs && (path.startsWith("app/src/main/") || path.startsWith("khatkit-ui/src/main/"))
        }
        assertEquals("kedge 在 app+khatkit-ui 内的条数（§10 口径）", 159, kedgeRoots)
        assertTrue(
            "白名单只应收 src/main 路径",
            whitelist.keys.all { "/src/main/" in it },
        )
    }

    // ---- 以下两条：护栏逻辑自检（喂伪造输入，不依赖仓库真实状态） ----

    @Test
    fun guardFlagsForgedViolation() {
        val forged = mapOf(
            "app/src/main/java/FakeNewPage.kt" to """
                package fake
                import androidx.compose.runtime.Composable
                import heizige.kk.${"kedge"}.components.KedgeCard
                @Composable fun Fake() = KedgeCard {}
            """.trimIndent(),
            "app/src/main/java/CleanPage.kt" to """
                package fake
                import ui.feice.mochi.basic.MochiListRow
            """.trimIndent(),
        )
        val violations = LegacyImportGuard.findViolations(forged, whitelist = emptySet())
        assertEquals("伪造的 kedge import 必须被抓到", 1, violations.size)
        assertTrue("失败信息要带路径", violations[0].startsWith("app/src/main/java/FakeNewPage.kt -> "))
        assertTrue("失败信息要带命中前缀", "heizige.kk.kedge" in violations[0])
    }

    @Test
    fun guardAcceptsWhitelistedAndCleanSources() {
        val forged = mapOf(
            "app/src/main/java/LegacyPage.kt" to ("import top.yukonga." + "miuix.kmp.theme.MiuixTheme"),
            "app/src/main/java/CleanPage.kt" to "import ui.feice.mochi.theme.MochiTheme",
        )
        assertEquals(
            "白名单内的存量文件、以及只 import mochi 的文件都不算违规",
            0,
            LegacyImportGuard.findViolations(forged, setOf("app/src/main/java/LegacyPage.kt")).size,
        )
        // 反向：把白名单指向干净文件 ⇒ 该条目应被判为过期
        assertEquals(
            "白名单指向不含禁用 import 的文件应判过期",
            listOf("app/src/main/java/CleanPage.kt"),
            LegacyImportGuard.findStaleWhitelist(forged, setOf("app/src/main/java/CleanPage.kt")),
        )
    }
}
