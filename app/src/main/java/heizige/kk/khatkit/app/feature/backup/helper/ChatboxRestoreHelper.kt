package heizige.kk.khatkit.app.feature.backup.helper

import android.util.Log
import androidx.core.net.toUri
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.saveUploadFromBytes
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.feature.backup.data.ChatboxRestoreResult
import heizige.kk.khatkit.app.feature.backup.data.importer.ChatboxImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chatbox 导入副作用；ViewModel 仅提供当前设置快照并接收计数。 */
internal class ChatboxRestoreHelper(
    private val settingsStore: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val filesManager: FilesManager,
) {
    suspend fun restore(file: File, currentSettings: Settings): ChatboxRestoreResult =
        withContext(Dispatchers.IO) {
            var importedConversations = 0
            var skippedExistingConversations = 0
            val result = ChatboxImporter.importStreaming(
                file = file,
                assistantId = currentSettings.assistantId,
                providers = currentSettings.providers,
                shouldImportConversation = { conversationId ->
                    val exists = conversationRepository.existsConversationById(conversationId)
                    if (exists) skippedExistingConversations++
                    !exists
                },
                saveImage = { resource ->
                    val entity = filesManager.saveUploadFromBytes(
                        bytes = resource.bytes,
                        displayName = resource.fileName,
                        mimeType = resource.mimeType,
                    )
                    filesManager.getFile(entity).toUri().toString()
                },
                onConversation = { conversation ->
                    conversationRepository.insertConversation(conversation)
                    importedConversations++
                },
            )

            val targetAssistantId = currentSettings.assistantId
            settingsStore.update { latestSettings ->
                latestSettings.copy(
                    providers = result.providers + latestSettings.providers.filterNot { existing ->
                        result.providers.any { imported -> imported.id == existing.id }
                    },
                    assistants = latestSettings.assistants.map { assistant ->
                        if (result.hasConversationSystemPrompt && assistant.id == targetAssistantId) {
                            assistant.copy(allowConversationSystemPrompt = true)
                        } else {
                            assistant
                        }
                    },
                )
            }

            Log.i(
                "BackupViewModel",
                "restoreFromChatBox: import ${result.providers.size} providers, " +
                    "$importedConversations conversations, skip $skippedExistingConversations existing, " +
                    "import ${result.importedImageParts} images, drop ${result.skippedImageParts} images, " +
                    "skip ${result.skippedForkMessages} fork messages and ${result.skippedSessions} sessions",
            )
            ChatboxRestoreResult(
                importedProviders = result.providers.size,
                importedConversations = importedConversations,
                skippedExistingConversations = skippedExistingConversations,
                importedImageParts = result.importedImageParts,
                skippedImageParts = result.skippedImageParts,
                skippedEmptyMessages = result.skippedEmptyMessages,
                skippedForkMessages = result.skippedForkMessages,
                skippedSessions = result.skippedSessions,
            )
        }
}
