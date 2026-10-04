package heizige.kk.khatkit.app.core.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.workspace.WorkspaceShellStatus

private const val TAG = "ChatToolFactory"

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    // 搜索强制开启：模型没有内置搜索时始终走外部搜索
    return BuiltInTools.Search !in model.tools
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/**
 * C1-M：群聊侧传给工具层的记忆作用域。由生成管线在确定「本轮发言角色」之后构造，
 * 单聊路径不传（null）。
 */
data class GroupMemoryScope(val conversationId: String, val roleId: String)

/**
 * C1-M：一次生成里记忆工具的空间作用域。
 *
 * `spaceId` 是 `memory_search` / `memory_add` / `memory_link` / `memory_forget` 唯一
 * 允许碰的空间；`roleId` 只在群聊时有值，写库时落到 `memory_chunks.role_id`，供检索
 * 结果再做一次 viewer 过滤。
 */
data class MemoryToolScope(
    val spaceId: String,
    val roleId: String? = null,
)

/**
 * C1-M：记忆工具空间选择的唯一入口（纯函数，无 Android 依赖，可 JVM 单测）。
 *
 * 契约：「记忆空间键固定 `group:<conversationId>:role:<roleId>`，首次发言懒创建；
 * **不得回退到全局/助手空间**」「任何失败不得回退到全局记忆空间」。
 */
object MemoryToolScopeResolver {

    /** 契约固定键。参数缺失直接失败——宁可报错，也不静默换空间。 */
    fun groupSpaceId(conversationId: String, roleId: String): String {
        require(conversationId.isNotBlank()) { "群聊记忆空间缺少 conversationId" }
        require(roleId.isNotBlank()) { "群聊记忆空间缺少 roleId" }
        return GroupChat.memorySpaceId(conversationId, roleId)
    }

    fun forGroupChat(conversationId: String, roleId: String): MemoryToolScope =
        MemoryToolScope(groupSpaceId(conversationId, roleId), roleId)

    /**
     * 群聊 → 群空间，**这里没有** `useGlobalMemory` / `assistant.id` 的回退分支：
     * 一旦群聊角色拿不到自己的空间键就该失败，而不是把记忆写进全局或助手空间
     * （那正是 C1-08 要防的「三个空间互不串」被破坏的形式）。
     *
     * 单聊 → 原行为，一字未改：`useGlobalMemory` 时用全局空间，否则用助手空间。
     */
    fun resolve(
        useGlobalMemory: Boolean,
        assistantId: String,
        group: GroupMemoryScope?,
    ): MemoryToolScope = when {
        group != null -> forGroupChat(group.conversationId, group.roleId)
        useGlobalMemory -> MemoryToolScope(MemoryRepository.GLOBAL_MEMORY_ID)
        else -> MemoryToolScope(assistantId)
    }
}

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val json: Json,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val filesManager: FilesManager,
    private val khatKitToolProvider: KhatKitToolProvider? = null,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwd: String? = null,
        groupMemory: GroupMemoryScope? = null,
    ): List<Tool> = buildList {
        // C1-M：群聊时四个记忆工具一律用 `group:<conv>:role:<role>`，无全局/助手回退。
        val memoryScope = MemoryToolScopeResolver.resolve(
            useGlobalMemory = assistant.useGlobalMemory,
            assistantId = assistant.id.toString(),
            group = groupMemory,
        )
        val isGroupScope = memoryScope.roleId != null
        if (assistant.enableMemory) {
            add(
                buildMemorySearchTool(
                    json = json,
                    onSearch = { query, limit ->
                        memoryRepository.searchHybridInSpace(memoryScope.spaceId, query, limit)
                    },
                )
            )
            add(
                buildMemoryAddTool(
                    json = json,
                    onAdd = { content, sourceMessageId, confidence ->
                        memoryRepository.addMemory(
                            assistantId = memoryScope.spaceId,
                            content = content,
                            sourceMessageId = sourceMessageId,
                            confidence = confidence,
                            roleId = memoryScope.roleId,
                        )
                    },
                )
            )
            add(
                buildMemoryLinkTool(
                    json = json,
                    onLink = { src, dst, rel ->
                        memoryRepository.linkMemories(src, dst, rel, expectedSpaceId = memoryScope.spaceId)
                    },
                )
            )
            add(
                buildMemoryForgetTool(
                    json = json,
                    onForget = { id -> memoryRepository.forgetMemory(id, expectedSpaceId = memoryScope.spaceId) },
                )
            )
        }
        if (shouldUseExternalWebSearch(assistant, model)) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools))
        if (assistant.enableRecentChatsReference) {
            // C1-M：群聊里 `recent_chats` / `conversation_search` 走的是「该助手名下全部
            // 会话」，会把别的群和用户私聊的标题送给群角色——同样属于「隐藏但仍发送」。
            // 裁剪需要 ConversationTools 支持按会话过滤，属未授权文件，这里直接不下发：
            // 不给 schema，就不会有「看得见、调不动」的半截状态。
            if (!isGroupScope) {
                addAll(createConversationTools(conversationRepository, assistant.id))
            }
        }
        addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd))
        khatKitToolProvider?.let { provider ->
            addAll(provider.tools(model))
        }
        if (assistant.enabledSkills.isNotEmpty()) {
            addAll(
                createSkillTools(
                    enabledSkills = assistant.enabledSkills,
                    allSkills = withContext(Dispatchers.IO) { skillManager.listSkills() },
                )
            )
        }

        val mcpTools = mcpManager.getAllAvailableTools()
        val invalidNames = mcpTools
            .map { it.second }
            .distinct()
            .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
        if (invalidNames.isNotEmpty()) {
            throw InvalidMcpServerNamesException(invalidNames)
        }
        mcpTools.forEach { (serverId, serverName, tool) ->
            add(
                Tool(
                    name = "mcp__${serverName}__${tool.name}",
                    description = tool.description ?: "",
                    parameters = { tool.inputSchema },
                    needsApproval = { tool.needsApproval },
                    execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                )
            )
        }
    }

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String?): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, filesManager, cwd)
    }
}
