package heizige.kk.khatkit.app.core.data.datastore

import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.RoleTemplates
import kotlin.uuid.Uuid

internal fun migrateLegacyDefaultRole(role: Assistant): Assistant {
    if (role.id != DEFAULT_ASSISTANT_ID || role.name.isNotBlank() ||
        role.systemPrompt.isNotBlank() || role.description.isNotBlank()
    ) return role
    val template = RoleTemplates.toolbox()
    return role.copy(
        name = template.name,
        description = template.description,
        systemPrompt = template.systemPrompt,
        canManageRoles = true,
        useAssistantAvatar = true,
    )
}

/** 旧的全局模型只作迁移种子；一旦保存，后续切换模型不会改变其他角色。 */
internal fun Settings.withRoleModels(): Settings {
    val models = providers.filter { it.enabled }.flatMap { it.models }
        .filter { it.type == ModelType.CHAT }
    val seed = models.firstOrNull { it.id == chatModelId } ?: models.firstOrNull()
    return copy(assistants = assistants.map { role ->
        if (role.chatModelId == null && seed != null) role.copy(chatModelId = seed.id) else role
    })
}

internal fun Settings.requireRoleManager(actorId: Uuid): Assistant =
    requireNotNull(getAssistantById(actorId)) { "当前角色已删除" }.also {
        require(it.canManageRoles) { "只有主管角色可以管理团队" }
    }

internal fun Settings.validateRole(role: Assistant) {
    require(role.name.isNotBlank() && role.name.length <= 100) { "角色名称必须为 1-100 个字符" }
    require(role.description.isNotBlank() && role.description.length <= 4000) { "职责描述必须为 1-4000 个字符" }
    require(role.systemPrompt.length <= 32000) { "角色提示词过长" }
    require(providers.any { provider ->
        provider.enabled && provider.models.any { it.id == role.chatModelId && it.type == ModelType.CHAT }
    }) { "请绑定一个已启用的聊天模型 UID" }
}

internal fun Settings.createRole(actorId: Uuid, role: Assistant): Settings {
    requireRoleManager(actorId)
    validateRole(role)
    require(!role.canManageRoles) { "主管权限只能由用户授予" }
    require(assistants.none { it.id == role.id }) { "角色 UID 已存在" }
    return copy(assistants = assistants + role)
}

internal fun Settings.editRole(
    actorId: Uuid,
    targetId: Uuid,
    change: (Assistant) -> Assistant,
): Settings {
    requireRoleManager(actorId)
    val old = requireNotNull(getAssistantById(targetId)) { "目标角色不存在" }
    val updated = change(old)
    require(updated.id == old.id) { "角色 UID 不可修改" }
    require(updated.canManageRoles == old.canManageRoles) { "主管权限只能由用户修改" }
    validateRole(updated)
    return copy(assistants = assistants.map { if (it.id == targetId) updated else it })
}

internal fun Settings.deleteRole(actorId: Uuid, targetId: Uuid): Settings {
    requireRoleManager(actorId)
    require(actorId != targetId) { "主管不能删除自己" }
    val target = requireNotNull(getAssistantById(targetId)) { "目标角色不存在" }
    require(assistants.size > 1) { "至少保留一个角色" }
    // 仅移除配置，历史对话与记忆保留，不把配置操作变成历史数据删除操作。
    return removeAssistant(target)
}
