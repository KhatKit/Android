package heizige.kk.khatkit.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
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

private const val TAG = "OpenAITTSProvider"

class OpenAITTSProvider : TTSProvider<TTSProviderSetting.OpenAI> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "mp3") // Default to MP3
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/audio/speech")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val statusCode = response.code
            val statusMessage = response.message
            response.close()
            throw TTSProviderException(
                message = "TTS request failed: $statusCode $statusMessage",
                statusCode = statusCode
            )
        }

        val audioData = response.body.bytes()

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice
                )
            )
        )
    }
}
