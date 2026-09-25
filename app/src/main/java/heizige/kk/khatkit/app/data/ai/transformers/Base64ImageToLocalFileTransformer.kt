package heizige.kk.khatkit.app.data.ai.transformers

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.data.files.FilesManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Base64ImageToLocalFileTransformer @Inject constructor(
    private val filesManager: FilesManager,
) : OutputMessageTransformer {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            filesManager.convertBase64ImagePartToLocalFile(message)
        }
    }
}
