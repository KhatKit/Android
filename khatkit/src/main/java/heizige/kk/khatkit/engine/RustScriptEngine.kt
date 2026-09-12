package heizige.kk.khatkit.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Rust 引擎（mlua + rquickjs）的 Kotlin 门面。
 *
 * 设计文档 6.1 的 LuaJ 是纯 JVM 备选；这里走 native 以获得接近原生的性能，
 * 两个引擎实现同一个 [ScriptEngine] 接口，卡片脚本无需感知。
 *
 * 注意：native 句柄只在创建它的线程上使用，每个卡片执行应新建实例。
 */
class RustScriptEngine private constructor(private val kind: Int) : ScriptEngine {

    private val bridges = LinkedHashMap<String, Any>()
    private val dispatcher = RustBridgeDispatcher(bridges)
    private val handle: Long = nativeCreate(kind, dispatcher)

    override fun define(name: String, value: Any) {
        bridges[name] = value
        if (handle != 0L) {
            nativeDefine(handle, name, ReflectiveInvoker.methodNames(value).joinToString(","))
        }
    }

    override fun defineModule(name: String, source: String) {
        if (handle != 0L) nativeDefineModule(handle, name, source)
    }

    override fun eval(script: String, args: Map<String, Any?>): EngineResult {
        if (handle == 0L) return EngineResult.Err("RUST_ENGINE", "native 引擎创建失败")
        val resultJson = nativeEval(handle, script, JsonValues.encode(args))
            ?: return EngineResult.Err("RUST_ENGINE", "native 返回空")
        return try {
            val element = JsonValues.json.parseToJsonElement(resultJson)
            val obj = element as? JsonObject
                ?: return EngineResult.Ok(mapOf("result" to JsonValues.fromElement(element)))
            val error = (obj["__error"] as? JsonPrimitive)?.contentOrNull
            if (error != null) {
                EngineResult.Err("RUST_RUNTIME", error)
            } else {
                EngineResult.Ok(obj.mapValues { JsonValues.fromElement(it.value) })
            }
        } catch (e: Throwable) {
            EngineResult.Err("RUST_RUNTIME", e.message ?: "解析 native 返回值失败")
        }
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
    }

    companion object {
        const val KIND_LUA = 0
        const val KIND_JS = 1

        /** native 库是否可用；不可用时 EngineFactory 回退到 LuaJ/QuickJS。 */
        val isAvailable: Boolean by lazy {
            runCatching { System.loadLibrary("khatkit_core") }.isSuccess
        }

        fun create(kind: Int): RustScriptEngine = RustScriptEngine(kind)
    }
}
