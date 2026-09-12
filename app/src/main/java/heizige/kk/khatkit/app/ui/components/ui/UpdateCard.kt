package heizige.kk.khatkit.app.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.ui.components.ui.AppModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.ui.context.LocalToaster
import heizige.kk.khatkit.app.ui.hooks.useThrottle
import heizige.kk.khatkit.app.ui.pages.chat.ChatVM
import heizige.kk.khatkit.app.utils.UpdateCheckResponse
import heizige.kk.khatkit.app.utils.onError
import heizige.kk.khatkit.app.utils.onSuccess
import heizige.kk.khatkit.app.ui.icons.close
import heizige.kk.khatkit.app.ui.icons.download

@Composable
fun UpdateCard(vm: ChatVM) {
    val state by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    state.onError {
        Card {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_card_check_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = it.message ?: stringResource(R.string.update_card_unknown_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    state.onSuccess { info ->
        var showDetail by remember { mutableStateOf(false) }
        var dismissed by remember { mutableStateOf(false) }
        if (info.hasUpdate && (!dismissed || info.forceUpdate)) {
            Card(
                onClick = {
                    showDetail = true
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.update_card_new_version_found, info.latestVersion),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (!info.forceUpdate) {
                            IconButton(onClick = { dismissed = true }) {
                                Icon(
                                    imageVector = close,
                                    contentDescription = stringResource(R.string.update_card_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    MarkdownBlock(
                        content = info.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 200.dp)
                    )
                }
            }
        }
        if (showDetail) {
            val downloadHandler = useThrottle<UpdateCheckResponse>(500) { item ->
                vm.updateChecker.downloadUpdate(context, item)
                showDetail = false
                Toast.show(context.getString(R.string.update_card_downloading), isError = false)
            }
            AppModalBottomSheet(
                onDismissRequest = { if (!info.forceUpdate) showDetail = false },
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = info.latestVersion,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MarkdownBlock(
                        content = info.description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedCard(
                        onClick = {
                            downloadHandler(info)
                        },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "KhatKit-${info.latestVersion}.apk",
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = info.downloadUrl
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = download,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
