package heizige.kk.khatkit.exec

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineFactory
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.dependency.DependencyEnsureResult
import heizige.kk.khatkit.dependency.DependencyFallback
import heizige.kk.khatkit.dependency.DependencyManager
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
    private val dependencyManager: DependencyManager? = null,
    private val onDependencyStatus: ((String) -> Unit)? = null,
) {
    suspend fun execute(card: LoadedCard, args: Map<String, Any?>): EngineResult =
        withContext(Dispatchers.Default) {
            // 卡片/脚本/宿主任何一环出问题都收敛成 Err，绝不让异常冒泡打断整轮生成
            runCatching {
                ensureDependencies(card.manifest)?.let { return@runCatching it }
                when (card.engineKind) {
                    EngineKind.COMMAND -> executeCommand(card.manifest, args)
                    EngineKind.LUA, EngineKind.JS -> executeScript(card, args)
                }
            }.getOrElse { error ->
                EngineResult.Err("CARD_EXECUTOR", error.message ?: error.toString())
            }
        }

    /**
     * 执行前确保卡片声明的原生依赖包已下载、校验并加载为动态 bridge。
     * 返回 null 表示全部就绪；失败返回带中文说明的 Err（离线/校验失败等）。
     */
    private suspend fun ensureDependencies(manifest: CardManifest): EngineResult? {
        val requirements = manifest.requires.dependencies
        if (requirements.isEmpty()) return null
        val manager = dependencyManager
            ?: return EngineResult.Err(
                "DEPENDENCY_UNAVAILABLE",
                "卡片 ${manifest.name} 依赖原生依赖包（${requirements.joinToString { it.name }}），" +
                    "但当前宿主不支持依赖包运行时；请升级 KhatKit 后重试。",
            )
        // 同名依赖可声明多个版本（回滚清单）：某个版本被 Hub 撤销/下架时，按清单顺序尝试备用版本，
        // 每个候选版本的 sha256 + ECDSA 签名都由 DependencyManager 强制校验，安全边界不变。
        val groups = DependencyFallback.groups(requirements)
        groups.forEach { (name, candidates) ->
            var lastError: DependencyEnsureResult.Err? = null
            for ((index, req) in candidates.withIndex()) {
                onDependencyStatus?.invoke(
                    if (index == 0) {
                        "正在准备依赖包：${req.name} ${req.version}"
                    } else {
                        "原依赖版本不可用，正在尝试备用版本：${req.name} ${req.version}"
                    },
                )
                when (val result = manager.ensure(req)) {
                    is DependencyEnsureResult.Ok -> {
                        bridges.registerDynamic(name, result.bridge)
                        lastError = null
                        break
                    }

                    is DependencyEnsureResult.Err -> {
                        lastError = result
                        onDependencyStatus?.invoke("依赖包 ${req.name} ${req.version} 不可用：${result.code}")
                        if (!result.retryable) return EngineResult.Err(result.code, result.message)
                    }
                }
            }
            if (lastError != null) {
                val tried = if (candidates.size > 1) {
                    "；已尝试清单中的 ${candidates.size} 个版本 ${candidates.joinToString { it.version }}"
                } else {
                    "；请更新卡片或从卡片市场安装最新版本"
                }
                onDependencyStatus?.invoke("依赖包 ${name} 全部候选版本均不可用")
                return EngineResult.Err(lastError.code, lastError.message + tried)
            }
        }
        return null
    }

    private suspend fun executeScript(card: LoadedCard, args: Map<String, Any?>): EngineResult {
        val required = card.manifest.requiredBridges
        val missing = required - bridges.availableBridges()
        if (missing.isNotEmpty()) {
            return EngineResult.Err(
                "BRIDGE_UNAVAILABLE",
                "设备缺少卡片所需能力：$missing。请在聊天输入框「+」面板中开启对应权限（root / Shizuku / 无障碍 / 所有文件访问）后重试。",
            )
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
