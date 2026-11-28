package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListSection

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // 分组卡片之间 16dp，与设置主页一致；左右留白由外层底部弹窗给出
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // Provider type selector
        val context = LocalContext.current
        val resources = LocalResources.current
        val providers = remember { TTSProviderSetting.Types }
        val providerNames = providers.map { providerClass ->
            when (providerClass) {
                TTSProviderSetting.OpenAI::class -> "OpenAI"
                TTSProviderSetting.Gemini::class -> "Gemini"
                TTSProviderSetting.SystemTTS::class -> "System TTS"
                TTSProviderSetting.MiniMax::class -> resources.getString(R.string.setting_speech_vendor_minimax)
                TTSProviderSetting.Qwen::class -> resources.getString(R.string.setting_speech_vendor_qwen)
                TTSProviderSetting.Groq::class -> resources.getString(R.string.setting_speech_vendor_groq)
                TTSProviderSetting.XAI::class -> resources.getString(R.string.setting_speech_vendor_xai)
                TTSProviderSetting.MiMo::class -> "MiMo"
                TTSProviderSetting.ElevenLabs::class -> resources.getString(R.string.setting_speech_vendor_elevenlabs)
                TTSProviderSetting.FishAudio::class -> resources.getString(R.string.setting_speech_vendor_fish_audio)
                TTSProviderSetting.Volcengine::class -> resources.getString(R.string.setting_speech_vendor_volcengine)
                TTSProviderSetting.Step::class -> "Step"
                else -> providerClass.simpleName ?: resources.getString(R.string.setting_speech_unknown_provider)
            }
        }.toImmutableList()
        val currentTypeName = when (setting) {
            is TTSProviderSetting.OpenAI -> "OpenAI"
            is TTSProviderSetting.Gemini -> "Gemini"
            is TTSProviderSetting.SystemTTS -> "System TTS"
            is TTSProviderSetting.MiniMax -> resources.getString(R.string.setting_speech_vendor_minimax)
            is TTSProviderSetting.Qwen -> resources.getString(R.string.setting_speech_vendor_qwen)
            is TTSProviderSetting.Groq -> resources.getString(R.string.setting_speech_vendor_groq)
            is TTSProviderSetting.XAI -> resources.getString(R.string.setting_speech_vendor_xai)
            is TTSProviderSetting.MiMo -> "MiMo"
            is TTSProviderSetting.Step -> "Step"
            is TTSProviderSetting.ElevenLabs -> resources.getString(R.string.setting_speech_vendor_elevenlabs)
            is TTSProviderSetting.FishAudio -> resources.getString(R.string.setting_speech_vendor_fish_audio)
            is TTSProviderSetting.Volcengine -> resources.getString(R.string.setting_speech_vendor_volcengine)
        }

        MochiListSection {
            // 切换类型会按同一个 id 新建一份该类型的默认配置
            MochiListPickerRow(
                title = stringResource(R.string.setting_tts_page_provider_type),
                options = providerNames,
                selectedIndex = providerNames.indexOf(currentTypeName),
                onSelect = { index ->
                    val providerClass = providers.getOrNull(index) ?: return@MochiListPickerRow
                    val newSetting = when (providerClass) {
                        TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
                            id = setting.id,
                            name = "OpenAI TTS"
                        )

                        TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
                            id = setting.id,
                            name = "Gemini TTS"
                        )

                        TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                            id = setting.id,
                            name = "System TTS"
                        )

                        TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
                            id = setting.id,
                            name = "MiniMax TTS"
                        )

                        TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
                            id = setting.id,
                            name = "Qwen TTS"
                        )

                        TTSProviderSetting.Groq::class -> TTSProviderSetting.Groq(
                            id = setting.id,
                            name = "Groq TTS"
                        )

                        TTSProviderSetting.XAI::class -> TTSProviderSetting.XAI(
                            id = setting.id,
                            name = "xAI TTS"
                        )

                        TTSProviderSetting.MiMo::class -> TTSProviderSetting.MiMo(
                            id = setting.id,
                            name = "MiMo TTS"
                        )

                        TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                            id = setting.id,
                            name = "ElevenLabs TTS"
                        )

                        TTSProviderSetting.FishAudio::class -> TTSProviderSetting.FishAudio(
                            id = setting.id,
                            name = "Fish Audio TTS"
                        )

                        TTSProviderSetting.Step::class -> TTSProviderSetting.Step(
                            id = setting.id,
                            name = "Step TTS"
                        )

                        TTSProviderSetting.Volcengine::class -> TTSProviderSetting.Volcengine(id = setting.id)

                        else -> setting
                    }
                    onValueChange(newSetting)
                },
            )

            // Name
            SpeechTextFieldRow(
                label = stringResource(R.string.setting_tts_page_name),
                value = setting.name,
                onValueChange = { newName ->
                    onValueChange(setting.copyProvider(name = newName))
                },
                placeholder = stringResource(R.string.setting_tts_page_name_placeholder),
            )
        }

        // Provider-specific fields
        when (setting) {
            is TTSProviderSetting.OpenAI -> OpenAITTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Gemini -> GeminiTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.MiniMax -> MiniMaxTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Qwen -> QwenTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Groq -> GroqTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.XAI -> XAITTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.MiMo -> MiMoTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.ElevenLabs -> ElevenLabsTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.FishAudio -> FishAudioTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Step -> StepTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Volcengine -> VolcengineTTSConfiguration(setting, onValueChange)
        }
    }
}

/** 连接分组：API Key（打码 + 显示切换）+ Base URL，各服务共用。 */
@Composable
private fun TTSConnectionSection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKeyPlaceholder: String = "",
    baseUrlPlaceholder: String = "",
    apiKeyLabel: String = stringResource(R.string.setting_tts_page_api_key),
    footer: String? = null,
) {
    MochiListSection(footer = footer) {
        // API Key
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = apiKeyPlaceholder,
        )
        // Base URL
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_base_url),
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            placeholder = baseUrlPlaceholder,
        )
    }
}

@Composable
private fun OpenAITTSConfiguration(
    setting: TTSProviderSetting.OpenAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = stringResource(R.string.setting_tts_page_api_key_placeholder_openai),
        baseUrlPlaceholder = stringResource(R.string.setting_tts_page_base_url_placeholder),
    )

    // Voice
    val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

    MochiListSection {
        // Model
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            placeholder = stringResource(R.string.setting_tts_page_model_placeholder_openai),
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice -> onValueChange(setting.copy(voice = newVoice)) },
            onOptionSelected = { voice -> onValueChange(setting.copy(voice = voice)) },
            optionToString = { it },
        )
    }
}

@Composable
private fun MiMoTTSConfiguration(
    setting: TTSProviderSetting.MiMo,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // MiMo 配置均为自由输入 默认值只是占位
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "mimo-xxx",
        baseUrlPlaceholder = "https://api.xiaomimimo.com/v1",
    )

    // Voice
    val voices = listOf(
        "mimo_default",
        "冰糖",
        "茉莉",
        "苏打",
        "白桦",
        "Mia",
        "Chloe",
        "Milo",
        "Dean"
    )

    MochiListSection {
        // Model
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            placeholder = "mimo-v2-tts",
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice -> onValueChange(setting.copy(voice = newVoice)) },
            onOptionSelected = { voice -> onValueChange(setting.copy(voice = voice)) },
            optionToString = { it },
            placeholder = "mimo_default",
        )
    }
}

@Composable
private fun MiniMaxTTSConfiguration(
    setting: TTSProviderSetting.MiniMax,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        baseUrlPlaceholder = stringResource(R.string.setting_tts_page_base_url_placeholder),
    )

    // Voice ID
    val voiceIds = listOf(
        "male-qn-qingse",
        "male-qn-jingying",
        "male-qn-badao",
        "male-qn-daxuesheng",
        "female-shaonv",
        "female-yujie",
        "female-chengshu",
        "female-tianmei",
        "audiobook_male_1",
        "audiobook_female_1",
        "cartoon_pig"
    )

    val speedLabel = stringResource(R.string.setting_tts_page_speed)
    MochiListSection(
        footer = speechFooter(speedLabel to stringResource(R.string.setting_tts_page_speed_description)),
    ) {
        // Model
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            placeholder = "speech-2.8-hd",
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice_id),
            value = setting.voiceId,
            options = voiceIds,
            onValueChange = { newVoiceId -> onValueChange(setting.copy(voiceId = newVoiceId)) },
            onOptionSelected = { voiceId -> onValueChange(setting.copy(voiceId = voiceId)) },
            optionToString = { it },
        )
        // Speed
        SpeechNumberFieldRow(
            label = speedLabel,
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.25f..4.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
        )
    }
}

@Composable
private fun GeminiTTSConfiguration(
    setting: TTSProviderSetting.Gemini,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = stringResource(R.string.setting_tts_page_api_key_placeholder_gemini),
        baseUrlPlaceholder = stringResource(R.string.setting_tts_page_base_url_placeholder),
    )

    MochiListSection {
        // Model
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            placeholder = stringResource(R.string.setting_tts_page_model_placeholder_gemini),
        )
        // Voice Name
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_voice_name),
            value = setting.voiceName,
            onValueChange = { newVoiceName -> onValueChange(setting.copy(voiceName = newVoiceName)) },
            placeholder = stringResource(R.string.setting_tts_page_voice_name_placeholder),
        )
    }
}

@Composable
private fun SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val rateLabel = stringResource(R.string.setting_tts_page_speech_rate)
    val pitchLabel = stringResource(R.string.setting_tts_page_pitch)
    MochiListSection(
        footer = speechFooter(
            rateLabel to stringResource(R.string.setting_tts_page_speech_rate_description),
            pitchLabel to stringResource(R.string.setting_tts_page_pitch_description),
        ),
    ) {
        // Speech Rate
        SpeechNumberFieldRow(
            label = rateLabel,
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
        )
        // Pitch
        SpeechNumberFieldRow(
            label = pitchLabel,
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
        )
    }
}

@Composable
private fun QwenTTSConfiguration(
    setting: TTSProviderSetting.Qwen,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "sk-xxx",
        baseUrlPlaceholder = stringResource(R.string.setting_tts_page_base_url_placeholder),
    )

    // Model
    val models = listOf(
        "qwen-audio-3.0-tts-flash",
        "qwen-audio-3.0-tts-plus",
    )

    // Voice
    val voices = when (setting.model) {
        "qwen-audio-3.0-tts-plus" -> listOf("longanlingxin", "longanlufeng")
        "qwen-audio-3.0-tts-flash" -> listOf(
            "longanfengyue", "longanyuanfei", "longanlingxi", "longanxiaoxin",
            "longanhuan_v3.6", "longjielidou_v3.6", "longpaopao_v3.6",
            "longhuohuo_v3.6", "longchuanshu_v3.6", "loongmary",
            "loongeva_v3.6", "loongjohn",
        )

        else -> emptyList()
    }

    MochiListSection {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            options = models,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            onOptionSelected = { model ->
                // 选预设模型时顺带换成该模型的默认音色
                val defaultVoice = when (model) {
                    "qwen-audio-3.0-tts-plus" -> "longanlingxin"
                    "qwen-audio-3.0-tts-flash" -> "longanhuan_v3.6"
                    else -> setting.voice
                }
                onValueChange(setting.copy(model = model, voice = defaultVoice))
            },
            optionToString = { it },
            placeholder = "qwen-audio-3.0-tts-flash",
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice -> onValueChange(setting.copy(voice = newVoice)) },
            onOptionSelected = { voice -> onValueChange(setting.copy(voice = voice)) },
            optionToString = { it },
        )
    }

    // Audio Format
    val formats = listOf("wav", "mp3", "pcm", "opus")
    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000, 44100, 48000)

    MochiListSection(
        footer = speechFooter(
            "Audio Format" to "Audio encoding returned by Qwen TTS",
            "Sample Rate" to "Audio sample rate in Hz",
        ),
    ) {
        MochiListPickerRow(
            title = stringResource(R.string.setting_tts_label_audio_format),
            options = formats.toImmutableList(),
            selectedIndex = formats.indexOf(setting.format),
            onSelect = { index ->
                formats.getOrNull(index)?.let { format -> onValueChange(setting.copy(format = format)) }
            },
        )
        MochiListPickerRow(
            title = stringResource(R.string.setting_tts_label_sample_rate),
            options = sampleRates.map { it.toString() }.toImmutableList(),
            selectedIndex = sampleRates.indexOf(setting.sampleRate),
            onSelect = { index ->
                sampleRates.getOrNull(index)?.let { sampleRate -> onValueChange(setting.copy(sampleRate = sampleRate)) }
            },
        )
    }
}

@Composable
private fun GroqTTSConfiguration(
    setting: TTSProviderSetting.Groq,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "gsk_xxx",
        baseUrlPlaceholder = stringResource(R.string.setting_tts_page_base_url_placeholder),
    )

    // Voice
    val voices = listOf("austin", "natalie", "kailin")

    MochiListSection {
        // Model
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            placeholder = "canopylabs/orpheus-v1-english",
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice -> onValueChange(setting.copy(voice = newVoice)) },
            onOptionSelected = { voice -> onValueChange(setting.copy(voice = voice)) },
            optionToString = { it },
        )
    }
}

@Composable
private fun XAITTSConfiguration(
    setting: TTSProviderSetting.XAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "xai-xxx",
        baseUrlPlaceholder = "https://api.x.ai/v1",
    )

    // Voice ID
    val voices = listOf(
        "eve" to "Eve",
        "ara" to "Ara",
        "rex" to "Rex",
        "sal" to "Sal",
        "leo" to "Leo"
    )

    // Language
    val languages = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "fr" to "French",
        "de" to "German",
        "es-ES" to "Spanish (Spain)",
        "es-MX" to "Spanish (Mexico)",
        "pt-BR" to "Portuguese (Brazil)",
        "pt-PT" to "Portuguese (Portugal)",
        "it" to "Italian",
        "ru" to "Russian",
        "ar-EG" to "Arabic (Egypt)",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "bn" to "Bengali"
    )

    MochiListSection {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voiceId,
            options = voices,
            onValueChange = { newVoiceId -> onValueChange(setting.copy(voiceId = newVoiceId)) },
            onOptionSelected = { (voiceId, _) -> onValueChange(setting.copy(voiceId = voiceId)) },
            optionToString = { (_, description) -> description },
            selectedIndex = voices.indexOfFirst { it.first == setting.voiceId },
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_label_language),
            value = setting.language,
            options = languages,
            onValueChange = { newLanguage -> onValueChange(setting.copy(language = newLanguage)) },
            onOptionSelected = { (code, _) -> onValueChange(setting.copy(language = code)) },
            optionToString = { (code, displayName) -> "$displayName ($code)" },
            selectedIndex = languages.indexOfFirst { it.first == setting.language },
        )
    }
}

@Composable
private fun ElevenLabsTTSConfiguration(
    setting: TTSProviderSetting.ElevenLabs,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "sk_...",
        baseUrlPlaceholder = "https://api.elevenlabs.io",
    )

    // Model
    val models = listOf(
        "eleven_multilingual_v2" to "Eleven Multilingual v2",
        "eleven_v3" to "Eleven v3",
        "eleven_flash_v2_5" to "Eleven Flash v2.5"
    )

    MochiListSection {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            options = models,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            onOptionSelected = { (modelId, _) -> onValueChange(setting.copy(model = modelId)) },
            optionToString = { (modelId, displayName) -> "$displayName ($modelId)" },
            selectedIndex = models.indexOfFirst { it.first == setting.model },
        )
        // Voice ID
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voiceId,
            onValueChange = { newVoiceId -> onValueChange(setting.copy(voiceId = newVoiceId)) },
            placeholder = "JBFqnCBsd6RMkjVDRZzb",
        )
    }

    val stabilityLabel = stringResource(R.string.setting_tts_page_stability)
    val similarityLabel = stringResource(R.string.setting_tts_page_similarity_boost)
    MochiListSection(
        footer = speechFooter(
            stabilityLabel to stringResource(R.string.setting_tts_page_stability_description),
            similarityLabel to stringResource(R.string.setting_tts_page_similarity_boost_description),
        ),
    ) {
        // Stability
        SpeechNumberFieldRow(
            label = stabilityLabel,
            value = setting.stability,
            onValueChange = { newStability ->
                onValueChange(setting.copy(stability = newStability.coerceIn(0f, 1f)))
            },
            placeholder = "0.5",
        )
        // Similarity Boost
        SpeechNumberFieldRow(
            label = similarityLabel,
            value = setting.similarityBoost,
            onValueChange = { newSimilarityBoost ->
                onValueChange(setting.copy(similarityBoost = newSimilarityBoost.coerceIn(0f, 1f)))
            },
            placeholder = "0.75",
        )
    }
}

@Composable
private fun FishAudioTTSConfiguration(
    setting: TTSProviderSetting.FishAudio,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = "https://fish.audio/app/api-keys",
        baseUrlPlaceholder = "https://api.fish.audio",
    )

    // Model (可自由输入 + 预设下拉，同 ElevenLabs)
    val models = listOf(
        "s2.1-pro" to stringResource(R.string.setting_tts_fish_model_s21_pro),
        "s2.1-pro-free" to stringResource(R.string.setting_tts_fish_model_s21_pro_free),
        "s2-pro" to "S2-Pro",
        "s1" to "S1"
    )

    MochiListSection {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            options = models,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            onOptionSelected = { (modelId, _) -> onValueChange(setting.copy(model = modelId)) },
            optionToString = { (modelId, displayName) -> "$displayName ($modelId)" },
            selectedIndex = models.indexOfFirst { it.first == setting.model },
        )
        // Voice ID (reference_id)
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_voice_id),
            value = setting.referenceId,
            onValueChange = { newReferenceId -> onValueChange(setting.copy(referenceId = newReferenceId)) },
            placeholder = "802e3bc2b27e49c2995d23ef70e6ac89",
        )
    }

    val temperatureLabel = stringResource(R.string.setting_tts_page_temperature)
    val speedLabel = stringResource(R.string.setting_tts_page_speed)
    MochiListSection(
        footer = speechFooter(
            temperatureLabel to stringResource(R.string.setting_tts_page_temperature_description),
            speedLabel to stringResource(R.string.setting_tts_page_fish_audio_speed_description),
        ),
    ) {
        // Temperature
        SpeechNumberFieldRow(
            label = temperatureLabel,
            value = setting.temperature,
            onValueChange = { newTemperature ->
                onValueChange(setting.copy(temperature = newTemperature.coerceIn(0f, 1f)))
            },
            placeholder = "0.7",
        )
        // Speed
        SpeechNumberFieldRow(
            label = speedLabel,
            value = setting.speed,
            onValueChange = { newSpeed ->
                onValueChange(setting.copy(speed = newSpeed.coerceIn(0.5f, 2f)))
            },
            placeholder = "1.0",
        )
    }
}

@Composable
private fun StepTTSConfiguration(
    setting: TTSProviderSetting.Step,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_tts_page_api_key)
    TTSConnectionSection(
        apiKey = setting.apiKey,
        onApiKeyChange = { newApiKey -> onValueChange(setting.copy(apiKey = newApiKey)) },
        baseUrl = setting.baseUrl,
        onBaseUrlChange = { newBaseUrl -> onValueChange(setting.copy(baseUrl = newBaseUrl)) },
        apiKeyPlaceholder = stringResource(R.string.setting_tts_step_api_key_placeholder),
        baseUrlPlaceholder = "https://api.stepfun.com",
        apiKeyLabel = apiKeyLabel,
        footer = speechFooter(apiKeyLabel to stringResource(R.string.setting_tts_step_api_key_footer)),
    )

    // Model
    val models = listOf(
        "step-tts-mini" to stringResource(R.string.setting_tts_step_model_mini),
        "step-tts-vivid" to stringResource(R.string.setting_tts_step_model_vivid),
        "stepaudio-2.5-tts" to stringResource(R.string.setting_tts_step_model_audio25),
        "step-tts-2" to stringResource(R.string.setting_tts_step_model_tts2),
    )

    // Voice
    // 部分常用 voice-id, 完整列表见官方开发指南
    // https://platform.stepfun.com/docs/zh/guides/developer/tts
    val voices = listOf(
        "elegantgentle-female" to stringResource(R.string.setting_tts_volc_voice_elegantgentle_female),
        "livelybreezy-female" to stringResource(R.string.setting_tts_volc_voice_livelybreezy_female),
        "energeticconfident-female" to stringResource(R.string.setting_tts_volc_voice_energeticconfident_female),
        "jingdiannvsheng" to stringResource(R.string.setting_tts_volc_voice_jingdiannvsheng),
        "wenroushunv" to stringResource(R.string.setting_tts_volc_voice_wenroushunv),
        "tianmeinvsheng" to stringResource(R.string.setting_tts_volc_voice_tianmeinvsheng),
        "qingchunshaonv" to stringResource(R.string.setting_tts_volc_voice_qingchunshaonv),
        "wenrounvsheng" to stringResource(R.string.setting_tts_volc_voice_wenrounvsheng),
        "ruanmengnvsheng" to stringResource(R.string.setting_tts_volc_voice_ruanmengnvsheng),
        "youyanvsheng" to stringResource(R.string.setting_tts_volc_voice_youyanvsheng),
        "lengyanyujie" to stringResource(R.string.setting_tts_volc_voice_lengyanyujie),
        "shuangkuaijiejie" to stringResource(R.string.setting_tts_volc_voice_shuangkuaijiejie),
        "wenjingxuejie" to stringResource(R.string.setting_tts_volc_voice_wenjingxuejie),
        "linjiajiejie" to stringResource(R.string.setting_tts_volc_voice_linjiajiejie),
        "linjiameimei" to stringResource(R.string.setting_tts_volc_voice_linjiameimei),
        "zhixingjiejie" to stringResource(R.string.setting_tts_volc_voice_zhixingjiejie),
        "cixingnansheng" to stringResource(R.string.setting_tts_volc_voice_cixingnansheng),
        "wenrounansheng" to stringResource(R.string.setting_tts_volc_voice_wenrounansheng),
        "yuanqinansheng" to stringResource(R.string.setting_tts_volc_voice_yuanqinansheng),
        "zhengpaiqingnian" to stringResource(R.string.setting_tts_volc_voice_zhengpaiqingnian),
        "ruyananshi" to stringResource(R.string.setting_tts_volc_voice_ruyananshi),
        "boyinnansheng" to stringResource(R.string.setting_tts_volc_voice_boyinnansheng),
        "shenchennanyin" to stringResource(R.string.setting_tts_volc_voice_shenchennanyin),
        "shuangkuainansheng" to stringResource(R.string.setting_tts_volc_voice_shuangkuainansheng),
        "ganliannvsheng" to stringResource(R.string.setting_tts_volc_voice_ganliannvsheng),
        "qinhenvsheng" to stringResource(R.string.setting_tts_volc_voice_qinhenvsheng),
        "huolinvsheng" to stringResource(R.string.setting_tts_volc_voice_huolinvsheng),
        "jilingshaonv" to stringResource(R.string.setting_tts_volc_voice_jilingshaonv),
        "yuanqishaonv" to stringResource(R.string.setting_tts_volc_voice_yuanqishaonv),
        "wenrougongzi" to stringResource(R.string.setting_tts_volc_voice_wenrougongzi),
        "qingniandaxuesheng" to stringResource(R.string.setting_tts_volc_voice_qingniandaxuesheng),
    )

    MochiListSection {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_model),
            value = setting.model,
            options = models,
            onValueChange = { newModel -> onValueChange(setting.copy(model = newModel)) },
            onOptionSelected = { (modelId, _) -> onValueChange(setting.copy(model = modelId)) },
            optionToString = { (_, description) -> description },
            selectedIndex = models.indexOfFirst { it.first == setting.model },
        )
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_page_voice),
            value = setting.voice,
            options = voices,
            onValueChange = { newVoice -> onValueChange(setting.copy(voice = newVoice)) },
            onOptionSelected = { (voiceId, _) -> onValueChange(setting.copy(voice = voiceId)) },
            optionToString = { (_, description) -> description },
            selectedIndex = voices.indexOfFirst { it.first == setting.voice },
        )
    }

    // Response Format
    val formats = listOf("mp3", "wav", "pcm", "opus", "flac")
    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000)

    val speedLabel = stringResource(R.string.setting_tts_page_speed)
    MochiListSection(
        footer = speechFooter(
            stringResource(R.string.setting_tts_label_response_format) to stringResource(R.string.setting_tts_option_response_format),
            speedLabel to stringResource(R.string.setting_tts_option_speed),
            stringResource(R.string.setting_tts_label_volume) to stringResource(R.string.setting_tts_option_volume),
            stringResource(R.string.setting_tts_label_sample_rate) to stringResource(R.string.setting_tts_option_sample_rate),
        ),
    ) {
        SpeechPresetFieldRow(
            label = stringResource(R.string.setting_tts_label_response_format),
            value = setting.responseFormat,
            options = formats,
            onValueChange = { newFormat -> onValueChange(setting.copy(responseFormat = newFormat)) },
            onOptionSelected = { format -> onValueChange(setting.copy(responseFormat = format)) },
            optionToString = { it },
        )
        // Speed
        SpeechNumberFieldRow(
            label = speedLabel,
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.5f..2.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
        )
        // Volume
        SpeechNumberFieldRow(
            label = stringResource(R.string.setting_tts_label_volume),
            value = setting.volume,
            onValueChange = { newVolume ->
                if (newVolume in 0.1f..2.0f) {
                    onValueChange(setting.copy(volume = newVolume))
                }
            },
        )
        MochiListPickerRow(
            title = stringResource(R.string.setting_tts_label_sample_rate),
            options = sampleRates.map { rate -> "$rate Hz" }.toImmutableList(),
            selectedIndex = sampleRates.indexOf(setting.sampleRate),
            onSelect = { index ->
                sampleRates.getOrNull(index)?.let { rate -> onValueChange(setting.copy(sampleRate = rate)) }
            },
        )
    }

    // Instruction (仅 stepaudio-2.5-tts 生效)
    MochiListSection(
        footer = speechFooter(stringResource(R.string.setting_tts_label_instruction) to stringResource(R.string.setting_tts_option_instruction)),
    ) {
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_label_instruction),
            value = setting.instruction,
            onValueChange = { newInstruction ->
                // 服务端上限 200 字符, 客户端做一层保护
                if (newInstruction.length <= 200) {
                    onValueChange(setting.copy(instruction = newInstruction))
                }
            },
            placeholder = stringResource(R.string.setting_tts_option_instruction_placeholder),
            singleLine = false,
            maxLines = 4,
        )
    }
}

@Composable
private fun VolcengineTTSConfiguration(
    setting: TTSProviderSetting.Volcengine,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    MochiListSection(
        footer = speechFooter(
            stringResource(R.string.setting_tts_page_api_key) to stringResource(R.string.setting_tts_volc_api_key_desc),
            stringResource(R.string.setting_tts_volc_resource_id) to stringResource(R.string.setting_tts_volc_resource_id_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = stringResource(R.string.setting_tts_page_api_key),
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
        )
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_page_base_url),
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = "https://openspeech.bytedance.com",
        )
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_volc_resource_id),
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
        )
    }

    val speedLabel = stringResource(R.string.setting_tts_page_speed)
    MochiListSection(
        footer = speechFooter(
            stringResource(R.string.setting_tts_volc_voice_id) to stringResource(R.string.setting_tts_volc_voice_id_desc),
            speedLabel to stringResource(R.string.setting_tts_volc_speed_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = stringResource(R.string.setting_tts_volc_voice_id),
            value = setting.speaker,
            onValueChange = { onValueChange(setting.copy(speaker = it)) },
        )
        SpeechNumberFieldRow(
            label = speedLabel,
            value = setting.speechRate,
            onValueChange = { onValueChange(setting.copy(speechRate = it.coerceIn(-50, 100))) },
        )
    }
}
