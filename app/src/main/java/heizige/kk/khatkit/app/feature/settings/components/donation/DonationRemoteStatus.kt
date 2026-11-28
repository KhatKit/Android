package heizige.kk.khatkit.app.feature.settings.components.donation

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.settings.data.DonationUiState
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows

@Composable
internal fun DonationRemoteStatus(state: DonationUiState, onRetry: () -> Unit) {
    if (state.loading) {
        MochiListSectionFooter(stringResource(R.string.sponsor_loading))
    } else if (state.failed) {
        MochiListSection {
            MochiListRow(
                title = stringResource(R.string.sponsor_load_failed),
                subtitle = stringResource(if (state.snapshot == null) R.string.sponsor_local_qr_available else R.string.sponsor_using_cache),
            )
            MochiListButton(stringResource(R.string.sponsor_retry), onClick = onRetry)
        }
    }
}

internal fun LazyListScope.donationSupporterRows(state: DonationUiState, title: String, onMore: () -> Unit) {
    mochiListSection(key = "supporters", header = title) {
        rows(state.supporters, key = { "supporter:${it.id}" }) { supporter ->
            MochiListRow(title = supporter.name, subtitle = supporter.message, value = supporter.amount)
        }
        if (state.wallLoaded && state.supporters.isEmpty()) {
            row("empty") {
                MochiEmptyState(title = stringResource(R.string.sponsor_wall_empty), message = stringResource(R.string.sponsor_wall_empty_hint))
            }
        }
        if (!state.loading && (!state.wallLoaded || state.nextOffset < state.total)) {
            row("more") {
                if (state.wallLoading) {
                    MochiListRow(title = stringResource(R.string.sponsor_loading))
                } else {
                    MochiListButton(stringResource(if (state.wallFailed) R.string.sponsor_retry else R.string.sponsor_load_more), onClick = onMore)
                }
            }
        }
        if (state.wallFailed) {
            row("error") { MochiListRow(title = stringResource(R.string.sponsor_wall_load_failed)) }
        } else if (!state.wallLoaded && state.loading) {
            row("loading") { MochiListRow(title = stringResource(R.string.sponsor_loading)) }
        }
    }
}
