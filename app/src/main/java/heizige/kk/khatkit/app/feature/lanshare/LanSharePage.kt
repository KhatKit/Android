package heizige.kk.khatkit.app.feature.lanshare

import androidx.compose.ui.platform.LocalResources
import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.app.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun LanSharePage() {
    val manager = hiltViewModel<LanShareViewModel>().manager
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) manager.select(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) manager.start() else manager.showError(resources.getString(R.string.lanshare_permission_denied))
    }
    val sessionUrl = state.url
    val active = sessionUrl != null
    val canEditDirectory = !active && !state.isBusy
    MochiSettingsPageScaffold(title = stringResource(R.string.lanshare_page_title)) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MochiListSection {
                MochiListRow(
                    title = if (active) stringResource(R.string.lanshare_status_active) else stringResource(R.string.lanshare_status_idle),
                    subtitle = if (active) stringResource(R.string.lanshare_status_link) else null,
                    leading = { MochiIonicon("wifi") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        if (state.isBusy) MochiActivityIndicator(Modifier.size(20.dp))
                        else MochiButton(
                            onClick = {
                                if (active) manager.stop()
                                else if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_LOCAL_NETWORK,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                else manager.start()
                            },
                            style = if (active) MochiButtonStyle.Tonal else MochiButtonStyle.Filled,
                        ) { Text(if (active) stringResource(R.string.lanshare_stop) else stringResource(R.string.lanshare_start)) }
                    },
                )
                if (sessionUrl != null) {
                    MochiListRow(
                        title = stringResource(R.string.lanshare_copy_url),
                        subtitle = stringResource(R.string.lanshare_url_hint),
                        onClick = {
                            val clip = ClipData.newPlainText(resources.getString(R.string.lanshare_page_title), sessionUrl)
                            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                        },
                        leading = { MochiIonicon("copy") },
                    )
                    SelectionContainer {
                        Text(sessionUrl, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 4,
                            overflow = TextOverflow.Ellipsis, style = MochiTheme.textStyles.footnote)
                    }
                }
            }
            state.error?.let { Text(it, color = MochiTheme.colorScheme.error) }
            MochiListSection {
                MochiListRow(
                    title = stringResource(R.string.lanshare_shared_dir),
                    subtitle = state.directory?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.lanshare_no_dir),
                    onClick = if (canEditDirectory) ({ picker.launch(null) }) else null,
                    leading = { MochiIonicon("folder-open") },
                    trailing = {
                        if (state.directory != null && canEditDirectory) {
                            MochiIconButton(onClick = manager::removeDirectory, style = MochiButtonStyle.Text) {
                                MochiIonicon("close", contentDescription = stringResource(R.string.lanshare_remove_dir))
                            }
                        }
                    },
                )
            }
            Text(stringResource(R.string.lanshare_text_transfer), style = MochiTheme.textStyles.titleMedium)
            MochiTextField(
                value = state.outgoing,
                onValueChange = manager::editText,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.lanshare_text_outgoing_placeholder),
                singleLine = false,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lanshare_text_incoming), Modifier.weight(1f), style = MochiTheme.textStyles.footnote)
                if (state.incoming.isNotEmpty()) {
                    MochiIconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.lanshare_text_incoming), state.incoming))
                    }, style = MochiButtonStyle.Text) {
                        MochiIonicon("copy", contentDescription = stringResource(R.string.lanshare_copy_incoming))
                    }
                }
            }
            SelectionContainer { Text(state.incoming.ifBlank { stringResource(R.string.lanshare_empty_incoming) }) }
        }
    }
}
