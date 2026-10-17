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
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.chatBubble
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.title
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus

/**
 * 「界面偏好设置」二级页（MD3 风格）。
 *
 * 设置主页首组只留一行「界面偏好设置」（见 `SettingPage.kt`），点进来就是这一页；
 * 页内四个子项各自再进自己的三级页，顺序与设置主页原先平铺时一致：
 * 主题 / 消息显示 / 字体 / 代码块。
 *
 * 页内没有任何开关，纯导航，故不取 `SettingViewModel`。
 */
@Composable
fun SettingPreferencesUIPage() {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingPreferencesUIPageMiuix()
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    KedgeSettingsPageScaffold(
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
                        leadingContent = { Icon(dashboard, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_themes_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_themes)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUIMessage) },
                        leadingContent = { Icon(chatBubble, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_message_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_message)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUIFont) },
                        leadingContent = { Icon(title, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_font_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_font)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUICode) },
                        leadingContent = { Icon(code, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_code_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui_code)) },
                    )
                }
            }
        }
    }
}
