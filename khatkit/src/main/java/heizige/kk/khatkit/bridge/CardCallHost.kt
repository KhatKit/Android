package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardRunContext
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/** Host-owned ancestry and budget. Script arguments cannot forge either. */
class CardCallChain private constructor(
    internal val names: List<String>,
    internal val deadlineAt: Long,
    private val remaining: AtomicInteger,
) {
    internal fun descend(name: String): CardCallChain? =
        if (remaining.getAndUpdate { (it - 1).coerceAtLeast(0) } == 0) null
        else CardCallChain(names + name, deadlineAt, remaining)

    internal companion object {
        fun root(name: String) = CardCallChain(listOf(name), System.currentTimeMillis() + 30_000, AtomicInteger(12))
    }
}

/** The runner must submit the resolved child through the normal tracked execution path. */
class CardCallHost(
    private val findInstalled: suspend (String) -> LoadedCard?,
    private val run: suspend (LoadedCard, Map<String, Any?>, String, CardCallChain, Long) -> EngineResult,
) {
    internal fun bind(manifest: CardManifest, context: BridgeContext, chain: CardCallChain?): CardCallBridge {
        val lineage by lazy { chain ?: CardCallChain.root(manifest.name) }
        return ScopedCardCallBridge(context) { name, args ->
            if (name == null || !CardManifest.CardCalls.NAME_PATTERN.matches(name)) {
                return@ScopedCardCallBridge errorResult("CARD_CALL_NAME")
            }
            if (manifest.isCommand || manifest.isWasm || name !in manifest.cardCalls.targets ||
                context.policy("card.call") in setOf("deny", "ask")
            ) return@ScopedCardCallBridge errorResult("CARD_CALL_NOT_DECLARED")
            if (name in lineage.names) return@ScopedCardCallBridge errorResult("CARD_CALL_CYCLE")
            if (lineage.names.size >= 4) return@ScopedCardCallBridge errorResult("CARD_CALL_DEPTH")
            val deadlineAt = minOf(context.deadline.at().takeIf { it > 0 } ?: Long.MAX_VALUE, lineage.deadlineAt)
            val remainingMs = deadlineAt - System.currentTimeMillis()
            if (remainingMs <= 0) return@ScopedCardCallBridge errorResult("CARD_CALL_TIMEOUT")
            val parent = CardRunContext.current.get() ?: return@ScopedCardCallBridge errorResult("CARD_CALL_NO_RUN")
            val next = lineage.descend(name) ?: return@ScopedCardCallBridge errorResult("CARD_CALL_LIMIT")
            // The child runs on a separate engine thread and may use the parent's compute slot.
            parent.awaitInteraction {
                withTimeoutOrNull(remainingMs) {
                    val child = findInstalled(name) ?: return@withTimeoutOrNull errorResult("CARD_CALL_NOT_INSTALLED")
                    if (child.manifest.name != name || manifest.name !in child.manifest.cardCalls.allowedCallers) {
                        return@withTimeoutOrNull errorResult("CARD_CALL_NOT_EXPORTED")
                    }
                    if (!child.isSafeEntry()) return@withTimeoutOrNull errorResult("CARD_CALL_CAPABILITIES")
                    when (val result = run(child, args.orEmpty().filterKeys { !it.startsWith("__khatkit_") },
                        context.interactionId, next, deadlineAt)) {
                        is EngineResult.Ok -> mapOf("ok" to true, "value" to result.value)
                        is EngineResult.Err -> errorResult(result.code, result.message)
                    }
                } ?: errorResult("CARD_CALL_TIMEOUT")
            }
        }
    }

    private fun LoadedCard.isSafeEntry(): Boolean = with(manifest) {
        !widgetExecution && bridgeSubset == null && !isCommand && !isWasm && privilege == "none" &&
            engineKind in setOf(EngineKind.LUA, EngineKind.JS) &&
            requiredBridges.all { it in CardManifest.CardCalls.CHILD_BRIDGES } &&
            requires.run { libs.isEmpty() && dependencies.isEmpty() && bins.isEmpty() && env.isEmpty() } &&
            pricing?.price?.let { it <= 0.0 } != false
    }

    private fun errorResult(code: String, message: String = code): Map<String, Any?> =
        mapOf("ok" to false, "error" to mapOf("code" to code, "message" to message))
}

class ScopedCardCallBridge(
    override val context: BridgeContext,
    private val invoke: (String?, Map<String, Any?>?) -> Map<String, Any?>,
) : CardCallBridge, ContextAwareBridge {
    override fun call(name: String?, args: Map<String, Any?>?): Map<String, Any?> = invoke(name, args)
}
