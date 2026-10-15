package heizige.kk.khatkit.app.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import heizige.kk.khatkit.app.core.ui.components.ui.Toast
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CardRunsPage(vm: CardRunsViewModel = hiltViewModel()) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    KedgeSettingsPageScaffold(title = stringResource(R.string.card_runs_title)) { padding ->
        if (runs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.card_runs_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(runs, key = { it.id }) { run ->
                    CardRunRow(run) {
                        vm.rerun(run) { result ->
                            scope.launch {
                                Toast.show(
                                    result.message.ifBlank { if (result.ok) "已重新运行" else "运行失败" },
                                    !result.ok,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRunRow(run: CardRunEntity, onRerun: () -> Unit) {
    val terminal = run.status == CardRunEntity.SUCCEEDED ||
        run.status == CardRunEntity.FAILED ||
        run.status == CardRunEntity.CANCELLED
    KedgeOptionItem(
        modifier = Modifier.fillMaxWidth(),
        onClick = {},
        titleContent = { Text(run.cardName, maxLines = 1) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${run.status} · ${run.trigger} · ${DateFormat.getDateTimeInstance().format(Date(run.startedAt))}")
                run.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
        },
        trailingContent = {
            if (terminal) {
                KedgeTextButton(onClick = onRerun) { Text(stringResource(R.string.card_runs_rerun)) }
            }
        },
    )
}
