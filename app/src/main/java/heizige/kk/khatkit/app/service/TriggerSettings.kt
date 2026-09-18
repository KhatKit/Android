package heizige.kk.khatkit.app.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 事件触发器开关（SharedPreferences 持久化）。
 *
 * - master：总开关，关闭时所有卡片都不自动运行
 * - disabledCards：按卡片关闭（默认所有声明了 events 的卡片都启用）
 */
class TriggerSettings(context: Context) {

    data class State(
        val masterEnabled: Boolean = false,
        val disabledCards: Set<String> = emptySet(),
    ) {
        fun isCardEnabled(name: String): Boolean = name !in disabledCards
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    val masterEnabled: Boolean get() = _state.value.masterEnabled

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER, enabled).apply()
        _state.value = _state.value.copy(masterEnabled = enabled)
    }

    fun setCardEnabled(name: String, enabled: Boolean) {
        val disabled = _state.value.disabledCards.toMutableSet()
        if (enabled) disabled.remove(name) else disabled.add(name)
        prefs.edit().putStringSet(KEY_DISABLED_CARDS, disabled).apply()
        _state.value = _state.value.copy(disabledCards = disabled)
    }

    fun isCardEnabled(name: String): Boolean = _state.value.isCardEnabled(name)

    private fun read(): State = State(
        masterEnabled = prefs.getBoolean(KEY_MASTER, false),
        disabledCards = prefs.getStringSet(KEY_DISABLED_CARDS, emptySet()).orEmpty(),
    )

    companion object {
        private const val PREFS_NAME = "khatkit_trigger_settings"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_DISABLED_CARDS = "disabled_cards"
    }
}
