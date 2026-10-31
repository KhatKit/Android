package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ai.McpPicker
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus

@Composable
fun AssistantMcpPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_mcp),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        // 服务器列表是一张 Mochi 分组卡片；留白与设置页一致。
        McpPicker(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = PageMetrics.TopContentPadding,
                end = 16.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            assistant = assistant,
            servers = mcpServerConfigs,
            onUpdateAssistant = { vm.update(it) }
        )
    }
}
