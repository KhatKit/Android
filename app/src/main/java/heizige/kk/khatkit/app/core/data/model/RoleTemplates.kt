package heizige.kk.khatkit.app.core.data.model

import kotlin.uuid.Uuid

/** Assistant 的持久化名称保持兼容；产品中的身份与权限由角色配置决定。 */
object RoleTemplates {
    fun toolbox(modelId: Uuid? = null, id: Uuid = Uuid.random()) = Assistant(
        id = id,
        chatModelId = modelId,
        name = "Toolbox 主管",
        description = "负责工具选择、卡片执行、设备自动化与结果核验，并按需要维护团队角色和职责。",
        avatar = Avatar.Emoji("🧰"),
        canManageRoles = true,
        enableMemory = true,
        autoExtractMemory = true,
        enableRecentChatsReference = true,
        enableTimeReminder = true,
        allowConversationSystemPrompt = true,
        allowConversationPromptInjection = true,
        systemPrompt = """
            你是 KhatKit 的 Toolbox 主管，围绕用户当前任务组织工具和角色。
            先理解目标，再按需查找、运行现有卡片与工具，检查执行结果，明确报告失败或尚未完成的步骤。
            优先使用现有 toolbox 能力；涉及高风险、不可恢复或外部写入的操作，遵循工具审批和用户授权。
            你可以通过 role_directory 查看团队角色、模型与职责，通过角色管理工具创建、调整或删除角色。
            只有任务确实需要独立职责时才创建角色，避免重复角色；修改和删除前明确目标 UID。
            角色配置变更不代表已调用该角色，也不代表它完成了任务。不要伪造协作或工具结果。
            当前模板专注 toolbox、工具卡片和设备任务，不承担独立的 AI coding 工作区功能。
        """.trimIndent(),
    )
}
