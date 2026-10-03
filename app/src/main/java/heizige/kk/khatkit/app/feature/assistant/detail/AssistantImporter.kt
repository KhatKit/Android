package heizige.kk.khatkit.app.feature.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardCodec
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardException
import heizige.kk.khatkit.app.core.data.ai.tavern.PngCharacterCard
import heizige.kk.khatkit.app.core.util.ImageUtils
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    assistant: Assistant? = null,
    onUpdate: (Assistant) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(assistant = assistant, onImport = onUpdate)
    }
}

@Composable
private fun SillyTavernImporter(
    assistant: Assistant?,
    onImport: (Assistant) -> Unit,
) {
    val context = LocalContext.current
    // 文案在 composable 作用域取：LocalContext 取值不是 configuration-aware，
    // 语言切换后不会重组，Toast 会停在旧语言
    val importFailed = stringResource(R.string.assistant_importer_import_failed)
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        Toast.show(exception.message ?: importFailed)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        Toast.show(exception.message ?: importFailed)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KedgeButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading,
            shapes = ButtonDefaults.shapes(),
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png))
        }

        KedgeButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading,
            shapes = ButtonDefaults.shapes(),
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json))
        }
        val cardJson = assistant?.tavernCardJson
        if (!cardJson.isNullOrBlank()) {
            val exportFailed = stringResource(R.string.assistant_importer_export_failed)
            val exportName = assistant.name.ifBlank { "character" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val exportJsonLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    runCatching {
                        val text = CharacterCardCodec.export(cardJson, assistant.name)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                                ?: error(exportFailed)
                        }
                    }.onFailure { Toast.show(it.message ?: exportFailed) }
                }
            }
            val exportPngLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/png"),
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    runCatching {
                        val bytes = PngCharacterCard.embed(CharacterCardCodec.export(cardJson, assistant.name))
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                ?: error(exportFailed)
                        }
                    }.onFailure { Toast.show(it.message ?: exportFailed) }
                }
            }
            KedgeButton(
                onClick = { exportJsonLauncher.launch("$exportName.json") },
                enabled = !isLoading,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.assistant_importer_export_tavern_json))
            }
            KedgeButton(
                onClick = { exportPngLauncher.launch("$exportName.png") },
                enabled = !isLoading,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.assistant_importer_export_tavern_png))
            }
        }
    }
}

private fun localizeCardError(context: Context, exception: CharacterCardException): String = when (exception.code) {
    "missing_data" -> context.getString(R.string.assistant_importer_missing_data_field)
    "missing_name" -> context.getString(R.string.assistant_importer_missing_name_field)
    "missing_spec" -> context.getString(R.string.assistant_importer_missing_spec_field)
    "unsupported_spec" -> context.getString(
        R.string.assistant_importer_unsupported_spec,
        exception.arg ?: exception.message.orEmpty(),
    )
    else -> exception.message ?: context.getString(R.string.assistant_importer_import_failed)
}

private suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    onImport: (Assistant) -> Unit,
    toaster: ToasterState,
    filesManager: FilesManager,
) {
    try {
        val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        val (jsonString, backgroundStr) = withContext(Dispatchers.IO) {
            when (mime) {
                "image/png" -> {
                    val result = ImageUtils.getTavernCharacterMeta(context, uri)
                    result.map { base64Data ->
                        val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                        val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
                        json to bg
                    }.getOrElse { throw it }
                }

                "application/json" -> {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() }
                        ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                    json to null
                }

                else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
            }
        }
        val assistant = try {
            CharacterCardCodec.importAssistant(jsonString, backgroundStr)
        } catch (exception: CharacterCardException) {
            throw IllegalArgumentException(localizeCardError(context, exception))
        }
        onImport(assistant)
    } catch (exception: Exception) {
        exception.printStackTrace()
        Toast.show(
            message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
            isError = true
        )
    }
}
