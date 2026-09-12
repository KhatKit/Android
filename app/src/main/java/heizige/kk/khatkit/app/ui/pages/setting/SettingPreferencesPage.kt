package heizige.kk.khatkit.app.ui.pages.setting

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
import heizige.kk.khatkit.app.ui.icons.language
import heizige.kk.khatkit.app.ui.icons.lightMode
import heizige.kk.khatkit.app.ui.icons.notifications
import heizige.kk.khatkit.app.ui.icons.palette
import heizige.kk.khatkit.app.ui.icons.settings

@Composable
fun SettingPreferencesPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_preferences),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        leadingContent = { Icon(lightMode, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_theme)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                        leadingContent = { Icon(notifications, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                        leadingContent = { Icon(settings, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_general)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_general_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                        leadingContent = { Icon(palette, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                        leadingContent = { Icon(language, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_network_desc)) },
                    )
                }
            }
        }
    }
}
