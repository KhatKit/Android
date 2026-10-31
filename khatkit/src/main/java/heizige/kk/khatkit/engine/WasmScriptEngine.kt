package heizige.kk.khatkit.engine

import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * WASM 卡片引擎（docscard-runtime-wasm / ABI v1）。
 *
 * 卡片是编译好的 `card.wasm`：不再有"脚本文本"，逻辑固化在二进制里。
 * 事件按 ABI v1 约定打包成 `{"type":"run","payload":args}` 交给 `kk_handle`；
 * 卡片里的 host_call 全部转成 [RustBridgeDispatcher.dispatch]，因此 ui / 无障碍 /
 * 网络 / 图像等能力复用既有 Kotlin bridge 实现，UI 也走既有的 Compose 节点树渲染链。
 *
 * 卡片目录约定：`card.json`（元数据，供 Kotlin 侧认知）+ `manifest.json`（ABI 校验用）
 * + `card.wasm`。manifest.json 必须过运行时的 manifest 校验（runtime=wasm、abi_version=1）。
 */
class WasmScriptEngine private constructor(
    private val wasmBytes: ByteArray,
    private val manifestJson: String,
) : ScriptEngine {

    private val bridges = LinkedHashMap<String, Any>()
    private val dispatcher = RustBridgeDispatcher(bridges)
    private val handle: Long = nativeCardLoad(wasmBytes, manifestJson, dispatcher)
    private var initialized = false
    private var closed = false

    override fun define(name: String, value: Any) {
        bridges[name] = value
    }

    /** WASM 卡片没有 require 模块（逻辑编译进二进制），这里保持接口兼容。 */
    override fun defineModule(name: String, source: String) = Unit

    override fun eval(script: String, args: Map<String, Any?>): EngineResult {
        if (closed) return EngineResult.Err("WASM_ENGINE", "wasm 卡片引擎已关闭")
        if (handle == 0L) {
            return EngineResult.Err("WASM_ENGINE", "wasm 卡片加载失败（nativeCardLoad 返回 0）")
        }
        // kk_init may call host.locale/time; bridges must be injected first.
        val initCode = if (initialized) 0 else nativeCardInit(handle, manifestJson)
        initialized = initCode == 0
        if (!initialized) {
            return EngineResult.Err("WASM_ENGINE", "kk_init 返回错误码 $initCode")
        }
        val event = JsonValues.encode(mapOf("type" to "run", "payload" to args))
        val resultJson = nativeCardHandle(handle, event)
            ?: return EngineResult.Err("WASM_ENGINE", "native 返回空")
        return parseResult(resultJson)
    }

    /** 聊天页小组件：同一份 wasm，事件 type 换成 widget。 */
    fun evalWidget(event: Map<String, Any?>): EngineResult {
        if (closed) return EngineResult.Err("WASM_ENGINE", "wasm 卡片引擎已关闭")
        if (handle == 0L) {
            return EngineResult.Err("WASM_ENGINE", "wasm 卡片加载失败")
        }
        if (!initialized) {
            val code = nativeCardInit(handle, manifestJson)
            if (code != 0) return EngineResult.Err("WASM_ENGINE", "kk_init 返回错误码 $code")
            initialized = true
        }
        val payload = JsonValues.encode(event)
        val widgetEvent = """{"type":"widget","payload":$payload}"""
        val resultJson = nativeCardHandle(handle, widgetEvent)
            ?: return EngineResult.Err("WASM_ENGINE", "native 返回空")
        return parseResult(resultJson)
    }

    private fun parseResult(resultJson: String): EngineResult = try {
        val element = JsonValues.json.parseToJsonElement(resultJson)
        when (val obj = element as? JsonObject) {
            null -> EngineResult.Ok(mapOf("result" to JsonValues.fromElement(element)))
            else -> {
                val error = (obj["__error"] as? JsonPrimitive)?.contentOrNull
                if (error != null) {
                    EngineResult.Err("WASM_RUNTIME", error)
                } else {
                    EngineResult.Ok(obj.mapValues { JsonValues.fromElement(it.value) })
                }
            }
        }
    } catch (e: Throwable) {
        EngineResult.Err("WASM_RUNTIME", e.message ?: "解析 native 返回值失败")
    }

    override fun close() {
        if (handle != 0L && !closed) {
            closed = true
            try {
                if (initialized) nativeCardShutdown(handle)
            } finally {
                nativeCardClose(handle)
            }
        }
    }

    companion object {
        const val KIND_WASM = 2

        /** 从卡片目录加载（`card.wasm` + `manifest.json`）。 */
        fun create(cardDir: File): WasmScriptEngine {
            val wasm = File(cardDir, "card.wasm")
            require(wasm.isFile) { "WASM_CARD_MISSING_WASM: $cardDir" }
            val manifest = File(cardDir, "manifest.json")
            require(manifest.isFile) { "WASM_CARD_MISSING_MANIFEST: $cardDir" }
            return create(wasm.readBytes(), manifest.readText())
        }

        fun create(wasm: ByteArray, manifestJson: String): WasmScriptEngine =
            WasmScriptEngine(wasm, manifestJson)

        /** WASM 引擎与 Lua/JS 共用同一个 khatkit_core.so，可用性由它决定。 */
        val isAvailable: Boolean
            get() = RustScriptEngine.isAvailable
    }
}
