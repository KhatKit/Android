package heizige.kk.khatkit.app.data.ai

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProviderFailoverTest {

    private fun model(id: String, abilities: List<ModelAbility> = listOf(ModelAbility.TOOL)) =
        Model(modelId = id, displayName = id, abilities = abilities)

    private fun provider(
        name: String,
        enabled: Boolean = true,
        models: List<Model> = listOf(model("$name-chat")),
    ) = ProviderSetting.OpenAI(name = name, enabled = enabled, models = models)

    @Test
    fun chainStartsWithPrimaryThenEnabledProviders() {
        val primary = provider("primary")
        val settings = Settings(
            providers = listOf(primary, provider("backup-a"), provider("backup-b")),
        )
        val chain = ProviderFailover.buildChain(settings, primary, primary.models.first())
        assertEquals(listOf("primary", "backup-a", "backup-b"), chain.map { it.first.name })
    }

    @Test
    fun disabledProvidersAreSkipped() {
        val primary = provider("primary")
        val settings = Settings(
            providers = listOf(primary, provider("disabled", enabled = false), provider("backup")),
        )
        val chain = ProviderFailover.buildChain(settings, primary, primary.models.first())
        assertEquals(listOf("primary", "backup"), chain.map { it.first.name })
    }

    @Test
    fun providersWithoutChatModelsAreSkipped() {
        val primary = provider("primary")
        val settings = Settings(
            providers = listOf(primary, provider("empty", models = emptyList()), provider("backup")),
        )
        val chain = ProviderFailover.buildChain(settings, primary, primary.models.first())
        assertEquals(listOf("primary", "backup"), chain.map { it.first.name })
    }

    @Test
    fun nonChatModelsAreNotSelected() {
        val primary = provider("primary")
        val imageOnly = Model(modelId = "img", type = ModelType.IMAGE)
        val settings = Settings(
            providers = listOf(primary, provider("image-provider", models = listOf(imageOnly))),
        )
        val chain = ProviderFailover.buildChain(settings, primary, primary.models.first())
        assertEquals(listOf("primary"), chain.map { it.first.name })
    }

    @Test
    fun retryableErrorsAreEligible() {
        assertTrue(ProviderFailover.isEligible(IOException("connection reset")))
        assertTrue(ProviderFailover.isEligible(RuntimeException("HTTP 429 Too Many Requests")))
        assertTrue(ProviderFailover.isEligible(RuntimeException("rate limit exceeded")))
        assertTrue(ProviderFailover.isEligible(RuntimeException("model is overloaded, please retry")))
    }

    @Test
    fun nonRetryableErrorsAreNotEligible() {
        assertFalse(ProviderFailover.isEligible(RuntimeException("HTTP 401 Unauthorized")))
        assertFalse(ProviderFailover.isEligible(RuntimeException("invalid request body")))
    }
}
