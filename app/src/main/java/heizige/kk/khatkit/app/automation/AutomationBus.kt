package heizige.kk.khatkit.app.automation

import android.content.Context
import android.provider.Settings
import heizige.kk.khatkit.app.service.AutomationOverlayService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

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

    /** 一次待用户确认的自动化授权请求，由悬浮看板展示。 */
    data class ApprovalRequest(
        val id: String,
        val title: String,
        val detail: String,
    )

    private const val MAX_RECENT = 4
    private const val APPROVAL_TIMEOUT_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<AutomationStatus?>(null)
    val status: StateFlow<AutomationStatus?> = _status.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var handsOffProvider: (() -> Boolean)? = null

    @Volatile
    private var sessionApproved = false

    @Volatile
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private val approvalMutex = Mutex()

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

    /** 注册放手模式读取器（安装时调用）；开启后所有授权请求直接放行。 */
    fun setHandsOffProvider(provider: () -> Boolean) {
        handsOffProvider = provider
    }

    /**
     * 请求一次自动化授权：放手模式开启或本次运行已授权时立即返回 true；
     * 否则发布 [pendingApproval] 等待悬浮看板上的「允许 / 拒绝」，
     * 60s 未响应按拒绝处理，同一时刻只允许一个请求。
     * 无悬浮窗权限时无法弹窗，直接拒绝（不阻塞）。
     */
    suspend fun requestApproval(title: String, detail: String): Boolean {
        if (isHandsOff() || sessionApproved) return true
        val context = appContext ?: return false
        if (!Settings.canDrawOverlays(context)) return false
        return approvalMutex.withLock {
            if (isHandsOff() || sessionApproved) return@withLock true
            val deferred = CompletableDeferred<Boolean>()
            val request = ApprovalRequest(id = UUID.randomUUID().toString(), title = title, detail = detail)
            approvalDeferred = deferred
            _pendingApproval.value = request
            val approved = try {
                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() } ?: false
            } finally {
                deferred.complete(false)
                if (approvalDeferred === deferred) approvalDeferred = null
                _pendingApproval.value = null
            }
            if (approved) sessionApproved = true
            approved
        }
    }

    /** 看板「允许」：放行当前授权请求，本次运行后续操作不再询问。 */
    fun approve() {
        approvalDeferred?.complete(true)
    }

    /** 看板「拒绝」：拒绝当前授权请求，调用方应终止本次操作。 */
    fun deny() {
        approvalDeferred?.complete(false)
    }

    /** 一次运行结束（成功/失败都调用），看板随即进入空闲并自动隐藏。 */
    fun clear() {
        _status.value = null
        sessionApproved = false
    }

    private fun isHandsOff(): Boolean = handsOffProvider?.invoke() == true

    /**
     * 注册应用级监听：状态或待授权出现时拉起悬浮窗服务。
     * 在 [android.app.Application.onCreate] 里调用一次；服务空闲后自行 stopSelf。
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val applicationContext = context.applicationContext
        appContext = applicationContext
        scope.launch {
            combine(status, pendingApproval) { status, approval -> status != null || approval != null }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) AutomationOverlayService.start(applicationContext)
                }
        }
    }
}
