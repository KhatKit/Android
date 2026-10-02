package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.feature.settings.components.ThemeColorSettingGroupMiuix
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.ui.theme.ColorMode as UiColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 主题偏好页的 Miuix 风格版（KernelSU 双文件架构）。
 */
@Composable
fun SettingPreferencesThemePageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val colorMode = rememberColorMode()
    var showCustomColor by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetThemes.first().standardLight.primary) }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_theme),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceDropdown(
                title = stringResource(R.string.greeting_settings_theme_mode),
                summary = colorModeLabel(colorMode.value),
                items = UiColorMode.entries.map { colorModeLabel(it) },
                selectedIndex = UiColorMode.entries.indexOf(colorMode.value),
                onSelectedIndexChange = { colorMode.value = UiColorMode.entries[it] },
            )
        }
        miuixGroup {
            ThemeColorSettingGroupMiuix(
                dynamicColor = settings.dynamicColor,
                themeId = settings.themeId,
                onUpdateDynamicColor = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                onSelectTheme = { vm.updateSettings(settings.copy(themeId = it)) },
                onCustomColorClick = { showCustomColor = true },
            )
        }
        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_amoled_dark_mode_title),
                summary = stringResource(R.string.setting_display_page_amoled_dark_mode_desc),
                checked = amoledDarkMode,
                onCheckedChange = { amoledDarkMode = it },
            )
        }
    }

    ThemeCustomColorSheet(
        visible = showCustomColor,
        initialColor = customColor,
        history = settings.customColorHistory,
        onDismiss = { showCustomColor = false },
        onColorChanged = { customColor = it },
        onConfirm = { color ->
            val argb = color.toArgb().toLong() and 0xFFFFFFFFL
            val custom = CustomTheme(
                name = String.format("#%08X", color.toArgb()),
                primaryColorArgb = argb,
            )
            vm.updateSettings(
                settings.copy(
                    dynamicColor = false,
                    customThemes = settings.customThemes + custom,
                    themeId = custom.id,
                    customColorHistory = (
                        listOf(argb) +
                            settings.customColorHistory.filterNot { it == argb }
                        ).take(8),
                )
            )
            showCustomColor = false
        },
    )
}

@Composable
private fun colorModeLabel(mode: UiColorMode): String = stringResource(
    when (mode) {
        UiColorMode.SYSTEM -> R.string.greeting_settings_theme_system
        UiColorMode.LIGHT -> R.string.greeting_settings_theme_light
        UiColorMode.DARK -> R.string.greeting_settings_theme_dark
    }
)
