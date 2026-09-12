package heizige.kk.khatkit.engine

/**
 * 脚本引擎抽象（设计文档第 6 节）。
 *
 * 设计约束：
 * 1. 卡片脚本不得依赖任何引擎独有特性，保证 Lua <-> JS 可平滑切换
 * 2. 宿主对象由外部注入，引擎只负责绑定
 * 3. 执行结果统一收敛为 [EngineResult]，异常不外泄到脚本
 */
interface ScriptEngine {
    /** 注入宿主能力，key 即脚本里的全局名 */
    fun define(name: String, value: Any)

    /**
     * 注册脚本库模块（设计文档 8.1/8.2）。
     * 注册后脚本里 `require(name)` 即拿到模块导出值。
     */
    fun defineModule(name: String, source: String)

    /** 执行脚本文本，args 为 AI/UI 填好的参数 */
    fun eval(script: String, args: Map<String, Any?>): EngineResult

    fun close()
}

sealed interface EngineResult {
    data class Ok(val value: Map<String, Any?>) : EngineResult
    data class Err(val code: String, val message: String) : EngineResult

    fun valueOrNull(): Map<String, Any?>? = (this as? Ok)?.value

    fun getOrThrow(): Map<String, Any?> = when (this) {
        is Ok -> value
        is Err -> throw CardExecutionException(code, message)
    }
}

class CardExecutionException(val code: String, message: String) : RuntimeException(message)

enum class EngineKind { LUA, JS, COMMAND }

object EngineFactory {
    /**
     * 优先用 Rust 核心（mlua / rquickjs）以获得接近原生的性能；
     * native 库缺失或加载失败时自动回退到纯 JVM 的 LuaJ / QuickJS。
     */
    @Volatile
    var preferNative: Boolean = true

    fun create(kind: EngineKind): ScriptEngine = when (kind) {
        EngineKind.LUA -> if (preferNative && RustScriptEngine.isAvailable) {
            RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        } else {
            LuaEngine()
        }
        EngineKind.JS -> if (preferNative && RustScriptEngine.isAvailable) {
            RustScriptEngine.create(RustScriptEngine.KIND_JS)
        } else {
            JsEngine()
        }
        EngineKind.COMMAND -> throw IllegalArgumentException("command 卡片不经脚本引擎")
    }

    /** 按 card.json 的 engine 字段解析；auto 时按 entry 文件存在性选择（JS 优先）。 */
    fun resolve(engine: String, hasLua: Boolean, hasJs: Boolean): EngineKind = when (engine) {
        "lua" -> EngineKind.LUA
        "js" -> EngineKind.JS
        "command" -> EngineKind.COMMAND
        else -> if (hasJs) EngineKind.JS else EngineKind.LUA
    }
}
