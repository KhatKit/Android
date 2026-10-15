package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus

@Composable
fun SettingPreferencesUICodePage(vm: SettingViewModel = hiltViewModel()) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingPreferencesUICodePageMiuix(vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_preferences_ui_code),
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
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_code_block_auto_wrap_title,
                            null,
                            checked = displaySetting.codeBlockAutoWrap,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_code_block_auto_collapse_title,
                            null,
                            checked = displaySetting.codeBlockAutoCollapse,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_line_numbers_title,
                            null,
                            checked = displaySetting.showLineNumbers,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showLineNumbers = it)) },
                        )
                    )
                }
            }
        }
    }
}
