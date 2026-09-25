package heizige.kk.khatkit.app.feature.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.automation.AutomationOverlayService
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentHashMap

/**
 * 授权操作类别：审批策略按类别持久化，10 分钟记住同样按类别生效。
 * [SHELL_ROOT] 覆盖 root / Shizuku 命令执行；[FILE_DELETE] / [APP_MANAGE] 预留给后续
 * 更细粒度的调用方（卡片脚本内部的文件 / 应用操作暂不细分）。
 */
enum class ApprovalCategory(val id: String, val label: String) {
    CARD_RUN("card_run", "运行卡片"),
    UI_ACTION("ui_action", "界面操作"),
    SHELL_ROOT("shell_root", "Shell / Root"),
    FILE_DELETE("file_delete", "删除文件"),
    APP_MANAGE("app_manage", "应用管理");

    companion object {
        fun fromId(id: String?): ApprovalCategory? = entries.firstOrNull { it.id == id }
    }
}

/** 单个类别的审批策略：默认每次询问。 */
enum class ApprovalPolicy(val id: String, val label: String) {
    ALWAYS_ASK("always_ask", "每次询问"),
    ALLOW("allow", "允许"),
    DENY("deny", "拒绝");

    companion object {
        fun fromId(id: String?): ApprovalPolicy? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 自动化状态总线：卡片脚本 / 事件触发 / AI 设备工具在运行时通过它发布当前步骤，
 * 宿主悬浮窗（[AutomationOverlayService]）订阅 [status] 渲染「自动化运行中」看板。
 *
 * - [update] 发布一步，自动追加到 [AutomationStatus.recent]（最多 4 条，连续重复去重）
 * - [requestCancel] 用户在看板点「停止」，长脚本可通过 `ui.isCancelled()` 轮询感知
 * - [finish] 一次运行结束，看板先展示「任务完成」约 3s 再自动隐藏
 * - [clear] 看板服务在完成态展示结束后复位总线；业务侧结束请调用 [finish]
 */
object AutomationBus {

    data class AutomationStatus(
        val label: String,
        val detail: String = "",
        val recent: List<String> = emptyList(),
        val cancelRequested: Boolean = false,
        /** 一次运行已结束，看板短暂展示完成提示后隐藏；下一次 [update] 会清除。 */
        val finished: Boolean = false,
        val activeSince: Long,
    )

    /** 一次待用户确认的自动化授权请求，由悬浮看板展示。 */
    data class ApprovalRequest(
        val id: String,
        val title: String,
        val detail: String,
        /** 请求所属操作类别：审批策略与 10 分钟记住都按它生效。 */
        val category: ApprovalCategory,
        /** 请求创建时间，看板倒计时以它为起点，与 [APPROVAL_TIMEOUT_MS] 对齐。 */
        val requestedAt: Long = System.currentTimeMillis(),
    )

    private const val MAX_RECENT = 4

    /** 通知回退渠道 ID 与通知 ID；无悬浮窗能力时用高优先级通知征求授权。 */
    private const val APPROVAL_CHANNEL_ID = "automation_approval"
    private const val APPROVAL_NOTIFICATION_ID = 2004

    /** 审批策略持久化文件名 / key 前缀（khatkit 自动化设置）。 */
    private const val POLICY_PREFS = "khatkit_automation"
    private const val POLICY_KEY_PREFIX = "approval_policy_"

    /** 「记住 10 分钟」按钮设置的有效期：期间同类请求自动放行（不跨策略拒绝）。 */
    const val APPROVAL_REMEMBER_MS = 10 * 60_000L

    /**
     * 显式运行会话持有的有效期：期间每次 [update] 都会续期，超过该时长没有任何活动
     * 即视为泄漏/陈旧的持有，看板不再等待并强制收尾，保证悬浮板必定消失。
     */
    const val SESSION_HOLD_TTL_MS = 30_000L

    /** 通知回退的广播动作：点击通知上的「允许 / 拒绝」按钮。 */
    const val ACTION_APPROVAL_ALLOW = "heizige.kk.khatkit.app.action.APPROVAL_ALLOW"
    const val ACTION_APPROVAL_DENY = "heizige.kk.khatkit.app.action.APPROVAL_DENY"

    /** 看板按钮决议：✓ / ✗ / 记住 10 分钟（仅 ✓ 提升为整个会话放行）。 */
    private enum class ApprovalOutcome { APPROVED, DENIED, REMEMBERED }

    /** 授权等待上限，超时按拒绝处理；看板倒计时与它保持同一时间基准。 */
    const val APPROVAL_TIMEOUT_MS = 60_000L

    /** 临时隐藏看板的默认时长；[hideOverlayTemporarily] 会把时长夹在 1s..30s 之间。 */
    const val DEFAULT_OVERLAY_HIDE_MS = 5_000L
    private const val MIN_OVERLAY_HIDE_MS = 1_000L
    private const val MAX_OVERLAY_HIDE_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<AutomationStatus?>(null)
    val status: StateFlow<AutomationStatus?> = _status.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    /** 看板是否被 AI 临时隐藏：true 时只播退场动画，服务/窗口保持挂载。 */
    private val _overlayHidden = MutableStateFlow(false)
    val overlayHidden: StateFlow<Boolean> = _overlayHidden.asStateFlow()

    private var overlayHideJob: Job? = null

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var handsOffProvider: (() -> Boolean)? = null

    @Volatile
    private var sessionApproved = false

    /** 卡片/触发等显式运行是否正在进行：期间看板不会因长时间无更新自动结束会话。 */
    @Volatile
    private var sessionHeld = false

    /** [sessionHeld] 的最近一次活动时间（[begin] 或持有期间的 [update]），用于 TTL 判定。 */
    @Volatile
    private var sessionHeldAt = 0L

    @Volatile
    private var approvalDeferred: CompletableDeferred<ApprovalOutcome>? = null
    private val approvalMutex = Mutex()

    /** 按类别的持久化审批策略存储（安装时注入）。 */
    @Volatile
    private var policyPrefs: SharedPreferences? = null

    /** 「记住 10 分钟」的到期时间戳（按类别，仅进程内有效）。 */
    private val rememberedUntil = ConcurrentHashMap<ApprovalCategory, Long>()

    /** 发布/更新当前步骤；重复的 label 不会刷屏历史，并结束上一次的完成态。 */
    fun update(label: String, detail: String = "") {
        if (label.isBlank()) return
        // 持有期间的状态更新为会话续期，长任务不会因 TTL 被误判为陈旧持有
        if (sessionHeld) sessionHeldAt = System.currentTimeMillis()
        val entry = if (detail.isBlank()) label else "$label · $detail"
        // 完成态之后的新活动视为一次全新运行：历史与停止标记都重新开始
        val prev = _status.value?.takeUnless { it.finished }
        val recent = when {
            prev == null -> listOf(entry)
            prev.recent.lastOrNull() == entry -> prev.recent
            else -> (prev.recent + entry).takeLast(MAX_RECENT)
        }
        _status.value = AutomationStatus(
            label = label,
            detail = detail,
            recent = recent,
            // 新一次运行（上一次已结束）重置停止标记
            cancelRequested = prev?.cancelRequested ?: false,
            finished = false,
            activeSince = prev?.activeSince ?: System.currentTimeMillis(),
        )
    }

    /**
     * 显式运行（卡片 / 触发）开始：在首次 [update] 前调用，运行期间看板不会把
     * 长时间无更新误判为会话结束；持有期间每次 [update] 续期，[finish] / [clear]
     * 释放该标记，超过 [SESSION_HOLD_TTL_MS] 无活动即视为陈旧持有。
     */
    fun begin() {
        sessionHeld = true
        sessionHeldAt = System.currentTimeMillis()
    }

    /** 是否有显式运行正在有效持有会话（直接 AI 设备工具序列为 false）。 */
    fun isSessionHeld(): Boolean =
        sessionHeld && System.currentTimeMillis() - sessionHeldAt < SESSION_HOLD_TTL_MS

    /** 陈旧持有的剩余时长：看板空闲判定最多再等这么久即强制结束；未持有时为 0。 */
    fun sessionHoldRemainingMs(): Long {
        if (!sessionHeld) return 0L
        return (sessionHeldAt + SESSION_HOLD_TTL_MS - System.currentTimeMillis()).coerceAtLeast(0L)
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

    /** 读取某类别的持久化审批策略；未设置时默认 [ApprovalPolicy.ALWAYS_ASK]。 */
    fun policyOf(category: ApprovalCategory): ApprovalPolicy =
        ApprovalPolicy.fromId(
            runCatching { policyPrefs?.getString(POLICY_KEY_PREFIX + category.id, null) }.getOrNull()
        ) ?: ApprovalPolicy.ALWAYS_ASK

    /** 持久化某类别的审批策略（设置页调用，立即生效）。 */
    fun setPolicy(category: ApprovalCategory, policy: ApprovalPolicy) {
        policyPrefs?.edit()?.putString(POLICY_KEY_PREFIX + category.id, policy.id)?.apply()
    }

    /**
     * 请求一次自动化授权。判定顺序：
     * 1. 放手模式开启 → 直接放行（覆盖一切）；
     * 2. 类别策略 allow / deny → 直接放行 / 拒绝；
     * 3. 本次运行已授权（看板 ✓）或类别处于 10 分钟记住期内 → 直接放行；
     * 4. 无障碍悬浮窗或 SYSTEM_ALERT_WINDOW 可用 → 看板展示「✗ / ✓」，长按 ✓ 可记住 10 分钟；
     * 5. 两者都不可用 → 高优先级通知「允许 / 拒绝」，通知权限也没有时直接拒绝。
     * 60s 未响应按拒绝处理；同一时刻只允许一个请求。
     */
    suspend fun requestApproval(title: String, detail: String, category: ApprovalCategory): Boolean {
        if (isHandsOff()) return true
        when (policyOf(category)) {
            ApprovalPolicy.ALLOW -> return true
            ApprovalPolicy.DENY -> return false
            ApprovalPolicy.ALWAYS_ASK -> Unit
        }
        if (sessionApproved || isRemembered(category)) return true
        val context = appContext ?: return false
        return approvalMutex.withLock {
            if (isHandsOff()) return@withLock true
            when (policyOf(category)) {
                ApprovalPolicy.ALLOW -> return@withLock true
                ApprovalPolicy.DENY -> return@withLock false
                ApprovalPolicy.ALWAYS_ASK -> Unit
            }
            if (sessionApproved || isRemembered(category)) return@withLock true
            val deferred = CompletableDeferred<ApprovalOutcome>()
            val request = ApprovalRequest(
                id = UUID.randomUUID().toString(),
                title = title,
                detail = detail,
                category = category,
            )
            approvalDeferred = deferred
            _pendingApproval.value = request
            val posted = if (canShowOverlay(context)) {
                // 授权必须可见：即便看板正处于临时隐藏，也强制恢复显示
                showOverlay()
                true
            } else {
                postApprovalNotification(context, request)
            }
            if (!posted) {
                // 无悬浮窗也无通知权限：保持现状直接拒绝，不阻塞
                approvalDeferred = null
                _pendingApproval.value = null
                return@withLock false
            }
            val outcome = try {
                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() } ?: ApprovalOutcome.DENIED
            } finally {
                deferred.complete(ApprovalOutcome.DENIED)
                if (approvalDeferred === deferred) approvalDeferred = null
                _pendingApproval.value = null
                cancelApprovalNotification()
            }
            if (outcome == ApprovalOutcome.APPROVED) sessionApproved = true
            outcome != ApprovalOutcome.DENIED
        }
    }

    /** 看板「允许」：放行当前授权请求，本次运行后续操作不再询问。 */
    fun approve() {
        approvalDeferred?.complete(ApprovalOutcome.APPROVED)
    }

    /** 看板「拒绝」：拒绝当前授权请求，调用方应终止本次操作。 */
    fun deny() {
        approvalDeferred?.complete(ApprovalOutcome.DENIED)
    }

    /**
     * 看板「记住 10 分钟」：放行当前请求，并让同类请求在 [APPROVAL_REMEMBER_MS] 内
     * 自动放行；不提升为整个会话放行（✓ 的语义保持不变）。
     */
    fun approveAndRemember() {
        val request = _pendingApproval.value ?: return
        rememberedUntil[request.category] = System.currentTimeMillis() + APPROVAL_REMEMBER_MS
        approvalDeferred?.complete(ApprovalOutcome.REMEMBERED)
    }

    private fun isRemembered(category: ApprovalCategory): Boolean =
        (rememberedUntil[category] ?: 0L) > System.currentTimeMillis()

    /** 看板可用的判定：无障碍悬浮窗或 SYSTEM_ALERT_WINDOW 任一即可（与看板服务一致）。 */
    private fun canShowOverlay(context: Context): Boolean =
        AccessibilityBridgeHolder.current() != null || Settings.canDrawOverlays(context)

    /**
     * 无悬浮窗能力时的回退：发一条高优先级通知，带「允许 / 拒绝」两个广播按钮，
     * 等待期间与看板共用同一 [approvalDeferred]，60s 超时由调用方按拒绝处理。
     * @return 通知是否成功发出；没有 POST_NOTIFICATIONS / 通知被关闭时返回 false。
     */
    private fun postApprovalNotification(context: Context, request: ApprovalRequest): Boolean {
        if (!canPostNotifications(context)) return false
        return runCatching {
            ensureApprovalChannel(context)
            val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.automation_approval_notification_title))
                .setContentText(
                    context.getString(
                        R.string.automation_approval_notification_text,
                        request.title,
                        request.detail,
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .addAction(
                    0,
                    context.getString(R.string.automation_approval_action_deny),
                    approvalActionPendingIntent(context, request, ACTION_APPROVAL_DENY, 1),
                )
                .addAction(
                    0,
                    context.getString(R.string.automation_approval_action_allow),
                    approvalActionPendingIntent(context, request, ACTION_APPROVAL_ALLOW, 2),
                )
                .build()
            NotificationManagerCompat.from(context).notify(APPROVAL_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    /** 取消授权通知；请求结束（允许 / 拒绝 / 超时）时调用，看板路径下为 no-op。 */
    fun cancelApprovalNotification() {
        val context = appContext ?: return
        runCatching { NotificationManagerCompat.from(context).cancel(APPROVAL_NOTIFICATION_ID) }
    }

    private fun approvalActionPendingIntent(
        context: Context,
        request: ApprovalRequest,
        action: String,
        offset: Int,
    ): PendingIntent {
        val intent = Intent(context, ApprovalActionReceiver::class.java)
            .setAction(action)
            .setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            request.id.hashCode() + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureApprovalChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(APPROVAL_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                APPROVAL_CHANNEL_ID,
                context.getString(R.string.automation_approval_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setShowBadge(false) }
        )
    }

    /**
     * 临时隐藏悬浮看板（AI 发现看板遮挡屏幕内容时使用）：只播退场动画，服务与窗口
     * 保持挂载，[durationMs] 到时后自动恢复显示（夹在 1s..30s）。期间 [update] 不会
     * 让它提前出现；新的授权请求（[requestApproval]）会强制恢复显示。
     */
    fun hideOverlayTemporarily(durationMs: Long = DEFAULT_OVERLAY_HIDE_MS) {
        val duration = durationMs.coerceIn(MIN_OVERLAY_HIDE_MS, MAX_OVERLAY_HIDE_MS)
        overlayHideJob?.cancel()
        _overlayHidden.value = true
        overlayHideJob = scope.launch {
            delay(duration)
            overlayHideJob = null
            _overlayHidden.value = false
        }
    }

    /** 立即恢复看板显示（取消临时隐藏计时）。 */
    fun showOverlay() {
        overlayHideJob?.cancel()
        overlayHideJob = null
        _overlayHidden.value = false
    }

    /** 看板当前是否处于临时隐藏状态（授权请求不受影响，会强制显示）。 */
    fun isOverlayHidden(): Boolean = _overlayHidden.value

    /**
     * 一次运行结束（成功/失败都调用）：保留最后一帧并标记 [AutomationStatus.finished]，
     * 看板先展示完成提示，约 3s 后播放退场动画并隐藏。期间出现新的 [update]
     * （例如事件触发再次运行）会清除完成态、取消退场。
     * 同时释放 [begin] 的会话持有标记；没有正在进行的运行时为 no-op。
     * 直接 AI 工具序列不调用它，由看板在空闲一个退场窗口后代为判定会话结束。
     */
    fun finish() {
        sessionHeld = false
        sessionHeldAt = 0L
        // 会话结束：临时隐藏状态复位，保证完成提示可见
        showOverlay()
        val prev = _status.value ?: return
        if (prev.finished) return
        _status.value = prev.copy(finished = true)
    }

    /** 看板服务在完成态退场后复位总线；业务侧结束一次运行请调用 [finish]。 */
    fun clear() {
        sessionHeld = false
        sessionHeldAt = 0L
        _status.value = null
        sessionApproved = false
        showOverlay()
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
        policyPrefs = applicationContext.getSharedPreferences(POLICY_PREFS, Context.MODE_PRIVATE)
        scope.launch {
            combine(status, pendingApproval) { status, approval -> status != null || approval != null }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) AutomationOverlayService.start(applicationContext)
                }
        }
    }
}
