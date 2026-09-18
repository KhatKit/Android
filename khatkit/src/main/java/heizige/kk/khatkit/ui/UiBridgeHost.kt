package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** 一个等待宿主 UI 处理的请求。 */
sealed interface UiRequest {
    data class Form(
        val title: String,
        val items: List<Map<String, Any?>>,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
    ) : UiRequest

    data class Confirm(
        val title: String,
        val message: String,
        val danger: Boolean,
        val deferred: CompletableDeferred<Boolean>,
    ) : UiRequest

    data class Show(val card: Map<String, Any?>) : UiRequest
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

    override fun form(title: String, items: List<Map<String, Any?>>): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Form(title, items, deferred)
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

    override fun show(card: Map<String, Any?>) {
        _shown.value = card
        _request.value = UiRequest.Show(card)
    }

    override fun automationStatus(label: String, detail: String) {
        onAutomationStatus(label, detail)
    }

    override fun isCancelled(): Boolean = onCancelled()

    fun submitForm(values: Map<String, Any?>?) {
        val current = _request.value as? UiRequest.Form ?: return
        current.deferred.complete(values)
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
            else -> Unit
        }
        _request.value = null
    }
}
