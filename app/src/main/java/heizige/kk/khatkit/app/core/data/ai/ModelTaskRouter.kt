package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class ModelTaskType {
    CHAT,
    MEMORY,
    SUMMARY,
    TITLE,
    OCR,
    TRANSLATION,
    UI_CONTROL,
}

enum class ModelRoutePolicy {
    PRIORITY,
    ROUND_ROBIN,
    COST_FIRST,
    QUALITY_FIRST,
    LATENCY_FIRST,
}

data class ModelRouteCandidate(
    val providerId: String,
    val modelId: String,
    val priority: Int = 0,
    val costRank: Int = 0,
    val qualityRank: Int = 0,
    val latencyRank: Int = 0,
)

data class ModelTaskBinding(
    val candidates: List<ModelRouteCandidate>,
    val policy: ModelRoutePolicy = ModelRoutePolicy.PRIORITY,
)

class ModelTaskRouter(
    private val cooldown: Duration = 30.seconds,
) {
    private val failures = mutableMapOf<String, Long>()
    private val cursors = mutableMapOf<ModelTaskType, Int>()

    @Synchronized
    fun choose(task: ModelTaskType, binding: ModelTaskBinding, nowMillis: Long): ModelRouteCandidate? {
        val available = binding.candidates.filterNot { isCoolingDown(it, nowMillis) }
        if (available.isEmpty()) return null
        return when (binding.policy) {
            ModelRoutePolicy.PRIORITY -> available.maxByOrNull { it.priority }
            ModelRoutePolicy.COST_FIRST -> available.minByOrNull { it.costRank }
            ModelRoutePolicy.QUALITY_FIRST -> available.maxByOrNull { it.qualityRank }
            ModelRoutePolicy.LATENCY_FIRST -> available.minByOrNull { it.latencyRank }
            ModelRoutePolicy.ROUND_ROBIN -> {
                val cursor = cursors.getOrDefault(task, 0)
                val selected = available[cursor % available.size]
                cursors[task] = (cursor + 1) % available.size
                selected
            }
        }
    }

    @Synchronized
    fun reportFailure(candidate: ModelRouteCandidate, nowMillis: Long) {
        failures[key(candidate)] = nowMillis
    }

    @Synchronized
    fun reportSuccess(candidate: ModelRouteCandidate) {
        failures.remove(key(candidate))
    }

    @Synchronized
    fun isCooling(candidate: ModelRouteCandidate, nowMillis: Long): Boolean = isCoolingDown(candidate, nowMillis)

    private fun isCoolingDown(candidate: ModelRouteCandidate, nowMillis: Long): Boolean =
        failures[key(candidate)]?.let { nowMillis - it < cooldown.inWholeMilliseconds } == true

    private fun key(candidate: ModelRouteCandidate): String = "${candidate.providerId}:${candidate.modelId}"
}

/**
 * Builds task bindings from the model slots already exposed by Settings.
 * This keeps routing backward compatible while the UI for explicit per-task
 * pools is developed.
 */
fun Settings.taskBinding(task: ModelTaskType, preferredId: kotlin.uuid.Uuid? = null): ModelTaskBinding {
    val selectedId = preferredId ?: when (task) {
        ModelTaskType.CHAT,
        ModelTaskType.UI_CONTROL -> chatModelId
        ModelTaskType.MEMORY,
        ModelTaskType.TITLE -> fastModelId
        ModelTaskType.SUMMARY -> compressModelId
        ModelTaskType.OCR -> ocrModelId
        ModelTaskType.TRANSLATION -> translateModeId
    }
    val selected = findModelById(selectedId)
    val candidates = buildList<ModelRouteCandidate> {
        selected?.findProvider(providers)?.let { provider ->
            add(
                ModelRouteCandidate(
                    provider.id.toString(),
                    selected.id.toString(),
                    priority = 100,
                    costRank = 100,
                    qualityRank = 100,
                    latencyRank = 50,
                )
            )
        }
        providers.filter { it.enabled }.forEach { provider ->
            provider.models
                .filter { it.type == ModelType.CHAT && it.id != selected?.id }
                .forEachIndexed { index, model ->
                    add(
                        ModelRouteCandidate(
                            providerId = provider.id.toString(),
                            modelId = model.id.toString(),
                            priority = 90 - index,
                            costRank = 40 - index,
                            qualityRank = 60 - index,
                            latencyRank = 20 - index,
                        )
                    )
                }
        }
    }
    return ModelTaskBinding(candidates = candidates.distinctBy { it.providerId to it.modelId })
}
