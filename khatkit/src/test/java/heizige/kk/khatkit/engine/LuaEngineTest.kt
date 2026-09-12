package heizige.kk.khatkit.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaEngineTest {

    class FakeTool {
        fun readText(path: String): String = "hello $path"
        fun add(a: Int, b: Int): Int = a + b
    }

    @Test
    fun returnsResultTable() {
        val engine = LuaEngine()
        val result = engine.eval("return { count = 3, name = 'ok' }", emptyMap())
        assertTrue(result is EngineResult.Ok)
        val value = result.getOrThrow()
        assertEquals(3, value["count"])
        assertEquals("ok", value["name"])
    }

    @Test
    fun readsArgs() {
        val engine = LuaEngine()
        val result = engine.eval(
            "return { quality = args.quality, first = args.paths[1] }",
            mapOf("quality" to 80, "paths" to listOf("a.pdf", "b.pdf")),
        ).getOrThrow()
        assertEquals(80, result["quality"])
        assertEquals("a.pdf", result["first"])
    }

    @Test
    fun callsInjectedBridge() {
        val engine = LuaEngine()
        engine.define("tool", FakeTool())
        val result = engine.eval(
            "return { text = tool.readText('a.txt'), sum = tool.add(2, 5) }",
            emptyMap(),
        ).getOrThrow()
        assertEquals("hello a.txt", result["text"])
        assertEquals(7, result["sum"])
    }

    @Test
    fun requiresRegisteredModule() {
        val engine = LuaEngine()
        engine.defineModule("my.math", "local M = {}; function M.add(a, b) return a + b end; return M")
        val result = engine
            .eval("local m = require('my.math'); return { sum = m.add(2, 3) }", emptyMap())
            .getOrThrow()
        assertEquals(5, result["sum"])
    }

    @Test
    fun runtimeErrorIsCaught() {
        val engine = LuaEngine()
        val result = engine.eval("error('boom')", emptyMap())
        assertTrue(result is EngineResult.Err)
    }

    @Test
    fun autoResolvePrefersJsWhenBothPresent() {
        assertEquals(EngineKind.JS, EngineFactory.resolve("auto", hasLua = true, hasJs = true))
        assertEquals(EngineKind.LUA, EngineFactory.resolve("auto", hasLua = true, hasJs = false))
    }
}
