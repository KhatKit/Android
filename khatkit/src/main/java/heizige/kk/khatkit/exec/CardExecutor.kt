package heizige.kk.khatkit.exec

import heizige.kk.khatkit.localization.RuntimeText
import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.CardCallChain
import heizige.kk.khatkit.bridge.UiBridge
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineFactory
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiMode
import heizige.kk.khatkit.dependency.DependencyEnsureResult
import heizige.kk.khatkit.dependency.DependencyFallback
import heizige.kk.khatkit.dependency.DependencyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** 执行 engine: command 形态的命令，由宿主实现（走 shizuku/root）。 */
fun interface CommandRunner {
    fun run(command: String): String
}

/**
 * 一次卡片运行的可调项。
 *
 * [uiMode] 是**运行级**的 UI 开关（见 [UiMode]）：`INTERACTIVE` 才可能弹层，
 * `HEADLESS` 下 `ui.*` 全部立即返回。默认值由宿主按触发来源决定，脚本与 AI
 * 也可以在一次调用里覆盖（AI 侧参数 `__khatkit_ui`）。
 */
data class CardExecutionOptions(
    val ui: UiBridge? = null,
    val uiMode: UiMode = UiMode.INTERACTIVE,
    val locales: List<String>? = null,
    val deadlineAt: Long = 0L,
    val interactionId: String? = null,
    /**
     * 卡片内部敏感 bridge 的审批策略。
     *
     * 普通页面/后台触发由宿主显式设为 [CardApprovalMode.BYPASS]，这样它们不会
     * 弹出自动化审批；AI 工具调用保留默认的 [CardApprovalMode.DEFAULT]。
     */
    val approvalMode: CardApprovalMode = CardApprovalMode.DEFAULT,
    val cardCallChain: CardCallChain? = null,
)

enum class CardApprovalMode {
    /** 使用宿主提供的审批闸门。 */
    DEFAULT,

    /** 本次卡片运行不显示审批，直接放行 bridge 内部的审批点。 */
    BYPASS,
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
    private val approvalGate: ApprovalGate? = null,
    private val messageResolver: (RuntimeText) -> String = RuntimeText::diagnosticMessage,
) {
    suspend fun execute(
        card: LoadedCard,
        args: Map<String, Any?>,
        options: CardExecutionOptions = CardExecutionOptions(),
    ): EngineResult {
        val run = CardRunContext.current.get() ?: CardRunContext(currentCoroutineContext().job)
        return withContext(Dispatchers.IO + CardRunContext.current.asContextElement(run)) {
            // 卡片/脚本/宿主任何一环出问题都收敛成 Err，绝不让异常冒泡打断整轮生成
            runCatching {
                ensureDependencies(card.manifest)?.let { return@runCatching it }
                when (card.engineKind) {
                    EngineKind.COMMAND -> executeCommand(card.manifest, args)
                    EngineKind.LUA, EngineKind.JS, EngineKind.WASM -> executeScript(card, args, options)
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                EngineResult.Err("CARD_EXECUTOR", error.message ?: error.toString())
            }
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
                messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_HOST_UNSUPPORTED, listOf(manifest.name, requirements.joinToString { it.name }))),
            )
        // 同名依赖可声明多个版本（回滚清单）：某个版本被 Hub 撤销/下架时，按清单顺序尝试备用版本，
        // 每个候选版本的 sha256 + ECDSA 签名都由 DependencyManager 强制校验，安全边界不变。
        val groups = DependencyFallback.groups(requirements)
        groups.forEach { (name, candidates) ->
            var lastError: DependencyEnsureResult.Err? = null
            for ((index, req) in candidates.withIndex()) {
                onDependencyStatus?.invoke(
                    if (index == 0) {
                        messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_PREPARING, listOf(req.name, req.version)))
                    } else {
                        messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_FALLBACK, listOf(req.name, req.version)))
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
                        onDependencyStatus?.invoke(messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_UNAVAILABLE, listOf(req.name, req.version, result.code))))
                        if (!result.retryable) return EngineResult.Err(result.code, result.message)
                    }
                }
            }
            if (lastError != null) {
                val tried = if (candidates.size > 1) {
                    messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_TRIED_VERSIONS, listOf(candidates.size, candidates.joinToString { it.version })))
                } else {
                    messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_UPDATE_CARD))
                }
                onDependencyStatus?.invoke(messageResolver(RuntimeText(RuntimeText.Code.DEPENDENCY_ALL_UNAVAILABLE, listOf(name))))
                return EngineResult.Err(lastError.code, listOf(lastError.message, tried).joinToString("\n"))
            }
        }
        return null
    }

    private suspend fun executeScript(card: LoadedCard, args: Map<String, Any?>, options: CardExecutionOptions): EngineResult {
        val required = card.bridgeSubset ?: card.manifest.requiredBridges
        val available = bridges.availableBridges(options.uiMode, options.ui)
        val missing = required - available
        if (missing.isNotEmpty()) {
            return EngineResult.Err(
                "BRIDGE_UNAVAILABLE",
                messageResolver(RuntimeText(RuntimeText.Code.RUNTIME_MISSING_CAPABILITIES, listOf(missing))),
            )
        }

        // 先解析脚本库（可能跨线程挂起）；引擎创建后到 eval 之间不能再有挂起点，
        // 否则 native 引擎句柄可能被换到别的线程使用。
        val modules = libResolver?.resolve(card.manifest.requires.libs, card.engineKind).orEmpty()

        // WASM 卡片从 card.dir 读 card.wasm + manifest.json；Lua/JS 不需要目录。
        val engine = if (card.engineKind == EngineKind.WASM && card.wasmBytes != null) {
            check(heizige.kk.khatkit.engine.RustScriptEngine.isAvailable) { "RUST_ENGINE_UNAVAILABLE" }
            heizige.kk.khatkit.engine.WasmScriptEngine.create(card.wasmBytes, requireNotNull(card.wasmManifestJson))
        } else EngineFactory.create(card.engineKind, card.dir)
        val runResources = mutableListOf<AutoCloseable>()
        return try {
            val runApprovalGate = when (options.approvalMode) {
                CardApprovalMode.DEFAULT -> approvalGate
                CardApprovalMode.BYPASS -> ALLOW_ALL_APPROVAL_GATE
            }
            if (!bridges.inject(
                    engine, card.manifest, approvalGate = runApprovalGate, runResources = runResources,
                    uiOverride = options.ui, uiMode = options.uiMode,
                    locales = options.locales, deadlineAt = options.deadlineAt,
                    requiredOverride = card.bridgeSubset, interactionId = options.interactionId,
                    cardCallChain = options.cardCallChain,
                )
            ) {
                EngineResult.Err("BRIDGE_INJECT_FAILED", messageResolver(RuntimeText(RuntimeText.Code.RUNTIME_BRIDGE_INJECTION)))
            } else {
                modules.forEach { lib -> engine.defineModule(lib.name, lib.source) }
                if (card.widgetExecution && engine is heizige.kk.khatkit.engine.WasmScriptEngine) {
                    engine.evalWidget(args)
                } else engine.eval(card.scriptText, args)
            }
        } finally {
            try {
                runResources.asReversed().forEach { resource -> runCatching { resource.close() } }
            } finally {
                engine.close()
            }
        }
    }

    private fun executeCommand(manifest: CardManifest, args: Map<String, Any?>): EngineResult {
        val template = manifest.command
            ?: return EngineResult.Err("COMMAND_MISSING", messageResolver(RuntimeText(RuntimeText.Code.RUNTIME_COMMAND_MISSING)))
        val runner = commandRunner
            ?: return EngineResult.Err("COMMAND_UNAVAILABLE", messageResolver(RuntimeText(RuntimeText.Code.RUNTIME_COMMAND_UNAVAILABLE)))
        return runCatching {
            val command = renderCommand(template, args)
            runner.run(command)
        }.fold(
            onSuccess = { EngineResult.Ok(mapOf("output" to it)) },
            onFailure = { EngineResult.Err("COMMAND_FAILED", it.message ?: messageResolver(RuntimeText(RuntimeText.Code.RUNTIME_COMMAND_FAILED))) },
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

        private val ALLOW_ALL_APPROVAL_GATE = ApprovalGate { _, _, _ -> true }
    }
}
