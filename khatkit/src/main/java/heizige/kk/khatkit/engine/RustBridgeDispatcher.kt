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
        // 运行超时在这里统一收口：到点后所有 bridge 调用都直接失败，不再进入实现
        val context = (target as? heizige.kk.khatkit.bridge.ContextAwareBridge)?.context
        if (context?.deadline?.expired() == true) {
            return JsonValues.encode(mapOf("__error" to TIMEOUT_ERROR))
        }
        return try {
            val result = ReflectiveInvoker.invoke(target, method, parseArgs(argsJson))
            JsonValues.encode(result)
        } catch (e: Throwable) {
            JsonValues.encode(mapOf("__error" to (e.message ?: "dispatch error")))
        }
    }

    private companion object {
        /** 超时后的统一文案（产品的一部分，脚本按它判断是否重试）。 */
        const val TIMEOUT_ERROR = "卡片运行超时，已终止"
    }

    private fun parseArgs(argsJson: String): List<Any?> {
        if (argsJson.isBlank()) return emptyList()
        val element = runCatching { JsonValues.json.parseToJsonElement(argsJson) }.getOrNull()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { JsonValues.fromElement(it) }
    }
}
