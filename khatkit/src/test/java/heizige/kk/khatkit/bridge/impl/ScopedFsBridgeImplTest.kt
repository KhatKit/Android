package heizige.kk.khatkit.bridge.impl

import android.content.ContextWrapper
import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.FsBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

/**
 * `fs` 的沙箱边界测试（规格 T0.4 验收项）。
 *
 * 用只覆盖 `filesDir` 的假 Context 而不是 mock 框架：khatkit 模块没有 mock 依赖，
 * 而这些方法只用到 `context.filesDir`。
 */
class ScopedFsBridgeImplTest {

    private val cardName = "card"

    private fun tempRoot(): File = createTempDirectory("khatkit-fs").toFile()

    private fun fs(
        roots: Set<String> = emptySet(),
        gate: ApprovalGate? = null,
        root: File,
    ): FsBridge = ScopedFsBridgeImpl(
        context = FakeContext(root),
        cardName = cardName,
        roots = roots,
        approvalGate = gate,
    )

    private fun privateFile(root: File, name: String, content: String = "x"): File =
        File(root, "cards/$cardName/$name").apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    // ------------------------------------------------------------------ 路径归一化

    @Test
    fun `canonicalize escape is rejected`() {
        val root = tempRoot()
        val outside = tempRoot()
        val secret = File(outside, "secret.txt").apply { writeText("top secret") }

        val bridge = fs(roots = setOf(root.canonicalPath), root = root)
        // /a/../../etc/passwd 这类相对回退：规范化后落在 root 之外，必须拒绝
        assertRejected { bridge.read(File(root, "a/../../etc/passwd").path) }
        assertRejected { bridge.read(secret.absolutePath) }
        assertRejected { bridge.write(File(root, "x/../../escape.txt").path, "x") }
    }

    @Test
    fun `symlink pointing outside roots is rejected`() {
        val root = tempRoot()
        val outside = tempRoot()
        val secret = File(outside, "secret.txt").apply { writeText("top secret") }

        // 环境不允许建软链时跳过（某些 CI 的 /tmp 挂载会禁掉）
        val link = File(root, "link.txt")
        if (runCatching { Files.createSymbolicLink(link.toPath(), secret.toPath()) }.isFailure) return

        val bridge = fs(roots = setOf(root.canonicalPath), root = root)
        // canonicalPath 会把软链解析成真实目标，所以按目标判越界
        assertRejected { bridge.read(link.absolutePath) }
        assertEquals("top secret", secret.readText())
    }

    @Test
    fun `empty roots only allow the card private directory`() {
        val root = tempRoot()
        val bridge = fs(root = root)
        privateFile(root, "note.txt", "hello")
        assertEquals("hello", bridge.read(File(root, "cards/$cardName/note.txt").absolutePath))

        // 私有目录之外一律拒绝（哪怕就在应用 filesDir 下）
        assertRejected { bridge.read(File(root, "other.txt").absolutePath) }
        assertRejected { bridge.write(File(root, "other.txt").absolutePath, "x") }
    }

    @Test
    fun `declared roots are readable and writable`() {
        val root = tempRoot()
        val declared = File(root, "declared").apply { mkdirs() }
        val bridge = fs(roots = setOf(declared.canonicalPath), root = root)
        val target = File(declared, "sub/file.txt")

        bridge.write(target.absolutePath, "written")
        assertEquals("written", bridge.read(target.absolutePath))
        assertTrue(bridge.exists(target.absolutePath))
        assertEquals("file.txt", bridge.stat(target.absolutePath)["name"])
    }

    @Test
    fun `stat and list return structured rows`() {
        val root = tempRoot()
        val bridge = fs(root = root)
        val dir = File(root, "cards/$cardName/notes").apply { mkdirs() }
        File(dir, "a.txt").writeText("aa")
        File(dir, "nested").mkdirs()
        File(dir, "nested/b.txt").writeText("b")

        // 非递归也返回子目录本身（只是不下钻）
        val flat = bridge.list(dir.absolutePath, recursive = false, limit = 10)
        assertEquals(2, flat.size)
        // list 不保证顺序（底层是 File.listFiles），按名字排序后比
        assertEquals(listOf("a.txt", "nested"), flat.map { it["name"].toString() }.sorted())
        assertEquals(false, flat.single { it["name"] == "a.txt" }["isDir"])
        assertEquals(true, flat.single { it["name"] == "nested" }["isDir"])
        assertEquals(2L, flat.single { it["name"] == "a.txt" }["size"])

        assertEquals(3, bridge.list(dir.absolutePath, recursive = true, limit = 10).size)
        assertEquals(1, bridge.list(dir.absolutePath, recursive = true, limit = 1).size)
        assertEquals(0, bridge.list(dir.absolutePath, recursive = false, limit = 0).size)
        assertEquals(2L, bridge.stat(File(dir, "a.txt").absolutePath)["size"])
    }

    // ------------------------------------------------------------------ 删除审批

    @Test
    fun `delete asks the gate and refuses when denied`() {
        val root = tempRoot()
        val target = privateFile(root, "gone.txt")
        val asked = mutableListOf<String>()

        val denied = fs(gate = ApprovalGate { title, _, category -> asked += "$title|$category"; false }, root = root)
        assertRejected { denied.delete(target.absolutePath) }
        assertEquals(listOf("删除卡片文件|file_delete"), asked)
        assertTrue("拒绝后文件必须还在", target.exists())

        val allowed = fs(gate = ApprovalGate { _, _, _ -> true }, root = root)
        assertTrue(allowed.delete(target.absolutePath))
        assertFalse(target.exists())
    }

    @Test
    fun `missing gate means deny`() {
        val root = tempRoot()
        val target = privateFile(root, "gone.txt")
        assertRejected { fs(gate = null, root = root).delete(target.absolutePath) }
        assertTrue(target.exists())
    }

    @Test
    fun `non empty directory requires recursive`() {
        val root = tempRoot()
        val dir = File(root, "cards/$cardName/folder").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        val bridge = fs(gate = ApprovalGate { _, _, _ -> true }, root = root)

        assertRejected { bridge.delete(dir.absolutePath, recursive = false) }
        assertTrue(bridge.delete(dir.absolutePath, recursive = true))
        assertFalse(dir.exists())
    }

    // ------------------------------------------------------------------ 压缩

    @Test
    fun `zip then unzip round trip stays inside the sandbox`() {
        val root = tempRoot()
        val bridge = fs(root = root)
        val source = File(root, "cards/$cardName/pack").apply { mkdirs() }
        File(source, "hello.txt").writeText("hello zip")

        val out = bridge.zip(listOf(source.absolutePath), File(root, "cards/$cardName/pack.zip").absolutePath)
        assertTrue(File(out).exists())

        val dir = bridge.unzip(out, File(root, "cards/$cardName/unpacked").absolutePath)
        assertEquals("hello zip", File(dir, "pack/hello.txt").readText())
    }

    @Test
    fun `zip rejects sources outside the sandbox`() {
        val root = tempRoot()
        val secret = File(tempRoot(), "secret.txt").apply { writeText("top secret") }
        val bridge = fs(root = root)
        assertRejected {
            bridge.zip(listOf(secret.absolutePath), File(root, "cards/$cardName/pack.zip").absolutePath)
        }
    }

    @Test
    fun `unzip rejects zip slip entries`() {
        val root = tempRoot()
        val bridge = fs(root = root)
        val evil = File(root, "cards/$cardName/evil.zip")
        ZipOutputStream(evil.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("owned".toByteArray())
            zos.closeEntry()
        }
        assertRejected { bridge.unzip(evil.absolutePath, File(root, "cards/$cardName/unpacked").absolutePath) }
        assertFalse(File(root, "cards/$cardName/escaped.txt").exists())
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("应当抛错：路径越界或被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue("错误要有中文说明", e.message.orEmpty().isNotBlank())
        } catch (e: SecurityException) {
            assertTrue("错误要有中文说明", e.message.orEmpty().isNotBlank())
        }
    }

    /** 只覆盖 `filesDir` 的最小 Context：fs 的沙箱逻辑只用这一个字段。 */
    private class FakeContext(private val filesDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = filesDir
    }
}