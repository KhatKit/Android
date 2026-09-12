package heizige.kk.khatkit.exec

import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

enum class CardRunState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class CardRunInfo(
    val id: String,
    val cardName: String,
    val state: CardRunState,
    val startedAt: Long = System.currentTimeMillis(),
    val result: EngineResult? = null,
    val error: String? = null,
)

/**
 * 卡片运行状态机（设计文档 15.2 第 10 问）。
 *
 * 并发上限、运行状态、取消都由宿主统一管：
 * - 同时运行的卡片数不超过 [maxConcurrent]
 * - 每次运行有独立 id 与状态，UI/日志可观测
 * - 取消走协程取消，脚本侧无需关心生命周期
 *
 * 幂等性由卡片自身负责（同一 args 重复执行应当安全），宿主只保证状态可追踪。
 */
class CardRunManager(
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2,
    private val historyLimit: Int = 50,
    private val execute: suspend (LoadedCard, Map<String, Any?>) -> EngineResult,
) {
    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

    private val _runs = MutableStateFlow<Map<String, CardRunInfo>>(emptyMap())
    val runs: StateFlow<Map<String, CardRunInfo>> = _runs.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val completions = ConcurrentHashMap<String, CompletableDeferred<EngineResult>>()

    /** 提交并等待结果（AI tool / MCP 调用走这里）。 */
    suspend fun run(card: LoadedCard, args: Map<String, Any?>): EngineResult {
        val id = Uuid.random().toString().take(8)
        val completion = CompletableDeferred<EngineResult>()
        completions[id] = completion
        publish(CardRunInfo(id = id, cardName = card.manifest.name, state = CardRunState.QUEUED))

        jobs[id] = scope.launch(Dispatchers.Default) {
            try {
                semaphore.withPermit {
                    update(id) { it.copy(state = CardRunState.RUNNING) }
                    val result = try {
                        execute(card, args)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        EngineResult.Err("CARD_RUN_CRASH", e.message ?: "unknown")
                    }
                    update(id) {
                        it.copy(
                            state = if (result is EngineResult.Ok) CardRunState.DONE else CardRunState.FAILED,
                            result = result,
                            error = (result as? EngineResult.Err)?.message,
                        )
                    }
                    completion.complete(result)
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(state = CardRunState.CANCELLED, error = "已取消") }
                completion.complete(EngineResult.Err("CARD_CANCELLED", "已取消"))
                throw e
            } finally {
                jobs.remove(id)
                completions.remove(id)
            }
        }
        return completion.await()
    }

    fun cancel(id: String): Boolean {
        val job = jobs[id] ?: return false
        update(id) { it.copy(state = CardRunState.CANCELLED, error = "已取消") }
        completions[id]?.complete(EngineResult.Err("CARD_CANCELLED", "已取消"))
        job.cancel()
        return true
    }

    fun activeCount(): Int = jobs.size

    fun snapshot(): List<CardRunInfo> = _runs.value.values.sortedByDescending { it.startedAt }

    private fun publish(info: CardRunInfo) {
        _runs.update { current ->
            val next = current + (info.id to info)
            if (next.size <= historyLimit) {
                next
            } else {
                next.values.sortedByDescending { it.startedAt }.take(historyLimit).associateBy { it.id }
            }
        }
    }

    private fun update(id: String, transform: (CardRunInfo) -> CardRunInfo) {
        _runs.update { current -> current[id]?.let { current + (id to transform(it)) } ?: current }
    }
}
