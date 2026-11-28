package heizige.kk.khatkit.app.feature.assistant.detail

import heizige.kk.khatkit.uikit.mochi.MochiButton
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardCodec
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardException
import heizige.kk.khatkit.app.core.data.ai.tavern.PngCharacterCard
import heizige.kk.khatkit.app.core.util.ImageHelper
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.feature.assistant.detail.helper.importAssistantFromUri



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
        // UI-1：按钮占满内容宽度并按内容截断。原来是「自适应宽度 + 超长中文标题」，
        // 文本把 MochiButton 撑到比面板内容区还宽，左右两边被面板圆角裁掉（截图里那道切边）。
        // 图标与文字的间距交给 MochiButton 自己的 spacedBy(8dp)，不再重复加 padding。
        MochiButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AutoAIIcon(name = "tavern")
            Text(
                text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        MochiButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AutoAIIcon(name = "tavern")
            Text(
                text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            MochiButton(
                onClick = { exportJsonLauncher.launch("$exportName.json") },
                enabled = !isLoading,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.assistant_importer_export_tavern_json))
            }
            MochiButton(
                onClick = { exportPngLauncher.launch("$exportName.png") },
                enabled = !isLoading,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.assistant_importer_export_tavern_png))
            }
        }
    }
}
