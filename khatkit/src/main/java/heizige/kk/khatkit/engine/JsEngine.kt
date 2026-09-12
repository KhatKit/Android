package heizige.kk.khatkit.engine

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * QuickJS 实现。与 Lua 引擎共享同一套卡片 API：
 * 宿主对象经反射包成 JS 全局对象，脚本里 `tool.compressImage(...)` 一步到位。
 *
 * 注意：QuickJS 是原生库，仅 Android 运行时可用，JVM 单测不覆盖。
 */
class JsEngine : ScriptEngine {
    private val bindings = linkedMapOf<String, Any>()
    private val modules = linkedMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    override fun define(name: String, value: Any) {
        bindings[name] = value
    }

    override fun defineModule(name: String, source: String) {
        modules[name] = source
    }

    override fun eval(script: String, args: Map<String, Any?>): EngineResult {
        val context = QuickJSContext.create()
        return try {
            registerDispatcher(context)
            bindings.forEach { (name, target) -> registerBridge(context, name, target) }
            registerModules(context)
            context.evaluate("globalThis.args = ${toJson(args)};")

            val wrapped = """
                (function () {
                  var __r = (function () {
                $script
                  })();
                  return JSON.stringify(__r === undefined ? null : __r);
                })()
            """.trimIndent()

            val raw = context.evaluate(wrapped) as? String
            val value = raw?.let { parseResult(it) } ?: emptyMap()
            EngineResult.Ok(value)
        } catch (e: Throwable) {
            EngineResult.Err("JS_RUNTIME", e.message ?: e.toString())
        } finally {
            context.destroy()
        }
    }

    override fun close() {
        bindings.clear()
    }

    // ---------------------------------------------------------------------

    /** 注册 require + 模块表；模块用 CommonJS 形状：`module.exports = ...`。 */
    private fun registerModules(context: QuickJSContext) {
        if (modules.isEmpty()) return
        context.evaluate(
            """
            globalThis.__khatkitModules = globalThis.__khatkitModules || {};
            globalThis.__khatkitCache = globalThis.__khatkitCache || {};
            if (typeof globalThis.require !== 'function') {
              globalThis.require = function (name) {
                if (Object.prototype.hasOwnProperty.call(globalThis.__khatkitCache, name)) {
                  return globalThis.__khatkitCache[name];
                }
                var src = globalThis.__khatkitModules[name];
                if (src === undefined) throw new Error('module not found: ' + name);
                var module = { exports: {} };
                new Function('module', 'exports', 'require', src)(
                  module, module.exports, globalThis.require
                );
                globalThis.__khatkitCache[name] = module.exports;
                return module.exports;
              };
            }
            """.trimIndent()
        )
        modules.forEach { (name, source) ->
            context.evaluate("globalThis.__khatkitModules[${toJson(name)}] = ${toJson(source)};")
        }
    }

    private fun registerDispatcher(context: QuickJSContext) {
        context.globalObject.setProperty("__khatkitCall", JSCallFunction { callArgs ->
            val bridgeName = callArgs.getOrNull(0) as? String
                ?: error("bridge name required")
            val methodName = callArgs.getOrNull(1) as? String
                ?: error("method name required")
            val argsJson = callArgs.getOrNull(2) as? String
            val target = bindings[bridgeName] ?: error("unknown bridge: $bridgeName")
            val parsedArgs = parseArgs(argsJson)
            val result = invoke(target, methodName, parsedArgs)
            toJson(result)
        })
    }

    private fun registerBridge(context: QuickJSContext, name: String, target: Any) {
        val methods = target.javaClass.methods
            .filter { it.declaringClass != Any::class.java }
            .filter { !it.name.startsWith("get") || it.parameterCount > 0 }
            .distinctBy { it.name }
        val shim = buildString {
            append("globalThis.").append(name).append(" = {")
            methods.forEach { method ->
                append(method.name).append(": function(){ return JSON.parse(__khatkitCall(")
                append('"').append(name).append('"').append(',')
                append('"').append(method.name).append('"').append(',')
                append("JSON.stringify(Array.prototype.slice.call(arguments)))); },")
            }
            append("};")
        }
        context.evaluate(shim)
    }

    private fun invoke(target: Any, methodName: String, args: List<Any?>): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName }
            ?: error("unknown method: $methodName")
        val params = method.parameterTypes
        val converted = Array<Any?>(params.size) { i -> convert(args.getOrNull(i), params[i]) }
        return method.invoke(target, *converted)
    }

    private fun convert(value: Any?, type: Class<*>): Any? = when (type) {
        String::class.java -> value?.toString()
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> (value as? Number)?.toInt()
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> (value as? Number)?.toLong()
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> (value as? Number)?.toDouble()
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> (value as? Number)?.toFloat()
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType ->
            value as? Boolean ?: value?.toString()?.toBooleanStrictOrNull()
        else -> value
    }

    private fun parseArgs(argsJson: String?): List<Any?> {
        if (argsJson.isNullOrBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(argsJson) }.getOrNull() ?: return emptyList()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { fromJson(it) }
    }

    private fun parseResult(raw: String): Map<String, Any?> {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return when (element) {
            null, JsonNull -> emptyMap()
            is JsonObject -> element.mapValues { fromJson(it.value) }
            else -> mapOf("result" to fromJson(element))
        }
    }

    private fun fromJson(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            element.content.contains('.') -> element.content.toDoubleOrNull()
            else -> element.content.toLongOrNull() ?: element.content
        }
        is JsonArray -> element.map { fromJson(it) }
        is JsonObject -> element.mapValues { fromJson(it.value) }
    }

    private fun toJson(value: Any?): String = json.encodeToString(JsonElement.serializer(), toJsonElement(value))

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
}
