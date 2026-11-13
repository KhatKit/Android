package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.visibility
import heizige.kk.khatkit.app.core.ui.icons.visibilityOff
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.asr.sherpa.SherpaModelManager
import heizige.kk.khatkit.asr.sherpa.SherpaModels
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.MochiProgressRing
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    // 分组卡片之间 16dp，与设置主页一致；左右留白由外层底部弹窗给出
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        val providerTypeLabel = stringResource(R.string.setting_asr_configure_provider_type)
        val nameLabel = stringResource(R.string.setting_asr_configure_name)
        MochiListSection(
            footer = speechFooter(
                providerTypeLabel to stringResource(R.string.setting_asr_configure_provider_type_desc),
                nameLabel to stringResource(R.string.setting_asr_configure_name_desc),
            ),
        ) {
            // 类型只读展示：创建时已经选定，这里不能切换
            MochiListRow(
                title = providerTypeLabel,
                value = when (setting) {
                    is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
                    is ASRProviderSetting.DashScope -> "DashScope"
                    is ASRProviderSetting.Volcengine -> "Volcengine"
                    is ASRProviderSetting.MiMo -> "MiMo"
                    is ASRProviderSetting.Step -> "Step"
                    is ASRProviderSetting.OpenAITranscribe -> "OpenAI Transcribe"
                    is ASRProviderSetting.GeminiTranscribe -> "Gemini Transcribe"
                    is ASRProviderSetting.SherpaLocal -> "本地识别 (sherpa-onnx)"
                },
            )
            SpeechTextFieldRow(
                label = nameLabel,
                value = setting.name,
                onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                placeholder = "OpenAI Realtime",
            )
        }

        when (setting) {
            is ASRProviderSetting.OpenAIRealtime -> OpenAIRealtimeASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.DashScope -> DashScopeASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.Volcengine -> VolcengineASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.MiMo -> MiMoASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.Step -> StepASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.OpenAITranscribe -> OpenAITranscribeASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.GeminiTranscribe -> GeminiTranscribeASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.SherpaLocal -> SherpaLocalASRConfiguration(setting, onValueChange)
        }
    }
}

@Composable
private fun GeminiTranscribeASRConfiguration(
    setting: ASRProviderSetting.GeminiTranscribe,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val baseUrlLabel = stringResource(R.string.setting_asr_configure_base_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_gemini_api_key_desc),
            baseUrlLabel to stringResource(R.string.setting_asr_configure_gemini_base_url_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "AIza...",
        )
        SpeechTextFieldRow(
            label = baseUrlLabel,
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = "https://generativelanguage.googleapis.com/v1beta",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    val sampleRateLabel = stringResource(R.string.setting_asr_configure_sample_rate)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_language_iso_desc),
            sampleRateLabel to stringResource(R.string.setting_asr_configure_sample_rate_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "gemini-2.5-flash",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
        SpeechNumberFieldRow(
            label = sampleRateLabel,
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            placeholder = "Sample Rate",
        )
    }
}

@Composable
private fun OpenAITranscribeASRConfiguration(
    setting: ASRProviderSetting.OpenAITranscribe,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val baseUrlLabel = stringResource(R.string.setting_asr_configure_base_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_openai_api_key_desc),
            baseUrlLabel to stringResource(R.string.setting_asr_configure_openai_transcribe_base_url_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "sk-...",
        )
        SpeechTextFieldRow(
            label = baseUrlLabel,
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = "https://api.openai.com/v1",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    val sampleRateLabel = stringResource(R.string.setting_asr_configure_sample_rate)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_language_iso_desc),
            sampleRateLabel to stringResource(R.string.setting_asr_configure_sample_rate_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "gpt-4o-transcribe",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
        SpeechNumberFieldRow(
            label = sampleRateLabel,
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            placeholder = "Sample Rate",
        )
    }
}

@Composable
private fun OpenAIRealtimeASRConfiguration(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val websocketLabel = stringResource(R.string.setting_asr_configure_websocket_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_openai_api_key_desc),
            websocketLabel to stringResource(R.string.setting_asr_configure_openai_websocket_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "sk-...",
        )
        SpeechTextFieldRow(
            label = websocketLabel,
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            placeholder = "wss://api.openai.com/v1/realtime?intent=transcription",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    val promptLabel = stringResource(R.string.setting_asr_configure_prompt)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_language_iso_desc),
            promptLabel to stringResource(R.string.setting_asr_configure_prompt_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "gpt-4o-transcribe",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
        // 提示词可能较长：多行输入
        SpeechTextFieldRow(
            label = promptLabel,
            value = setting.prompt,
            onValueChange = { onValueChange(setting.copy(prompt = it)) },
            placeholder = "Optional",
            singleLine = false,
        )
    }

    val vadLabel = stringResource(R.string.setting_asr_configure_vad_threshold)
    val prefixLabel = stringResource(R.string.setting_asr_configure_prefix_padding)
    val silenceLabel = stringResource(R.string.setting_asr_configure_silence_duration)
    MochiListSection(
        footer = speechFooter(
            vadLabel to stringResource(R.string.setting_asr_configure_vad_desc),
            prefixLabel to stringResource(R.string.setting_asr_configure_prefix_padding_desc),
            silenceLabel to stringResource(R.string.setting_asr_configure_silence_duration_desc),
        ),
    ) {
        SpeechNumberFieldRow(
            label = vadLabel,
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in 0.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            placeholder = "VAD Threshold",
        )
        SpeechNumberFieldRow(
            label = prefixLabel,
            value = setting.prefixPaddingMs,
            onValueChange = { value ->
                if (value in 0..2000) {
                    onValueChange(setting.copy(prefixPaddingMs = value))
                }
            },
            placeholder = "Prefix Padding",
        )
        SpeechNumberFieldRow(
            label = silenceLabel,
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 100..5000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            placeholder = "Silence Duration",
        )
    }
}

@Composable
private fun DashScopeASRConfiguration(
    setting: ASRProviderSetting.DashScope,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val websocketLabel = stringResource(R.string.setting_asr_configure_websocket_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_dashscope_api_key_desc),
            websocketLabel to stringResource(R.string.setting_asr_configure_dashscope_websocket_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "sk-...",
        )
        SpeechTextFieldRow(
            label = websocketLabel,
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            placeholder = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_language_iso_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "qwen3-asr-flash-realtime-2026-02-10",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "zh",
        )
    }

    val vadLabel = stringResource(R.string.setting_asr_configure_vad_threshold)
    val silenceLabel = stringResource(R.string.setting_asr_configure_silence_duration)
    MochiListSection(
        footer = speechFooter(
            vadLabel to stringResource(R.string.setting_asr_configure_dashscope_vad_desc),
            silenceLabel to stringResource(R.string.setting_asr_configure_silence_duration_desc),
        ),
    ) {
        SpeechNumberFieldRow(
            label = vadLabel,
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in -1.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            placeholder = "VAD Threshold",
        )
        SpeechNumberFieldRow(
            label = silenceLabel,
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 200..6000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            placeholder = "Silence Duration",
        )
    }
}

@Composable
private fun VolcengineASRConfiguration(
    setting: ASRProviderSetting.Volcengine,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val websocketLabel = stringResource(R.string.setting_asr_configure_websocket_url)
    val resourceLabel = stringResource(R.string.setting_asr_configure_resource_id)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_volcengine_api_key_desc),
            websocketLabel to stringResource(R.string.setting_asr_configure_volcengine_websocket_desc),
            resourceLabel to stringResource(R.string.setting_asr_configure_resource_id_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "your-api-key",
        )
        SpeechTextFieldRow(
            label = websocketLabel,
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            placeholder = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async",
        )
        SpeechTextFieldRow(
            label = resourceLabel,
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            placeholder = "volc.seedasr.sauc.duration",
        )
    }

    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    val silenceLabel = stringResource(R.string.setting_page_asr_silence_duration)
    MochiListSection(
        footer = speechFooter(
            languageLabel to stringResource(R.string.setting_asr_configure_language_code_desc),
            silenceLabel to stringResource(R.string.setting_page_asr_silence_duration_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
        SpeechNumberFieldRow(
            label = silenceLabel,
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 300..5000) onValueChange(setting.copy(silenceDurationMs = value))
            },
            placeholder = stringResource(R.string.setting_page_asr_milliseconds),
        )
    }
}

@Composable
private fun MiMoASRConfiguration(
    setting: ASRProviderSetting.MiMo,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val baseUrlLabel = stringResource(R.string.setting_asr_configure_base_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_mimo_api_key_desc),
            baseUrlLabel to stringResource(R.string.setting_asr_configure_mimo_base_url_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "sk-... or tp-...",
        )
        SpeechTextFieldRow(
            label = baseUrlLabel,
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = "https://api.xiaomimimo.com/v1",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_mimo_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_mimo_language_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "mimo-v2.5-asr",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
    }

    val sampleRateLabel = stringResource(R.string.setting_asr_configure_sample_rate)
    val segmentLabel = stringResource(R.string.setting_asr_configure_segment_duration)
    MochiListSection(
        footer = speechFooter(
            sampleRateLabel to stringResource(R.string.setting_asr_configure_mimo_sample_rate_desc),
            segmentLabel to stringResource(R.string.setting_asr_configure_mimo_segment_desc),
        ),
    ) {
        SpeechNumberFieldRow(
            label = sampleRateLabel,
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            placeholder = "Sample Rate",
        )
        SpeechNumberFieldRow(
            label = segmentLabel,
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            placeholder = "Segment Duration (s)",
        )
    }
}

@Composable
private fun StepASRConfiguration(
    setting: ASRProviderSetting.Step,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val apiKeyLabel = stringResource(R.string.setting_asr_configure_api_key)
    val baseUrlLabel = stringResource(R.string.setting_asr_configure_base_url)
    MochiListSection(
        footer = speechFooter(
            apiKeyLabel to stringResource(R.string.setting_asr_configure_step_api_key_desc),
            baseUrlLabel to stringResource(R.string.setting_asr_configure_step_base_url_desc),
        ),
    ) {
        SpeechSecretFieldRow(
            label = apiKeyLabel,
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            placeholder = "your-stepfun-api-key",
        )
        SpeechTextFieldRow(
            label = baseUrlLabel,
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            placeholder = "https://api.stepfun.com",
        )
    }

    val modelLabel = stringResource(R.string.setting_asr_configure_model)
    val languageLabel = stringResource(R.string.setting_asr_configure_language)
    val hotwordsLabel = stringResource(R.string.setting_asr_configure_step_hotwords)
    MochiListSection(
        footer = speechFooter(
            modelLabel to stringResource(R.string.setting_asr_configure_step_model_desc),
            languageLabel to stringResource(R.string.setting_asr_configure_step_language_desc),
            hotwordsLabel to stringResource(R.string.setting_asr_configure_step_hotwords_desc),
        ),
    ) {
        SpeechTextFieldRow(
            label = modelLabel,
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            placeholder = "stepaudio-2.5-asr",
        )
        SpeechTextFieldRow(
            label = languageLabel,
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            placeholder = "auto",
        )
        SpeechTextFieldRow(
            label = hotwordsLabel,
            // 用逗号分隔展示, 输入时按逗号 split 回 List
            value = setting.hotwords.joinToString(","),
            onValueChange = { text ->
                val list = text.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                onValueChange(setting.copy(hotwords = list))
            },
            placeholder = "热词1, 热词2, 热词3",
        )
    }

    val sampleRateLabel = stringResource(R.string.setting_asr_configure_sample_rate)
    val segmentLabel = stringResource(R.string.setting_asr_configure_segment_duration)
    MochiListSection(
        footer = speechFooter(
            sampleRateLabel to stringResource(R.string.setting_asr_configure_step_sample_rate_desc),
            segmentLabel to stringResource(R.string.setting_asr_configure_step_segment_desc),
        ),
    ) {
        SpeechNumberFieldRow(
            label = sampleRateLabel,
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            placeholder = "Sample Rate",
        )
        SpeechNumberFieldRow(
            label = segmentLabel,
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            placeholder = "Segment Duration (s)",
        )
    }

    MochiListSection {
        SpeechSwitchRow(
            title = stringResource(R.string.setting_asr_configure_step_itn),
            subtitle = stringResource(R.string.setting_asr_configure_step_itn_desc),
            checked = setting.enableItn,
            onCheckedChange = { onValueChange(setting.copy(enableItn = it)) },
        )
        SpeechSwitchRow(
            title = stringResource(R.string.setting_asr_configure_step_timestamp),
            subtitle = stringResource(R.string.setting_asr_configure_step_timestamp_desc),
            checked = setting.enableTimestamp,
            onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) },
        )
    }
}

@Composable
private fun SherpaLocalASRConfiguration(
    setting: ASRProviderSetting.SherpaLocal,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val appEntryPoint = rememberAppEntryPoint()
    val downloadProvider = appEntryPoint.khatKitToolProvider()
    val store = remember { SherpaModelManager(context) }
    val scope = rememberCoroutineScope()
    var downloadedIds by remember {
        mutableStateOf(
            SherpaModels.PRESETS.filter { store.isDownloaded(it.id) }.map { it.id }.toSet()
        )
    }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 引擎（原生库）按需下载
    var engineReady by remember { mutableStateOf(store.isEngineReady()) }
    var engineDownloading by remember { mutableStateOf(false) }
    var engineProgress by remember { mutableFloatStateOf(0f) }
    var engineError by remember { mutableStateOf<String?>(null) }

    MochiListSection(
        header = "识别引擎",
        footer = "原生库不进安装包，首次使用需要下载一次；之后完全离线",
    ) {
        MochiListRow(
            title = "sherpa-onnx ${SherpaModels.ENGINE_VERSION}",
            subtitle = "约 ${SherpaModels.ENGINE_SIZE_BYTES / 1024 / 1024} MB · " +
                if (engineReady) "已安装" else "未安装",
            trailing = {
                when {
                    engineDownloading -> MochiProgressRing(
                        progress = engineProgress,
                        size = 24.dp,
                    )

                    engineReady -> SherpaActionButton(
                        text = "删除",
                        destructive = true,
                        onClick = {
                            store.deleteEngine()
                            engineReady = false
                        },
                    )

                    else -> SherpaActionButton(
                        text = "下载",
                        onClick = {
                            engineError = null
                            scope.launch {
                                engineDownloading = true
                                engineProgress = 0f
                                runCatching {
                                    val archive = downloadProvider.downloadFile(
                                        SherpaModels.ENGINE_URL,
                                        "sherpa-onnx-${SherpaModels.ENGINE_VERSION}.aar",
                                    ) ?: error("下载失败")
                                    store.installEngineArchive(archive).also { archive.delete() }
                                }.onSuccess {
                                    engineReady = true
                                }.onFailure {
                                    engineError = it.message ?: "引擎下载失败"
                                }
                                engineDownloading = false
                            }
                        },
                    )
                }
            },
        )
        // 下载进度条与错误信息：卡片内的自定义块
        if (engineDownloading || engineError != null) {
            SpeechSectionBlock {
                SherpaStatusContent(
                    progress = if (engineDownloading) engineProgress else null,
                    error = engineError,
                )
            }
        }
    }

    MochiListSection(
        header = "本地模型",
        footer = "下载后保存在应用内部存储，识别全程离线、不上传音频",
    ) {
        SherpaModels.PRESETS.forEach { preset ->
            val isDownloaded = preset.id in downloadedIds
            val isSelected = setting.modelId == preset.id
            // 已下载的模型整行可点：选用该模型，选中项行尾显示对勾（原单选按钮「当前使用 / 使用该模型」）
            MochiListRow(
                title = preset.displayName,
                subtitle = "${preset.sizeBytes / 1024 / 1024} MB · ${if (preset.streaming) "流式" else "离线"} · ${preset.type}" +
                    if (isDownloaded) " · " + (if (isSelected) "当前使用" else "使用该模型") else "",
                onClick = if (isDownloaded) {
                    {
                        onValueChange(
                            setting.copy(
                                modelId = preset.id,
                                modelType = preset.type,
                            )
                        )
                    }
                } else {
                    null
                },
                checked = isSelected,
                trailing = {
                    when {
                        downloadingId == preset.id -> MochiProgressRing(
                            progress = progress,
                            size = 24.dp,
                        )

                        isDownloaded -> SherpaActionButton(
                            text = "删除",
                            destructive = true,
                            onClick = {
                                store.delete(preset.id)
                                downloadedIds = downloadedIds - preset.id
                                if (setting.modelId == preset.id) {
                                    onValueChange(setting.copy(modelId = ""))
                                }
                            },
                        )

                        else -> SherpaActionButton(
                            text = "下载",
                            onClick = {
                                errorMessage = null
                                scope.launch {
                                    downloadingId = preset.id
                                    progress = 0f
                                    runCatching {
                                        val archive = downloadProvider.downloadFile(
                                            preset.url,
                                            "${preset.id}.tar.bz2",
                                        ) ?: error("下载失败")
                                        store.installModelArchive(preset, archive).also { archive.delete() }
                                    }.onSuccess {
                                        downloadedIds = downloadedIds + preset.id
                                        onValueChange(
                                            setting.copy(
                                                modelId = preset.id,
                                                modelType = preset.type,
                                            )
                                        )
                                    }.onFailure {
                                        errorMessage = it.message ?: "下载失败"
                                    }
                                    downloadingId = null
                                }
                            },
                        )
                    }
                },
            )
            if (downloadingId == preset.id) {
                SpeechSectionBlock {
                    SherpaStatusContent(progress = progress, error = null)
                }
            }
        }

        errorMessage?.let {
            SpeechSectionBlock {
                SherpaStatusContent(progress = null, error = it)
            }
        }
    }

    MochiListSection(
        footer = speechFooter(
            "线程数" to "识别占用的 CPU 线程，越大越快但更耗电",
            *(if (setting.modelType == "whisper") arrayOf("识别语言" to "auto 自动检测；也可填 zh / en 等") else emptyArray()),
        ),
    ) {
        SpeechNumberFieldRow(
            label = "线程数",
            value = setting.threads,
            onValueChange = { value ->
                if (value in 1..8) {
                    onValueChange(setting.copy(threads = value))
                }
            },
        )
        if (setting.modelType == "whisper") {
            SpeechTextFieldRow(
                label = "识别语言",
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                placeholder = "auto",
            )
        }
    }
}

/** 本地识别行尾的文字按钮（下载 / 删除）：紧凑高度，放进列表行不撑高。 */
@Composable
private fun SherpaActionButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    MochiButton(
        onClick = onClick,
        style = MochiButtonStyle.Text,
        minHeight = 32.dp,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = text,
            color = if (destructive) MochiTheme.colorScheme.error else MochiTheme.colorScheme.primary,
        )
    }
}

/** 下载进度条 + 错误信息，放在 [SpeechSectionBlock] 里。 */
@Composable
private fun SherpaStatusContent(progress: Float?, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (progress != null) {
            MochiProgressBar(progress = progress)
        }
        if (error != null) {
            Text(
                text = error,
                color = MochiTheme.colorScheme.error,
                style = MochiTheme.textStyles.footnote,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 语音服务配置共用的 Mochi 表单行（TTSProviderConfigure 也用）
// ---------------------------------------------------------------------------------------------

/** 表单行标签宽度：「WebSocket URL」「语音检测阈值」都放得下，同一张卡片里输入区对齐成一列。 */
internal val SpeechLabelWidth: Dp = 112.dp

/** 把「标签：说明」逐行拼成分组卡片的 footer；原来每个字段下面的说明文字都收在这里。 */
internal fun speechFooter(vararg lines: Pair<String, String>): String? =
    lines.filter { it.second.isNotBlank() }
        .joinToString("\n") { (label, desc) -> "$label：$desc" }
        .ifBlank { null }

/** 分组卡片里的自定义内容：左右与行文字对齐，卡片按它登记的起点画分隔线。 */
@Composable
internal fun SpeechSectionBlock(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth()
            .padding(MochiListDefaults.RowPadding),
    ) {
        content()
    }
}

/** 普通文本输入行。 */
@Composable
internal fun SpeechTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
) {
    MochiListTextFieldRow(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        maxLines = maxLines,
        labelWidth = SpeechLabelWidth,
    )
}

/** 密钥输入行：默认打码，行尾的眼睛切换明文。 */
@Composable
internal fun SpeechSecretFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MochiListTextFieldRow(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = placeholder,
            secure = !visible,
            labelWidth = SpeechLabelWidth,
        )
        MochiIconButton(
            onClick = { visible = !visible },
            style = MochiButtonStyle.Text,
            size = 40.dp,
            modifier = Modifier.padding(end = 6.dp),
        ) {
            Icon(
                imageVector = if (visible) visibilityOff else visibility,
                contentDescription = if (visible) "隐藏 $label" else "显示 $label",
                tint = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 数字输入行（替代 OutlinedNumberInput）：本地保留输入文本，合法数字才回调；
 * 不合法时文字变成错误色。范围校验仍由调用方在 [onValueChange] 里做。
 */
@Composable
internal fun <T : Number> SpeechNumberFieldRow(
    label: String,
    value: T,
    onValueChange: (T) -> Unit,
    placeholder: String = "",
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val valid = text.isValidSpeechNumber()
    MochiListTextFieldRow(
        value = text,
        onValueChange = { newValue ->
            text = newValue
            if (newValue.isValidSpeechNumber()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val newVal = when (value) {
                        is Int -> newValue.toInt() as T
                        is Float -> newValue.toFloat() as T
                        is Double -> newValue.toDouble() as T
                        else -> throw IllegalArgumentException("Unsupported number type")
                    }
                    onValueChange(newVal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        label = label,
        placeholder = placeholder,
        labelWidth = SpeechLabelWidth,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (value is Int) KeyboardType.Number else KeyboardType.Decimal,
        ),
        colors = if (valid) {
            MochiListDefaults.colors()
        } else {
            MochiListDefaults.colors(content = MochiTheme.colorScheme.error)
        },
    )
}

private val SpeechNumberRegex = Regex("^[+-]?\\d+(\\.\\d+)?$")

private fun String.isValidSpeechNumber(): Boolean = matches(SpeechNumberRegex)

/** 开关行：点整行也能切换。 */
@Composable
internal fun SpeechSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { MochiSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/**
 * 可自由输入 + 预设下拉的文本行（替代可编辑的 SelectTextField）：
 * 左边照常输入，行尾的下拉按钮列出预设，选中项带对勾。
 */
@Composable
internal fun <T> SpeechPresetFieldRow(
    label: String,
    value: String,
    options: List<T>,
    onValueChange: (String) -> Unit,
    onOptionSelected: (T) -> Unit,
    optionToString: (T) -> String,
    selectedIndex: Int = options.indexOfFirst { optionToString(it) == value },
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MochiListTextFieldRow(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = placeholder,
            labelWidth = SpeechLabelWidth,
        )
        if (options.isNotEmpty()) {
            MochiIconDropdown(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                items = options.map { MochiItem(label = optionToString(it)) }.toImmutableList(),
                onItemClick = { index ->
                    options.getOrNull(index)?.let(onOptionSelected)
                    expanded = false
                },
                selectedIndex = selectedIndex,
                size = 36.dp,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = keyboardArrowDown,
                    contentDescription = label,
                    tint = MochiTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
