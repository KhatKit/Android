package heizige.kk.khatkit.exec

import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 卡片并发闸门：同时运行的卡片数不超过 [maxConcurrent]。
 *
 * 幂等性由卡片自身负责（同一 args 重复执行应当安全），宿主只保证并发有上限。
 */
class CardRunManager(
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2,
    private val execute: suspend (LoadedCard, Map<String, Any?>) -> EngineResult,
) {
    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

    /** 提交并等待结果（AI tool / MCP 调用走这里）。 */
    suspend fun run(card: LoadedCard, args: Map<String, Any?>): EngineResult {
        val completion = CompletableDeferred<EngineResult>()
        scope.launch(Dispatchers.Default) {
            try {
                semaphore.withPermit {
                    val result = try {
                        execute(card, args)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        EngineResult.Err("CARD_RUN_CRASH", e.message ?: "unknown")
                    }
                    completion.complete(result)
                }
            } catch (e: CancellationException) {
                completion.complete(EngineResult.Err("CARD_CANCELLED", "已取消"))
                throw e
            }
        }
        return completion.await()
    }
}
