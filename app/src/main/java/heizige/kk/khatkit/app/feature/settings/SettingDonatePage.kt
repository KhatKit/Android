package heizige.kk.khatkit.app.feature.settings

import androidx.compose.ui.platform.LocalResources
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.util.writeClipboardText
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationIntroduction
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationPaymentCard
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationPortals
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationProjectLinks
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationProjectNote
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationQrImage
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationSupporters
import heizige.kk.khatkit.app.feature.settings.components.donation.DonationRemoteStatus
import heizige.kk.khatkit.app.feature.settings.components.donation.donationSupporterRows
import heizige.kk.khatkit.app.feature.settings.data.DonationChannel
import heizige.kk.khatkit.app.feature.settings.data.DonationChannels
import heizige.kk.khatkit.app.feature.settings.helper.DonationPaymentLinkHelper
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.mochiSheetBackdrop
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState

@Composable
fun SettingDonatePage(vm: DonationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val tabs = rememberMochiTabScaffoldState()
    val qrSheet = rememberMochiSheetState()
    var paymentIndex by rememberSaveable { mutableIntStateOf(0) }
    var enlargedIndex by rememberSaveable { mutableIntStateOf(0) }
    var amountIndex by rememberSaveable { mutableIntStateOf(1) }
    var copying by androidx.compose.runtime.remember { mutableStateOf(false) }
    val channel = DonationChannel.entries[paymentIndex]
    val pendingQr by vm.pendingQr.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val sponsors by vm.sponsors.collectAsStateWithLifecycle()
    val profile = sponsors.snapshot?.profile
    LaunchedEffect(vm) { vm.refreshSponsors() }
    val canSave = pendingQr == null && !saving
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { vm.exportTo(it) }

    fun saveQr(selected: DonationChannel) {
        if (!vm.prepareExport(selected.qr)) return
        try {
            exportLauncher.launch(resources.getString(R.string.donate_page_export_file_name, resources.getString(selected.label)))
        } catch (_: ActivityNotFoundException) {
            vm.cancelExport()
            Toast.show(resources.getString(R.string.donate_page_save_failed), isError = true)
        }
    }

    fun copyLink(selected: DonationChannel) {
        if (copying) return
        copying = true
        scope.launch {
            try {
                val link = withContext(Dispatchers.IO) { DonationPaymentLinkHelper.read(context, selected.qr) }
                if (link != null && context.writeClipboardText(link)) {
                    Toast.show(resources.getString(R.string.sponsor_link_copied, resources.getString(selected.label)))
                } else {
                    Toast.show(resources.getString(R.string.sponsor_copy_failed), isError = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.show(resources.getString(R.string.sponsor_copy_failed), isError = true)
            } finally {
                copying = false
            }
        }
    }

    val shareLabel = stringResource(R.string.share)
    val closeLabel = stringResource(R.string.sponsor_close_qr)
    val items = persistentListOf(
        MochiTabItem(stringResource(R.string.donate_page_title), selectedIcon = { MochiIonicon("heart", tint = it) }) { MochiIonicon("heart-outline", tint = it) },
        MochiTabItem(stringResource(R.string.sponsor_wall_tab), selectedIcon = { MochiIonicon("medal", tint = it) }) { MochiIonicon("medal-outline", tint = it) },
        MochiTabItem(stringResource(R.string.sponsor_profile_tab), selectedIcon = { MochiIonicon("person-circle", tint = it) }) { MochiIonicon("person-circle-outline", tint = it) },
    )
    val wallTitle = stringResource(R.string.sponsor_wall_title)
    MochiTabScaffold(items, state = tabs, modifier = Modifier.mochiSheetBackdrop(qrSheet)) { tab ->
        LaunchedEffect(tab, sponsors.loading, sponsors.wallLoaded) {
            if (tab == 1 && !sponsors.loading && !sponsors.wallLoaded) vm.loadSupporters()
        }
        MochiLazyCompactTitleScaffold(
            title = items[tab].label,
            modifier = Modifier.fillMaxSize(),
            navigationIcon = { BackButton() },
            actions = {
                MochiBarIconButton(
                    onClick = {
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.sponsor_share_text, profile?.projectUrl ?: DonationChannels.projectUrl))
                            }, shareLabel))
                        } catch (_: ActivityNotFoundException) {
                            Toast.show(resources.getString(R.string.setting_page_no_share_app), isError = true)
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = shareLabel },
                    navId = "share",
                    contentKey = "share-outline",
                ) { MochiIonicon("share-outline") }
            },
        ) {
            if (sponsors.loading || sponsors.failed) {
                item("remote-status") { DonationRemoteStatus(sponsors, onRetry = vm::refreshSponsors) }
            }
            when (tab) {
                0 -> {
                    item("introduction") { DonationIntroduction(remote = profile) }
                    item("payment") {
                        DonationPaymentCard(
                            channel = channel,
                            onChannelChange = { paymentIndex = it.ordinal },
                            amountIndex = amountIndex,
                            onAmountChange = { amountIndex = it },
                            canSave = canSave,
                            copying = copying,
                            onSave = { saveQr(channel) },
                            onCopy = { copyLink(channel) },
                            onEnlarge = { enlargedIndex = paymentIndex; qrSheet.show() },
                        )
                    }
                    item("portals") { DonationPortals(sponsors) }
                    item("supporters") { DonationSupporters(sponsors, onViewAll = { tabs.select(1) }) }
                    item("thanks") { MochiListSectionFooter(stringResource(R.string.sponsor_footer)) }
                }
                1 -> {
                    donationSupporterRows(sponsors, wallTitle, onMore = { vm.loadSupporters(retry = true) })
                    item("thanks") { MochiListSectionFooter(stringResource(R.string.sponsor_wall_note)) }
                }
                2 -> {
                    item("project") { DonationIntroduction(profile = true, remote = profile) }
                    item("project-links") { DonationProjectLinks(profile) }
                    item("support-note") { DonationProjectNote() }
                }
            }
        }
    }
    // Always composed: state owns appearance, dismissal and predictive back.
    MochiSheet(qrSheet) {
        val enlarged = DonationChannel.entries[enlargedIndex]
        MochiSheetHeader(
            title = stringResource(enlarged.label),
            navigationIcon = {
                MochiBarIconButton(
                    onClick = { qrSheet.hide() },
                    modifier = Modifier.semantics { contentDescription = closeLabel },
                    contentKey = "close",
                ) { MochiIonicon("close") }
            },
        )
        MochiSheetContent {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DonationQrImage(enlarged, Modifier.widthIn(max = 400.dp).fillMaxWidth())
                MochiButton(onClick = { saveQr(enlarged) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    MochiIonicon("download-outline", modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.donate_page_save_qr))
                }
            }
        }
    }
}
