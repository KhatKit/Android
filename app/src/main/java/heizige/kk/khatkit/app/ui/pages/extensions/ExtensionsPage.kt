package heizige.kk.khatkit.app.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
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
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.ui.context.LocalNavController
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.utils.plus
import heizige.kk.khatkit.app.ui.icons.bolt
import heizige.kk.khatkit.app.ui.icons.book4
import heizige.kk.khatkit.app.ui.icons.extension
import heizige.kk.khatkit.app.ui.icons.folder

@Composable
fun ExtensionsPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.extensions_page_title),
                navigationIcon = { BackButton() },
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
