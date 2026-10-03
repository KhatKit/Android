package heizige.kk.khatkit.app.core.data.ai

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
}

data class ModelRouteCandidate(
    val providerId: String,
    val modelId: String,
    val priority: Int = 0,
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

    private fun isCoolingDown(candidate: ModelRouteCandidate, nowMillis: Long): Boolean =
        failures[key(candidate)]?.let { nowMillis - it < cooldown.inWholeMilliseconds } == true

    private fun key(candidate: ModelRouteCandidate): String = "${candidate.providerId}:${candidate.modelId}"
}
