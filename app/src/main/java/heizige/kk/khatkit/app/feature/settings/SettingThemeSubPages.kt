package heizige.kk.khatkit.app.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.sliderItem
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalKkThemeReveal
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import heizige.kk.khatkit.app.core.ui.theme.rememberColorModeChanger
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.effect.content.crossfadeOrChange
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.theme.MochiTheme

/**
 * 主题设置的三张二级页：配色、材质、列表卡片。
 *
 * 三页的顶栏与设置页统一（KedgeSettingsPageScaffold，左上返回和「设置 / 界面」是同一枚），
 * 正文是懒加载列表；分组走 [CardGroup]
 * （Mochi 分组卡片：26dp 圆角、iOS 26 行高与分隔线、按下整行灰底），行高变化用 Mochi
 * 同款尺寸弹簧。圆角与项间距读 listCardStyle()，所以「列表卡片」页的滑块在这三页和
 * 其它设置页上都能立刻看到效果。
 */

/** 二级页：配色（明暗模式 + 系统取色 + 主题色色板）。 */
@Composable
fun SettingThemeColorsPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val colorMode = heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode()
    val changeColorMode = rememberColorModeChanger(colorMode)
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 莫奈色板回显：预设主题取它的主色，自定义主题取保存的种子色；跟随壁纸取色时没有选中项
    val selectedSeedColor: Color? = if (settings.dynamicColor) {
        null
    } else {
        PresetThemes.firstOrNull { it.id == settings.themeId }?.standardLight?.primary
            ?: settings.customThemes.firstOrNull { it.id == settings.themeId }
                ?.let { Color(it.primaryColorArgb.toInt()) }
    }
    val presetSeeds = remember { PresetThemes.map { it.standardLight.primary }.toImmutableList() }
    val modeLabels = listOf(
        stringResource(R.string.greeting_settings_theme_system),
        stringResource(R.string.greeting_settings_theme_light),
        stringResource(R.string.greeting_settings_theme_dark),
    ).toImmutableList()

    KedgeSettingsPageScaffold(
        title = "配色",
        largeTitle = false,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
        item(key = "mode") {
            CardGroup(
                title = { Text("明暗") },
                footer = "明暗跟系统走时，系统换深色这套配色自己跟着换；固定成浅色或深色就不再跟系统。",
            ) {
                item(
                    headlineContent = { Text("模式") },
                    trailingContent = {
                        MochiSegmentedControl(
                            selectedIndex = ColorMode.entries.indexOf(colorMode.value).coerceAtLeast(0),
                            onSelect = { index -> changeColorMode(ColorMode.entries[index]) },
                            items = modeLabels,
                            behavior = ThemeToggleBehavior,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.greeting_settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.greeting_settings_dynamic_color_desc)) },
                    onClick = {
                        if (dynamicColorSupported) {
                            vm.updateSettings(settings.copy(dynamicColor = !settings.dynamicColor))
                        }
                    },
                    trailingContent = {
                        KkSwitch(
                            checked = dynamicColorSupported && settings.dynamicColor,
                            enabled = dynamicColorSupported,
                            onCheckedChange = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                        )
                    },
                )
            }
        }

        item(key = "seed") {
            CardGroup(
                title = { Text("主题色") },
                footer = "按 MD3 规则从种子色生成整套配色，色块上是生成的主色、辅助色和第三色。" +
                    "选了色块后，「系统取色」换成同样明暗的静态配色。",
            ) {
                item(
                    headlineContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (settings.dynamicColor) {
                                Text(
                                    text = "当前跟随壁纸取色，选下面的配色会自动关闭动态取色",
                                    style = MochiTheme.textStyles.footnote,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MochiSeedPalette(
                                selected = selectedSeedColor,
                                onSelect = { color -> applyThemeSeed(color, settings) { vm.updateSettings(it) } },
                                modifier = Modifier.fillMaxWidth(),
                                seeds = presetSeeds,
                                custom = true,
                            )
                        }
                    },
                )
            }
        }
        }
    }
}

/** 二级页：材质（玻璃、按钮样式）。 */
@Composable
fun SettingThemeMaterialsPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // 换外观（玻璃开关）照 Mochi demo 原位模糊交叉淡化
    val themeReveal = LocalKkThemeReveal.current
    val buttonStyleLabels = listOf(
        stringResource(R.string.setting_display_page_button_style_glass),
        stringResource(R.string.setting_display_page_button_style_solid),
    ).toImmutableList()

    KedgeSettingsPageScaffold(
        title = "材质",
        largeTitle = false,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
        item(key = "materials") {
            CardGroup(
                title = { Text("材质") },
                footer = "玻璃让栏、菜单、面板和圆形按钮透出背后的内容；按钮样式选圆形按钮是玻璃还是实心。",
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_effects_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_effects_desc)) },
                    onClick = {
                        themeReveal.crossfadeOrChange {
                            vm.updateSettings(
                                settings.copy(
                                    displaySetting = settings.displaySetting.copy(
                                        effectsEnabled = !settings.displaySetting.effectsEnabled,
                                    ),
                                )
                            )
                        }
                    },
                    trailingContent = {
                        KkSwitch(
                            checked = settings.displaySetting.effectsEnabled,
                            onCheckedChange = { on ->
                                themeReveal.crossfadeOrChange {
                                    vm.updateSettings(
                                        settings.copy(
                                            displaySetting = settings.displaySetting.copy(effectsEnabled = on),
                                        )
                                    )
                                }
                            },
                        )
                    },
                )
                // 按钮样式只在总开关开着时有意义：visible 走行级展开/收起动画，卡片高度跟着一起收放
                item(
                    visible = settings.displaySetting.effectsEnabled,
                    headlineContent = { Text(stringResource(R.string.setting_display_page_button_style_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_button_style_desc)) },
                    trailingContent = {
                        MochiSegmentedControl(
                            selectedIndex = if (settings.displaySetting.glassButtons) 0 else 1,
                            onSelect = { index ->
                                themeReveal.crossfadeOrChange {
                                    vm.updateSettings(
                                        settings.copy(
                                            displaySetting = settings.displaySetting.copy(
                                                glassButtons = index == 0,
                                            ),
                                        )
                                    )
                                }
                            },
                            items = buttonStyleLabels,
                            behavior = ThemeToggleBehavior,
                        )
                    },
                )
            }
        }
        }
    }
}

/** 二级页：列表卡片（圆角与卡片间距）。改的是全局 listCardStyle()，本页卡片和其它列表一起变。 */
@Composable
fun SettingThemeListCardsPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    KedgeSettingsPageScaffold(
        title = "列表卡片",
        largeTitle = false,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
        item(key = "listCards") {
            CardGroup(
                title = { Text("列表卡片") },
                footer = "调的是分组卡片的圆角与卡片之间的距离；改完立刻生效，所有列表一起变。",
            ) {
                sliderItem(
                    title = { stringResource(R.string.setting_display_page_card_corner_large) },
                    value = settings.listCardLargeCorner,
                    onValueChange = {
                        val large = it.roundToInt().toFloat()
                        vm.updateSettings(
                            settings.copy(
                                listCardLargeCorner = large,
                                listCardSmallCorner = settings.listCardSmallCorner.coerceAtMost(large),
                            )
                        )
                    },
                    valueRange = 8f..40f,
                    defaultValue = 26f,
                    steps = 31,
                    suffix = "dp",
                )
                sliderItem(
                    title = { stringResource(R.string.setting_display_page_card_corner_small) },
                    value = settings.listCardSmallCorner,
                    onValueChange = {
                        vm.updateSettings(
                            settings.copy(listCardSmallCorner = it.roundToInt().toFloat())
                        )
                    },
                    valueRange = 0f..settings.listCardLargeCorner.coerceAtLeast(1f),
                    defaultValue = 0f,
                    steps = (settings.listCardLargeCorner.roundToInt().coerceAtLeast(1) - 1).coerceAtLeast(0),
                    suffix = "dp",
                )
                sliderItem(
                    title = { stringResource(R.string.setting_display_page_card_gap) },
                    value = settings.listCardGap,
                    onValueChange = {
                        vm.updateSettings(settings.copy(listCardGap = it.roundToInt().toFloat()))
                    },
                    valueRange = 0f..16f,
                    defaultValue = 0f,
                    steps = 15,
                    suffix = "dp",
                )
            }
        }
        }
    }
}

/** 和 Mochi demo 的 Toggles 同一尺寸（高 32、段内左右 12）。 */
private val ThemeToggleBehavior = MochiSegmentedDefaults.behavior(height = 32.dp, segmentPadding = 12.dp)

/**
 * 从色板选一个种子色：预设色直接换成那套主题；色板外的颜色复用同种子的自定义主题，
 * 没有就建一个。两种情况都关掉「系统取色」，否则选了不生效。
 */
private fun applyThemeSeed(color: Color, settings: Settings, update: (Settings) -> Unit) {
    val preset = PresetThemes.firstOrNull { it.standardLight.primary == color }
    if (preset != null) {
        update(settings.copy(dynamicColor = false, themeId = preset.id))
        return
    }
    val argb = color.toArgb().toLong() and 0xFFFFFFFFL
    val existing = settings.customThemes.firstOrNull { it.primaryColorArgb == argb }
    if (existing != null) {
        update(settings.copy(dynamicColor = false, themeId = existing.id))
        return
    }
    val custom = CustomTheme(
        name = String.format("#%08X", color.toArgb()),
        primaryColorArgb = argb,
    )
    update(
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
}
