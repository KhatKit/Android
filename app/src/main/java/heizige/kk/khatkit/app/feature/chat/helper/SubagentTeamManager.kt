package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ai.UsageRecorder
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
class SubagentTeamManager @Inject constructor(
    private val providers: ProviderManager,
    private val usage: UsageRecorder,
) {
    private val permits = Semaphore(2)

    fun tool(actorId: Uuid, settings: () -> Settings, conversationId: Uuid?): Tool =
        createSubagentTeamTool(actorId, settings, permits) { role, task, snapshot ->
            val model = snapshot.findModelById(role.chatModelId ?: snapshot.chatModelId)
                ?.takeIf { it.type == ModelType.CHAT }
                ?: return@createSubagentTeamTool null
            val provider = model.findProvider(snapshot.providers.filter { it.enabled })
                ?.takeIf { it.enabled } ?: return@createSubagentTeamTool null
            val result = providers.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.system(workerPrompt(role)), UIMessage.user(task)),
                params = TextGenerationParams(
                    model = model.copy(tools = emptySet()),
                    maxTokens = (role.maxTokens ?: 1800).coerceIn(1, 1800),
                    temperature = role.temperature,
                    topP = role.topP,
                    reasoningLevel = role.reasoningLevel,
                ),
            )
            usage.record(UsageRecorder.SOURCE_SUBAGENT, result.usage, provider.id.toString(), model.id.toString(),
                model.modelId, role.id.toString(), conversationId?.toString())
            result.message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
        }
}

private fun workerPrompt(role: Assistant): String = buildString {
    appendLine("Role: ${role.name.take(100)}")
    appendLine("Responsibilities: ${role.description.take(4000)}")
    appendLine(role.systemPrompt.take(8000))
    append("This is a single delegated text task. Return findings for the supervising assistant. " +
        "You have no access to other chats, tools or memory. Do not claim to have performed external actions.")
}

internal fun createSubagentTeamTool(
    actorId: Uuid,
    settings: () -> Settings,
    permits: Semaphore,
    generate: suspend (Assistant, String, Settings) -> String?,
): Tool = Tool(
    name = "delegate_team_tasks",
    description = "把最多三个独立文本任务分给现有角色并汇总结果。先用 role_directory 获取角色 UID。" +
        "只能提交用户可核对的必要文字，不要传密钥或整段对话。执行前必须由用户确认。",
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
                        put("role_uid", buildJsonObject { put("type", "string") })
                        put("task", buildJsonObject { put("type", "string"); put("maxLength", 5000) })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("role_uid"), JsonPrimitive("task"))))
                })
            })
        }, required = listOf("tasks"))
    },
    execute = { input ->
        val snapshot = settings()
        require(snapshot.getAssistantById(actorId)?.enableSubagentTeam == true) { "子代理团队已关闭" }
        val tasks = (input as? JsonObject)?.get("tasks") as? JsonArray ?: error("缺少任务列表")
        require(tasks.size in 1..3) { "每批需要 1 到 3 个子任务" }
        val assignments = tasks.map { entry ->
            val task = entry as? JsonObject ?: error("子任务必须是对象")
            val roleId = Uuid.parse(task.requiredText("role_uid"))
            val text = task.requiredText("task").trim()
            require(roleId != actorId && text.length in 1..5000) { "不能委派给自己，任务需为 1 到 5000 个字符" }
            requireNotNull(snapshot.getAssistantById(roleId)) { "子代理角色已删除" } to text
        }
        require(assignments.map { it.first.id }.distinct().size == assignments.size) { "每批每个角色只能接一个任务" }
        val results = coroutineScope {
            assignments.map { (role, task) ->
                async {
                    val response = try {
                        withTimeoutOrNull(60_000L) {
                            permits.withPermit { generate(role, task, snapshot) }
                                ?.trim()?.takeIf { it.isNotEmpty() }
                                ?.let { "text" to it.take(8000) } ?: ("error" to "模型不可用或未返回文字")
                        } ?: ("error" to "子任务超时")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        "error" to "子代理模型调用失败"
                    }
                    buildJsonObject {
                        put("role_uid", role.id.toString())
                        put("role", role.name)
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

private fun JsonObject.requiredText(key: String): String =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("缺少 $key")
