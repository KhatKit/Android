package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

@Composable
fun AssistantRequestPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Miuix：外壳换成 KedgeSettingsPageScaffold（Miuix Scaffold + 页面级毛玻璃 +
    // Miuix 折叠行为），内容**直接透传**给页面自己滚动。
    //
    // 不能写成 MiuixSettingsPage { item { ... } }：AssistantRequestContent 自己是
    // verticalScroll 的 Column，塞进 LazyColumn 的 item 后 maxHeight 变成 Infinity，
    // 一进页面就崩（"Vertically scrollable component was measured with an infinity
    // maximum height constraints"）。
    val miuixTitle = stringResource(R.string.assistant_page_tab_request)
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeSettingsPageScaffold(
            title = miuixTitle,
            navigationIcon = { BackButton() },
        ) { innerPadding ->
            AssistantRequestContent(
                // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
                // 旧写法硬编码 PaddingValues(horizontal = 16.dp)（因为 MiuixSettingsPage
                // 自己用 LazyColumn 的 contentPadding 处理了顶栏），改成内容直接透传后
                // 顶部就是 0，content 直接压在 TopAppBar 下面。
                innerPadding = innerPadding,
                assistant = assistant,
                onUpdate = { vm.update(it) },
            )
        }
        return
    }

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = miuixTitle,
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantRequestContent(
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
internal fun AssistantRequestContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        HorizontalDivider()

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
