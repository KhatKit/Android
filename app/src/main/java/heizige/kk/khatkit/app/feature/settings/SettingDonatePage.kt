package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

/**
 * 捐赠页（MD3 版）。内容已按要求清空，只保留顶栏。
 *
 * 原先这里有「捐赠方式」（Kofi / 爱发电）与「赞助者列表」，两块都已移除；
 * 对应的 `SponsorAPI` / `Sponsor` 模型 / DI 绑定 / 赞助提醒弹窗与
 * `sponsorAlertDismissedAt` 设置项也一并删掉了（要恢复时按这里的结构重写即可）。
 */
@Composable
fun SettingDonatePage() {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingDonatePageMiuix()
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.donate_page_title),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    ) { paddings ->
        // 内容已清空，只留一个吃满内边距的空壳。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddings)
        )
    }
}