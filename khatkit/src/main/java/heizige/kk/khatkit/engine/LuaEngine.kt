package heizige.kk.khatkit.engine

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceLuaToJava
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * LuaJ 实现（安卓 v1 主力：纯 JVM，零 NDK）。
 *
 * 每个卡片执行应使用独立实例，避免全局状态串味。
 *
 * bridge 以「绑定方法表」注入，脚本用与 JS 一致的 `tool.readText(path)` 点号语法调用，
 * 不需要 LuaJ 特有的冒号语法，保证卡片脚本可平滑切到 JS。
 */
class LuaEngine : ScriptEngine {
    private val globals: Globals = JsePlatform.standardGlobals()

    override fun define(name: String, value: Any) {
        globals.set(name, wrapBridge(value))
    }

    override fun defineModule(name: String, source: String) {
        val preload = globals.get("package").get("preload")
        preload.set(
            name,
            object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    val chunk = globals.load(source, "@$name")
                    return chunk.call()
                }
            },
        )
    }

    override fun eval(script: String, args: Map<String, Any?>): EngineResult {
        return try {
            globals.set("args", LuaValues.toLua(args))
            val chunk = globals.load(script, "card")
            val returned = chunk.call()
            EngineResult.Ok(LuaValues.asResultMap(returned))
        } catch (e: LuaError) {
            EngineResult.Err("LUA_RUNTIME", e.message ?: "lua error")
        } catch (e: Throwable) {
            EngineResult.Err("LUA_RUNTIME", e.message ?: e.toString())
        }
    }

    override fun close() {
        // LuaJ 无显式释放
    }

    // ---------------------------------------------------------------------

    private fun wrapBridge(value: Any): LuaValue = when (value) {
        is LuaValue -> value
        is String, is Boolean, is Int, is Long, is Double, is Float,
        is Map<*, *>, is Iterable<*>, is Array<*>,
        -> LuaValues.toLua(value)
        else -> bridgeTable(value)
    }

    private fun bridgeTable(target: Any): LuaTable {
        val table = LuaTable()
        target.javaClass.methods
            .asSequence()
            .filter { it.declaringClass != Any::class.java }
            .filter { !it.name.contains('$') }
            .distinctBy { it.name }
            .forEach { method ->
                table.set(method.name, object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val params = method.parameterTypes
                        val converted = Array<Any?>(params.size) { index ->
                            CoerceLuaToJava.coerce(args.arg(index + 1), params[index])
                        }
                        val result = method.invoke(target, *converted)
                        return LuaValues.toLua(result)
                    }
                })
            }
        return table
    }
}
