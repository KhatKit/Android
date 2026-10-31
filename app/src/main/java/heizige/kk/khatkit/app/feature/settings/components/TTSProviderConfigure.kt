package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
        val providers = remember { TTSProviderSetting.Types }
        val providerNames = providers.map { providerClass ->
            when (providerClass) {
                TTSProviderSetting.OpenAI::class -> "OpenAI"
                TTSProviderSetting.Gemini::class -> "Gemini"
                TTSProviderSetting.SystemTTS::class -> "System TTS"
                TTSProviderSetting.MiniMax::class -> "MiniMax"
                TTSProviderSetting.Qwen::class -> "Qwen"
                TTSProviderSetting.Groq::class -> "Groq"
                TTSProviderSetting.XAI::class -> "xAI"
                TTSProviderSetting.MiMo::class -> "MiMo"
                TTSProviderSetting.ElevenLabs::class -> "ElevenLabs"
                TTSProviderSetting.FishAudio::class -> "Fish Audio"
                TTSProviderSetting.Volcengine::class -> "火山引擎"
                TTSProviderSetting.Step::class -> "Step"
                else -> providerClass.simpleName ?: "Unknown"
            }
        }.toImmutableList()
        val currentTypeName = when (setting) {
            is TTSProviderSetting.OpenAI -> "OpenAI"
            is TTSProviderSetting.Gemini -> "Gemini"
            is TTSProviderSetting.SystemTTS -> "System TTS"
            is TTSProviderSetting.MiniMax -> "MiniMax"
            is TTSProviderSetting.Qwen -> "Qwen"
            is TTSProviderSetting.Groq -> "Groq"
            is TTSProviderSetting.XAI -> "xAI"
            is TTSProviderSetting.MiMo -> "MiMo"
            is TTSProviderSetting.Step -> "Step"
            is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
            is TTSProviderSetting.FishAudio -> "Fish Audio"
            is TTSProviderSetting.Volcengine -> "火山引擎"
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
            title = "Audio Format",
            options = formats.toImmutableList(),
            selectedIndex = formats.indexOf(setting.format),
            onSelect = { index ->
                formats.getOrNull(index)?.let { format -> onValueChange(setting.copy(format = format)) }
            },
        )
        MochiListPickerRow(
            title = "Sample Rate",
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
            label = "Language",
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
        "s2.1-pro" to "S2.1-Pro (推荐)",
        "s2.1-pro-free" to "S2.1-Pro Free (免费)",
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
        apiKeyPlaceholder = "从阶跃星辰官网获取密钥",
        baseUrlPlaceholder = "https://api.stepfun.com",
        apiKeyLabel = apiKeyLabel,
        footer = speechFooter(apiKeyLabel to "从阶跃星辰官网获取密钥: platform.stepfun.com/interface-key"),
    )

    // Model
    val models = listOf(
        "step-tts-mini" to "step-tts-mini (轻量, 便宜)",
        "step-tts-vivid" to "step-tts-vivid (情感丰富)",
        "stepaudio-2.5-tts" to "stepaudio-2.5-tts (语境感知, 支持 instruction)",
        "step-tts-2" to "step-tts-2 (上一代)",
    )

    // Voice
    // 部分常用 voice-id, 完整列表见官方开发指南
    // https://platform.stepfun.com/docs/zh/guides/developer/tts
    val voices = listOf(
        "elegantgentle-female" to "气质温婉 (elegantgentle-female)",
        "livelybreezy-female" to "活力轻快 (livelybreezy-female)",
        "energeticconfident-female" to "活力自信 (energeticconfident-female)",
        "jingdiannvsheng" to "经典女声 (jingdiannvsheng)",
        "wenroushunv" to "温柔熟女 (wenroushunv)",
        "tianmeinvsheng" to "甜美女声 (tianmeinvsheng)",
        "qingchunshaonv" to "清纯少女 (qingchunshaonv)",
        "wenrounvsheng" to "温柔女声 (wenrounvsheng)",
        "ruanmengnvsheng" to "软萌女生 (ruanmengnvsheng)",
        "youyanvsheng" to "优雅女生 (youyanvsheng)",
        "lengyanyujie" to "冷艳御姐 (lengyanyujie)",
        "shuangkuaijiejie" to "爽快姐姐 (shuangkuaijiejie)",
        "wenjingxuejie" to "文静学姐 (wenjingxuejie)",
        "linjiajiejie" to "邻家姐姐 (linjiajiejie)",
        "linjiameimei" to "邻家妹妹 (linjiameimei)",
        "zhixingjiejie" to "知性姐姐 (zhixingjiejie)",
        "cixingnansheng" to "磁性男声 (cixingnansheng)",
        "wenrounansheng" to "温柔男声 (wenrounansheng)",
        "yuanqinansheng" to "元气男声 (yuanqinansheng)",
        "zhengpaiqingnian" to "正派青年 (zhengpaiqingnian)",
        "ruyananshi" to "儒雅男士 (ruyananshi)",
        "boyinnansheng" to "播音男声 (boyinnansheng)",
        "shenchennanyin" to "深沉男音 (shenchennanyin)",
        "shuangkuainansheng" to "爽快男声 (shuangkuainansheng)",
        "ganliannvsheng" to "干练女声 (ganliannvsheng)",
        "qinhenvsheng" to "亲切女声 (qinhenvsheng)",
        "huolinvsheng" to "活力女声 (huolinvsheng)",
        "jilingshaonv" to "机灵少女 (jilingshaonv)",
        "yuanqishaonv" to "元气少女 (yuanqishaonv)",
        "wenrougongzi" to "温柔公子 (wenrougongzi)",
        "qingniandaxuesheng" to "青年大学生 (qingniandaxuesheng)",
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
            "Response Format" to "音频编码格式 (注意 StepFun API 字段名为 camelCase)",
            speedLabel to "语速 (0.5 - 2.0, 1.0 为正常)",
            "Volume" to "音量 (0.1 - 2.0, 1.0 为正常)",
            "Sample Rate" to "采样率 (Hz)",
        ),
    ) {
        SpeechPresetFieldRow(
            label = "Response Format",
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
            label = "Volume",
            value = setting.volume,
            onValueChange = { newVolume ->
                if (newVolume in 0.1f..2.0f) {
                    onValueChange(setting.copy(volume = newVolume))
                }
            },
        )
        MochiListPickerRow(
            title = "Sample Rate",
            options = sampleRates.map { rate -> "$rate Hz" }.toImmutableList(),
            selectedIndex = sampleRates.indexOf(setting.sampleRate),
            onSelect = { index ->
                sampleRates.getOrNull(index)?.let { rate -> onValueChange(setting.copy(sampleRate = rate)) }
            },
        )
    }

    // Instruction (仅 stepaudio-2.5-tts 生效)
    MochiListSection(
        footer = speechFooter("Instruction" to "全局语境指令, 仅 stepaudio-2.5-tts 生效 (≤200 字符, 留空不下发)"),
    ) {
        SpeechTextFieldRow(
            label = "Instruction",
            value = setting.instruction,
            onValueChange = { newInstruction ->
                // 服务端上限 200 字符, 客户端做一层保护
                if (newInstruction.length <= 200) {
                    onValueChange(setting.copy(instruction = newInstruction))
                }
            },
            placeholder = "例如: 语气温柔, 语速偏慢",
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
            "API Key" to "请填写豆包语音控制台的 API Key，不是火山方舟控制台的 API Key。",
            "资源 ID" to "需与已开通的服务和音色匹配，默认 seed-tts-2.0。",
        ),
    ) {
        SpeechSecretFieldRow(
            label = "API Key",
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
            label = "资源 ID",
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
        )
    }

    val speedLabel = stringResource(R.string.setting_tts_page_speed)
    MochiListSection(
        footer = speechFooter(
            "音色 ID" to "填写控制台中的音色 ID，默认使用 VV 音色。",
            speedLabel to "范围 -50～100，0 为正常语速，-50 为半速，100 为两倍速。",
        ),
    ) {
        SpeechTextFieldRow(
            label = "音色 ID",
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
