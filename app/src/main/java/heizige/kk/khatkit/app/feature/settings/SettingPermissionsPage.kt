package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold

/**
 * 权限页：常规权限 / 系统级权限 / 高危权限三组清单 + 实时授权状态。
 *
 * 骨架用 Mochi 大标题脚手架，清单本身也是 Mochi 分组列表（[PermissionChecklist] compact 形态），
 * 与欢迎页整屏授权体验同一套组件；边距由脚手架给，所以这里传零留白。
 */
@Composable
fun SettingPermissionsPage() {
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.setting_page_permissions),
        navigationIcon = { BackButton() },
    ) {
        item(key = "checklist") {
            PermissionChecklist(
                modifier = Modifier.fillMaxWidth(),
                compact = true,
                contentPadding = PaddingValues(),
            )
        }
    }
}
