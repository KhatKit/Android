package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.feature.settings.components.CodeDisplaySettingsGroupMiuix

/**
 * 「代码块」二级页的 Miuix 风格版。
 *
 * 由原「界面偏好设置」页（`SettingPreferencesUIPageMiuix.kt`）的第三个 miuixGroup 拆出：
 * 自动换行 / 自动折叠 / 显示行号，仍复用 [CodeDisplaySettingsGroupMiuix]（与 MD3 侧同键同义）。
 */
@Composable
fun SettingPreferencesUICodePageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui_code),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            CodeDisplaySettingsGroupMiuix(
                codeBlockAutoWrap = displaySetting.codeBlockAutoWrap,
                codeBlockAutoCollapse = displaySetting.codeBlockAutoCollapse,
                showLineNumbers = displaySetting.showLineNumbers,
                onUpdateCodeBlockAutoWrap = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                },
                onUpdateCodeBlockAutoCollapse = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                },
                onUpdateShowLineNumbers = {
                    updateDisplaySetting(displaySetting.copy(showLineNumbers = it))
                },
            )
        }
    }
}
