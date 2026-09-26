package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.FormItem
import heizige.kk.khatkit.app.core.ui.components.ui.OutlinedNumberInput

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import heizige.kk.khatkit.asr.sherpa.SherpaModelStore
import heizige.kk.khatkit.asr.sherpa.SherpaModels
import kotlinx.coroutines.launch

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        FormItem(
            label = { Text(stringResource(R.string.setting_asr_configure_provider_type)) },
            description = { Text(stringResource(R.string.setting_asr_configure_provider_type_desc)) }
        ) {
            OutlinedTextField(
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
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }

        FormItem(
            label = { Text(stringResource(R.string.setting_asr_configure_name)) },
            description = { Text(stringResource(R.string.setting_asr_configure_name_desc)) }
        ) {
            OutlinedTextField(
                value = setting.name,
                onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("OpenAI Realtime") },
                shape = RoundedCornerShape(16.dp)
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
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_gemini_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("AIza...") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_gemini_base_url_desc)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://generativelanguage.googleapis.com/v1beta") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gemini-2.5-flash") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text(stringResource(R.string.setting_asr_configure_sample_rate_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }
}

@Composable
private fun OpenAITranscribeASRConfiguration(
    setting: ASRProviderSetting.OpenAITranscribe,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_transcribe_base_url_desc)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.openai.com/v1") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gpt-4o-transcribe") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text(stringResource(R.string.setting_asr_configure_sample_rate_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }
}

@Composable
private fun OpenAIRealtimeASRConfiguration(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_websocket_desc)) }
    ) {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://api.openai.com/v1/realtime?intent=transcription") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gpt-4o-transcribe") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_prompt)) },
        description = { Text(stringResource(R.string.setting_asr_configure_prompt_desc)) }
    ) {
        OutlinedTextField(
            value = setting.prompt,
            onValueChange = { onValueChange(setting.copy(prompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("Optional") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_vad_threshold)) },
        description = { Text(stringResource(R.string.setting_asr_configure_vad_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in 0.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "VAD Threshold"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_prefix_padding)) },
        description = { Text(stringResource(R.string.setting_asr_configure_prefix_padding_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.prefixPaddingMs,
            onValueChange = { value ->
                if (value in 0..2000) {
                    onValueChange(setting.copy(prefixPaddingMs = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Prefix Padding"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_silence_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_silence_duration_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 100..5000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Silence Duration"
        )
    }
}

@Composable
private fun DashScopeASRConfiguration(
    setting: ASRProviderSetting.DashScope,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_dashscope_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_dashscope_websocket_desc)) }
    ) {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://dashscope.aliyuncs.com/api-ws/v1/realtime") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen3-asr-flash-realtime-2026-02-10") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("zh") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_vad_threshold)) },
        description = { Text(stringResource(R.string.setting_asr_configure_dashscope_vad_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in -1.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "VAD Threshold"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_silence_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_silence_duration_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 200..6000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Silence Duration"
        )
    }
}

@Composable
private fun VolcengineASRConfiguration(
    setting: ASRProviderSetting.Volcengine,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_volcengine_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-api-key") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_volcengine_websocket_desc)) }
    ) {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_resource_id)) },
        description = { Text(stringResource(R.string.setting_asr_configure_resource_id_desc)) }
    ) {
        OutlinedTextField(
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("volc.seedasr.sauc.duration") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_code_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }
    FormItem(
        label = { Text(stringResource(R.string.setting_page_asr_silence_duration)) },
        description = { Text(stringResource(R.string.setting_page_asr_silence_duration_desc)) },
    ) {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 300..5000) onValueChange(setting.copy(silenceDurationMs = value))
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_page_asr_milliseconds),
        )
    }

}

@Composable
private fun MiMoASRConfiguration(
    setting: ASRProviderSetting.MiMo,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-... or tp-...") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_base_url_desc)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.xiaomimimo.com/v1") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-v2.5-asr") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_language_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_sample_rate_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_segment_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_segment_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Segment Duration (s)"
        )
    }
}

@Composable
private fun StepASRConfiguration(
    setting: ASRProviderSetting.Step,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-stepfun-api-key") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_base_url_desc)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.stepfun.com") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("stepaudio-2.5-asr") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_language_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") },
            shape = RoundedCornerShape(16.dp)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_sample_rate_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_segment_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_segment_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Segment Duration (s)"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_step_itn)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_itn_desc)) }
    ) {
        androidx.compose.material3.Switch(
            checked = setting.enableItn,
            onCheckedChange = { onValueChange(setting.copy(enableItn = it)) }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_step_timestamp)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_timestamp_desc)) }
    ) {
        androidx.compose.material3.Switch(
            checked = setting.enableTimestamp,
            onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_step_hotwords)) },
        description = { Text(stringResource(R.string.setting_asr_configure_step_hotwords_desc)) }
    ) {
        OutlinedTextField(
            // 用逗号分隔展示, 输入时按逗号 split 回 List
            value = setting.hotwords.joinToString(","),
            onValueChange = { text ->
                val list = text.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                onValueChange(setting.copy(hotwords = list))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("热词1, 热词2, 热词3") },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SherpaLocalASRConfiguration(
    setting: ASRProviderSetting.SherpaLocal,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SherpaModelStore(context) }
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

    FormItem(
        label = { Text("识别引擎") },
        description = { Text("原生库不进安装包，首次使用需要下载一次；之后完全离线") },
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "sherpa-onnx ${SherpaModels.ENGINE_VERSION}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "约 ${SherpaModels.ENGINE_SIZE_BYTES / 1024 / 1024} MB · " +
                                if (engineReady) "已安装" else "未安装",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        engineDownloading -> CircularProgressIndicator(
                            progress = { engineProgress },
                            modifier = Modifier.size(24.dp),
                        )

                        engineReady -> TextButton(
                            onClick = {
                                store.deleteEngine()
                                engineReady = false
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("删除")
                        }

                        else -> TextButton(
                            onClick = {
                                engineError = null
                                scope.launch {
                                    engineDownloading = true
                                    engineProgress = 0f
                                    runCatching {
                                        store.downloadEngine { engineProgress = it }
                                    }.onSuccess {
                                        engineReady = true
                                    }.onFailure {
                                        engineError = it.message ?: "引擎下载失败"
                                    }
                                    engineDownloading = false
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("下载")
                        }
                    }
                }
                if (engineDownloading) {
                    LinearProgressIndicator(
                        progress = { engineProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                engineError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    FormItem(
        label = { Text("本地模型") },
        description = { Text("下载后保存在应用内部存储，识别全程离线、不上传音频") },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SherpaModels.PRESETS.forEach { preset ->
                val isDownloaded = preset.id in downloadedIds
                val isSelected = setting.modelId == preset.id
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "${preset.sizeBytes / 1024 / 1024} MB · ${if (preset.streaming) "流式" else "离线"} · ${preset.type}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when {
                                downloadingId == preset.id -> CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(24.dp),
                                )

                                isDownloaded -> TextButton(
                                    onClick = {
                                        store.delete(preset.id)
                                        downloadedIds = downloadedIds - preset.id
                                        if (setting.modelId == preset.id) {
                                            onValueChange(setting.copy(modelId = ""))
                                        }
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text("删除")
                                }

                                else -> TextButton(
                                    onClick = {
                                        errorMessage = null
                                        scope.launch {
                                            downloadingId = preset.id
                                            progress = 0f
                                            runCatching {
                                                store.download(preset) { progress = it }
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
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text("下载")
                                }
                            }
                        }

                        if (downloadingId == preset.id) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (isDownloaded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onValueChange(
                                            setting.copy(
                                                modelId = preset.id,
                                                modelType = preset.type,
                                            )
                                        )
                                    },
                                )
                                Text(
                                    text = if (isSelected) "当前使用" else "使用该模型",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    FormItem(
        label = { Text("线程数") },
        description = { Text("识别占用的 CPU 线程，越大越快但更耗电") },
    ) {
        OutlinedNumberInput(
            value = setting.threads,
            onValueChange = { value ->
                if (value in 1..8) {
                    onValueChange(setting.copy(threads = value))
                }
            },
        )
    }

    if (setting.modelType == "whisper") {
        FormItem(
            label = { Text("识别语言") },
            description = { Text("auto 自动检测；也可填 zh / en 等") },
        ) {
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("auto") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
