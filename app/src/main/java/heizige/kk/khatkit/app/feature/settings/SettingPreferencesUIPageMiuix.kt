package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.chatBubble
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.title
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics

/**
 * 「界面偏好设置」二级页（Miuix 风格）。
 *
 * 与 MD3 侧 [SettingPreferencesUIPage] 同义同序：设置主页只留一行入口，
 * 这一页列出 主题 / 消息显示 / 字体 / 代码块 四个子项，各自再进三级页。
 */
@Composable
fun SettingPreferencesUIPageMiuix() {
    val navController = LocalNavController.current

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_themes),
                summary = stringResource(R.string.setting_page_themes_desc),
                icon = dashboard,
                onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_ui_message),
                summary = stringResource(R.string.setting_page_preferences_ui_message_desc),
                icon = chatBubble,
                onClick = { navController.navigate(Screen.SettingPreferencesUIMessage) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_ui_font),
                summary = stringResource(R.string.setting_page_preferences_ui_font_desc),
                icon = title,
                onClick = { navController.navigate(Screen.SettingPreferencesUIFont) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_ui_code),
                summary = stringResource(R.string.setting_page_preferences_ui_code_desc),
                icon = code,
                onClick = { navController.navigate(Screen.SettingPreferencesUICode) },
            )
        }
    }
}
