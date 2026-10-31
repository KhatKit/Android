package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus

@Composable
fun AssistantRequestPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 页面外壳统一走 KedgeSettingsPageScaffold；内容是 LazyColumn，每个分组（请求头 /
    // 请求体）一项，每项是一张 Mochi 分组卡片，留白与设置页一致。
    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_request),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantRequestContent(
            // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
internal fun AssistantRequestContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = innerPadding + PaddingValues(
            start = 16.dp,
            top = PageMetrics.TopContentPadding,
            end = 16.dp,
            bottom = 8.dp + PageMetrics.BottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "custom_headers") {
            CustomHeaders(
                headers = assistant.customHeaders,
                onUpdate = {
                    onUpdate(
                        assistant.copy(
                            customHeaders = it
                        )
                    )
                }
            )
        }

        item(key = "custom_bodies") {
            CustomBodies(
                customBodies = assistant.customBodies,
                onUpdate = {
                    onUpdate(
                        assistant.copy(
                            customBodies = it
                        )
                    )
                }
            )
        }
    }
}
