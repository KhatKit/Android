package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.hooks.heroAnimation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.construction
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.menuBook
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.sms
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.theme.MochiTheme

/**
 * 角色详情：头像 + 名字 + 人格摘要，下面一整张 Mochi 分组卡片列出七个子页入口。
 *
 * 骨架是 [MochiLargeTitleScaffold]，每行是 [MochiListRow]（图标 + 标题 + 副标题 + ›），
 * 点进去分别是基本 / 提示词 / 扩展 / 记忆 / 请求参数 / MCP / 本地工具。
 */
@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    MochiLargeTitleScaffold(
        title = assistant.name.ifBlank {
            stringResource(R.string.assistant_page_default_assistant)
        },
        navigationIcon = { BackButton() },
    ) {
        AssistantHeader(
            assistant = assistant,
            providerName = providers
                .firstOrNull { provider -> provider.models.any { it.id == assistant.chatModelId } }
                ?.name,
        )

        MochiListSection {
            AssistantDetailEntry(
                icon = { EntryIcon(settings) },
                title = stringResource(R.string.assistant_page_tab_basic),
                subtitle = stringResource(R.string.assistant_detail_basic_desc),
                onClick = { navController.navigate(Screen.AssistantBasic(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(sms) },
                title = stringResource(R.string.assistant_page_tab_prompt),
                subtitle = stringResource(R.string.assistant_detail_prompt_desc),
                onClick = { navController.navigate(Screen.AssistantPrompt(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(extension) },
                title = stringResource(R.string.assistant_page_tab_extensions),
                subtitle = stringResource(R.string.assistant_detail_extensions_desc),
                onClick = { navController.navigate(Screen.AssistantInjections(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(psychology) },
                title = stringResource(R.string.assistant_page_tab_memory),
                subtitle = stringResource(R.string.assistant_detail_memory_desc),
                onClick = { navController.navigate(Screen.AssistantMemory(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(code) },
                title = stringResource(R.string.assistant_page_tab_request),
                subtitle = stringResource(R.string.assistant_detail_request_desc),
                onClick = { navController.navigate(Screen.AssistantRequest(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(construction) },
                title = stringResource(R.string.assistant_page_tab_mcp),
                subtitle = stringResource(R.string.assistant_detail_mcp_desc),
                onClick = { navController.navigate(Screen.AssistantMcp(id)) },
            )
            AssistantDetailEntry(
                icon = { EntryIcon(menuBook) },
                title = stringResource(R.string.assistant_page_tab_local_tools),
                subtitle = stringResource(R.string.assistant_detail_local_tools_desc),
                onClick = { navController.navigate(Screen.AssistantLocalTool(id)) },
            )
        }
    }
}

@Composable
private fun EntryIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MochiTheme.colorScheme.primary,
    )
}

@Composable
private fun AssistantDetailEntry(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        leading = icon,
        chevron = true,
        onClick = onClick,
    )
}

@Composable
private fun AssistantHeader(
    assistant: Assistant,
    providerName: String?,
) {
    val cs = MochiTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UIAvatar(
            value = assistant.avatar,
            name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            onUpdate = null,
            avatarShape = assistant.avatarShape,
            defaultIconName = providerName,
            avatarColor = assistant.avatarColor,
            modifier = Modifier
                .size(100.dp)
                .heroAnimation("assistant_${assistant.id}"),
        )

        Text(
            text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            style = MochiTheme.textStyles.titleLarge,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (assistant.systemPrompt.isNotBlank()) {
            Text(
                text = assistant.systemPrompt.take(100) + if (assistant.systemPrompt.length > 100) "..." else "",
                style = MochiTheme.textStyles.subhead,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
