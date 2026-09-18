package heizige.kk.khatkit.app.automation

import android.content.Context
import heizige.kk.khatkit.app.service.AutomationOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 自动化状态总线：卡片脚本 / 事件触发 / AI 设备工具在运行时通过它发布当前步骤，
 * 宿主悬浮窗（[AutomationOverlayService]）订阅 [status] 渲染「自动化运行中」看板。
 *
 * - [update] 发布一步，自动追加到 [AutomationStatus.recent]（最多 4 条，连续重复去重）
 * - [requestCancel] 用户在看板点「停止」，长脚本可通过 `ui.isCancelled()` 轮询感知
 * - [clear] 一次运行结束（服务会在约 3s 后自动隐藏看板）
 */
object AutomationBus {

    data class AutomationStatus(
        val label: String,
        val detail: String = "",
        val recent: List<String> = emptyList(),
        val cancelRequested: Boolean = false,
        val activeSince: Long,
    )

    private const val MAX_RECENT = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<AutomationStatus?>(null)
    val status: StateFlow<AutomationStatus?> = _status.asStateFlow()

    @Volatile
    private var installed = false

    /** 发布/更新当前步骤；重复的 label 不会刷屏历史。 */
    fun update(label: String, detail: String = "") {
        if (label.isBlank()) return
        val entry = if (detail.isBlank()) label else "$label · $detail"
        val prev = _status.value
        val recent = when {
            prev == null -> listOf(entry)
            prev.recent.lastOrNull() == entry -> prev.recent
            else -> (prev.recent + entry).takeLast(MAX_RECENT)
        }
        _status.value = AutomationStatus(
            label = label,
            detail = detail,
            recent = recent,
            // 新一次运行（上一次已 clear）重置停止标记
            cancelRequested = prev?.cancelRequested ?: false,
            activeSince = prev?.activeSince ?: System.currentTimeMillis(),
        )
    }

    /** 看板「停止」按钮：请求取消当前自动化，脚本侧轮询 [isCancelRequested]。 */
    fun requestCancel() {
        val prev = _status.value ?: return
        if (prev.cancelRequested) return
        _status.value = prev.copy(cancelRequested = true)
    }

    fun isCancelRequested(): Boolean = _status.value?.cancelRequested == true

    /** 一次运行结束（成功/失败都调用），看板随即进入空闲并自动隐藏。 */
    fun clear() {
        _status.value = null
    }

    /**
     * 注册应用级监听：状态出现时拉起悬浮窗服务。
     * 在 [android.app.Application.onCreate] 里调用一次；服务空闲后自行 stopSelf。
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        scope.launch {
            status.map { it != null }.distinctUntilChanged().collect { active ->
                if (active) AutomationOverlayService.start(appContext)
            }
        }
    }
}
