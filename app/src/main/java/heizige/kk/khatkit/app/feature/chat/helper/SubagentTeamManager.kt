package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ai.UsageRecorder
import heizige.kk.khatkit.app.core.data.ai.tools.DELEGATE_TEAM_TASKS
import heizige.kk.khatkit.app.core.data.ai.tools.TEMPORARY_SUBAGENT
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.datastore.team
import heizige.kk.khatkit.app.core.data.datastore.withTeam
import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

internal data class SubagentAssignment(val role: Assistant, val task: String, val temporary: Boolean)

@Singleton
class SubagentTeamManager @Inject constructor(
    private val providers: ProviderManager,
    private val usage: UsageRecorder,
) {
    private val permits = Semaphore(2)

    fun tool(actorId: Uuid, update: suspend ((Settings) -> Settings) -> Unit, conversationId: Uuid, chatModelId: Uuid): Tool =
        createSubagentTeamTool(actorId, conversationId, chatModelId, update, permits) { assignment, snapshot ->
            val (role, task, temporary) = assignment
            val modelId = role.chatModelId ?: if (temporary) chatModelId else snapshot.chatModelId
            val (model, provider) = snapshot.subagentModel(modelId) ?: return@createSubagentTeamTool null
            val result = providers.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = subagentMessages(role, task),
                params = TextGenerationParams(
                    model = model.copy(tools = emptySet()),
                    maxTokens = (role.maxTokens ?: 1800).coerceIn(1, 1800),
                    temperature = role.temperature,
                    topP = role.topP,
                    reasoningLevel = role.reasoningLevel,
                ),
            )
            usage.record(UsageRecorder.SOURCE_SUBAGENT, result.usage, provider.id.toString(), model.id.toString(),
                model.modelId, role.id.toString(), conversationId.toString())
            result.message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
        }
}

internal fun subagentMessages(role: Assistant, task: String): List<UIMessage> {
    val prompt = buildString {
        appendLine("Role: ${role.name.take(100)}")
        appendLine("Responsibilities: ${role.description.take(4000)}")
        if (role.systemPrompt.isNotBlank()) appendLine(role.systemPrompt.take(8000))
        append("This is a single delegated text task. Return findings for the supervising assistant. " +
            "You have no access to other chats, tools or memory. Do not claim to have performed external actions.")
    }
    return listOf(UIMessage.system(prompt), UIMessage.user(task))
}

internal fun createSubagentTeamTool(
    actorId: Uuid,
    conversationId: Uuid,
    chatModelId: Uuid,
    update: suspend ((Settings) -> Settings) -> Unit,
    permits: Semaphore,
    generate: suspend (SubagentAssignment, Settings) -> String?,
): Tool = Tool(
    name = DELEGATE_TEAM_TASKS,
    description = "委派最多三个独立文本任务。先按职责匹配角色目录中的已有角色，匹配时填写 role_uid。" +
        "只有缺少合适角色时才填写 temporary_role，给出本次任务所需角色的名称和职责。两种方式选一。" +
        "新建的临时角色使用当前聊天模型，同一会话可按返回的 UID 再次委派。按返回顺序汇总结果。" +
        "只能提交用户可核对的必要文字，不要传密钥或整段对话。执行前必须由用户确认。",
    systemPrompt = { _, _ ->
        "委派前根据团队目录中的职责选择已有角色，目录变化后用 role_directory 刷新。" +
            "同一角色的相关工作可以合并成一项。只有团队缺少所需职责时才拟定临时角色，" +
            "通过 delegate_team_tasks 的 temporary_role 指定名称和职责。临时分工无需调用 role_create。"
    },
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(buildJsonObject {
            put("tasks", buildJsonObject {
                put("type", "array")
                put("minItems", 1)
                put("maxItems", 3)
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("role_uid", buildJsonObject {
                            put("type", "string")
                            put("description", "职责匹配的已有角色 UID。创建临时角色时省略。")
                        })
                        put("temporary_role", buildJsonObject {
                            put("type", "object")
                            put("description", "仅在没有合适的已有角色时填写。")
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject { put("type", "string"); put("maxLength", 100) })
                                put("description", buildJsonObject { put("type", "string"); put("maxLength", 4000) })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("description"))))
                        })
                        put("task", buildJsonObject { put("type", "string"); put("maxLength", 5000) })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("task"))))
                })
            })
        }, required = listOf("tasks"))
    },
    execute = { input ->
        val tasks = (input as? JsonObject)?.get("tasks") as? JsonArray ?: error("缺少任务列表")
        require(tasks.size in 1..3) { "每批需要 1 到 3 个子任务" }
        lateinit var snapshot: Settings
        lateinit var assignments: List<SubagentAssignment>
        update { current ->
            val team = current.team(conversationId)
            require(actorId in team.delegates && current.getAssistantById(actorId) != null) { "当前对话的子代理团队已关闭" }
            snapshot = current
            assignments = tasks.mapIndexed { index, entry ->
                val task = entry as? JsonObject ?: error("子任务必须是对象")
                task.resolveAssignment(index, actorId, current, team.temporaryRoles, chatModelId)
            }
            require(assignments.distinctBy { it.role.id }.size == assignments.size) { "每批每个角色只能接一个任务" }
            val temps = assignments.filter { it.temporary }.map { it.role }
            require(temps.distinctBy { it.description.trim().lowercase().ifBlank { it.name } }.size == temps.size) {
                "每批每种职责只能委派一次"
            }
            val roles = (team.temporaryRoles + temps).distinctBy { it.id }
            if (roles == team.temporaryRoles) current else current.withTeam(conversationId, team.copy(temporaryRoles = roles))
        }
        val results = coroutineScope {
            assignments.map { assignment ->
                async {
                    val response = try {
                        withTimeoutOrNull(60_000L) {
                            permits.withPermit { generate(assignment, snapshot) }
                                ?.trim()?.takeIf { it.isNotEmpty() }
                                ?.let { "text" to it.take(8000) } ?: ("error" to "模型不可用或未返回文字")
                        } ?: ("error" to "子任务超时")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        "error" to "子代理模型调用失败"
                    }
                    buildJsonObject {
                        put("role_uid", assignment.role.id.toString())
                        put("role", assignment.role.name)
                        put(response.first, response.second)
                    }
                }
            }.awaitAll()
        }
        listOf(UIMessagePart.Text(buildJsonObject {
            put("results", buildJsonArray { results.forEach { add(it) } })
        }.toString()))
    },
)

private fun JsonObject.resolveAssignment(index: Int, actorId: Uuid, snapshot: Settings, temporaryRoles: List<Assistant>, chatModelId: Uuid): SubagentAssignment {
    val text = requiredText("task").trim()
    require(text.length in 1..5000) { "任务需为 1 到 5000 个字符" }
    val profile = get("temporary_role")?.takeUnless { it is JsonNull }
    if (profile == null) {
        val target = requiredText("role_uid")
        // 旧版本已进入审批的请求没有临时角色资料，按原来的名称和分工恢复。
        if (target == TEMPORARY_SUBAGENT) {
            val name = "临时子代理 ${index + 1}"
            val role = temporaryRoles.firstOrNull { it.name == name && it.description.isEmpty() } ?: temporaryRole(name, "", chatModelId)
            return SubagentAssignment(role, text, temporary = true)
        }
        val id = Uuid.parse(target)
        require(id != actorId) { "不能委派给自己" }
        temporaryRoles.firstOrNull { it.id == id }?.let { return SubagentAssignment(it, text, temporary = true) }
        val role = requireNotNull(snapshot.getAssistantById(id)) { "当前对话找不到这个角色" }
        return SubagentAssignment(role, text, temporary = false)
    }

    require(get("role_uid") == null || get("role_uid") is JsonNull) { "已有角色和临时角色只能选择一种" }
    require(profile is JsonObject) { "临时角色必须是对象" }
    val name = profile.requiredText("name").trim()
    val duties = profile.requiredText("description").trim()
    require(name.length in 1..100 && duties.length in 1..4000) { "请提供临时角色的名称和职责" }
    val match = snapshot.assistants.firstOrNull {
        it.id != actorId && it.description.trim().equals(duties, ignoreCase = true) &&
            snapshot.subagentModel(it.chatModelId ?: snapshot.chatModelId) != null
    }
    require(match == null) { "已有相同职责的角色，请改用 role_uid ${match?.id} 并重新确认" }
    val role = temporaryRoles.firstOrNull { it.description.trim().equals(duties, ignoreCase = true) }
        ?: temporaryRole(name, duties, chatModelId)
    return SubagentAssignment(role, text, temporary = true)
}

private fun Settings.subagentModel(id: Uuid?): Pair<Model, ProviderSetting>? {
    val model = findModelById(id)?.takeIf { it.type == ModelType.CHAT } ?: return null
    val provider = model.findProvider(providers.filter { it.enabled })?.takeIf { it.enabled } ?: return null
    return model to provider
}

private fun temporaryRole(name: String, duties: String, modelId: Uuid) =
    Assistant(name = name, description = duties, chatModelId = modelId, reasoningLevel = ReasoningLevel.OFF)

private fun JsonObject.requiredText(key: String): String =
    requireNotNull((get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content) { "缺少 $key" }
