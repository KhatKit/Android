package heizige.kk.khatkit.app.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiSuperellipse
import ui.feice.mochi.theme.MochiTheme

@Composable
fun SettingDonatePage(vm: DonationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var paymentIndex by rememberSaveable { mutableIntStateOf(0) }
    val pendingQr by vm.pendingQr.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val methods = persistentListOf(
        stringResource(R.string.donate_page_alipay),
        stringResource(R.string.donate_page_wechat),
    )
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> vm.exportTo(uri) }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.donate_page_title),
        largeTitle = false,
        scrollUnderTopBar = true,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                horizontal = MochiListDefaults.RowPadding,
                vertical = PageMetrics.TopContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = MochiScrollDefaults.flingBehavior(),
        ) {
            item(key = "support") {
                DonationIntroduction()
            }
            item(key = "payment") {
                // Crossfade owns the changing QR and hint size; the card adds no second animation.
                MochiListCard(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    animateHeight = false,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DonationPaymentPicker(methods, paymentIndex) { paymentIndex = it }
                        MochiBlurCrossfade(paymentIndex, modifier = Modifier.fillMaxWidth()) { index ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Image(
                                    painter = painterResource(donationQrResource(index)),
                                    contentDescription = stringResource(R.string.donate_page_qr_description, methods[index]),
                                    colorFilter = ColorFilter.tint(MochiTheme.colorScheme.primary),
                                    modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f),
                                )
                                Text(
                                    text = stringResource(R.string.donate_page_scan_hint, methods[index]),
                                    style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        MochiButton(
                            onClick = {
                                if (vm.prepareExport(donationQrResource(paymentIndex))) {
                                    try {
                                        exportLauncher.launch(context.getString(R.string.donate_page_export_file_name, methods[paymentIndex]))
                                    } catch (_: ActivityNotFoundException) {
                                        vm.cancelExport()
                                        Toast.show(context.getString(R.string.donate_page_save_failed), isError = true)
                                    }
                                }
                            },
                            enabled = pendingQr == null && !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MochiIonicon("download-outline", modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.donate_page_save_qr))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationPaymentPicker(
    methods: ImmutableList<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold)
    val labels = methods.map { measurer.measure(it, labelStyle, maxLines = 1) }
    val labelWidth = with(density) { labels.maxOf { it.size.width }.toDp() }
    val labelHeight = with(density) { labels.maxOf { it.size.height }.toDp() }
    val behavior = MochiSegmentedDefaults.behavior(height = maxOf(44.dp, labelHeight + 16.dp))
    val requiredWidth = (labelWidth + behavior.segmentPadding * 2) * methods.size + 4.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= requiredWidth) {
            MochiSegmentedControl(
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                items = methods,
                modifier = Modifier.fillMaxWidth(),
                behavior = behavior,
            )
        } else {
            // Use the library menu when two full labels cannot fit beside one another.
            var expanded by remember { mutableStateOf(false) }
            MochiDropdown(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                items = methods.map { MochiItem(it) }.toImmutableList(),
                onItemClick = onSelect,
                selectedIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MochiBlurText(
                    methods[selectedIndex],
                    modifier = Modifier.weight(1f),
                    style = MochiTheme.textStyles.body.copy(textAlign = TextAlign.Center),
                )
                MochiIonicon("chevron-down", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DonationIntroduction() {
    Column(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(MochiSuperellipse(5f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.donate_page_support_title),
                style = MochiTheme.textStyles.titleLarge.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.donate_page_support_description),
                style = MochiTheme.textStyles.subhead.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun donationQrResource(index: Int): Int =
    if (index == 0) R.drawable.donate_alipay_qr else R.drawable.donate_wechat_qr
