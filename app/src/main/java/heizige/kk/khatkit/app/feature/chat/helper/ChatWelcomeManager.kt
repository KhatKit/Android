package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import heizige.kk.khatkit.app.feature.chat.data.ALL_TOOLS
import heizige.kk.khatkit.app.feature.chat.data.ALL_TOOLS_WIDGET
import heizige.kk.khatkit.app.feature.chat.data.ChatWelcomeState
import heizige.kk.khatkit.app.feature.chat.data.TERMINAL
import heizige.kk.khatkit.app.feature.chat.data.TERMINAL_WIDGET
import heizige.kk.khatkit.app.feature.chat.data.WelcomeWidget
import heizige.kk.khatkit.app.feature.chat.data.WidgetCells
import heizige.kk.khatkit.app.feature.chat.data.WidgetSizeLimits



/**
 * Keeps completed widgets across recomposition/rotation; owns only foreground welcome runs.
 */
internal class ChatWelcomeManager(
    private val scope: CoroutineScope,
    val provider: KhatKitToolProvider,
) {
    private var itemOrder: List<String> = emptyList()
    private val _state = MutableStateFlow(ChatWelcomeState())
    val state = _state.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val runTokens = mutableMapOf<String, Long>()
    private var nextRunToken = 0L
    private var locales = emptyList<String>()
    private var revision = -1L

    suspend fun prepare(localeTags: List<String>, catalogueRevision: Long) {
        if (locales != localeTags || revision != catalogueRevision) {
            val cards = provider.chatCards()
            val terminalAvailable = provider.terminalPluginAvailable()
            val languageChanged = locales != localeTags
            val previous = if (!languageChanged) _state.value.widgets.associateBy { it.card.manifest.name } else emptyMap()
            locales = localeTags
            revision = catalogueRevision
            // 用户拖过的顺序优先；没排过（或新装）的按安装顺序排在后面。sortedBy 是稳定排序。
            val order = provider.widgetOrder().withIndex().associate { (index, name) -> name to index }
            itemOrder = provider.widgetOrder()
            _state.value = ChatWelcomeState(
                widgets = cards.mapNotNull { card -> card.manifest.widget?.let { card to it } }.map { (card, spec) ->
                    val limits = WidgetSizeLimits.of(spec.size)
                    val size = limits.clamp(
                        provider.widgetSize(card.manifest.name)?.let { (w, h) -> WidgetCells(w, h) } ?: limits.default,
                    )
                    val old = previous[card.manifest.name]?.takeIf { it.card.manifest.version == card.manifest.version }
                    old?.copy(card = card, enabled = provider.widgetEnabled(card), size = size, limits = limits)
                        ?: WelcomeWidget(card, provider.widgetEnabled(card), size, limits)
                }.sortedBy { order[it.card.manifest.name] ?: Int.MAX_VALUE },
                toolSize = provider.widgetSize(ALL_TOOLS_WIDGET)?.let { WidgetSizeLimits.ALL_TOOLS.clamp(WidgetCells(it.first, it.second)) }
                    ?: WidgetSizeLimits.ALL_TOOLS.default,
                terminalAvailable = terminalAvailable,
                terminalEnabled = terminalAvailable && provider.widgetEnabled(TERMINAL_WIDGET),
                terminalSize = provider.widgetSize(TERMINAL_WIDGET)?.let {
                    WidgetSizeLimits.TERMINAL.clamp(WidgetCells(it.first, it.second))
                } ?: WidgetSizeLimits.TERMINAL.default,
            )
        }
        _state.value.widgets.filter { it.enabled && it.content == null && it.error == null }.forEach {
            refresh(it.card.manifest.name)
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

    fun setTerminalEnabled(enabled: Boolean) {
        if (!_state.value.terminalAvailable) return
        _state.update { it.copy(terminalEnabled = enabled) }
        provider.setWidgetEnabled(TERMINAL_WIDGET, enabled)
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
                publish { it.copy(content = content, loading = false, error = null) }
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

    /**
     * 拖动边框时每越过一格调一次（只改内存，内容按新格数现挑版本，不重跑脚本）；
     * 松手时 [persist] = true 才落盘。
     */
    fun setSize(name: String, size: WidgetCells, persist: Boolean) {
        if (name == TERMINAL_WIDGET) {
            val saved = WidgetSizeLimits.TERMINAL.clamp(size)
            _state.update { it.copy(terminalSize = saved) }
            if (persist) provider.setWidgetSize(name, saved.width, saved.height)
            return
        }
        if (name == ALL_TOOLS_WIDGET) {
            val saved = WidgetSizeLimits.ALL_TOOLS.clamp(size)
            _state.update { it.copy(toolSize = saved) }
            if (persist) provider.setWidgetSize(name, saved.width, saved.height)
            return
        }
        update(name) { it.copy(size = it.limits.clamp(size)) }
        if (persist) {
            val saved = _state.value.widgets.firstOrNull { it.card.manifest.name == name }?.size ?: return
            provider.setWidgetSize(name, saved.width, saved.height)
        }
    }

    /**
     * 编辑态拖动换位：把 [name] 挪到已开启组件里的第 [index] 位，只改内存；
     * 关着的组件留在原位。松手时调 [persistOrder]。
     */
    fun move(name: String, index: Int) {
        _state.update { state ->
            val enabled = state.widgets.filter { it.enabled }
            val from = enabled.indexOfFirst { it.card.manifest.name == name }
            if (from < 0 || index !in enabled.indices || from == index) return@update state
            val reordered = enabled.toMutableList().apply { add(index, removeAt(from)) }.iterator()
            state.copy(widgets = state.widgets.map { if (it.enabled) reordered.next() else it })
        }
    }

    fun itemOrder(names: List<String>): List<String> {
        val available = names.toSet()
        return (itemOrder.filter { it in available } + names.filterNot { it in itemOrder }).distinct()
    }

    fun itemSize(name: String, fallback: WidgetCells): WidgetCells {
        return when (name) {
            ALL_TOOLS_WIDGET -> _state.value.toolSize
            TERMINAL_WIDGET -> _state.value.terminalSize
            else -> fallback
        }
    }

    fun moveItem(name: String, index: Int, names: List<String>) {
        val current = itemOrder(names).toMutableList()
        val from = current.indexOf(name)
        if (from < 0 || index !in current.indices || from == index) return
        current.add(index, current.removeAt(from))
        itemOrder = current
        val rank = current.withIndex().associate { (position, item) -> item to position }
        _state.update { state ->
            state.copy(widgets = state.widgets.sortedBy { rank[it.card.manifest.name] ?: Int.MAX_VALUE })
        }
    }

    fun persistOrder() {
        provider.setWidgetOrder(itemOrder.ifEmpty { _state.value.widgets.map { it.card.manifest.name } })
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
        _state.update { state ->
            state.copy(widgets = state.widgets.map { it.copy(loading = false) })
        }
    }

    private fun update(name: String, transform: (WelcomeWidget) -> WelcomeWidget) {
        _state.update { state ->
            state.copy(widgets = state.widgets.map { if (it.card.manifest.name == name) transform(it) else it })
        }
    }
}
