package heizige.kk.khatkit.app.service

import android.content.Context
import heizige.kk.khatkit.card.CardManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 用户对单张卡片的触发覆盖（SharedPreferences JSON 持久化，按卡片名存储）。
 *
 * - events = null：沿用卡片 manifest 声明的 events
 * - events = 空列表：显式关闭该卡片的所有事件
 * - maxRetries / retryDelaySeconds：运行失败后的重试策略（0 = 保持默认不重试）
 */
@Serializable
data class CardTriggerOverride(
    val events: List<CardManifest.Event>? = null,
    @SerialName("max_retries") val maxRetries: Int = 0,
    @SerialName("retry_delay_seconds") val retryDelaySeconds: Int = 0,
) {
    val isEmpty: Boolean
        get() = events == null && maxRetries <= 0 && retryDelaySeconds <= 0

    companion object {
        val NONE = CardTriggerOverride()
    }
}

/**
 * 事件触发器开关（SharedPreferences 持久化）。
 *
 * - master：总开关，关闭时所有卡片都不自动运行
 * - disabledCards：按卡片关闭（默认所有声明了 events 的卡片都启用）
 * - 每卡事件覆盖与重试配置见 [CardTriggerOverride]
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    val masterEnabled: Boolean get() = _state.value.masterEnabled

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

    private fun read(): State = State(
        masterEnabled = prefs.getBoolean(KEY_MASTER, false),
        disabledCards = prefs.getStringSet(KEY_DISABLED_CARDS, emptySet()).orEmpty(),
    )

    companion object {
        private const val PREFS_NAME = "khatkit_trigger_settings"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_DISABLED_CARDS = "disabled_cards"
        private const val KEY_OVERRIDE_PREFIX = "override_"
    }
}
