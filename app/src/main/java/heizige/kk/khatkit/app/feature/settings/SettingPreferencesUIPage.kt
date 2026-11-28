package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.chatBubble
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.title
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import ui.feice.mochi.icons.MochiIonicon

/**
 * 「界面偏好设置」入口页，分流到主题、导航栏、消息显示、字体和代码块。
 */
@Composable
fun SettingPreferencesUIPage() {
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_ui),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("uiPreferences") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        chevron = true,
                        leadingContent = { Icon(dashboard, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_themes_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_themes)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingThemeNavBar) },
                        chevron = true,
                        leadingContent = { MochiIonicon("compass") },
                        supportingContent = { Text(stringResource(R.string.setting_preferences_navigation_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_preferences_navigation_title)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUIMessage) },
                        chevron = true,
                        leadingContent = { Icon(chatBubble, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_message_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_message)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUIFont) },
                        chevron = true,
                        leadingContent = { Icon(title, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_font_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_font)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUICode) },
                        chevron = true,
                        leadingContent = { Icon(code, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_code_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_code)) },
                    )
                }
            }
        }
    }
}
