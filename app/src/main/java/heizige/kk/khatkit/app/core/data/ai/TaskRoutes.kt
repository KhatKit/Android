package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class RouteBlockedException : IllegalStateException(
    "Budget exceeded. Generation is read-only until the daily or monthly limit resets.",
)

object TaskRoutes {
    val router = ModelTaskRouter()
    private val usage = ConcurrentHashMap<String, Long>()
    private val counts = ConcurrentHashMap<String, Int>()

    fun resolve(
        settings: Settings,
        task: ModelTaskType,
        preferredId: Uuid? = null,
        nowMillis: Long = System.currentTimeMillis(),
        limits: BudgetLimits = BudgetLimits(),
        spent: BudgetUsage = BudgetUsage(),
    ): Model {
        val binding = settings.taskBinding(task, preferredId)
        val chosen = router.choose(task, binding, nowMillis)
            ?: throw IllegalStateException("No model available for $task")
        val cheaper = binding.candidates.any { it.costRank < chosen.costRank && !router.isCooling(it, nowMillis) }
        when (budgetAction(spent, limits, cheaper)) {
            BudgetAction.ALLOW -> record(task, chosen.modelId)
            BudgetAction.DEGRADE -> {
                val cheap = binding.candidates.filterNot { router.isCooling(it, nowMillis) }
                    .minByOrNull { it.costRank }
                    ?: throw RouteBlockedException()
                record(task, cheap.modelId)
                return model(settings, cheap)
            }
            BudgetAction.READ_ONLY -> throw RouteBlockedException()
        }
        return model(settings, chosen)
    }

    fun snapshot(): Map<String, Int> = counts.toMap()

    private fun model(settings: Settings, candidate: ModelRouteCandidate): Model {
        return settings.findModelById(runCatching { Uuid.parse(candidate.modelId) }.getOrNull())
            ?: throw IllegalStateException("Routed model is missing")
    }

    private fun record(task: ModelTaskType, modelId: String) {
        counts.merge("${task.name}:$modelId", 1, Int::plus)
        usage.merge(task.name, 1L, Long::plus)
    }
}
