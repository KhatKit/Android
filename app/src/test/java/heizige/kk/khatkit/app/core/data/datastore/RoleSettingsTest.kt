package heizige.kk.khatkit.app.core.data.datastore

import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.data.model.RoleTemplates
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class RoleSettingsTest {
    private val model = Model(modelId = "test-chat", displayName = "Test Chat")
    private val supervisor = RoleTemplates.toolbox(model.id)
    private val worker = Assistant(name = "检索员", description = "负责检索和核对信息", chatModelId = model.id)
    private val settings = Settings(
        assistantId = worker.id,
        assistants = listOf(supervisor, worker),
        providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
        chatModelId = model.id,
    )

    @Test
    fun `legacy assistant JSON keeps identity and defaults to ordinary role`() {
        val uid = Uuid.random()
        val role = Json.decodeFromString<Assistant>("""{"id":"$uid","name":"旧助手","systemPrompt":"旧提示词"}""")
        assertEquals(uid, role.id)
        assertEquals("旧提示词", role.systemPrompt)
        assertEquals("", role.description)
        assertFalse(role.canManageRoles)
    }

    @Test
    fun `only untouched legacy default becomes toolbox supervisor`() {
        val legacy = Assistant(id = DEFAULT_ASSISTANT_ID)
        val migrated = migrateLegacyDefaultRole(legacy)
        assertEquals(legacy.id, migrated.id)
        assertTrue(migrated.canManageRoles)
        assertEquals("Toolbox 主管", migrated.name)
        val customized = legacy.copy(systemPrompt = "用户自定义提示词")
        assertSame(customized, migrateLegacyDefaultRole(customized))
        assertSame(worker, migrateLegacyDefaultRole(worker))
    }

    @Test
    fun `model migration pins global model without overwriting explicit bindings`() {
        val another = Model(modelId = "another")
        val old = worker.copy(chatModelId = null)
        val migrated = settings.copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(model, another))),
            assistants = listOf(old, supervisor.copy(chatModelId = another.id)),
        ).withRoleModels()
        assertEquals(model.id, migrated.assistants[0].chatModelId)
        assertEquals(another.id, migrated.assistants[1].chatModelId)
        assertEquals(
            migrated.assistants,
            migrated.copy(chatModelId = another.id).withRoleModels().assistants,
        )
    }

    @Test
    fun `manager can create and edit a role with its own model and intelligence`() {
        val newRole = worker.copy(id = Uuid.random(), name = "核验员")
        val created = settings.createRole(supervisor.id, newRole)
        val updated = created.editRole(supervisor.id, newRole.id) {
            it.copy(description = "核验工具结果", reasoningLevel = ReasoningLevel.HIGH, avatar = Avatar.Emoji("核"))
        }
        assertEquals(supervisor, updated.getAssistantById(supervisor.id))
        assertEquals(worker, updated.getAssistantById(worker.id))
        assertEquals(ReasoningLevel.HIGH, updated.getAssistantById(newRole.id)?.reasoningLevel)
    }

    @Test
    fun `ordinary role cannot create update or delete`() {
        assertThrows(IllegalArgumentException::class.java) {
            settings.createRole(worker.id, worker.copy(id = Uuid.random()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.editRole(worker.id, supervisor.id) { it.copy(name = "changed") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.deleteRole(worker.id, supervisor.id)
        }
    }

    @Test
    fun `AI cannot grant supervisor authority or change immutable identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            settings.createRole(supervisor.id, worker.copy(id = Uuid.random(), canManageRoles = true))
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.editRole(supervisor.id, worker.id) { it.copy(canManageRoles = true) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.editRole(supervisor.id, worker.id) { it.copy(id = Uuid.random()) }
        }
    }

    @Test
    fun `invalid role names descriptions and model bindings are rejected`() {
        listOf(
            worker.copy(name = ""),
            worker.copy(description = ""),
            worker.copy(chatModelId = Uuid.random()),
            worker.copy(chatModelId = null),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { settings.validateRole(invalid) }
        }
        val disabled = settings.copy(providers = listOf(ProviderSetting.OpenAI(enabled = false, models = listOf(model))))
        assertThrows(IllegalArgumentException::class.java) { disabled.validateRole(worker) }
        val image = model.copy(type = ModelType.IMAGE)
        val imageOnly = settings.copy(providers = listOf(ProviderSetting.OpenAI(models = listOf(image))))
        assertThrows(IllegalArgumentException::class.java) { imageOnly.validateRole(worker) }
    }

    @Test
    fun `deleting selected role repairs selection and prevents deleting self`() {
        val deleted = settings.deleteRole(supervisor.id, worker.id)
        assertEquals(listOf(supervisor), deleted.assistants)
        assertEquals(supervisor.id, deleted.assistantId)
        assertThrows(IllegalArgumentException::class.java) { settings.deleteRole(supervisor.id, supervisor.id) }
        assertThrows(IllegalArgumentException::class.java) { settings.deleteRole(supervisor.id, Uuid.random()) }
    }

    @Test
    fun `duplicate identity and deleted supervisor cannot mutate roles`() {
        assertThrows(IllegalArgumentException::class.java) { settings.createRole(supervisor.id, worker) }
        val deleted = settings.copy(assistants = listOf(worker))
        assertThrows(IllegalArgumentException::class.java) {
            deleted.createRole(supervisor.id, worker.copy(id = Uuid.random()))
        }
    }

    @Test
    fun `toolbox template has stable supplied identity and role fields round trip`() {
        val decoded = Json.decodeFromString<Assistant>(Json.encodeToString(supervisor))
        assertEquals(supervisor, decoded)
        assertEquals(model.id, decoded.chatModelId)
        assertTrue(decoded.useAssistantAvatar)
        assertTrue(decoded.description.isNotBlank())
        assertFalse(worker.canManageRoles)
    }
}
