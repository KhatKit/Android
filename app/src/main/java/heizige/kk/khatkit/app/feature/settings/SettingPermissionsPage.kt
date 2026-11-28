package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklistSection
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults

/** 权限按常规、系统和高级访问分组切换；授权动作仍由共享清单处理。 */
@Composable
fun SettingPermissionsPage() {
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val sectionLabels = listOf(
        stringResource(R.string.setting_permissions_category_basic),
        stringResource(R.string.setting_permissions_category_system),
        stringResource(R.string.setting_permissions_category_advanced),
    ).toImmutableList()
    val selectedSection = PermissionChecklistSection.entries[selectedSectionIndex]

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_permissions),
        navigationIcon = { BackButton() },
        largeTitle = false,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                bottom = MochiTabScaffoldDefaults.contentBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            item(key = "permission-sections") {
                MochiSegmentedControl(
                    selectedIndex = selectedSectionIndex,
                    onSelect = { selectedSectionIndex = it.coerceIn(0, sectionLabels.lastIndex) },
                    items = sectionLabels,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "permission-checklist") {
                MochiBlurCrossfade(
                    targetState = selectedSection,
                    modifier = Modifier.fillMaxWidth(),
                ) { section ->
                    PermissionChecklist(
                        modifier = Modifier.fillMaxWidth(),
                        compact = true,
                        section = section,
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }
    }
}
