package heizige.kk.khatkit.app.feature.automation.data
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 事件触发器开关（SharedPreferences 持久化）。
 *
 * - master：总开关，关闭时所有卡片都不自动运行
 * - disabledCards：按卡片关闭（默认所有声明了 events 的卡片都启用）
 * - maxParallel：卡片运行并发上限（1..3，默认 1，由 helper/TriggerManager 队列执行）
 * - 每卡事件覆盖与重试配置见 [heizige.kk.khatkit.app.feature.automation.data.CardTriggerOverride]
 */
class TriggerSettingsRepository(context: Context) {

    data class State(
        val masterEnabled: Boolean = false,
        val disabledCards: Set<String> = emptySet(),
        val maxParallel: Int = DEFAULT_MAX_PARALLEL,
    ) {
        fun isCardEnabled(name: String): Boolean = name !in disabledCards
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    val masterEnabled: Boolean get() = _state.value.masterEnabled

    /** 卡片运行并发上限（1..3）。 */
    val maxParallel: Int get() = _state.value.maxParallel

    /** 读取某卡片的覆盖配置；解析失败按“无覆盖”处理。 */
    fun overrideFor(name: String): CardTriggerOverride {
        val raw = prefs.getString(KEY_OVERRIDE_PREFIX + name, null) ?: return CardTriggerOverride.NONE
        return runCatching { json.decodeFromString<CardTriggerOverride>(raw) }
            .getOrDefault(CardTriggerOverride.NONE)
    }

    /** 写入某卡片的覆盖配置；无覆盖时删除 key。 */
    fun setOverride(name: String, override: CardTriggerOverride) {
        val editor = prefs.edit()
        if (override.isEmpty) {
            editor.remove(KEY_OVERRIDE_PREFIX + name)
        } else {
            val raw = runCatching { json.encodeToString(override) }.getOrNull() ?: return
            editor.putString(KEY_OVERRIDE_PREFIX + name, raw)
        }
        editor.apply()
    }

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

    fun setMaxParallel(value: Int) {
        val coerced = value.coerceIn(MIN_MAX_PARALLEL, MAX_MAX_PARALLEL)
        prefs.edit().putInt(KEY_MAX_PARALLEL, coerced).apply()
        _state.value = _state.value.copy(maxParallel = coerced)
    }

    private fun read(): State = State(
        masterEnabled = prefs.getBoolean(KEY_MASTER, false),
        disabledCards = prefs.getStringSet(KEY_DISABLED_CARDS, emptySet()).orEmpty(),
        maxParallel = prefs.getInt(KEY_MAX_PARALLEL, DEFAULT_MAX_PARALLEL)
            .coerceIn(MIN_MAX_PARALLEL, MAX_MAX_PARALLEL),
    )

    companion object {
        private const val PREFS_NAME = "khatkit_trigger_settings"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_DISABLED_CARDS = "disabled_cards"
        private const val KEY_OVERRIDE_PREFIX = "override_"
        private const val KEY_MAX_PARALLEL = "max_parallel"

        const val MIN_MAX_PARALLEL = 1
        const val MAX_MAX_PARALLEL = 3
        const val DEFAULT_MAX_PARALLEL = 1
    }
}
