package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.book4
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

@Composable
fun ExtensionsPage() {
    val navController = LocalNavController.current

    // Miuix 直接用 MiuixSettingsPage 骨架：整页只有一组入口，无需 MD3 的
    // Scaffold + CardGroup 两层。
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        MiuixSettingsPage(
            title = stringResource(R.string.extensions_page_title),
            navigationIcon = { BackButton() },
        ) {
            miuixGroup {
                PreferenceArrow(
                    title = stringResource(R.string.assistant_page_quick_messages),
                    summary = stringResource(R.string.extensions_page_quick_messages_desc),
                    icon = bolt,
                    onClick = { navController.navigate(Screen.QuickMessages) },
                )
                PreferenceArrow(
                    title = stringResource(R.string.extensions_page_prompts),
                    summary = stringResource(R.string.extensions_page_prompts_desc),
                    icon = book4,
                    onClick = { navController.navigate(Screen.Prompts) },
                )
                PreferenceArrow(
                    title = stringResource(R.string.extensions_page_agent_skills),
                    summary = stringResource(R.string.extensions_page_agent_skills_desc),
                    icon = extension,
                    onClick = { navController.navigate(Screen.Skills) },
                )
                PreferenceArrow(
                    title = stringResource(R.string.extensions_page_workspace),
                    summary = stringResource(R.string.extensions_page_workspace_desc),
                    icon = folder,
                    onClick = { navController.navigate(Screen.Workspaces) },
                )
            }
        }
        return
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.extensions_page_title),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.extensions_page_section_extensions)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.QuickMessages) },
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_quick_messages_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Prompts) },
                        leadingContent = { Icon(book4, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_prompts)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_prompts_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Skills) },
                        leadingContent = { Icon(extension, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_agent_skills)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_agent_skills_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = { Icon(folder, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_workspace)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_workspace_desc)) },
                    )
                }
            }
        }
    }
}
