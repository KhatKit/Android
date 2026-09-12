package heizige.kk.khatkit.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import heizige.kk.khatkit.asr.ASRController
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.asr.ASRState
import heizige.kk.khatkit.asr.ASRStatus
import heizige.kk.khatkit.asr.appendAmplitude
import heizige.kk.khatkit.asr.calculateRmsAmplitude
import heizige.kk.khatkit.asr.sherpa.NativeLibLoader
import heizige.kk.khatkit.asr.sherpa.SherpaModelStore
import heizige.kk.khatkit.asr.sherpa.SherpaModels
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
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "SherpaASR"

// 离线模式单次最多保留约 6 分钟（16kHz/16bit/mono ≈ 11.5MB），防止长录音 OOM
private const val MAX_RECORD_BYTES = 12 * 1024 * 1024

/**
 * 本地识别（sherpa-onnx），全程离线、不需要 API Key。
 *
 * - 流式模型（streaming=true，如 zipformer 14M/20M）：边说边出字，走 OnlineRecognizer；
 * - 离线模型（paraformer / whisper / sense-voice）：录音结束后整段识别。
 */
class SherpaASRController(
    private val context: Context,
    private val provider: ASRProviderSetting.SherpaLocal,
) : ASRController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // 离线模式缓存
    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()

    // 流式模式状态
    private var onlineRecognizer: OnlineRecognizer? = null
    private var committedText = ""

    private val isStreaming: Boolean
        get() = SherpaModels.find(provider.modelId)?.streaming == true

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("需要麦克风权限")
            return
        }

        val store = SherpaModelStore(context)
        if (!store.isEngineReady()) {
            setError("请先在设置里下载本地识别引擎")
            return
        }
        NativeLibLoader.libPath = store.engineLib.absolutePath

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
        }
        committedText = ""

        _state.update {
            ASRState(status = ASRStatus.Listening, isAvailable = true)
        }
        if (isStreaming) {
            startStreamingRecorder()
        } else {
            startOfflineRecorder()
        }
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        if (isStreaming) {
            // 流式模式：partial 已经实时回传，直接结束
            _state.update { it.copy(status = ASRStatus.Idle) }
        } else {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            scope.launch {
                try {
                    transcribeOffline()
                } catch (e: Exception) {
                    Log.e(TAG, "Local transcription failed", e)
                    setError(e.message ?: "本地识别失败")
                } finally {
                    _state.update { it.copy(status = ASRStatus.Idle) }
                }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        releaseRecorder()
        onlineRecognizer?.let { runCatching { it.release() } }
        onlineRecognizer = null
        scope.cancel()
    }

    // ------------------------------------------------------------------
    // 流式
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startStreamingRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val modelDir = SherpaModelStore(context).modelDir(provider.modelId)
            val recognizer = try {
                createOnlineRecognizer(modelDir)
            } catch (e: Exception) {
                Log.e(TAG, "Load online model failed", e)
                setError(e.message ?: "模型加载失败")
                return@launch
            }
            onlineRecognizer = recognizer
            val stream = recognizer.createStream()
            var lastEmitted = ""

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
                        _state.update {
                            it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude))
                        }

                        val samples = FloatArray(read / 2)
                        var index = 0
                        while (index + 1 < read) {
                            val lo = buffer[index].toInt() and 0xFF
                            val hi = buffer[index + 1].toInt()
                            samples[index / 2] = ((hi shl 8) or lo).toShort() / 32768f
                            index += 2
                        }

                        stream.acceptWaveform(samples, sampleRate)
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }

                        val text = recognizer.getResult(stream).text
                        val full = (committedText + text).trim()
                        if (full != lastEmitted) {
                            lastEmitted = full
                            withContext(Dispatchers.Main.immediate) {
                                onTranscriptChange?.invoke(full)
                            }
                        }

                        if (recognizer.isEndpoint(stream)) {
                            committedText = full
                            if (committedText.isNotEmpty() && !committedText.endsWith(" ")) {
                                committedText += " "
                            }
                            recognizer.reset(stream)
                            lastEmitted = committedText.trim()
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Streaming recognition failed", e)
                    setError(e.message ?: "识别失败")
                }
            } finally {
                releaseRecorder()
                runCatching { stream.release() }
                runCatching { recognizer.release() }
                onlineRecognizer = null
            }
        }
    }

    private fun createOnlineRecognizer(modelDir: File): OnlineRecognizer {
        require(modelDir.isDirectory) { "模型未下载：${provider.modelId}" }
        val encoder = modelDir.pickModel("encoder")
        // streaming zipformer 的 int8 decoder 无法加载，必须用 fp32 decoder
        val decoder = modelDir.pickModel("decoder", preferInt8 = false)
        val joiner = modelDir.pickModel("joiner")
        val tokens = modelDir.tokensPath()

        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                joiner = joiner.absolutePath,
            ),
            tokens = tokens.orEmpty(),
            numThreads = provider.threads,
            provider = "cpu",
            debug = false,
            modelType = "zipformer",
        )
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
            enableEndpoint = true,
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0f),
                rule2 = EndpointRule(true, 1.2f, 0f),
                rule3 = EndpointRule(false, 0f, 20f),
            ),
        )
        // 绝对路径加载模型时必须传 null assetManager，否则 sherpa 会 abort
        return OnlineRecognizer(null, config)
    }

    // ------------------------------------------------------------------
    // 离线（录完再识别）
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startOfflineRecorder() {
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
                        _state.update {
                            it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude))
                        }
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
                setError(e.message ?: "录音失败")
            } finally {
                releaseRecorder()
            }
        }
    }

    private suspend fun transcribeOffline() {
        val pcmBytes = synchronized(bufferLock) {
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            bytes
        }
        if (pcmBytes.isEmpty()) return

        val modelDir = SherpaModelStore(context).modelDir(provider.modelId)
        val text = withContext(Dispatchers.IO) {
            recognizeOffline(modelDir, pcmBytes)
        }
        if (text.isNotBlank()) {
            withContext(Dispatchers.Main.immediate) {
                onTranscriptChange?.invoke(text)
            }
        }
    }

    private fun recognizeOffline(modelDir: File, pcmBytes: ByteArray): String {
        require(modelDir.isDirectory) { "模型未下载：${provider.modelId}" }

        val samples = FloatArray(pcmBytes.size / 2)
        var index = 0
        while (index + 1 < pcmBytes.size) {
            val lo = pcmBytes[index].toInt() and 0xFF
            val hi = pcmBytes[index + 1].toInt()
            samples[index / 2] = ((hi shl 8) or lo).toShort() / 32768f
            index += 2
        }

        val onnxFiles = modelDir.onnxFiles()
        val tokens = modelDir.tokensPath()
        val language = provider.language.takeIf { it.isNotBlank() } ?: "auto"

        val modelConfig = when (provider.modelType) {
            "whisper" -> {
                val encoder = onnxFiles.firstOrNull { "encoder" in it.name }
                    ?: error("Whisper 模型缺少 encoder.onnx")
                val decoder = onnxFiles.firstOrNull { "decoder" in it.name }
                    ?: error("Whisper 模型缺少 decoder.onnx")
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        language = if (language == "auto") "" else language,
                        task = "transcribe",
                    ),
                    tokens = tokens.orEmpty(),
                    numThreads = provider.threads,
                    provider = "cpu",
                    debug = false,
                    modelType = "whisper",
                )
            }

            "transducer" -> {
                val encoder = onnxFiles.firstOrNull { "encoder" in it.name }
                    ?: error("Transducer 模型缺少 encoder.onnx")
                val decoder = modelDir.onnxFiles(preferInt8 = false).firstOrNull { "decoder" in it.name }
                    ?: error("Transducer 模型缺少 decoder.onnx")
                val joiner = onnxFiles.firstOrNull { "joiner" in it.name }
                    ?: error("Transducer 模型缺少 joiner.onnx")
                OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.orEmpty(),
                    numThreads = provider.threads,
                    provider = "cpu",
                    debug = false,
                    modelType = "transducer",
                )
            }

            "sense_voice" -> {
                val model = onnxFiles.firstOrNull { it.name.startsWith("model") }
                    ?: error("SenseVoice 模型缺少 model.onnx")
                OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = model.absolutePath,
                        language = language,
                        useInverseTextNormalization = true,
                    ),
                    tokens = tokens.orEmpty(),
                    numThreads = provider.threads,
                    provider = "cpu",
                    debug = false,
                    modelType = "sense_voice",
                )
            }

            else -> {
                val model = onnxFiles.firstOrNull { it.name.startsWith("model") }
                    ?: error("Paraformer 模型缺少 model.onnx")
                OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(model = model.absolutePath),
                    tokens = tokens.orEmpty(),
                    numThreads = provider.threads,
                    provider = "cpu",
                    debug = false,
                    modelType = "paraformer",
                )
            }
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
        )
        // 绝对路径加载模型时必须传 null assetManager，否则 sherpa 会 abort
        val recognizer = OfflineRecognizer(null, config)
        return try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, 16000)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    // ------------------------------------------------------------------

    private fun File.onnxFiles(preferInt8: Boolean = true): List<File> = walkTopDown()
        .filter { it.isFile && it.name.endsWith(".onnx") && it.length() > 0 }
        .sortedBy { file ->
            val isInt8 = file.name.contains(".int8.")
            if (preferInt8 == isInt8) 0 else 1
        }
        .toList()

    private fun File.tokensPath(): String? = walkTopDown()
        .firstOrNull { it.isFile && it.name == "tokens.txt" }
        ?.absolutePath

    private fun File.pickModel(keyword: String, preferInt8: Boolean = true): File =
        onnxFiles(preferInt8).firstOrNull { keyword in it.name }
            ?: error("模型缺少 $keyword.onnx")

    private fun releaseRecorder() {
        val recorder = audioRecord ?: return
        audioRecord = null
        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message,
                isAvailable = true,
            )
        }
    }
}
