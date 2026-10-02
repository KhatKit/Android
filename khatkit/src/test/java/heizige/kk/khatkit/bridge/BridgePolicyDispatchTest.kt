package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.engine.RustBridgeDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方法级策略在 dispatch 层的落地（规格 §2 要点 2）。
 *
 * 关键不变式：**未声明策略时行为与之前完全一致**（实现层自己的闸门决定要不要问），
 * 只有 manifest 显式写了 `allow` / `deny` 才改变结果。
 */
class BridgePolicyDispatchTest {

    private class RecordingFs(private val grants: RunGrants) : FsBridge {
        var asked = 0

        override fun delete(path: String, recursive: Boolean): Boolean {
            if (!grants.isGranted("fs.delete")) asked++
            return true
        }

        override fun read(path: String) = ""
        override fun write(path: String, content: String, append: Boolean) = Unit
        override fun exists(path: String) = false
        override fun stat(path: String): Map<String, Any?> = emptyMap()
        override fun list(path: String, recursive: Boolean, limit: Int): List<Map<String, Any?>> = emptyList()
        override fun copy(src: String, dst: String) = Unit
        override fun move(src: String, dst: String) = Unit
        override fun mkdir(path: String) = Unit
        override fun zip(paths: List<String>, output: String) = output
        override fun unzip(zipPath: String, outputDir: String) = outputDir
        override fun readBase64(path: String) = ""
        override fun saveBase64(data: String, outputPath: String) = outputPath
        override fun openDir(path: String) = Unit
    }

    private fun dispatcherFor(
        permissions: Map<String, String>,
        fs: FsBridge,
    ): Pair<RustBridgeDispatcher, BridgeContext> {
        val context = BridgeContext(cardName = "c", engine = "lua", quotaMb = 1, permissions = permissions)
        return RustBridgeDispatcher(mapOf("fs" to ScopedFsBridge(context, fs))) to context
    }

    @Test
    fun `deny short circuits before the implementation runs`() {
        val fs = RecordingFs(RunGrants())
        val (dispatcher, _) = dispatcherFor(mapOf("fs.delete" to "deny"), fs)

        val result = dispatcher.dispatch("fs", "delete", """["/a"]""")

        assertTrue(result, result.contains("禁止调用"))
        assertTrue(result.contains("fs.delete"))
        assertEquals("实现层不该被调用", 0, fs.asked)
    }

    @Test
    fun `allow lets the implementation skip asking`() {
        val context = BridgeContext(
            cardName = "c",
            engine = "lua",
            quotaMb = 1,
            permissions = mapOf("fs.delete" to "allow"),
        )
        val fs = RecordingFs(context.grants)
        val dispatcher = RustBridgeDispatcher(mapOf("fs" to ScopedFsBridge(context, fs)))

        val result = dispatcher.dispatch("fs", "delete", """["/a"]""")

        assertFalse(result, result.contains("__error"))
        assertEquals("allow 之后不该再问用户", 0, fs.asked)
        assertTrue(context.grants.isGranted("fs.delete"))
    }

    @Test
    fun `undeclared policy keeps the implementation default`() {
        val context = BridgeContext(cardName = "c", engine = "lua", quotaMb = 1)
        val fs = RecordingFs(context.grants)
        val dispatcher = RustBridgeDispatcher(mapOf("fs" to ScopedFsBridge(context, fs)))

        val result = dispatcher.dispatch("fs", "delete", """["/a"]""")

        assertFalse(result, result.contains("__error"))
        assertEquals("未声明策略时实现层照旧要问", 1, fs.asked)
        assertFalse(context.grants.isGranted("fs.delete"))
    }

    @Test
    fun `declared ask does not double prompt`() {
        val context = BridgeContext(
            cardName = "c",
            engine = "lua",
            quotaMb = 1,
            permissions = mapOf("fs.delete" to "ask"),
        )
        val fs = RecordingFs(context.grants)
        val dispatcher = RustBridgeDispatcher(mapOf("fs" to ScopedFsBridge(context, fs)))

        dispatcher.dispatch("fs", "delete", """["/a"]""")

        assertEquals("ask 只问一次（由实现层问）", 1, fs.asked)
        assertFalse("ask 不该被当成放行", context.grants.isGranted("fs.delete"))
    }

    @Test
    fun `timeout wins over policy`() {
        val context = BridgeContext(
            cardName = "c",
            engine = "lua",
            quotaMb = 1,
            permissions = mapOf("fs.delete" to "allow"),
        )
        context.deadline.setAt(System.currentTimeMillis() - 1)
        val fs = RecordingFs(context.grants)
        val dispatcher = RustBridgeDispatcher(mapOf("fs" to ScopedFsBridge(context, fs)))

        val result = dispatcher.dispatch("fs", "delete", """["/a"]""")

        assertTrue(result, result.contains("超时"))
        assertEquals(0, fs.asked)
    }

    @Test
    fun `policy keys are bridge dot method`() {
        val context = BridgeContext(
            cardName = "c",
            engine = "lua",
            quotaMb = 1,
            permissions = mapOf("fs.delete" to "deny", "ai.chat" to "allow"),
        )
        assertEquals("deny", context.policy("fs.delete"))
        assertEquals("allow", context.policy("ai.chat"))
        assertEquals("", context.policy("fs.read"))
    }

    @Test
    fun `bridges without context are unaffected`() {
        // 动态依赖包注入的 bridge 没有 BridgeContext，策略判定要跳过而不是崩
        val plain = object : FsBridge {
            override fun delete(path: String, recursive: Boolean) = true
            override fun read(path: String) = "ok"
            override fun write(path: String, content: String, append: Boolean) = Unit
            override fun exists(path: String) = true
            override fun stat(path: String): Map<String, Any?> = emptyMap()
            override fun list(path: String, recursive: Boolean, limit: Int) = emptyList<Map<String, Any?>>()
            override fun copy(src: String, dst: String) = Unit
            override fun move(src: String, dst: String) = Unit
            override fun mkdir(path: String) = Unit
            override fun zip(paths: List<String>, output: String) = output
            override fun unzip(zipPath: String, outputDir: String) = outputDir
            override fun readBase64(path: String) = ""
            override fun saveBase64(data: String, outputPath: String) = outputPath
            override fun openDir(path: String) = Unit
                    }
        val dispatcher = RustBridgeDispatcher(mapOf("fs" to plain))
        assertEquals("\"ok\"", dispatcher.dispatch("fs", "read", """["/a"]"""))
    }
}