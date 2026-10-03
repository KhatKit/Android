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
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics

/** 主题入口页的 Miuix 风格版，结构与 [SettingPreferencesPageMiuix] 一致。 */
@Composable
fun SettingThemesPageMiuix() {
    val navController = LocalNavController.current

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_themes),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_theme_manage),
                summary = stringResource(R.string.setting_page_theme_manage_desc),
                icon = palette,
                onClick = { navController.navigate(Screen.SettingTheme) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_theme_preferences),
                summary = stringResource(R.string.setting_page_preferences_theme_desc),
                icon = lightMode,
                onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
            )
        }
    }
}