package heizige.kk.khatkit.app.feature.settings.components.donation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.feature.settings.data.DonationChannels
import heizige.kk.khatkit.app.feature.settings.data.DonationProfile
import heizige.kk.khatkit.app.feature.settings.data.DonationUiState
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.shape.MochiSuperellipse
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun DonationIntroduction(profile: Boolean = false, remote: DonationProfile? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = remote?.avatarUrl ?: R.mipmap.ic_launcher,
            error = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(MochiSuperellipse(5f)),
        )
        Text(
            if (profile) remote?.name ?: stringResource(R.string.app_name) else stringResource(R.string.donate_page_support_title),
            style = MochiTheme.textStyles.titleLarge.copy(textAlign = TextAlign.Center),
        )
        Text(
            if (profile) remote?.description ?: stringResource(R.string.sponsor_project_description) else stringResource(R.string.donate_page_support_description),
            modifier = Modifier.padding(horizontal = MochiListDefaults.RowPadding),
            style = MochiTheme.textStyles.subhead.copy(textAlign = TextAlign.Center),
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DonationPortals(state: DonationUiState) {
    val context = LocalContext.current
    MochiListSection(header = stringResource(R.string.sponsor_other_channels)) {
        DonationChannels.portals.forEach { portal ->
            // A successfully loaded empty list is authoritative; offline first use gets owner-confirmed links.
            val url = portal.destination(state.snapshot)
            MochiListRow(
                title = stringResource(portal.label),
                value = if (url == null) stringResource(when {
                    state.loading -> R.string.sponsor_loading
                    state.failed -> R.string.sponsor_temporarily_unavailable
                    else -> R.string.sponsor_link_pending
                }) else null,
                enabled = url != null,
                onClick = url?.let { { context.openUrl(it) } },
                leading = { MochiIonicon(portal.icon) },
                leadingStyle = MochiListLeadingStyle.Symbol,
                chevron = url != null,
            )
        }
        val email = state.snapshot?.profile?.contactEmail
        MochiListRow(
            title = stringResource(R.string.sponsor_business),
            subtitle = email,
            value = if (email == null) stringResource(when {
                state.loading -> R.string.sponsor_loading
                state.failed -> R.string.sponsor_temporarily_unavailable
                else -> R.string.sponsor_unavailable
            }) else null,
            enabled = email != null,
            onClick = email?.let { { context.openUrl(Uri.fromParts("mailto", it, null).toString()) } },
            leading = { MochiIonicon("business-outline") },
            leadingStyle = MochiListLeadingStyle.Symbol,
            chevron = email != null,
        )
    }
}

@Composable
internal fun DonationSupporters(state: DonationUiState, onViewAll: () -> Unit) {
    MochiListSection(header = stringResource(R.string.sponsor_wall_title)) {
        val supporters = state.snapshot?.acknowledgements.orEmpty()
        if (supporters.isEmpty()) {
            MochiEmptyState(
                title = stringResource(when {
                    state.loaded -> R.string.sponsor_wall_empty
                    state.failed -> R.string.sponsor_temporarily_unavailable
                    else -> R.string.sponsor_loading
                }),
                message = stringResource(if (state.loaded) R.string.sponsor_wall_empty_hint else R.string.sponsor_local_qr_available),
                icon = { MochiIonicon("heart-outline", modifier = Modifier.size(40.dp)) },
            )
        } else supporters.forEach { supporter ->
            MochiListRow(title = supporter.name, subtitle = supporter.message, value = supporter.amount)
        }
        MochiListButton(stringResource(R.string.sponsor_view_wall), onClick = onViewAll)
    }
}

@Composable
internal fun DonationProjectLinks(remote: DonationProfile?) {
    val context = LocalContext.current
    val profile = remote ?: DonationProfile(
        name = stringResource(R.string.app_name), projectUrl = DonationChannels.projectUrl, websiteUrl = DonationChannels.websiteUrl,
    )
    MochiListSection(header = stringResource(R.string.sponsor_project_links)) {
        MochiListRow(
            title = stringResource(R.string.sponsor_project_source),
            subtitle = profile.projectUrl.removePrefix("https://").removePrefix("http://"),
            leading = { MochiIonicon("logo-github") },
            leadingStyle = MochiListLeadingStyle.Symbol,
            chevron = true,
            onClick = { context.openUrl(profile.projectUrl) },
        )
        MochiListRow(
            title = stringResource(R.string.sponsor_project_website),
            subtitle = profile.websiteUrl.removePrefix("https://").removePrefix("http://"),
            leading = { MochiIonicon("globe-outline") },
            leadingStyle = MochiListLeadingStyle.Symbol,
            chevron = true,
            onClick = { context.openUrl(profile.websiteUrl) },
        )
    }
}

@Composable
internal fun DonationProjectNote() {
    MochiListCard {
        Column(Modifier.padding(MochiListDefaults.RowPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sponsor_project_note_title), style = MochiTheme.textStyles.headlineMedium)
            Text(stringResource(R.string.sponsor_project_note), style = MochiTheme.textStyles.subhead, color = MochiTheme.colorScheme.onSurfaceVariant)
        }
    }
}
