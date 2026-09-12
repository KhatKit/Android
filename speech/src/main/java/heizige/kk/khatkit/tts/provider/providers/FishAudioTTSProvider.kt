package heizige.kk.khatkit.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import heizige.kk.khatkit.tts.model.AudioChunk
import heizige.kk.khatkit.tts.model.AudioFormat
import heizige.kk.khatkit.tts.model.TTSRequest
import heizige.kk.khatkit.tts.provider.TTSProvider
import heizige.kk.khatkit.tts.provider.TTSProviderException
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import heizige.kk.khatkit.common.http.okhttp.toMediaType
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "FishAudioTTSProvider"

class FishAudioTTSProvider : TTSProvider<TTSProviderSetting.FishAudio> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.FishAudio,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            if (providerSetting.referenceId.isNotBlank()) {
                put("reference_id", providerSetting.referenceId)
            }
            put("format", providerSetting.format)
            put("temperature", providerSetting.temperature.toDouble())
            put("top_p", providerSetting.topP.toDouble())
            put("prosody", JSONObject().apply {
                put("speed", providerSetting.speed.toDouble())
            })
            put("chunk_length", providerSetting.chunkLength)
            put("normalize", providerSetting.normalize)
            put("latency", providerSetting.latency)
        }

        Log.i(TAG, "generateSpeech: model=${providerSetting.model}, referenceId=${providerSetting.referenceId}")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/v1/tts")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("model", providerSetting.model)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e(TAG, "generateSpeech: ${response.code} ${response.message}")
            Log.e(TAG, "generateSpeech: $errorBody")
            throw TTSProviderException(
                message = "Fish Audio TTS request failed: ${response.code} ${response.message}",
                statusCode = response.code
            )
        }

        val audioData = response.body.bytes()

        val audioFormat = when (providerSetting.format.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "opus" -> AudioFormat.OPUS
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioData,
                format = audioFormat,
                isLast = true,
                metadata = mapOf(
                    "provider" to "fish-audio",
                    "model" to providerSetting.model,
                    "referenceId" to providerSetting.referenceId
                )
            )
        )
    }
}
