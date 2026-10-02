package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.MediaPickerBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** `mediaPicker.pickMedia` 的 `media` 取值。 */
enum class PickMediaKind {
    IMAGE,
    VIDEO,
    ANY;

    companion object {
        fun from(raw: Any?): PickMediaKind = when (raw?.toString()?.trim()?.lowercase()) {
            "video" -> VIDEO
            "any", "all" -> ANY
            else -> IMAGE
        }
    }
}

/**
 * `mediaPicker.pickMedia(options)` 的解析结果。
 *
 * options 非法时退回默认值，不报错：`media` 只认 `image` / `video` / `any`，
 * `multiple` 接受布尔 / 数字 / 字符串，`max` 钳在 1..[MAX_COUNT]。
 */
data class PickMediaOptions(
    val kind: PickMediaKind = PickMediaKind.IMAGE,
    val multiple: Boolean = true,
    val max: Int = DEFAULT_MAX,
) {
    companion object {
        /** 未声明 `max` 时的默认上限。 */
        const val DEFAULT_MAX = 9

        /** 单次可选数量上限，避免卡片把选择器当批量下载入口。 */
        const val MAX_COUNT = 50

        fun from(options: Map<String, Any?>?): PickMediaOptions {
            if (options.isNullOrEmpty()) return PickMediaOptions()
            val multiple = when (val raw = options["multiple"]) {
                is Boolean -> raw
                is Number -> raw.toDouble() != 0.0
                is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                else -> true
            }
            val max = ((options["max"] as? Number)?.toInt() ?: DEFAULT_MAX).coerceIn(1, MAX_COUNT)
            return PickMediaOptions(
                kind = PickMediaKind.from(options["media"] ?: options["type"]),
                multiple = multiple,
                max = if (multiple) max else 1,
            )
        }
    }
}

/** 一次等待用户挑选的请求；宿主渲染完选择器调 [MediaPickerHost.submit] 回传。 */
data class MediaPickRequest(
    val cardName: String,
    val options: PickMediaOptions,
    val deferred: CompletableDeferred<List<String>>,
)

/**
 * `mediaPicker` bridge 的宿主侧实现（脚本同步阻塞，Compose 侧渲染选择器）。
 *
 * 与 [UiBridgeHost] 同一套模式：脚本线程在这里阻塞，宿主观察 [request] 弹
 * `KhatKitMediaPicker`，用户选好后调 [submit] 回传 URI 字符串。
 *
 * 选择器给的 URI 在卡片运行结束后可能失效，所以回传前由 [importToCache] 把文件
 * 复制进应用缓存（`cacheDir/cards/<cardName>/picked/`），只把真实路径交给脚本。
 */
class MediaPickerHost(
    private val importToCache: (cardName: String, uris: List<String>) -> List<String>,
    private val timeoutMillis: Long = 300_000,
    /** 阻塞等待用户挑选时通知宿主（如自动化看板提示「等待选择媒体」）。 */
    private val onAwaiting: (cardName: String) -> Unit = {},
) : MediaPickerBridge {

    private val _request = MutableStateFlow<MediaPickRequest?>(null)
    val request: StateFlow<MediaPickRequest?> = _request.asStateFlow()

    /** 未绑定卡片名的兜底入口；[heizige.kk.khatkit.bridge.BridgeRegistry] 注入时用 [forCard] 换成绑定卡片名的视图。 */
    override fun pickMedia(options: Map<String, Any?>): List<String> = pick("", options)

    /** 绑定本次运行卡片名的视图。 */
    fun forCard(cardName: String): MediaPickerBridge = CardScopedMediaPicker(this, cardName)

    /**
     * 阻塞等待用户挑选；用户取消、超时或宿主不可用时返回空列表。
     *
     * 脚本线程调用（[heizige.kk.khatkit.exec.CardExecutor] 在 Dispatchers.Default 上跑）。
     */
    fun pick(cardName: String, options: Map<String, Any?>?): List<String> {
        val deferred = CompletableDeferred<List<String>>()
        _request.value = MediaPickRequest(cardName, PickMediaOptions.from(options), deferred)
        onAwaiting(cardName)
        val picked = try {
            runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
        } finally {
            _request.value = null
        }
        if (picked.isNullOrEmpty()) return emptyList()
        return importToCache(cardName, picked)
    }

    /** 用户选好后回传 URI 字符串；无请求在途时忽略。 */
    fun submit(uris: List<String>) {
        _request.value?.deferred?.complete(uris)
    }

    /** 用户取消或宿主无法呈现选择器时调用。 */
    fun dismiss() {
        _request.value?.deferred?.complete(emptyList())
    }
}

/** 绑定卡片名的 `mediaPicker` 视图；由 BridgeRegistry 按 manifest 注入。 */
class CardScopedMediaPicker(
    private val host: MediaPickerHost,
    private val cardName: String,
) : MediaPickerBridge {
    override fun pickMedia(options: Map<String, Any?>): List<String> = host.pick(cardName, options)
}