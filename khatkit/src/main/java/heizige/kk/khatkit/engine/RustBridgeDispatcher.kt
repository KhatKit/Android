package heizige.kk.khatkit.engine

import kotlinx.serialization.json.JsonArray

/**
 * native 引擎回调宿主 bridge 的分发器。
 *
 * Rust 侧只认识 `dispatch(bridge, method, argsJson)`，具体实现留在 Kotlin，
 * 保证 native 层与业务解耦。
 */
class RustBridgeDispatcher(private val bridges: Map<String, Any>) {
    private val run = heizige.kk.khatkit.exec.CardRunContext.current.get()

    /** Called by native instruction hooks, including scripts that never call a bridge. */
    fun interruptReason(): Int = when {
        run?.cancelled == true -> 1
        bridges.values.any {
            (it as? heizige.kk.khatkit.bridge.ContextAwareBridge)?.context?.deadline?.expired() == true
        } -> 2
        (run?.computationMillis ?: 0L) > 120_000L -> 2
        else -> 0
    }

    /** Validate the selected ABI capability against the requested method and destination. */
    fun wasmAllowed(cap: String, method: String, argsJson: String): Boolean {
        val bridge = method.substringBefore('.')
        return when {
            cap == "host" -> bridge in setOf("host", "time", "i18n")
            cap == "ai:chat" -> method in setOf("ai.chat", "ai.complete")
            cap == "fs:workspace" -> bridge == "fs"
            cap == "ui:render" -> bridge == "ui"
            cap == "haptics" -> method == "system.vibrate"
            cap == "clipboard" -> method in setOf("tool.getClipboard", "tool.setClipboard")
            cap == "media:pick" -> method == "mediaPicker.pickMedia"
            cap == "image:toolbox" -> bridge == "imageToolbox"
            cap.startsWith("dep:") -> bridge == cap.substringAfter(':')
            cap.startsWith("net:") -> {
                if (bridge != "net") return false
                val url = parseArgs(argsJson).firstOrNull() as? String ?: return false
                val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
                val host = uri.host?.lowercase() ?: return false
                val domain = cap.substringAfter(':').lowercase()
                uri.scheme in setOf("http", "https") &&
                    (domain == "*" || host == domain || host.endsWith(".$domain"))
            }
            else -> cap in setOf("tool", "accessibility", "store", "download", "web",
                "browser", "schedule", "system", "shizuku", "root") && bridge == cap
        }
    }

    fun dispatch(bridge: String, method: String, argsJson: String): String {
        when (interruptReason()) {
            1 -> return JsonValues.encode(mapOf("__error" to "CARD_CANCELLED"))
            2 -> return JsonValues.encode(mapOf("__error" to TIMEOUT_ERROR))
        }
        val target = bridges[bridge]
            ?: return JsonValues.encode(mapOf("__error" to "unknown bridge: $bridge"))
        val context = (target as? heizige.kk.khatkit.bridge.ContextAwareBridge)?.context
        // 准入顺序：① 超时 → ② 方法级策略 → （③ ask 由实现层问用户，避免问两次）→ ④ 反射调用
        if (context?.deadline?.expired() == true) {
            return JsonValues.encode(mapOf("__error" to TIMEOUT_ERROR))
        }
        val key = "$bridge.$method"
        when (context?.policy(key)) {
            "deny" -> {
                context.grants.deny(key)
                return JsonValues.encode(mapOf("__error" to DENY_ERROR_PREFIX + key))
            }
            // allow 只记账，真正放行由实现层的闸门读 context.grants 决定（见 RunGrants）
            "allow" -> context?.grants?.grant(key)
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

        /** 命中 `permissions.methods` 的 `deny` 时的文案前缀。 */
        const val DENY_ERROR_PREFIX = "卡片声明禁止调用该能力："
    }

    private fun parseArgs(argsJson: String): List<Any?> {
        if (argsJson.isBlank()) return emptyList()
        val element = runCatching { JsonValues.json.parseToJsonElement(argsJson) }.getOrNull()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { JsonValues.fromElement(it) }
    }
}
