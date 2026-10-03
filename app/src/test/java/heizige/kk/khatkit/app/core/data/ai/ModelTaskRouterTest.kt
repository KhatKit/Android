package heizige.kk.khatkit.app.core.data.ai

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTaskRouterTest {
    private val candidates = listOf(
        ModelRouteCandidate("a", "m1", priority = 10),
        ModelRouteCandidate("b", "m2", priority = 5),
    )

    @Test
    fun `priority policy skips cooling candidate`() {
        val router = ModelTaskRouter(cooldown = 30.seconds)
        router.reportFailure(candidates[0], nowMillis = 100)

        assertEquals(
            candidates[1],
            router.choose(ModelTaskType.CHAT, ModelTaskBinding(candidates), nowMillis = 101),
        )
        assertNull(router.choose(ModelTaskType.CHAT, ModelTaskBinding(emptyList()), nowMillis = 101))
    }

    @Test
    fun `round robin advances independently per task`() {
        val router = ModelTaskRouter()
        val binding = ModelTaskBinding(candidates, ModelRoutePolicy.ROUND_ROBIN)

        assertEquals(candidates[0], router.choose(ModelTaskType.CHAT, binding, 0))
        assertEquals(candidates[1], router.choose(ModelTaskType.CHAT, binding, 0))
        assertEquals(candidates[0], router.choose(ModelTaskType.MEMORY, binding, 0))
    }
}
