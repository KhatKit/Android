package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.util.openUrl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height

/**
 * 捐赠页的 Miuix 风格版。赞助商列表沿用原有的 [Sponsors]（数据加载与渲染与风格无关）。
 */
@Composable
fun SettingDonatePageMiuix() {
    val context = LocalContext.current

    BoxWithConstraints {
    val maxHeight = this.maxHeight
    MiuixSettingsPage(
        title = stringResource(R.string.donate_page_title),
        navigationIcon = { BackButton() },
        bottomInnerPadding = 16.dp,
    ) {
        miuixGroup {
            PreferenceArrow(
                title = "Kofi",
                summary = stringResource(R.string.donate_page_kofi_desc),
                onClick = { context.openUrl("https://ko-fi.com/reovodev") },
                startAction = {
                    AsyncImage(
                        model = R.drawable.kofi,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                },
            )
            PreferenceArrow(
                title = "爱发电",
                summary = stringResource(R.string.donate_page_afdian_desc),
                onClick = { context.openUrl("https://afdian.com/a/reovo") },
                startAction = {
                    Icon(
                        painter = painterResource(R.drawable.afdian),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                },
            )
        }

        item {
            Text(
                text = stringResource(R.string.donate_page_sponsor_list),
                modifier = Modifier.padding(top = 8.dp, start = 12.dp),
                color = MiuixTheme.colorScheme.primary,
            )
        }

        item {
            Sponsors(
                // 必须是确定高度：Sponsors 内部是 LazyVerticalGrid，
                // 放进 LazyColumn 的 item{} 后外层 maxHeight 为 Infinity，
                // 会抛 "Vertically scrollable component was measured with an
                // infinity maximum height constraints"。fillMaxSize(0.6f) 在
                // item 里同样解析成 Infinity，改用屏幕高度比例写死。
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight * 0.55f)
            )
        }
    }
    }
}
