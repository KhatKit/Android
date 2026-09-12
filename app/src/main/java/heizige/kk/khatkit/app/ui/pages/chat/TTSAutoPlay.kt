package heizige.kk.khatkit.app.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.model.Conversation
import heizige.kk.khatkit.app.ui.context.LocalTTSState
import heizige.kk.khatkit.app.utils.extractQuotedContentAsText
import heizige.kk.khatkit.app.utils.removeBracketedContent

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    // Auto-play TTS after generation completes
    val tts = LocalTTSState.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)
    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { conversationId ->
            if (conversationId == currentConversation.id &&
                !vm.voiceSession.state.value.isActive &&
                updatedSetting.displaySetting.autoPlayTTSAfterGeneration
            ) {
                val lastMessage = currentConversation.currentMessages.lastOrNull()
                if (lastMessage != null && lastMessage.role == MessageRole.ASSISTANT) {
                    val text = lastMessage.toText()
                    var textToSpeak = text
                    if (updatedSetting.displaySetting.ttsOnlyReadQuoted) {
                        textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                    }
                    if (updatedSetting.displaySetting.ttsOnlyReadOutsideBrackets) {
                        textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                    }
                    if (textToSpeak.isNotBlank()) {
                        tts.speak(textToSpeak)
                    }
                }
            }
        }
    }
}
