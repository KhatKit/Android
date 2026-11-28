package heizige.kk.khatkit.app.feature.explore.data

import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus


/** 安装一个市场 MCP 条目的计划：预填表单的初始 config + 尚缺的必填项。 */
data class McpInstallPlan(
    val config: McpServerConfig,
    val missingRequired: List<String>,
) {
    /** 缺必填 => 只能存候选配置，不能连、也不能挂进助手。 */
    val needsConfig: Boolean get() = missingRequired.isNotEmpty()

    /** 必填齐全才允许 enable = true。 */
    val enable: Boolean get() = missingRequired.isEmpty()
}

/** 卡片/列表上展示的三态（互斥且穷尽，见 [mcpCardStatus]）。 */
enum class McpCardStatus {
    /** 必填齐全：已保存并启用，连接状态由 [McpStatus] 另行展示。 */
    CONFIGURED,

    /** 必填齐全但最近一次连接失败（含需重新授权）。 */
    CONNECT_FAILED,

    /** 缺必填项：只存了候选配置，绝不能显示成「已连接」。 */
    NEEDS_CONFIG,
}

/** 占位值：服务端只声明「这里要填」，真实值必须由用户提供。 */
