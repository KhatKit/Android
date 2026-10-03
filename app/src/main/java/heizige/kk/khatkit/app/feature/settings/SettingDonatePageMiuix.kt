package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage

/**
 * 捐赠页的 Miuix 风格版。内容已按要求清空，只保留顶栏。
 *
 * 原先这里是「捐赠方式」（Kofi / 爱发电）+「赞助者列表」，两块都已移除。
 */
@Composable
fun SettingDonatePageMiuix() {
    MiuixSettingsPage(
        title = stringResource(R.string.donate_page_title),
        navigationIcon = { BackButton() },
    ) {
        item {
            // 内容已清空，只留一个空壳把内边距吃掉。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }
}