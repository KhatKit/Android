package heizige.kk.khatkit.app.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.ui.RabbitLoadingIndicator
import heizige.kk.khatkit.app.feature.tasks.data.savedInputs
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CardInputHistoryDialog(
    cardName: String,
    history: List<CardRunEntity>?,
    onDismiss: () -> Unit,
    onReuse: (CardRunEntity) -> Unit,
) {
    val entries = remember(history) {
        history.orEmpty().mapNotNull { run -> run.savedInputs().takeIf { it.isNotEmpty() }?.let { run to it } }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_runs_input_history)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cardName, style = MaterialTheme.typography.titleSmall)
                when {
                    history == null -> RabbitLoadingIndicator()
                    entries.isEmpty() -> Text(stringResource(R.string.card_runs_input_history_empty))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.first.id }) { (run, inputs) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${DateFormat.getDateTimeInstance().format(Date(run.startedAt))} · ${run.cardVersion}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                CardInputValues(inputs)
                                MochiTextButton(onClick = { onReuse(run) }) {
                                    Text(stringResource(R.string.card_runs_input_reuse))
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            MochiTextButton(onClick = onDismiss) { Text(stringResource(R.string.card_runs_close)) }
        },
    )
}

@Composable
internal fun CardInputValues(inputs: Map<String, Any?>) {
    Text(
        inputs.entries.joinToString("\n") { (key, value) -> "$key: $value" },
        style = MaterialTheme.typography.bodySmall,
    )
}
