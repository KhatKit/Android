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

private const val TAG = "XAITTSProvider"

class XAITTSProvider : TTSProvider<TTSProviderSetting.XAI> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.XAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            put("voice_id", providerSetting.voiceId)
            put("language", providerSetting.language)
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/tts")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e(TAG, "generateSpeech: ${response.code} ${response.message}")
            Log.e(TAG, "generateSpeech: $errorBody")
            throw TTSProviderException(
                message = "xAI TTS request failed: ${response.code} ${response.message}",
                statusCode = response.code
            )
        }

        val audioData = response.body.bytes()

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "xai",
                    "voice_id" to providerSetting.voiceId,
                    "language" to providerSetting.language
                )
            )
        )
    }
}
