package heizige.kk.khatkit.app.feature.chat

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.Provider
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.provider.TextGenerationResult
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ai.UsageRecorder
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.feature.chat.helper.SubagentTeamManager
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentTeamDeviceTest {
    @Test
    fun sendsOnlyAssignedTextToTheBoundModelsAndReturnsBothResults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model1 = Model(modelId = "writer-model", tools = setOf(BuiltInTools.Search))
        val model2 = Model(modelId = "reviewer-model")
        val lead = Assistant(name = "Lead", enableSubagentTeam = true)
        val writer = Assistant(name = "Writer", chatModelId = model1.id, systemPrompt = "Write clearly.",
            presetMessages = listOf(UIMessage.user("private-history-marker")))
        val reviewer = Assistant(name = "Reviewer", chatModelId = model2.id, systemPrompt = "Review carefully.")
        val provider = ProviderSetting.OpenAI(models = listOf(model1, model2), enabled = true)
        val settings = Settings.dummy().copy(assistants = listOf(lead, writer, reviewer), providers = listOf(provider))
        val manager = ProviderManager(OkHttpClient(), context)
        manager.registerProvider("openai", object : Provider<ProviderSetting.OpenAI> {
            override suspend fun listModels(providerSetting: ProviderSetting.OpenAI) = error("unexpected listModels")
            override suspend fun streamText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>,
                params: TextGenerationParams) = error("unexpected streamText")

            override suspend fun generateText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>,
                params: TextGenerationParams): TextGenerationResult {
                assertEquals(2, messages.size)
                assertTrue(params.tools.isEmpty())
                assertTrue(params.model.tools.isEmpty())
                assertFalse(messages.first().toText().contains("private-history-marker"))
                val pair = when (params.model.modelId) {
                    "writer-model" -> "Write clearly." to "draft"
                    "reviewer-model" -> "Review carefully." to "review"
                    else -> error("unexpected model")
                }
                assertTrue(messages.first().toText().contains(pair.first))
                assertEquals(pair.second, messages.last().toText())
                return TextGenerationResult("test", params.model.modelId, UIMessage.assistant("result-${pair.second}"))
            }
        })
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val tool = SubagentTeamManager(manager, UsageRecorder(database.usageRecordDao())).tool(lead.id, { settings }, null)
            val input = Json.parseToJsonElement("""{"tasks":[{"role_uid":"${writer.id}","task":"draft"},{"role_uid":"${reviewer.id}","task":"review"}]}""")
            assertTrue(tool.needsApproval(input))
            val output = (tool.execute(input).single() as UIMessagePart.Text).text
            assertTrue(output, output.contains("result-draft") && output.contains("result-review"))
        } finally {
            database.close()
        }
    }
}
