package heizige.kk.khatkit.app.ui.pages.setting

import heizige.kk.khromia.components.AnimatedRadioItem
import heizige.kk.khromia.components.ExpandableOptionItem
import heizige.kk.khromia.text.OptionsText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import heizige.kk.khromia.components.OptionSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.ui.pages.setting.components.PresetThemeColorDots
import heizige.kk.khatkit.app.ui.pages.setting.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SettingPreferencesThemePage(vm: SettingVM = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val colorMode = heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCustomColor by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetThemes.first().standardLight.primary) }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_preferences_theme),
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
                val selectedColorModeText = when (colorMode.value) {
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.SYSTEM -> stringResource(R.string.greeting_settings_theme_system)
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.LIGHT -> stringResource(R.string.greeting_settings_theme_light)
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.DARK -> stringResource(R.string.greeting_settings_theme_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.greeting_settings_theme_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                        trailingContent = {
                            Select(
                                options = heizige.kk.khatkit.app.core.ui.theme.ColorMode.entries,
                                selectedOption = colorMode.value,
                                onOptionSelected = { colorMode.value = it },
                                optionToString = { mode ->
                                    stringResource(
                                        when (mode) {
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.SYSTEM -> R.string.greeting_settings_theme_system
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.LIGHT -> R.string.greeting_settings_theme_light
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.DARK -> R.string.greeting_settings_theme_dark
                                        }
                                    )
                                },
                                modifier = Modifier.width(150.dp),
                            )
                        },
                    )
                }
            }

            item {
                heizige.kk.khatkit.app.ui.pages.setting.components.ThemeColorSettingGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    dynamicColor = settings.dynamicColor,
                    themeId = settings.themeId,
                    onUpdateDynamicColor = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                    onSelectTheme = { vm.updateSettings(settings.copy(themeId = it)) },
                    onCustomColorClick = { showCustomColor = true },
                )
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
                        trailingContent = {
                            OptionSwitch(
                                checked = amoledDarkMode,
                                onCheckedChange = { amoledDarkMode = it }
                            )
                        },
                    )
                }
            }
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
