package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 卡片弹层的呈现选项（`ui.form` / `ui.show` 的 options 参数）。
 *
 * - [fullscreen]：占满屏幕宽高（无部分展开态），仍可下滑/返回关闭；
 * - [landscape]：弹层显示期间允许横屏（宿主把 Activity 方向设为 FULL_SENSOR，关闭后还原）；
 * - [height]：弹层高度占屏幕比例 0.1–1.0，null 用组件默认（约 0.9 上限）。
 *
 * 脚本传 `Map`（Lua table / JS object）即可，非法值忽略不报错。
 */
data class UiSheetOptions(
    val fullscreen: Boolean = false,
    val landscape: Boolean = false,
    val height: Float? = null,
) {
    /** 需要自定义大弹层（全屏或显式高度）时为 true。 */
    val usesLargeSheet: Boolean get() = fullscreen || height != null

    companion object {
        val DEFAULT = UiSheetOptions()

        /** 高度下限：低于 10% 没意义，钳到 0.1。 */
        const val MIN_HEIGHT = 0.1f

        fun from(map: Map<String, Any?>?): UiSheetOptions {
            if (map.isNullOrEmpty()) return DEFAULT
            val rawHeight = (map["height"] as? Number)?.toFloat()
            return UiSheetOptions(
                fullscreen = map["fullscreen"].toBooleanFlag(),
                landscape = map["landscape"].toBooleanFlag(),
                height = rawHeight
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.coerceIn(MIN_HEIGHT, 1f),
            )
        }

        private fun Any?.toBooleanFlag(): Boolean = when (this) {
            is Boolean -> this
            is Number -> toDouble() != 0.0
            is String -> equals("true", ignoreCase = true) || this == "1"
            else -> false
        }
    }
}

/** 一个等待宿主 UI 处理的请求。 */
sealed interface UiRequest {
    data class Sheet(
        val title: String,
        val url: String? = null,
        val actions: List<Map<String, Any?>>,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
    ) : UiRequest

    data class Form(
        val title: String,
        val items: List<Map<String, Any?>>,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
    ) : UiRequest

    data class Confirm(
        val title: String,
        val message: String,
        val danger: Boolean,
        val deferred: CompletableDeferred<Boolean>,
    ) : UiRequest

    data class Show(
        val card: Map<String, Any?>,
        val options: UiSheetOptions,
    ) : UiRequest
}

/**
 * ui bridge 的宿主侧实现（设计文档 7.2）。
 *
 * 脚本跑后台线程，UI 主线程；用 CompletableDeferred + withTimeoutOrNull 阻塞等待回传。
 * 宿主 Compose 层观察 [request]，渲染后调用 [submitForm] / [answerConfirm] 回传。
 */
class UiBridgeHost(
    private val timeoutMillis: Long = 300_000,
    private val onAutomationStatus: (String, String) -> Unit = { _, _ -> },
    private val onCancelled: () -> Boolean = { false },
) : UiBridge {

    private val _request = MutableStateFlow<UiRequest?>(null)
    val request: StateFlow<UiRequest?> = _request.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Float, String>?>(null)
    val progress: StateFlow<Pair<Float, String>?> = _progress.asStateFlow()

    private val _shown = MutableStateFlow<Map<String, Any?>?>(null)
    val shown: StateFlow<Map<String, Any?>?> = _shown.asStateFlow()

    override fun form(
        title: String,
        items: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Form(title, items, UiSheetOptions.from(options), deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun sheet(
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Sheet(title = title, actions = actions, options = UiSheetOptions.from(options), deferred = deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun webSheet(title: String, url: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Sheet(title, url, actions, UiSheetOptions.from(options), deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun confirm(title: String, message: String, danger: Boolean): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _request.value = UiRequest.Confirm(title, message, danger, deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } } ?: false
    }

    override fun progress(ratio: Float, label: String) {
        _progress.value = ratio.coerceIn(0f, 1f) to label
        // 进度文字同步到自动化看板（宿主未接线时为 no-op）
        if (label.isNotBlank()) onAutomationStatus(label, "")
    }

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) {
        _shown.value = card
        _request.value = UiRequest.Show(card, UiSheetOptions.from(options))
    }

    override fun automationStatus(label: String, detail: String) {
        // Keep the same status visible inside KhatKit while the external overlay
        // remains responsible for runs that continue over another app.
        if (label.isNotBlank()) _progress.value = 1f to if (detail.isBlank()) label else "$label：$detail"
        onAutomationStatus(label, detail)
    }

    override fun isCancelled(): Boolean = onCancelled()

    fun submitForm(values: Map<String, Any?>?) {
        val current = _request.value as? UiRequest.Form ?: return
        current.deferred.complete(values)
        _request.value = null
    }

    fun selectSheetAction(event: String, values: Map<String, Any?> = emptyMap()) {
        val current = _request.value as? UiRequest.Sheet ?: return
        current.deferred.complete(mapOf("event" to event, "values" to values))
        _request.value = null
    }

    fun answerConfirm(confirmed: Boolean) {
        val current = _request.value as? UiRequest.Confirm ?: return
        current.deferred.complete(confirmed)
        _request.value = null
    }

    fun dismiss() {
        when (val current = _request.value) {
            is UiRequest.Form -> current.deferred.complete(null)
            is UiRequest.Confirm -> current.deferred.complete(false)
            is UiRequest.Sheet -> current.deferred.complete(null)
            else -> Unit
        }
        _request.value = null
    }
}
