package heizige.kk.khatkit.app.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.feature.settings.mcp.McpServerConfigModal

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
    var opened by remember(initial) { mutableStateOf(false) }

    // 打开一次即可：用 opened 门控，避免重组时反复 open 把用户的输入冲掉。
    LaunchedEffect(initial) {
        if (!opened) {
            opened = true
            state.open(initial)
        }
    }
    // 表单自己 dismiss 时（返回 / 下滑 / 取消）把宿主的 target 一起清掉。
    LaunchedEffect(state.isEditing) {
        if (opened && !state.isEditing) onDismiss()
    }
    if (!opened) return

    McpServerConfigModal(state) {
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
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (masked.isNotBlank()) {
            Text(
                text = stringResource(R.string.explore_market_mcp_prefilled, masked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
