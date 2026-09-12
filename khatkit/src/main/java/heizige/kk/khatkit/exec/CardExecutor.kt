package heizige.kk.khatkit.exec

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineFactory
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 执行 engine: command 形态的命令，由宿主实现（走 shizuku/root）。 */
fun interface CommandRunner {
    fun run(command: String): String
}

/**
 * 卡片执行器：manifest + 引擎 + bridge 注入。
 *
 * 厚宿主、薄脚本：脚本只负责下单，长任务/平台差异都在 bridge 后面。
 */
class CardExecutor(
    val bridges: BridgeRegistry,
    private val commandRunner: CommandRunner? = null,
    private val libResolver: LibResolver? = null,
) {
    suspend fun execute(card: LoadedCard, args: Map<String, Any?>): EngineResult =
        withContext(Dispatchers.Default) {
            // 卡片/脚本/宿主任何一环出问题都收敛成 Err，绝不让异常冒泡打断整轮生成
            runCatching {
                when (card.engineKind) {
                    EngineKind.COMMAND -> executeCommand(card.manifest, args)
                    EngineKind.LUA, EngineKind.JS -> executeScript(card, args)
                }
            }.getOrElse { error ->
                EngineResult.Err("CARD_EXECUTOR", error.message ?: error.toString())
            }
        }

    private suspend fun executeScript(card: LoadedCard, args: Map<String, Any?>): EngineResult {
        val required = card.manifest.requiredBridges
        val missing = required - bridges.availableBridges()
        if (missing.isNotEmpty()) {
            return EngineResult.Err("BRIDGE_UNAVAILABLE", "设备缺少卡片所需能力：$missing")
        }

        // 先解析脚本库（可能跨线程挂起）；引擎创建后到 eval 之间不能再有挂起点，
        // 否则 native 引擎句柄可能被换到别的线程使用。
        val modules = libResolver?.resolve(card.manifest.requires.libs, card.engineKind).orEmpty()

        val engine = EngineFactory.create(card.engineKind)
        return try {
            if (!bridges.inject(engine, card.manifest.name, required, card.manifest.store.quotaMb)) {
                EngineResult.Err("BRIDGE_INJECT_FAILED", "bridge 注入失败")
            } else {
                modules.forEach { lib -> engine.defineModule(lib.name, lib.source) }
                engine.eval(card.scriptText, args)
            }
        } finally {
            engine.close()
        }
    }

    private fun executeCommand(manifest: CardManifest, args: Map<String, Any?>): EngineResult {
        val template = manifest.command
            ?: return EngineResult.Err("COMMAND_MISSING", "command 字段为空")
        val runner = commandRunner
            ?: return EngineResult.Err("COMMAND_UNAVAILABLE", "宿主未提供命令执行能力")
        return runCatching {
            val command = renderCommand(template, args)
            runner.run(command)
        }.fold(
            onSuccess = { EngineResult.Ok(mapOf("output" to it)) },
            onFailure = { EngineResult.Err("COMMAND_FAILED", it.message ?: "命令执行失败") },
        )
    }

    companion object {
        // 注意：Android 的 ICU 正则要求字面量 `}` 也转义，JVM 上不转义能过、真机会崩
        private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)\}""")

        /**
         * 对 `{}` 占位符做白名单转义，脚本无法拼命令。
         * 值只允许字母数字与 . _ - / : @ ，其余一律拒绝。
         */
        fun renderCommand(template: String, args: Map<String, Any?>): String =
            PLACEHOLDER.replace(template) { match ->
                val key = match.groupValues[1]
                val value = args[key]?.toString()
                    ?: throw IllegalArgumentException("缺少命令参数：$key")
                require(SAFE_VALUE.matches(value)) { "命令参数含非法字符：$key=$value" }
                value
            }

        private val SAFE_VALUE = Regex("^[A-Za-z0-9._\\-/:@]+$")
    }
}
