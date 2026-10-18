package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class WelcomeWidget(
    val card: LoadedCard,
    val enabled: Boolean,
    val loading: Boolean = false,
    val content: UiNode? = null,
    val error: String? = null,
    val refreshAction: String = "",
)

internal data class ChatWelcomeState(
    val widgets: List<WelcomeWidget> = emptyList(),
    /** AI 生成的开场提示（标题 + 说明）；空表示还没拿到（UI 退回内置静态提示）。 */
    val ideas: List<WelcomeIdeas.Idea> = emptyList(),
    val ideasLoading: Boolean = false,
)

/**
 * Keeps completed widgets across recomposition/rotation; owns only foreground welcome runs.
 *
 * [loadIdeas] 是「生成开场提示」这一层注入口：manager 不碰模型/设置，只负责缓存与并发，
 * 于是同一个会话里转屏、返回、重新可见都不会重复请求模型。
 */
internal class ChatWelcomeManager(
    private val scope: CoroutineScope,
    val provider: KhatKitToolProvider,
    private val loadIdeas: suspend (localeTags: List<String>, count: Int) -> List<WelcomeIdeas.Idea> = { _, _ -> emptyList() },
) {
    private val _state = MutableStateFlow(ChatWelcomeState())
    val state = _state.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val runTokens = mutableMapOf<String, Long>()
    private var nextRunToken = 0L
    private var locales = emptyList<String>()
    private var revision = -1L
    private var ideasJob: Job? = null
    /** 只自动试一次：快速模型没配/网络抖动时不要每次可见都再打一次请求。 */
    private var ideasAsked = false

    suspend fun prepare(localeTags: List<String>, catalogueRevision: Long) {
        if (locales != localeTags || revision != catalogueRevision) {
            val cards = provider.chatCards()
            // 语言变了：人设没变，但提示要按新语言重写，所以旧的一批必须丢掉并允许再请求一次。
            val languageChanged = locales != localeTags
            val previous = if (!languageChanged) _state.value.widgets.associateBy { it.card.manifest.name } else emptyMap()
            locales = localeTags
            revision = catalogueRevision
            _state.value = ChatWelcomeState(
                widgets = cards.filter { it.manifest.widget != null }.map { card ->
                    val old = previous[card.manifest.name]?.takeIf { it.card.manifest.version == card.manifest.version }
                    old?.copy(card = card, enabled = provider.widgetEnabled(card))
                        ?: WelcomeWidget(card, provider.widgetEnabled(card))
                },
                ideas = if (languageChanged) emptyList() else _state.value.ideas,
            )
            if (languageChanged) ideasAsked = false
        }
        _state.value.widgets.filter { it.enabled && it.content == null && it.error == null }.forEach {
            refresh(it.card.manifest.name)
        }
        requestIdeas(localeTags, force = false)
    }

    /** 「换一换」：丢掉上一批提示，重新向模型要一次。 */
    fun regenerateIdeas(localeTags: List<String>) {
        requestIdeas(localeTags, force = true)
    }

    private fun requestIdeas(localeTags: List<String>, force: Boolean) {
        if (!force) {
            if (ideasAsked || _state.value.ideas.isNotEmpty()) return
        }
        ideasAsked = true
        ideasJob?.cancel()
        _state.update { it.copy(ideasLoading = true) }
        ideasJob = scope.launch {
            val ideas = runCatching { loadIdeas(localeTags, WelcomeIdeas.COUNT) }.getOrDefault(emptyList())
            _state.update { state ->
                state.copy(
                    ideas = ideas.ifEmpty { state.ideas },
                    ideasLoading = false,
                )
            }
        }
    }

    fun setEnabled(name: String, enabled: Boolean) {
        if (!enabled) {
            runTokens.remove(name)
            jobs.remove(name)?.cancel()
        }
        update(name) { it.copy(enabled = enabled, loading = false, error = if (enabled) null else it.error) }
        provider.setWidgetEnabled(name, enabled)
    }

    fun refresh(name: String, action: String = "", values: Map<String, Any?> = emptyMap()) {
        if (jobs[name]?.isActive == true) return
        val widget = _state.value.widgets.firstOrNull { it.card.manifest.name == name && it.enabled } ?: return
        val token = ++nextRunToken
        runTokens[name] = token
        fun publish(transform: (WelcomeWidget) -> WelcomeWidget) {
            if (runTokens[name] == token) update(name, transform)
        }
        update(name) { it.copy(loading = true, error = null) }
        jobs[name] = scope.launch {
            try {
                val content = withTimeout(30_000L) { provider.runChatWidget(widget.card, locales, action, values) }
                publish { it.copy(content = content.root, refreshAction = content.refreshAction, loading = false, error = null) }
            } catch (timeout: TimeoutCancellationException) {
                publish { it.copy(loading = false, error = "WIDGET_TIMEOUT") }
            } catch (cancelled: CancellationException) {
                publish { it.copy(loading = false) }
                throw cancelled
            } catch (error: Exception) {
                publish { it.copy(loading = false, error = error.message?.take(240) ?: "WIDGET_FAILED") }
            }
        }
    }

    /** Auto-loading is read-only; only an explicit header click uses this action. */
    fun refreshFromHeader(name: String, values: Map<String, Any?>) {
        val widget = _state.value.widgets.firstOrNull { it.card.manifest.name == name && it.enabled } ?: return
        refresh(name, widget.refreshAction, values)
    }

    fun pause() {
        runTokens.clear()
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        ideasJob?.cancel()
        ideasJob = null
        _state.update { state ->
            state.copy(widgets = state.widgets.map { it.copy(loading = false) }, ideasLoading = false)
        }
    }

    private fun update(name: String, transform: (WelcomeWidget) -> WelcomeWidget) {
        _state.update { state ->
            state.copy(widgets = state.widgets.map { if (it.card.manifest.name == name) transform(it) else it })
        }
    }
}
