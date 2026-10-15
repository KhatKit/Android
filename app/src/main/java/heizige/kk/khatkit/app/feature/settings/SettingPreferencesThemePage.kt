package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khromia.components.AnimatedRadioButton
import heizige.kk.khromia.components.AnimatedRadioItem
import heizige.kk.khromia.components.ExpandableOptionItem
import heizige.kk.khromia.components.FancySlider
import heizige.kk.khromia.text.OptionsText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
import heizige.kk.khatkit.app.feature.settings.components.PresetThemeColorDots
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesThemePage(vm: SettingViewModel = hiltViewModel()) {
    // Miuix 走双文件（KernelSU SettingsMaterial/SettingsMiuix 架构）
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingPreferencesThemePageMiuix(vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val colorMode = heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode()
    // 界面风格 / 毛玻璃两项的落点是同一个 provider（`KhatKitToolProvider.uiStyle` /
    // `enableBlur`）。这里直接读它的 state，不做 remember 缓存，否则别处切风格后本页不同步。
    val khatKitProvider: heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider =
        rememberAppEntryPoint().khatKitToolProvider()
    val uiStyle = khatKitProvider.uiStyle
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
        containerColor = CustomColors.pageContainerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = PageMetrics.TopContentPadding,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
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
                heizige.kk.khatkit.app.feature.settings.components.ThemeColorSettingGroup(
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

            // 下面三组原先在「设置 → 偏好设置 → 界面」（`SettingPreferencesUIPage.kt`），
            // 按外观归属迁进主题偏好页：界面风格、毛玻璃、列表卡片样式。控件与持久化字段
            // 逐字未改 —— 风格/毛玻璃仍写 `KhatKitToolProvider`，卡片样式仍写
            // `Settings.listCardLargeCorner/listCardSmallCorner/listCardGap`。
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.greeting_settings_ui_style)) },
                ) {
                    KhatKitUiStyle.entries.forEach { style ->
                        item(
                            headlineContent = {
                                Text(if (style == KhatKitUiStyle.MATERIAL) "Material 3" else "Miuix")
                            },
                            trailingContent = {
                                AnimatedRadioButton(
                                    selected = uiStyle == style,
                                    onClick = null,
                                )
                            },
                            onClick = { khatKitProvider.uiStyle = style },
                        )
                    }
                }
            }

            item {
                // 毛玻璃开关：MD3 顶栏不做模糊，但该设置对 Miuix 生效，保留可改。
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_blur_title)) },
                        trailingContent = {
                            OptionSwitch(
                                checked = khatKitProvider.enableBlur,
                                onCheckedChange = { khatKitProvider.enableBlur = it },
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_list_card_settings)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_card_corner_large)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FancySlider(
                                    value = settings.listCardLargeCorner,
                                    onValueChange = {
                                        val large = it.roundToInt().toFloat()
                                        vm.updateSettings(
                                            settings.copy(
                                                listCardLargeCorner = large,
                                                listCardSmallCorner = settings.listCardSmallCorner
                                                    .coerceAtMost(large),
                                            )
                                        )
                                    },
                                    valueRange = 8f..40f,
                                    steps = 31,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = "${settings.listCardLargeCorner.roundToInt()}dp")
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_card_corner_small)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FancySlider(
                                    value = settings.listCardSmallCorner,
                                    onValueChange = {
                                        vm.updateSettings(
                                            settings.copy(listCardSmallCorner = it.roundToInt().toFloat())
                                        )
                                    },
                                    valueRange = 0f..settings.listCardLargeCorner.coerceAtLeast(1f),
                                    steps = (settings.listCardLargeCorner.roundToInt().coerceAtLeast(1) - 1)
                                        .coerceAtLeast(0),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = "${settings.listCardSmallCorner.roundToInt()}dp")
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_card_gap)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FancySlider(
                                    value = settings.listCardGap,
                                    onValueChange = {
                                        vm.updateSettings(
                                            settings.copy(listCardGap = it.roundToInt().toFloat())
                                        )
                                    },
                                    valueRange = 0f..16f,
                                    steps = 15,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = "${settings.listCardGap.roundToInt()}dp")
                            }
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
