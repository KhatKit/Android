package heizige.kk.khatkit.app.feature.backup.data

data class DeepSeekAttachment(val fileName: String, val mimeType: String, val bytes: ByteArray)

data class DeepSeekImportResult(
    val importedConversations: Int,
    val skippedExistingConversations: Int,
    val skippedEmptyConversations: Int,
    val missingAttachments: Int,
)
