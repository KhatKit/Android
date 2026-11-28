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
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
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
import ui.feice.mochi.theme.MochiTopBarStyle
import ui.feice.mochi.theme.PaletteStyle

private val paletteOptions = listOf(
    PaletteStyle.TonalSpot to "Tonal",
    PaletteStyle.Expressive to "Expressive",
    PaletteStyle.Vibrant to "Vibrant",
    PaletteStyle.Neutral to "Neutral",
    PaletteStyle.Content to "Content",
    PaletteStyle.Fidelity to "Fidelity",
)

private val topBarOptions = listOf(
    MochiTopBarStyle.Frosted to "毛玻璃",
    MochiTopBarStyle.GradientBlur to "渐变模糊",
    MochiTopBarStyle.SolidFade to "实色渐隐",
    MochiTopBarStyle.TranslucentFade to "半透明渐隐",
)

private val glassOptions = listOf(
    MochiGlassStyle.Regular to "标准折射",
    MochiGlassStyle.Frosted to "毛玻璃",
    MochiGlassStyle.Thick to "厚实折射",
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
        title = "配色",
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
            CardGroup(
                title = { Text("主题色") },
                footer = "按 MD3 规则从种子色生成整套配色，色块上是生成的主色、辅助色和第三色。" +
                    "选了色块后，「系统取色」换成同样明暗的静态配色。",
            ) {
                mochiThemePicker(
                    title = "取色算法",
                    options = paletteOptions,
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
        title = "材质",
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
            CardGroup(
                title = { Text("材质") },
                footer = "液态玻璃作用于导航栏、菜单和面板。顶栏样式决定内容滚到顶栏下方时的效果。",
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
                    title = "玻璃样式",
                    options = glassOptions,
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
                mochiThemePicker(
                    title = "顶栏样式",
                    options = topBarOptions,
                    selected = display.mochiTopBarStyle,
                ) { style ->
                    themeReveal.crossfadeOrChange {
                        vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(mochiTopBarStyle = style)) }
                    }
                }
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
        title = "列表卡片",
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
                    suffix = "dp",
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
                    suffix = "dp",
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
        "图标 + 文字",
        "仅选中项文字",
        "仅图标",
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
                title = { Text("底部导航栏") },
                footer = "标签样式跟 Mochi 标签栏的三种排法（默认「图标 + 文字」）。「仅选中项文字」和「仅图标」会把底栏换成 52dp 的图标栏；" +
                    "大屏（600–840dp）的左侧导航轨同步隐藏未选中项的文字。",
            ) {
                // 三档标签较长，放行尾会把标题列挤成竖排；改成标题 + 全宽分段控件
                item(
                    headlineContent = { Text("导航栏样式") },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "底部导航栏显示哪些文字",
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
                    headlineContent = { Text("滚动时收起") },
                    supportingContent = { Text("内容往下滚时缩成只剩当前图标的小胶囊，往上滚或点它展开") },
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
            CardGroup(
                title = { Text("栏玻璃") },
                footer = "往左更透明，往右加重玻璃底色。玻璃样式可在「材质」中选择。",
            ) {
                sliderItem(
                    title = { "通透度" },
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
                    suffix = "%",
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
