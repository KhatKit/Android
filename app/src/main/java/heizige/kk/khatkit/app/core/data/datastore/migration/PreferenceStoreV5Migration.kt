package heizige.kk.khatkit.app.core.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
import heizige.kk.khatkit.app.core.util.JsonInstant

/**
 * 工具调用默认开启：给能力为空的模型补上 TOOL 能力。
 * 只跑一次，之后用户手动取消的能力不会被覆盖。
 */
class PreferenceStoreV5Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 5
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[SettingsStore.PROVIDERS]?.let { json ->
            runCatching {
                JsonInstant.decodeFromString<List<ProviderSetting>>(json)
            }.getOrNull()?.let { providers ->
                prefs[SettingsStore.PROVIDERS] = JsonInstant.encodeToString(
                    providers.map { it.withDefaultToolAbility() }
                )
            }
        }
        prefs[SettingsStore.VERSION] = 5
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

private fun ProviderSetting.withDefaultToolAbility(): ProviderSetting {
    fun Model.normalized(): Model =
        if (abilities.isEmpty()) copy(abilities = listOf(ModelAbility.TOOL)) else this

    return when (this) {
        is ProviderSetting.OpenAI -> copy(models = models.map { it.normalized() })
        is ProviderSetting.Google -> copy(models = models.map { it.normalized() })
        is ProviderSetting.Claude -> copy(models = models.map { it.normalized() })
    }
}
