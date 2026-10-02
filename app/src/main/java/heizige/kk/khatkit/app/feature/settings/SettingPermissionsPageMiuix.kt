package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics

/**
 * 权限页的 Miuix 风格版。
 *
 * [PermissionChecklist] 内部是风格无关的（Khromia 组件），只换外层壳；
 * compact = true 让滚动交给 [MiuixSettingsPage] 的列表容器。
 */
@Composable
fun SettingPermissionsPageMiuix() {
    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_permissions),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        item {
            PermissionChecklist(
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
    }
}
