package heizige.kk.khatkit.app.feature.chat.helper

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.feature.chat.ChatManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** 悬浮窗要显示的会话，expanded 为 true 时展开成面板。 */
data class FloatingChatWindow(val conversationId: Uuid, val expanded: Boolean)

/**
 * 决定后台悬浮窗显示哪个会话、是否展开，不涉及窗口本身（移植上游 f048bed65 的 ChatFloatingTracker）。
 *
 * 只在开关打开且应用在后台时工作：切到后台时正在生成的会话、以及之后开始生成的会话成为目标；
 * 生成结束后继续显示，方便直接回复；用户关掉后等下一个开始生成的会话。
 * 回到前台时全部重置。
 *
 * @param enabled 开关（t148 接线：displaySetting.enableFloatingChatWindow），默认关闭时传 flowOf(false)
 */
class ChatFloatingTracker(
    appScope: AppScope,
    lazyChatManager: Lazy<ChatManager>,
    enabled: Flow<Boolean>,
) {
    // 只在后台用到，进程启动时不急着创建 ChatManager
    private val chatManager by lazyChatManager

    // 启动时当作在前台：没有界面时启动的进程也没有用户在等的生成
    private val isForeground = MutableStateFlow(true)
    private val actions = MutableSharedFlow<Action>(extraBufferCapacity = 8)

    val window: StateFlow<FloatingChatWindow?> = combine(enabled, isForeground) { on, foreground ->
        on && !foreground
    }
        .distinctUntilChanged()
        .flatMapLatest { active -> if (active) trackInBackground() else flowOf(null) }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    init {
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> isForeground.value = true
                        Lifecycle.Event.ON_STOP -> isForeground.value = false
                        else -> {}
                    }
                }
            )
        }
        // 悬浮窗显示期间持有会话引用，避免没有页面打开时被回收；换会话或不再显示时释放
        appScope.launch {
            window.map { it?.conversationId }.distinctUntilChanged().collectLatest { id ->
                if (id == null) return@collectLatest
                chatManager.addConversationReference(id)
                try {
                    awaitCancellation()
                } finally {
                    chatManager.removeConversationReference(id)
                }
            }
        }
    }

    fun expand() {
        actions.tryEmit(Action.Expand)
    }

    fun collapse() {
        actions.tryEmit(Action.Collapse)
    }

    /** 关掉悬浮窗，等下一个开始生成的会话 */
    fun dismiss() {
        actions.tryEmit(Action.Dismiss)
    }

    private fun trackInBackground(): Flow<FloatingChatWindow?> =
        merge(chatManager.getConversationJobs().map { Action.JobsChanged(it.keys) }, actions)
            .scan(TrackingState()) { state, action -> state.reduce(action) }
            .map { it.window }

    internal sealed interface Action {
        data class JobsChanged(val running: Set<Uuid>) : Action
        data object Expand : Action
        data object Collapse : Action
        data object Dismiss : Action
    }

    /** 状态转移是纯函数，单测可直接覆盖。 */
    internal data class TrackingState(
        val running: Set<Uuid> = emptySet(),
        val target: Uuid? = null,
        val expanded: Boolean = false,
    ) {
        val window: FloatingChatWindow? get() = target?.let { FloatingChatWindow(it, expanded) }

        fun reduce(action: Action): TrackingState = when (action) {
            // 同时开始的会话任取一个
            is Action.JobsChanged -> copy(
                running = action.running,
                target = (action.running - running).lastOrNull() ?: target,
            )

            Action.Expand -> copy(expanded = target != null)
            Action.Collapse -> copy(expanded = false)
            Action.Dismiss -> copy(target = null, expanded = false)
        }
    }
}
