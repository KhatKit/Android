package heizige.kk.khatkit.app.ui.pages.chat

import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.asr.ASRController
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.asr.providers.DashScopeASRController
import heizige.kk.khatkit.asr.providers.VolcengineASRController
import heizige.kk.khatkit.asr.providers.OpenAIRealtimeASRController
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.data.datastore.getSelectedASRProvider
import heizige.kk.khatkit.app.data.datastore.getSelectedTTSProvider
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionRecordAudio
import heizige.kk.khatkit.app.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.ui.context.LocalASRState
import heizige.kk.khatkit.app.ui.context.LocalTTSState
import heizige.kk.khatkit.app.ui.context.LocalToaster
import heizige.kk.khatkit.app.utils.extractQuotedContentAsText
import heizige.kk.khatkit.app.utils.removeBracketedContent
import heizige.kk.khatkit.app.utils.stripMarkdown
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.app.di.rememberAppEntryPoint

/** Lives above adaptive drawer branches, so resizing does not recreate the voice session. */
@Composable
fun rememberVoiceModeStarter(vm: ChatVM, settings: Settings): () -> Unit {
    val context = LocalContext.current.applicationContext
    val client = rememberAppEntryPoint().okHttpClient()
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val toaster = LocalToaster.current
    val permission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permission)
    val voice = vm.voiceSession
    val state by voice.state.collectAsStateWithLifecycle()
    val provider = settings.getSelectedASRProvider()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(voice, lifecycleOwner, provider, settings.getSelectedTTSProvider()) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voice.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voice.stop()
        }
    }

    val start: () -> Unit = {
        val blocked = when {
            provider == null -> context.getString(R.string.chat_page_voice_configure_asr)
            !provider.supportsServerVadVoiceMode -> context.getString(R.string.chat_page_voice_unsupported_asr)
            settings.getCurrentChatModel() == null -> context.getString(R.string.chat_page_voice_select_model)
            asr.state.value.isRecording -> context.getString(R.string.chat_page_voice_finish_dictation)
            vm.messageQueue.value.paused && vm.messageQueue.value.messages.isNotEmpty() ->
                context.getString(R.string.chat_page_voice_resume_queue)
            vm.conversation.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            } -> context.getString(R.string.chat_page_voice_pending_tools)
            else -> null
        }
        when {
            blocked != null -> Toast.show(message = blocked)
            !permission.allRequiredPermissionsGranted -> permission.requestPermissions()
            else -> voice.start(
                createAsr = { createVoiceAsr(context, client, checkNotNull(provider)) },
                speak = if (tts.isAvailable.value) {
                    { reply ->
                        var text = reply
                        if (settings.displaySetting.ttsOnlyReadQuoted) {
                            text = text.extractQuotedContentAsText() ?: text
                        }
                        if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                            text = text.removeBracketedContent() ?: text
                        }
                        text = text.stripMarkdown()
                        if (text.isNotBlank()) {
                            tts.speak(text)
                            // playbackState.Ended is also emitted between chunks. isSpeaking
                            // remains true until the entire synthesis/playback queue finishes.
                            combine(tts.isSpeaking, tts.error) { speaking, error ->
                                check(error == null) { error.orEmpty() }
                                !speaking
                            }.first { it }
                        }
                    }
                } else null,
                stopSpeaking = tts::stop,
            )
        }
    }

    if (state.isActive) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
    return start
}

private fun createVoiceAsr(context: Context, client: OkHttpClient, provider: ASRProviderSetting): ASRController {
    val delegate = when (provider) {
        is ASRProviderSetting.OpenAIRealtime -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            OpenAIRealtimeASRController(context, client, provider)
        }
        is ASRProviderSetting.DashScope -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            DashScopeASRController(context, client, provider)
        }
        is ASRProviderSetting.Volcengine -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            VolcengineASRController(context, client, provider)
        }
        else -> error(context.getString(R.string.chat_page_voice_no_endpointing))
    }
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .build()
    return object : ASRController by delegate {
        override fun start(onTranscriptChange: (String) -> Unit) {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "Unable to acquire audio focus for the microphone. Try again later."
            }
            delegate.start(onTranscriptChange)
        }

        override fun dispose() {
            try { delegate.dispose() } finally { audioManager.abandonAudioFocusRequest(focus) }
        }
    }
}
