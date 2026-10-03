package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.ai.util.KeyPool
import heizige.kk.khatkit.ai.util.ProviderAccount
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRouteTest {
    @Test
    fun `cost quality and latency pick different candidates`() {
        val cheap = ModelRouteCandidate("p", "cheap", costRank = 1, qualityRank = 1, latencyRank = 1)
        val strong = ModelRouteCandidate("p", "strong", costRank = 9, qualityRank = 9, latencyRank = 9)
        val router = ModelTaskRouter()
        val candidates = listOf(strong, cheap)
        assertEquals(cheap, router.choose(ModelTaskType.CHAT, ModelTaskBinding(candidates, ModelRoutePolicy.COST_FIRST), 0))
        assertEquals(strong, router.choose(ModelTaskType.MEMORY, ModelTaskBinding(candidates, ModelRoutePolicy.QUALITY_FIRST), 0))
        assertEquals(cheap, router.choose(ModelTaskType.OCR, ModelTaskBinding(candidates, ModelRoutePolicy.LATENCY_FIRST), 0))
    }

    @Test
    fun `chat and memory keep independent cooldowns`() {
        val router = ModelTaskRouter(cooldown = 30.seconds)
        val chat = ModelRouteCandidate("p", "chat", priority = 10)
        val memory = ModelRouteCandidate("p", "memory", priority = 10)
        router.reportFailure(chat, 0)
        assertEquals(memory, router.choose(ModelTaskType.MEMORY, ModelTaskBinding(listOf(memory)), 1))
        assertNull(router.choose(ModelTaskType.CHAT, ModelTaskBinding(listOf(chat)), 1))
    }

    @Test
    fun `429 cools a key then half-open probe restores it`() {
        val pool = KeyPool()
        val accounts = listOf(ProviderAccount("a", priority = 1), ProviderAccount("b", priority = 1))
        assertEquals("a", pool.choose("p", accounts, 0)?.apiKey)
        pool.reportFailure("a", 429, 0)
        assertEquals("b", pool.choose("p", accounts, 1_000)?.apiKey)
        assertNull(pool.choose("p", listOf(ProviderAccount("a")), 1_000))
        assertEquals("a", pool.choose("p", listOf(ProviderAccount("a")), 5_000)?.apiKey)
        pool.reportSuccess("a")
        assertEquals("a", pool.choose("p", listOf(ProviderAccount("a")), 5_001)?.apiKey)
    }

    @Test
    fun `backoff doubles and caps at five minutes`() {
        val pool = KeyPool()
        pool.reportFailure("a", 500, 0)
        assertEquals(5_000L, pool.coolingUntil("a"))
        pool.reportFailure("a", 500, 5_000)
        assertEquals(5_000L + 10_000L, pool.coolingUntil("a"))
    }

    @Test
    fun `budget degrades then becomes read only`() {
        val limits = BudgetLimits(dailyTokens = 10)
        assertEquals(BudgetAction.DEGRADE, budgetAction(BudgetUsage(dailyTokens = 10), limits, cheaperAvailable = true))
        assertEquals(BudgetAction.READ_ONLY, budgetAction(BudgetUsage(dailyTokens = 10), limits, cheaperAvailable = false))
        assertEquals(BudgetAction.ALLOW, budgetAction(BudgetUsage(dailyTokens = 9), limits, cheaperAvailable = false))
    }
}
