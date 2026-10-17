package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.RoleTemplates
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class RoleToolsTest {
    private val model = Model(modelId = "model-test", displayName = "Test")
    private val supervisor = RoleTemplates.toolbox(model.id)
    private val worker = Assistant(name = "worker", description = "verify", chatModelId = model.id)
    private var settings = Settings(
        assistantId = supervisor.id,
        chatModelId = model.id,
        assistants = listOf(supervisor, worker),
        providers = listOf(ProviderSetting.OpenAI(
            models = listOf(model),
            apiKey = "secret-api-key",
            baseUrl = "https://private-endpoint.example",
        )),
    )

    private fun tools(actor: Assistant = supervisor) = createRoleTools(actor.id, { settings }) { change ->
        settings = change(settings)
    }

    @Test
    fun `ordinary roles get directory only and supervisor mutations require approval`() {
        assertEquals(listOf("role_directory"), tools(worker).map { it.name })
        val managerTools = tools()
        assertEquals(listOf("role_directory", "role_create", "role_update", "role_delete"), managerTools.map { it.name })
        managerTools.drop(1).forEach { assertTrue(it.needsApproval(JsonObject(emptyMap()))) }
    }

    @Test
    fun `directory contains responsibilities model identity and intelligence but no credentials or prompts`() {
        val directory = buildRoleDirectory(settings, supervisor.id).toString()
        assertTrue(directory.contains(worker.id.toString()))
        assertTrue(directory.contains("verify"))
        assertTrue(directory.contains(model.id.toString()))
        assertTrue(directory.contains("intelligence"))
        assertFalse(directory.contains("secret-api-key"))
        assertFalse(directory.contains("private-endpoint"))
        assertFalse(directory.contains("systemPrompt"))
        assertTrue(tools().first().systemPrompt(model, emptyList()).contains(worker.id.toString()))
    }

    @Test
    fun `create update delete tools persist configuration without touching conversation repositories`() = runBlocking {
        val roleTools = tools().associateBy { it.name }
        val created = roleTools.getValue("role_create").execute(buildJsonObject {
            put("name", "检索员")
            put("description", "负责检索")
            put("model_uid", model.id.toString())
            put("intelligence", "high")
            put("avatar", "检")
        })
        val newRole = settings.assistants.last()
        assertEquals(ReasoningLevel.HIGH, newRole.reasoningLevel)
        assertTrue((created.first() as UIMessagePart.Text).text.contains(newRole.id.toString()))
        roleTools.getValue("role_update").execute(buildJsonObject {
            put("uid", newRole.id.toString())
            put("description", "负责信息核验")
        })
        assertEquals("负责信息核验", settings.assistants.last().description)
        assertEquals(ReasoningLevel.HIGH, settings.assistants.last().reasoningLevel)
        val deleted = roleTools.getValue("role_delete").execute(buildJsonObject {
            put("uid", newRole.id.toString())
        })
        assertEquals(listOf(supervisor, worker), settings.assistants)
        assertTrue((deleted.first() as UIMessagePart.Text).text.contains("\"history_preserved\":true"))
    }

    @Test
    fun `permission revoked during approval is checked at execution time`() {
        val update = tools().first { it.name == "role_update" }
        settings = settings.copy(assistants = settings.assistants.map { it.copy(canManageRoles = false) })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                update.execute(buildJsonObject {
                    put("uid", worker.id.toString())
                    put("name", "changed")
                })
            }
        }
        assertEquals("worker", settings.assistants.last().name)
    }

    @Test
    fun `malformed arguments cannot mutate settings`() {
        val update = tools().first { it.name == "role_update" }
        val before = settings
        listOf(
            """{"uid":"${worker.id}","name":null}""",
            """{"uid":"${worker.id}","intelligence":"genius"}""",
            """{"uid":"${worker.id}","model_uid":"${model.modelId}"}""",
            """{"uid":"${worker.id}","avatar":""}""",
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { update.execute(Json.parseToJsonElement(input)) }
            }
            assertEquals(before, settings)
        }
    }
}
