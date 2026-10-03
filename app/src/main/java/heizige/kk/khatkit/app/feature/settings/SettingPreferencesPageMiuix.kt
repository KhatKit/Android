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
import heizige.kk.khatkit.app.core.ui.icons.language
import heizige.kk.khatkit.app.core.ui.icons.notifications
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics

/**
 * 偏好设置首页的 Miuix 风格版（对照 KernelSU `SettingsMiuix.kt`：
 * Card 分组 + startAction 图标 + 12dp 边距）。
 *
 * 「主题」原本在这里，现在移到设置主页的 [Screen.SettingThemes] hub 下面去了。
 */
@Composable
fun SettingPreferencesPageMiuix() {
    val navController = LocalNavController.current

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_notification),
                summary = stringResource(R.string.setting_page_preferences_notification_desc),
                icon = notifications,
                onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_general),
                summary = stringResource(R.string.setting_page_preferences_general_desc),
                icon = settings,
                onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_ui),
                summary = stringResource(R.string.setting_page_preferences_ui_desc),
                icon = palette,
                onClick = { navController.navigate(Screen.SettingPreferencesUI) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences_network),
                summary = stringResource(R.string.setting_page_preferences_network_desc),
                icon = language,
                onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
            )
        }
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_permissions),
                summary = stringResource(R.string.setting_page_permissions_desc),
                icon = verifiedUser,
                onClick = { navController.navigate(Screen.SettingPermissions) },
            )
        }
    }
}
