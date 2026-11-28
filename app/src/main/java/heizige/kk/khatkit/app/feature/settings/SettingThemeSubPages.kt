package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroupScope
import heizige.kk.khatkit.app.core.ui.icons.navigation.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.components.ui.sliderItem
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalKkThemeReveal
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.ui.theme.resolvedMochiGlassStyle
import heizige.kk.khatkit.app.core.ui.theme.usesMochiSeedPalette
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import heizige.kk.khatkit.app.core.ui.theme.rememberColorModeChanger
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.NavBarLabelStyle
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.basic.MochiAlign
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.effect.content.crossfadeOrChange
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.MochiGlassStyle
import ui.feice.mochi.theme.PaletteStyle

private val paletteOptions = listOf(
    PaletteStyle.TonalSpot to R.string.setting_theme_palette_tonal,
    PaletteStyle.Expressive to R.string.setting_theme_palette_expressive,
    PaletteStyle.Vibrant to R.string.setting_theme_palette_vibrant,
    PaletteStyle.Neutral to R.string.setting_theme_palette_neutral,
    PaletteStyle.Content to R.string.setting_theme_palette_content,
    PaletteStyle.Fidelity to R.string.setting_theme_palette_fidelity,
)

private val glassOptions = listOf(
    MochiGlassStyle.Regular to R.string.setting_theme_glass_regular,
    MochiGlassStyle.Frosted to R.string.setting_theme_glass_frosted,
    MochiGlassStyle.Thick to R.string.setting_theme_glass_thick,
)

internal fun <T> CardGroupScope.mochiThemePicker(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    visible: Boolean = true,
    anchor: String? = null,
    optionIcon: (@Composable (T, Color) -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    item(
        visible = visible,
        anchor = anchor,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            var expanded by remember { mutableStateOf(false) }
            val entries = remember(options, optionIcon) {
                options.map { (value, label) ->
                    MochiItem(
                        label = label,
                        icon = optionIcon?.let { icon -> { tint -> icon(value, tint) } },
                    )
                }.toImmutableList()
            }
            val index = options.indexOfFirst { it.first == selected }
            MochiDropdown(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                items = entries,
                onItemClick = { onSelect(options[it].first) },
                selectedIndex = index,
                modifier = Modifier.widthIn(max = 160.dp),
                behavior = MochiDropdownDefaults.behavior(align = MochiAlign.End, liquidCover = false),
                buttonMinHeight = 34.dp,
                buttonPadding = PaddingValues(horizontal = 12.dp),
            ) {
                ui.feice.mochi.basic.Text(
                    text = options.getOrNull(index)?.second.orEmpty(),
                    style = MochiTheme.textStyles.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(keyboardArrowDown, contentDescription = null)
            }
        },
    )
}

internal fun CardGroupScope.mochiThemeSwitch(
    title: String,
    checked: Boolean,
    description: String? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    anchor: String? = null,
    onChange: (Boolean) -> Unit,
) {
    item(
        visible = visible,
        anchor = anchor,
        headlineContent = { Text(title) },
        supportingContent = description?.let { label -> { Text(label) } },
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailingContent = { KkSwitch(checked = checked, enabled = enabled, onCheckedChange = onChange) },
    )
}

/**
 * 主题设置的三张二级页：配色、材质、列表卡片。
 *
 * 三页的顶栏与设置页统一（MochiSettingsPageScaffold，左上返回和「设置 / 界面」是同一枚），
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
    val useSeedPalette = settings.displaySetting.usesMochiSeedPalette
    val scroll = rememberLazyListState()

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

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_theme_colors),
        scrollUnderTopBar = true,
    ) { innerPadding ->
        LazyColumn(
            state = scroll,
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
                title = { Text(stringResource(R.string.setting_theme_mode_group)) },
                footer = stringResource(R.string.setting_theme_mode_desc),
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_theme_mode)) },
                    trailingContent = {
                        MochiSegmentedControl(
                            selectedIndex = ColorMode.entries.indexOf(colorMode.value).coerceAtLeast(0),
                            onSelect = { index -> changeColorMode(ColorMode.entries[index]) },
                            items = modeLabels,
                            behavior = ThemeToggleBehavior,
                        )
                    },
                    anchor = settingAnchor(R.string.setting_display_page_button_style_title),
                )
                item(
                    visible = useSeedPalette,
                    headlineContent = { Text(stringResource(R.string.greeting_settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.greeting_settings_dynamic_color_desc)) },
                    onClick = {
                        if (dynamicColorSupported) {
                            vm.updateSettings { it.copy(dynamicColor = !it.dynamicColor) }
                        }
                    },
                    trailingContent = {
                        KkSwitch(
                            checked = dynamicColorSupported && settings.dynamicColor,
                            enabled = dynamicColorSupported,
                            onCheckedChange = { enabled -> vm.updateSettings { it.copy(dynamicColor = enabled) } },
                        )
                    },
                    anchor = settingAnchor(R.string.greeting_settings_dynamic_color),
                )
            }
        }

        if (useSeedPalette) item(key = "seed") {
            val algorithmTitle = stringResource(R.string.setting_theme_algorithm)
            val localizedPaletteOptions = paletteOptions.map { (value, label) -> value to stringResource(label) }
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_seed)) },
                footer = stringResource(R.string.setting_theme_seed_footer),
            ) {
                mochiThemePicker(
                    title = algorithmTitle,
                    options = localizedPaletteOptions,
                    selected = settings.displaySetting.mochiPaletteStyle,
                ) { style ->
                    vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(mochiPaletteStyle = style)) }
                }
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
                                    text = stringResource(R.string.setting_theme_dynamic_hint),
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
                                style = settings.displaySetting.mochiPaletteStyle,
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
    val display = settings.displaySetting
    val scroll = rememberLazyListState()
    // 换外观（玻璃开关）照 Mochi demo 原位模糊交叉淡化
    val themeReveal = LocalKkThemeReveal.current
    val buttonStyleLabels = listOf(
        stringResource(R.string.setting_display_page_button_style_glass),
        stringResource(R.string.setting_display_page_button_style_solid),
    ).toImmutableList()

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_theme_materials),
        scrollUnderTopBar = true,
    ) { innerPadding ->
        LazyColumn(
            state = scroll,
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
            val glassStyleTitle = stringResource(R.string.setting_theme_glass_style)
            val localizedGlassOptions = glassOptions.map { (value, label) -> value to stringResource(label) }
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_materials)) },
                footer = stringResource(R.string.setting_theme_materials_footer),
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_effects_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_effects_desc)) },
                    onClick = {
                        themeReveal.crossfadeOrChange {
                            vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(effectsEnabled = !it.displaySetting.effectsEnabled)) }
                        }
                    },
                    trailingContent = {
                        KkSwitch(
                            checked = settings.displaySetting.effectsEnabled,
                            onCheckedChange = { on ->
                                themeReveal.crossfadeOrChange {
                                    vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(effectsEnabled = on)) }
                                }
                            },
                        )
                    },
                    anchor = settingAnchor(R.string.setting_display_page_effects_title),
                )
                mochiThemePicker(
                    title = glassStyleTitle,
                    options = localizedGlassOptions,
                    selected = display.resolvedMochiGlassStyle,
                    visible = display.effectsEnabled,
                ) { style ->
                    themeReveal.crossfadeOrChange {
                        vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(mochiGlassStyle = style)) }
                    }
                }
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
                                    vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(glassButtons = index == 0)) }
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

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_theme_list_cards),
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
            val dpSuffix = stringResource(R.string.setting_unit_dp)
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_list_cards)) },
                footer = stringResource(R.string.setting_theme_cards_footer),
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
                    suffix = dpSuffix,
                    anchor = settingAnchor(R.string.setting_display_page_card_corner_large),
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
                    suffix = dpSuffix,
                    anchor = settingAnchor(R.string.setting_display_page_card_corner_small),
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
                    suffix = dpSuffix,
                    anchor = settingAnchor(R.string.setting_display_page_card_gap),
                )
            }
        }
        }
    }
}

/** 导航手势、标签栏和栏玻璃设置。 */
@Composable
fun SettingThemeNavBarPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val display = settings.displaySetting
    val predictiveBackTitle = stringResource(R.string.setting_preferences_predictive_back_title)
    val predictiveBackDescription = stringResource(R.string.setting_preferences_predictive_back_desc)
    val labelStyleLabels = listOf(
        stringResource(R.string.setting_theme_nav_icon_text),
        stringResource(R.string.setting_theme_nav_selected),
        stringResource(R.string.setting_theme_nav_icons),
    ).toImmutableList()

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_preferences_navigation_title),
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
        item(key = "gestures") {
            CardGroup(title = { Text(stringResource(R.string.setting_preferences_navigation_gestures)) }) {
                mochiThemeSwitch(
                    title = predictiveBackTitle,
                    description = predictiveBackDescription,
                    checked = display.mochiPredictiveBack,
                    anchor = settingAnchor(R.string.setting_preferences_predictive_back_title),
                ) { enabled ->
                    vm.updateSettings { current ->
                        current.copy(displaySetting = current.displaySetting.copy(mochiPredictiveBack = enabled))
                    }
                }
            }
        }
        item(key = "navBarStyle") {
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_navbar)) },
                footer = stringResource(R.string.setting_theme_navbar_footer),
            ) {
                // 三档标签较长，放行尾会把标题列挤成竖排；改成标题 + 全宽分段控件
                item(
                    headlineContent = { Text(stringResource(R.string.setting_theme_navbar_style)) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.setting_theme_navbar_desc),
                                style = MochiTheme.textStyles.footnote,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                            MochiSegmentedControl(
                                selectedIndex = NavBarLabelStyle.entries.indexOf(display.navBarLabelStyle).coerceAtLeast(0),
                                onSelect = { index ->
                                    vm.updateSettings(
                                        settings.copy(
                                            displaySetting = display.copy(
                                                navBarLabelStyle = NavBarLabelStyle.entries[index],
                                            ),
                                        )
                                    )
                                },
                                items = labelStyleLabels,
                                modifier = Modifier.fillMaxWidth(),
                                behavior = ThemeToggleBehavior,
                            )
                        }
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_theme_navbar_collapse)) },
                    supportingContent = { Text(stringResource(R.string.setting_theme_navbar_collapse_desc)) },
                    onClick = {
                        vm.updateSettings(
                            settings.copy(
                                displaySetting = display.copy(
                                    navBarCollapseOnScroll = !display.navBarCollapseOnScroll,
                                ),
                            )
                        )
                    },
                    trailingContent = {
                        KkSwitch(
                            checked = display.navBarCollapseOnScroll,
                            onCheckedChange = { on ->
                                vm.updateSettings(
                                    settings.copy(
                                        displaySetting = display.copy(navBarCollapseOnScroll = on),
                                    )
                                )
                            },
                        )
                    },
                )
            }
        }
        if (display.effectsEnabled) item(key = "barGlass") {
            val percentSuffix = stringResource(R.string.setting_unit_percent)
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_bar_glass)) },
                footer = stringResource(R.string.setting_theme_bar_glass_desc),
            ) {
                sliderItem(
                    title = { stringResource(R.string.setting_theme_transparency) },
                    value = display.glassTint,
                    onValueChange = { value ->
                        vm.updateSettings {
                            it.copy(displaySetting = it.displaySetting.copy(glassTint = (value * 100f).roundToInt() / 100f))
                        }
                    },
                    valueRange = 0f..1f,
                    defaultValue = 0.5f,
                    steps = 19,
                    displayScale = 100f,
                    suffix = percentSuffix,
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
