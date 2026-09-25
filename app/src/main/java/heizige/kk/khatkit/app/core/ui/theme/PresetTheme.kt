package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import heizige.kk.khatkit.app.core.ui.theme.presets.AutumnThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.BlackThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.ClaudeThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.MinimalThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.OceanThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.SakuraThemePreset
import heizige.kk.khatkit.app.core.ui.theme.presets.SpringThemePreset

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

private fun kodeHeadSeedPreset(id: String, name: String, argb: Long): PresetTheme {
    val theme = CustomTheme(id = id, name = name, primaryColorArgb = argb)
    return PresetTheme(
        id = id,
        name = { androidx.compose.material3.Text(name) },
        standardLight = theme.generateColorScheme(dark = false),
        standardDark = theme.generateColorScheme(dark = true),
    )
}

val PresetThemes by lazy {
    listOf(
        // KodeHead 同款种子色（默认第一项 = 紫色）
        kodeHeadSeedPreset("kode_purple", "Purple", 0xFF6750A4),
        kodeHeadSeedPreset("kode_teal", "Teal", 0xFF006A60),
        kodeHeadSeedPreset("kode_rose", "Rose", 0xFF984061),
        kodeHeadSeedPreset("kode_earth", "Earth", 0xFF6B5E40),
        kodeHeadSeedPreset("kode_blue", "Blue", 0xFF005AC1),
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
        MinimalThemePreset,
        ClaudeThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme {
    return PresetThemes.find { it.id == id } ?: SakuraThemePreset
}

fun findThemeById(id: String, customThemes: List<CustomTheme>): PresetTheme? {
    PresetThemes.find { it.id == id }?.let { return it }
    val custom = customThemes.find { it.id == id } ?: return null
    return PresetTheme(
        id = custom.id,
        name = { androidx.compose.material3.Text(custom.name) },
        standardLight = custom.generateColorScheme(dark = false),
        standardDark = custom.generateColorScheme(dark = true),
    )
}
