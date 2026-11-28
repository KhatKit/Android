package heizige.kk.khatkit.app.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

@Composable
fun SettingDonatePage() {
    val listState = rememberLazyListState()

    MochiSettingsPageScaffold(
        title = stringResource(R.string.donate_page_title),
        largeTitle = false,
        scrollUnderTopBar = true,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = MochiListDefaults.RowPadding,
                top = PageMetrics.TopContentPadding,
                end = MochiListDefaults.RowPadding,
                bottom = PageMetrics.TopContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
            flingBehavior = MochiScrollDefaults.flingBehavior(),
        ) {
            item(key = "alipay") {
                DonationQrSection(
                    paymentMethod = stringResource(R.string.donate_page_alipay),
                    qrDrawable = R.drawable.donate_alipay_qr,
                )
            }
            item(key = "wechat") {
                DonationQrSection(
                    paymentMethod = stringResource(R.string.donate_page_wechat),
                    qrDrawable = R.drawable.donate_wechat_qr,
                )
            }
        }
    }
}

@Composable
private fun DonationQrSection(
    paymentMethod: String,
    @DrawableRes qrDrawable: Int,
) {
    MochiListSection(
        header = paymentMethod,
        footer = stringResource(R.string.donate_page_scan_hint, paymentMethod),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MochiListDefaults.RowPadding),
            contentAlignment = Alignment.Center,
        ) {
            // The vector includes a four-module quiet zone. Tint only the QR modules;
            // the unpainted background inherits the grouped card in every appearance.
            Image(
                painter = painterResource(qrDrawable),
                contentDescription = stringResource(R.string.donate_page_qr_description, paymentMethod),
                colorFilter = ColorFilter.tint(MochiTheme.colorScheme.primary),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
    }
}
