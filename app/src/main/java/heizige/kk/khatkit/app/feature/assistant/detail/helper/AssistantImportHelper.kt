package heizige.kk.khatkit.app.feature.assistant.detail.helper

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardCodec
import heizige.kk.khatkit.app.core.data.ai.tavern.CharacterCardException
import heizige.kk.khatkit.app.core.util.ImageHelper
import heizige.kk.khatkit.app.R

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

internal suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    onImport: (Assistant) -> Unit,
    filesManager: FilesManager,
) {
    try {
        val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        val (jsonString, backgroundStr) = withContext(Dispatchers.IO) {
            when (mime) {
                "image/png" -> {
                    val result = ImageHelper.getTavernCharacterMeta(context, uri)
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
