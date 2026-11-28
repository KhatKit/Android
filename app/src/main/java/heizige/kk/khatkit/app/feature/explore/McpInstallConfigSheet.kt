package heizige.kk.khatkit.app.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.components.ai.mcp.McpServerConfigModal
import heizige.kk.khatkit.app.feature.explore.helper.planMcpInstall
import heizige.kk.khatkit.app.feature.explore.helper.rePlanMcpInstall
import heizige.kk.khatkit.app.feature.explore.helper.maskedMcpSummary

/**
 * P0-5：市场条目的「配置」入口 —— 复用设置页的 MCP 表单（[McpServerConfigModal]），
 * 但初始值来自条目本身：名称 / 传输 / URL / 请求头全部预填，缺必填时顶部给出提示。
 *
 * 「缺必填」的判定与保存语义都在 [planMcpInstall] / [rePlanMcpInstall] 里，
 * 本组件只负责把预填值与提示展示出来，不改任何持久化行为。
 */
@Composable
fun McpInstallConfigSheet(
    initial: McpServerConfig,
    missingRequired: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (McpServerConfig) -> Unit,
) {
    val state = useEditState<McpServerConfig> { onConfirm(it) }
    LaunchedEffect(initial) { state.open(initial) }
    // Both save and cancel release the market target after the native sheet has finished closing.
    McpServerConfigModal(state, onClosed = onDismiss) {
        McpInstallConfigHeader(initial = initial, missingRequired = missingRequired)
    }
}

/**
 * 表单顶部的提示条：缺哪些必填 + 预填内容的**脱敏**摘要。
 * 摘要走 [maskedMcpSummary]，所以 Authorization / X-Api-Key 一类头只显示掩码。
 */
@Composable
private fun McpInstallConfigHeader(
    initial: McpServerConfig,
    missingRequired: List<String>,
) {
    val masked = remember(initial) { maskedMcpSummary(initial) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (missingRequired.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.explore_market_mcp_need_config_keys,
                    missingRequired.joinToString(", "),
                ),
                style = MochiTheme.textStyles.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MochiTheme.colorScheme.error,
            )
        }
        if (masked.isNotBlank()) {
            Text(
                text = stringResource(R.string.explore_market_mcp_prefilled, masked),
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
