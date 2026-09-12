package heizige.kk.khatkit.engine

import kotlinx.serialization.json.JsonArray

/**
 * native 引擎回调宿主 bridge 的分发器。
 *
 * Rust 侧只认识 `dispatch(bridge, method, argsJson)`，具体实现留在 Kotlin，
 * 保证 native 层与业务解耦。
 */
class RustBridgeDispatcher(private val bridges: Map<String, Any>) {

    fun dispatch(bridge: String, method: String, argsJson: String): String {
        val target = bridges[bridge]
            ?: return JsonValues.encode(mapOf("__error" to "unknown bridge: $bridge"))
        return try {
            val result = ReflectiveInvoker.invoke(target, method, parseArgs(argsJson))
            JsonValues.encode(result)
        } catch (e: Throwable) {
            JsonValues.encode(mapOf("__error" to (e.message ?: "dispatch error")))
        }
    }

    private fun parseArgs(argsJson: String): List<Any?> {
        if (argsJson.isBlank()) return emptyList()
        val element = runCatching { JsonValues.json.parseToJsonElement(argsJson) }.getOrNull()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { JsonValues.fromElement(it) }
    }
}
