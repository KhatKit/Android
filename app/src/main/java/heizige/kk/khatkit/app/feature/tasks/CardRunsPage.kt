package heizige.kk.khatkit.app.feature.tasks

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.CardArtifactEntity
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.Toast
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun CardRunsPage(vm: CardRunsViewModel = hiltViewModel()) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val artifacts by vm.artifacts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRerun by remember { mutableStateOf<CardRunEntity?>(null) }
    var pendingText by remember { mutableStateOf<CardArtifactEntity?>(null) }
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
                    CardRunRow(
                        run = run,
                        artifacts = artifacts.filter { it.runId == run.id },
                        onOpenArtifact = { artifact ->
                            if (artifact.kind == CardArtifactEntity.TEXT) {
                                pendingText = artifact
                            } else {
                                scope.launch {
                                    runCatching { openArtifact(context, artifact) }
                                        .onFailure { Toast.show(context.getString(R.string.card_runs_artifact_open_failed), true) }
                                }
                            }
                        },
                        onShareArtifact = { artifact ->
                            scope.launch {
                                runCatching { shareArtifact(context, artifact) }
                                    .onFailure { Toast.show(context.getString(R.string.card_runs_artifact_open_failed), true) }
                            }
                        },
                        onRerun = { pendingRerun = run },
                    )
                }
            }
        }
    }
    pendingRerun?.let { run ->
        AppAlertDialog(
            onDismissRequest = { pendingRerun = null },
            title = { Text(stringResource(R.string.card_runs_rerun_confirm_title)) },
            text = { Text(stringResource(R.string.card_runs_rerun_confirm_message, run.cardName)) },
            confirmButton = {
                KedgeTextButton(onClick = {
                    pendingRerun = null
                    vm.rerun(run) { result ->
                        Toast.show(
                            result.message.ifBlank {
                                context.getString(
                                    if (result.ok) R.string.card_runs_rerun_started else R.string.card_runs_rerun_failed,
                                )
                            },
                            !result.ok,
                        )
                    }
                }) { Text(stringResource(R.string.card_runs_rerun)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { pendingRerun = null }) {
                    Text(stringResource(R.string.card_runs_cancel))
                }
            },
        )
    }
    pendingText?.let { artifact ->
        AppAlertDialog(
            onDismissRequest = { pendingText = null },
            title = { Text(artifact.displayName) },
            text = {
                Text(
                    artifact.textContent.orEmpty(),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                KedgeTextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(artifact.displayName, artifact.textContent))
                    pendingText = null
                }) { Text(stringResource(R.string.card_runs_artifact_copy)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { pendingText = null }) {
                    Text(stringResource(R.string.card_runs_cancel))
                }
            },
        )
    }
}

@Composable
private fun CardRunRow(
    run: CardRunEntity,
    artifacts: List<CardArtifactEntity>,
    onOpenArtifact: (CardArtifactEntity) -> Unit,
    onShareArtifact: (CardArtifactEntity) -> Unit,
    onRerun: () -> Unit,
) {
    val terminal = run.status == CardRunEntity.SUCCEEDED ||
        run.status == CardRunEntity.FAILED ||
        run.status == CardRunEntity.CANCELLED ||
        run.status == CardRunEntity.INTERRUPTED
    KedgeOptionItem(
        modifier = Modifier.fillMaxWidth(),
        onClick = {},
        titleContent = { Text(run.cardName, maxLines = 1) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${cardRunStatusLabel(run.status)} · ${run.trigger} · ${DateFormat.getDateTimeInstance().format(Date(run.startedAt))}")
                if (artifacts.isNotEmpty()) {
                    Text(stringResource(R.string.card_runs_artifacts, artifacts.size))
                    artifacts.forEach { artifact ->
                        val available = artifact.isAvailable()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (available) artifact.displayName
                                else stringResource(R.string.card_runs_artifact_missing, artifact.displayName),
                                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                                maxLines = 1,
                            )
                            if (artifact.kind == CardArtifactEntity.TEXT) {
                                Text(artifact.textContent.orEmpty(), maxLines = 2)
                            }
                            if (artifact.kind == CardArtifactEntity.FILE) {
                                val context = LocalContext.current
                                val details = listOfNotNull(
                                    artifact.mimeType,
                                    artifact.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" · "), maxLines = 1)
                            }
                            if (available) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    KedgeTextButton(onClick = { onOpenArtifact(artifact) }) {
                                        Text(stringResource(R.string.card_runs_artifact_open))
                                    }
                                    KedgeTextButton(onClick = { onShareArtifact(artifact) }) {
                                        Text(stringResource(R.string.card_runs_artifact_share))
                                    }
                                }
                            }
                        }
                    }
                }
                run.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
        },
        trailingContent = {
            if (terminal && run.trigger == "用户") {
                KedgeTextButton(onClick = onRerun) { Text(stringResource(R.string.card_runs_rerun)) }
            }
        },
    )
}

@Composable
private fun cardRunStatusLabel(status: String): String = stringResource(
    when (status) {
        CardRunEntity.QUEUED -> R.string.card_runs_status_queued
        CardRunEntity.PREPARING -> R.string.card_runs_status_preparing
        CardRunEntity.RUNNING -> R.string.card_runs_status_running
        CardRunEntity.WAITING_USER -> R.string.card_runs_status_waiting
        CardRunEntity.SUCCEEDED -> R.string.card_runs_status_succeeded
        CardRunEntity.FAILED -> R.string.card_runs_status_failed
        CardRunEntity.CANCELLED -> R.string.card_runs_status_cancelled
        else -> R.string.card_runs_status_interrupted
    },
)

private fun CardArtifactEntity.isAvailable(): Boolean = when (kind) {
    CardArtifactEntity.TEXT -> !textContent.isNullOrEmpty()
    CardArtifactEntity.LINK -> available && Uri.parse(uri).scheme in setOf("http", "https")
    CardArtifactEntity.FILE -> fileOrNull()?.canRead() == true
    else -> false
}

private fun CardArtifactEntity.fileOrNull(): File? =
    Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)?.takeIf { it.isFile }

private suspend fun openArtifact(context: Context, artifact: CardArtifactEntity) {
    require(artifact.isAvailable()) { "Artifact is unavailable" }
    val intent = if (artifact.kind == CardArtifactEntity.LINK) {
        Intent(Intent.ACTION_VIEW, Uri.parse(artifact.uri))
    } else {
        val uri = artifactFileUri(context, artifact)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, artifact.mimeType ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(artifact.displayName.substringAfterLast('.', "").lowercase())
                ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(intent)
}

private suspend fun shareArtifact(context: Context, artifact: CardArtifactEntity) {
    require(artifact.isAvailable()) { "Artifact is unavailable" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (artifact.kind == CardArtifactEntity.TEXT || artifact.kind == CardArtifactEntity.LINK) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, if (artifact.kind == CardArtifactEntity.TEXT) artifact.textContent else artifact.uri)
        } else {
            val uri = artifactFileUri(context, artifact)
            type = artifact.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.card_runs_artifact_share)))
}

private suspend fun artifactFileUri(context: Context, artifact: CardArtifactEntity): Uri {
    val file = artifact.fileOrNull() ?: error("Artifact file is unavailable")
    val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
    return runCatching { FileProvider.getUriForFile(context, authority, file) }
        .getOrElse {
            val cached = withContext(Dispatchers.IO) {
                require(file.length() <= 64L * 1024 * 1024) { "Artifact is too large to share" }
                val directory = File(context.cacheDir, "card_artifacts").apply { mkdirs() }
                File(directory, "${artifact.id}-${file.name}").also { file.copyTo(it, overwrite = true) }
            }
            FileProvider.getUriForFile(context, authority, cached)
        }
}
