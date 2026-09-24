package heizige.kk.khatkit.app.record

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.record.RecordedStep
import heizige.kk.khatkit.record.RecordingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 「录制人工操作」的进程内单例：无障碍服务把事件喂进 [onAccessibilityEvent]，
 * 这里把事件翻译成 [RecordedStep]，UI 观察 [state] 显示实时步数并另存为卡片。
 *
 * 事件 → 步骤映射：
 * - TYPE_VIEW_CLICKED      → click（viewId > text > desc > bounds 中心）
 * - TYPE_VIEW_TEXT_CHANGED → set_text（同控件去抖，只保留最终文本；密码框跳过）
 * - TYPE_VIEW_SCROLLED     → swipe（优先 scrollDelta，其次节点 bounds 推断方向）
 * - TYPE_WINDOW_STATE_CHANGED → open_app（可启动应用切前台）/ wait（其余窗口变化）
 *
 * 自己的包名与系统 UI / android 包的事件直接丢弃。
 */
object KhatKitOperationRecorder {

    data class State(
        val recording: Boolean = false,
        val steps: List<RecordedStep> = emptyList(),
        val full: Boolean = false,
    )

    private const val SYS_UI_PACKAGE = "com.android.systemui"
    private const val ANDROID_PACKAGE = "android"
    private const val MAX_TEXT = 128
    private const val MAX_INPUT = 512
    private const val NOTIFY_THROTTLE_MS = 500L

    private val session = RecordingSession()
    private val _state = MutableStateFlow(State())

    /** UI 观察：录制状态 + 已录制步骤。 */
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var skipPackages: Set<String> = emptySet()

    @Volatile
    private var lastForegroundPackage: String = ""

    @Volatile
    private var lastNotifyAt: Long = 0L

    private val launchableCache = HashMap<String, Boolean>()

    /** 开始录制；无障碍服务未连接时返回 false（UI 引导用户去系统设置开启）。 */
    fun start(context: Context): Boolean {
        if (_state.value.recording) return true
        if (AccessibilityBridgeHolder.current() == null) return false
        synchronized(this) {
            session.clear()
            appContext = context.applicationContext
            skipPackages = setOf(context.packageName, SYS_UI_PACKAGE, ANDROID_PACKAGE)
            lastForegroundPackage = ""
        }
        _state.value = State(recording = true)
        OperationRecorderNotifier.show(context.applicationContext, 0)
        return true
    }

    /** 停止录制，返回最终步骤（供保存）；重复调用返回上次结果。 */
    fun stop(context: Context?): List<RecordedStep> {
        val steps = synchronized(this) {
            if (!_state.value.recording) return _state.value.steps
            val snapshot = session.snapshot()
            _state.value = State(recording = false, steps = snapshot, full = session.isFull)
            snapshot
        }
        OperationRecorderNotifier.cancel(context ?: appContext ?: return steps)
        return steps
    }

    /** 丢弃当前录制结果（保存完成或用户清空）。 */
    fun discard(context: Context?) {
        synchronized(this) {
            session.clear()
            _state.value = State()
        }
        (context ?: appContext)?.let(OperationRecorderNotifier::cancel)
    }

    /** 无障碍服务回调入口：只做翻译与累积，不抛异常。 */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!_state.value.recording) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg in skipPackages) return
        val step = runCatching {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> clickStep(event, pkg)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> textChangedStep(event, pkg)
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollStep(event, pkg)
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> windowStep(event, pkg)
                else -> null
            }
        }.getOrNull() ?: return
        val accepted = synchronized(this) {
            val ok = session.add(step)
            _state.value = State(recording = true, steps = session.snapshot(), full = session.isFull)
            ok
        }
        if (accepted) maybeUpdateNotification()
    }

    // ---- 事件 → 步骤 ----

    private fun clickStep(event: AccessibilityEvent, pkg: String): RecordedStep? {
        val node = safeSource(event)
        val viewId = node?.viewIdResourceName.orEmpty()
        val text = node?.text?.toString()?.takeIf { it.isNotBlank() }
            ?: event.text.lastOrNull()?.toString()?.takeIf { it.isNotBlank() }
            ?: ""
        val desc = node?.contentDescription?.toString().orEmpty().take(MAX_TEXT)
        val center = node?.let(::boundsCenter)
        if (viewId.isBlank() && text.isBlank() && desc.isBlank() && center == null) return null
        return RecordedStep(
            type = RecordedStep.Type.CLICK,
            atMillis = event.eventTime,
            packageName = pkg,
            viewId = viewId,
            text = text.take(MAX_TEXT),
            desc = desc,
            x = center?.x ?: 0,
            y = center?.y ?: 0,
        )
    }

    private fun textChangedStep(event: AccessibilityEvent, pkg: String): RecordedStep? {
        if (event.isPassword) return null
        val value = event.text.lastOrNull()?.toString() ?: return null
        val node = safeSource(event)
        val viewId = node?.viewIdResourceName.orEmpty()
        val desc = node?.contentDescription?.toString().orEmpty().take(MAX_TEXT)
        val center = node?.let(::boundsCenter)
        if (viewId.isBlank() && desc.isBlank() && center == null) return null
        return RecordedStep(
            type = RecordedStep.Type.SET_TEXT,
            atMillis = event.eventTime,
            packageName = pkg,
            viewId = viewId,
            desc = desc,
            x = center?.x ?: 0,
            y = center?.y ?: 0,
            inputText = value.take(MAX_INPUT),
        )
    }

    private fun scrollStep(event: AccessibilityEvent, pkg: String): RecordedStep? {
        val node = safeSource(event)
        var dx = 0
        var dy = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dx = event.scrollDeltaX
            dy = event.scrollDeltaY
        }
        val direction = when {
            abs(dy) >= abs(dx) && dy != 0 -> if (dy > 0) "down" else "up"
            dx != 0 -> if (dx > 0) "right" else "left"
            else -> nodeBoundsDirection(node)
        } ?: return null
        val path = node?.let { swipePath(it, direction) } ?: IntArray(4)
        return RecordedStep(
            type = RecordedStep.Type.SWIPE,
            atMillis = event.eventTime,
            packageName = pkg,
            direction = direction,
            x = path[0],
            y = path[1],
            x2 = path[2],
            y2 = path[3],
        )
    }

    private fun windowStep(event: AccessibilityEvent, pkg: String): RecordedStep? {
        val changed = pkg != lastForegroundPackage
        lastForegroundPackage = pkg
        if (!changed) return null
        return if (isLaunchable(pkg)) {
            RecordedStep(RecordedStep.Type.OPEN_APP, event.eventTime, packageName = pkg)
        } else {
            RecordedStep(RecordedStep.Type.WAIT, event.eventTime, packageName = pkg)
        }
    }

    // ---- 小工具 ----

    private data class Point(val x: Int, val y: Int)

    private fun safeSource(event: AccessibilityEvent): AccessibilityNodeInfo? =
        runCatching { event.source }.getOrNull()

    private fun boundsCenter(node: AccessibilityNodeInfo): Point {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return Point(bounds.exactCenterX().toInt(), bounds.exactCenterY().toInt())
    }

    /** 无 scrollDelta 时（API < 28 或程序化滚动）按节点长宽推断主轴。 */
    private fun nodeBoundsDirection(node: AccessibilityNodeInfo?): String? {
        node ?: return null
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        return if (bounds.height() >= bounds.width()) "down" else "right"
    }

    /** 坐标兜底手势：在节点 bounds 内模拟与方向一致的滑动。 */
    private fun swipePath(node: AccessibilityNodeInfo, direction: String): IntArray {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val cx = bounds.exactCenterX().toInt()
        val cy = bounds.exactCenterY().toInt()
        val dx = (bounds.width() * 0.4f).toInt()
        val dy = (bounds.height() * 0.4f).toInt()
        return when (direction) {
            "down" -> intArrayOf(cx, cy + dy, cx, cy - dy)
            "up" -> intArrayOf(cx, cy - dy, cx, cy + dy)
            "right" -> intArrayOf(cx + dx, cy, cx - dx, cy)
            else -> intArrayOf(cx - dx, cy, cx + dx, cy)
        }
    }

    private fun maybeUpdateNotification() {
        val context = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt < NOTIFY_THROTTLE_MS) return
        lastNotifyAt = now
        OperationRecorderNotifier.update(context, _state.value.steps.size)
    }

    private fun isLaunchable(pkg: String): Boolean {
        val context = appContext ?: return false
        synchronized(launchableCache) {
            launchableCache[pkg]?.let { return it }
            val value = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
            launchableCache[pkg] = value
            return value
        }
    }
}
