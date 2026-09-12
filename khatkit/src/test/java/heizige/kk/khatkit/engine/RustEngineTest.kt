package heizige.kk.khatkit.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 端到端验证 Rust 核心（mlua + rquickjs）。
 *
 * 只在通过 KHATKIT_NATIVE_LIB 指定了宿主平台 .so 时运行；
 * Android 真机由 jniLibs 自动加载，不走这里。
 */
class RustEngineTest {

    class FakeTool {
        fun readText(path: String): String = "native:$path"
        fun add(a: Int, b: Int): Int = a + b
    }

    @Test
    fun luaEngineRuns() {
        assumeTrue(NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        engine.define("tool", FakeTool())
        val result = engine.eval(
            "return { text = tool.readText('a.txt'), sum = tool.add(2, 5) }",
            emptyMap(),
        ).getOrThrow()
        assertEquals("native:a.txt", result["text"])
        assertEquals(7L, result["sum"])
        engine.close()
    }

    @Test
    fun jsEngineRuns() {
        assumeTrue(NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_JS)
        engine.define("tool", FakeTool())
        val result = engine.eval(
            "return { text: tool.readText('b.txt'), sum: tool.add(3, 4) };",
            emptyMap(),
        ).getOrThrow()
        assertEquals("native:b.txt", result["text"])
        assertEquals(7L, result["sum"])
        engine.close()
    }

    @Test
    fun luaReadsArgs() {
        assumeTrue(NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        val result = engine.eval(
            "return { first = args.paths[1], quality = args.quality }",
            mapOf("quality" to 80, "paths" to listOf("a.pdf", "b.pdf")),
        ).getOrThrow()
        assertEquals("a.pdf", result["first"])
        assertEquals(80L, result["quality"])
        engine.close()
    }

    @Test
    fun luaRequireModule() {
        assumeTrue(NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        engine.defineModule("my.math", "local M = {}; function M.add(a, b) return a + b end; return M")
        val result = engine.eval(
            "local m = require('my.math'); return { sum = m.add(20, 22) }",
            emptyMap(),
        ).getOrThrow()
        assertEquals(42L, result["sum"])
        engine.close()
    }

    @Test
    fun jsRequireModule() {
        assumeTrue(NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_JS)
        engine.defineModule("my.tools", "module.exports = { double: function (x) { return x * 2; } };")
        val result = engine.eval(
            "var t = require('my.tools'); return { value: t.double(21) };",
            emptyMap(),
        ).getOrThrow()
        assertEquals(42L, result["value"])
        engine.close()
    }

    object NativeLib {
        val available: Boolean by lazy {
            val path = System.getProperty("khatkit.native.path")
                ?: System.getenv("KHATKIT_NATIVE_LIB")
                ?: return@lazy false
            val file = File(path)
            if (!file.exists()) return@lazy false
            runCatching { System.load(file.absolutePath) }.isSuccess
        }
    }
}
