package heizige.kk.khatkit.app.feature.chat.helper

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.petterp.floatingx.app.appHost
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxGravity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.feature.chat.floating.ChatFloatingBubble
import heizige.kk.khatkit.app.feature.chat.floating.ChatFloatingPanel
import heizige.kk.khatkit.app.feature.chat.floating.FloatingChatUiState
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.feature.chat.helper.ChatFloatingWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private const val TAG = "ChatFloatingManager"

private const val BUBBLE_TAG = "chat_floating_bubble"

private const val PANEL_TAG = "chat_floating_panel"

/**
 * 把 [ChatFloatingWindowManager] 的结果显示成系统悬浮窗（移植上游 f048bed65 的 ChatFloatingManager）。
 *
 * 复用 fork 已有的 FloatingX（gradle 里早已有 floatingx-app/compose/core，不是新依赖），
 * 窗口内容是本 fork 的 Mochi×MD3 组件（core/ui/floating/ChatFloatingContent.kt）。
 *
 * 待接线（t148）：开关字段 + 设置页入口 + DI 绑定；当前类没有被实例化，靠开关打开后由 DI 建。
 */
class ChatFloatingManager(
    private val context: Application,
    private val appScope: AppScope,
    private val tracker: ChatFloatingWindowManager,
    lazyChatManager: Lazy<ChatManager>,
) {
    private val chatManager by lazyChatManager
    private var bubble: FxControl? = null
    private var panel: FxControl? = null

    // 悬浮窗显示期间一直汇总，气泡和面板切换时直接拿到当前状态
    private val uiState: StateFlow<FloatingChatUiState?> = tracker.window
        .map { it?.conversationId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else chatState(id) }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    init {
        appScope.launch {
            tracker.window.map { it?.expanded }.distinctUntilChanged().collect(::render)
        }
    }

    private fun chatState(id: Uuid): Flow<FloatingChatUiState> {
        val error = chatManager.errors.map { errors ->
            errors.lastOrNull { it.conversationId == id }
        }
        return combine(
            chatManager.getConversationFlow(id),
            chatManager.getGenerationJobStateFlow(id).map { it != null },
            chatManager.getProcessingStatusFlow(id),
            chatManager.getMessageQueueFlow(id),
            error,
        ) { conversation, generating, processingStatus, queue, chatError ->
            buildFloatingChatUiState(conversation, generating, processingStatus, queue, chatError?.error?.message)
        }.flowOn(Dispatchers.Default)
    }

    private fun render(expanded: Boolean?) {
        // 后台弹不出权限申请页；没有悬浮窗权限就不显示，用户下次回到设置页再授权
        if (expanded == null || !Settings.canDrawOverlays(context)) {
            bubble?.cancel()
            panel?.cancel()
            bubble = null
            panel = null
            return
        }
        val bubbleWindow = bubble ?: installBubble().also { bubble = it }
        if (expanded) {
            bubbleWindow.hide()
            (panel ?: installPanel().also { panel = it }).show()
        } else {
            panel?.hide()
            bubbleWindow.show()
        }
    }

    private fun installBubble(): FxControl = FloatingX.install(BUBBLE_TAG) {
        compose {
            val state by uiState.collectAsState()
            KhatKitTheme {
                state?.let { ChatFloatingBubble(state = it, onClick = tracker::expand) }
            }
        }
        appHost(context)
        anchor(FxGravity.CENTER_END, dx = -24f, dy = -96f)
        animation(FxAnimations.scale())
    }

    private fun installPanel(): FxControl = FloatingX.install(PANEL_TAG) {
        compose {
            val state by uiState.collectAsState()
            KhatKitTheme {
                state?.let { s ->
                    ChatFloatingPanel(
                        state = s,
                        maxHeight = 420.dp,
                        onCollapse = tracker::collapse,
                        onDismiss = tracker::dismiss,
                        onOpenConversation = { openConversation(s.conversationId) },
                        onReply = { openConversation(s.conversationId) },
                        onStop = { appScope.launch { chatManager.stopGeneration(s.conversationId) } },
                    )
                }
            }
        }
        appHost(context)
        anchor(FxGravity.TOP_CENTER, dy = 8f)
        animation(FxAnimations.fade())
    }

    private fun openConversation(conversationId: Uuid) {
        // 有悬浮窗权限的应用允许从后台启动 Activity；RouteActivity.handleIntent 读 conversationId extra
        context.startActivity(
            Intent(context, RouteActivity::class.java)
                .putExtra("conversationId", conversationId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
