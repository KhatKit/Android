package heizige.kk.khatkit.app.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.data.model.Assistant
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.ui.context.LocalNavController
import heizige.kk.khatkit.app.ui.hooks.heroAnimation
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.utils.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.ui.icons.arrowForward
import heizige.kk.khatkit.app.ui.icons.code
import heizige.kk.khatkit.app.ui.icons.construction
import heizige.kk.khatkit.app.ui.icons.extension
import heizige.kk.khatkit.app.ui.icons.menuBook
import heizige.kk.khatkit.app.ui.icons.psychology
import heizige.kk.khatkit.app.ui.icons.settings
import heizige.kk.khatkit.app.ui.icons.sms

@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailVM = hiltViewModel<AssistantDetailVM, AssistantDetailVM.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = assistant.name.ifBlank {
                            stringResource(R.string.assistant_page_default_assistant)
                        },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AssistantHeader(
                    assistant = assistant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.AssistantBasic(id)) },
                        leadingContent = { Icon(settings, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_basic_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_basic)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantPrompt(id)) },
                        leadingContent = { Icon(sms, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_prompt_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_prompt)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantInjections(id)) },
                        leadingContent = { Icon(extension, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_extensions)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMemory(id)) },
                        leadingContent = { Icon(psychology, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_memory_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantRequest(id)) },
                        leadingContent = { Icon(code, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_request_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_request)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMcp(id)) },
                        leadingContent = { Icon(construction, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_mcp)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantLocalTool(id)) },
                        leadingContent = { Icon(menuBook, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_local_tools_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_local_tools)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantHeader(
    assistant: Assistant,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UIAvatar(
            value = assistant.avatar,
            name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            onUpdate = null,
            modifier = Modifier
                .size(100.dp)
                .heroAnimation("assistant_${assistant.id}")
        )

        Text(
            text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (assistant.systemPrompt.isNotBlank()) {
            Text(
                text = assistant.systemPrompt.take(100) + if (assistant.systemPrompt.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
