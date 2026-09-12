package heizige.kk.khatkit.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khatkit.asr.ASRController
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.asr.ASRState
import heizige.kk.khatkit.asr.ASRStatus
import heizige.kk.khatkit.asr.appendAmplitude
import heizige.kk.khatkit.asr.calculateRmsAmplitude
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.toMediaType
import heizige.kk.khatkit.common.http.okhttp.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val TAG = "GeminiTranscribeASR"

// 单次请求最多保留约 24MB PCM（16kHz/16bit/mono 约 12 分钟），防止长录音 OOM
private const val MAX_RECORD_BYTES = 24 * 1024 * 1024

/**
 * Google Gemini 音频转写：录音结束后把整段 PCM 包成 WAV，base64 内嵌到
 * `{baseUrl}/models/{model}:generateContent` 的 inline_data 里取文本。
 */
class GeminiTranscribeASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.GeminiTranscribe,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var transcript = ""

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
        }
        transcript = ""

        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true
            )
        }
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        scope.launch(Dispatchers.IO) {
            try {
                transcribe()
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                setError(e.message ?: "Gemini transcribe failed")
            } finally {
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        synchronized(bufferLock) {
                            if (currentBuffer.size() < MAX_RECORD_BYTES) {
                                currentBuffer.write(buffer, 0, read)
                            }
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private suspend fun transcribe() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = provider.sampleRate,
            channels = 1,
            bitsPerSample = 16
        )
        val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val prompt = buildString {
            append("Transcribe the following audio. Return only the transcript text, no commentary.")
            if (provider.language.isNotBlank() && provider.language != "auto") {
                append(" The audio language is ")
                append(provider.language)
                append(".")
            }
        }

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "audio/wav")
                                        .put("data", b64)
                                )
                            )
                    )
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0))

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/models/${provider.model}:generateContent?key=${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Gemini transcribe HTTP ${resp.code}: $respBody")
                }
                val json = runCatching { JSONObject(respBody) }.getOrElse {
                    throw IOException("Gemini transcribe response is not valid JSON: $respBody")
                }
                val parts = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val chunk = parts.optJSONObject(i)?.optString("text").orEmpty()
                            if (chunk.isNotBlank()) append(chunk)
                        }
                    }
                }.trim()
            }
        }

        if (text.isNotEmpty()) {
            transcript = listOf(transcript, text)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            _state.update { it.copy(transcript = transcript, errorMessage = null) }
            scope.launch { onTranscriptChange?.invoke(transcript) }
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** 把 raw PCM16 little-endian 数据封装成最小 WAV (RIFF/WAVE/fmt/data)。 */
        fun pcm16ToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcm.size
            val out = ByteArrayOutputStream(44 + dataSize)

            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 36 + dataSize)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 16)
            writeShortLE(out, 1)
            writeShortLE(out, channels)
            writeIntLE(out, sampleRate)
            writeIntLE(out, byteRate)
            writeShortLE(out, blockAlign)
            writeShortLE(out, bitsPerSample)
            out.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, dataSize)
            out.write(pcm)
            return out.toByteArray()
        }

        fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }

        fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
    }
}
