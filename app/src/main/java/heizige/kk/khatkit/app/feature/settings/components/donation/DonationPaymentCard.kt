package heizige.kk.khatkit.app.feature.settings.components.donation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.settings.data.DonationChannel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun DonationPaymentCard(
    channel: DonationChannel,
    onChannelChange: (DonationChannel) -> Unit,
    amountIndex: Int,
    onAmountChange: (Int) -> Unit,
    canSave: Boolean,
    copying: Boolean,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onEnlarge: () -> Unit,
) {
    val methods = DonationChannel.entries.map { stringResource(it.label) }.toImmutableList()
    val amounts = listOf(R.string.sponsor_amount_tea, R.string.sponsor_amount_coffee, R.string.sponsor_amount_support, R.string.sponsor_amount_custom)
    // The crossfade owns content size changes; do not add a second height spring on the card.
    MochiListCard(animateHeight = false) {
        Column(
            Modifier.fillMaxWidth().padding(MochiListDefaults.RowPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DonationPaymentPicker(methods, channel.ordinal) { onChannelChange(DonationChannel.entries[it]) }
            MochiBlurCrossfade(channel) { shownChannel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DonationQrImage(shownChannel, Modifier.widthIn(max = 252.dp).fillMaxWidth(), onEnlarge)
                    Text(
                        stringResource(R.string.sponsor_scan_hint, stringResource(shownChannel.label)),
                        style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MochiChipGroup(contentPadding = PaddingValues(0.dp), bleed = MochiListDefaults.RowPadding) {
                amounts.forEachIndexed { index, label ->
                    MochiChip(label = stringResource(label), selected = amountIndex == index, onClick = { onAmountChange(index) })
                }
            }
            MochiBlurText(
                text = if (amountIndex == 3) stringResource(R.string.sponsor_amount_custom_hint)
                    else stringResource(R.string.sponsor_amount_hint, listOf(5, 15, 50)[amountIndex]),
                style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
            // Stacked full-width actions retain their labels at large system font sizes.
            MochiButton(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                MochiIonicon("download-outline", modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.donate_page_save_qr))
            }
            MochiButton(onClick = onCopy, enabled = !copying, style = MochiButtonStyle.Tonal, modifier = Modifier.fillMaxWidth()) {
                MochiIonicon("copy-outline", modifier = Modifier.size(20.dp))
                Text(stringResource(if (copying) R.string.sponsor_copying else R.string.sponsor_copy_link))
            }
        }
    }
}

@Composable
internal fun DonationQrImage(channel: DonationChannel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val clickLabel = stringResource(R.string.sponsor_enlarge_qr)
    Image(
        painter = painterResource(channel.qr),
        contentDescription = stringResource(R.string.donate_page_qr_description, stringResource(channel.label)),
        // QR readability is independent of the app palette, including dark and Nothing appearances.
        colorFilter = ColorFilter.tint(Color.Black),
        modifier = modifier.aspectRatio(1f)
            .clip(MochiTheme.shapes.large)
            .background(Color.White)
            .then(if (onClick == null) Modifier else Modifier
                .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                .mochiPressDeform()),
    )
}

@Composable
private fun DonationPaymentPicker(methods: ImmutableList<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold)
    val labels = methods.map { measurer.measure(it, style, maxLines = 1) }
    val width = with(density) { labels.maxOf { it.size.width }.toDp() }
    val height = with(density) { labels.maxOf { it.size.height }.toDp() }
    val behavior = MochiSegmentedDefaults.behavior(height = maxOf(44.dp, height + 16.dp))
    val required = (width + behavior.segmentPadding * 2) * methods.size + 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= required) {
            MochiSegmentedControl(selectedIndex, onSelect, methods, modifier = Modifier.fillMaxWidth(), behavior = behavior)
        } else {
            var expanded by remember { mutableStateOf(false) }
            MochiDropdown(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                items = methods.map { MochiItem(it) }.toImmutableList(),
                onItemClick = onSelect,
                selectedIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MochiBlurText(methods[selectedIndex], modifier = Modifier.weight(1f), style = MochiTheme.textStyles.body.copy(textAlign = TextAlign.Center))
                MochiIonicon("chevron-down", modifier = Modifier.size(18.dp))
            }
        }
    }
}
