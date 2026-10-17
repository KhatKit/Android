package heizige.kk.khatkit.app.core.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.util.JsonInstant

/**
 * 助手消息气泡默认开启。
 *
 * 历史坑：`DisplaySetting.showAssistantBubble` 早期默认值是 `false`，而 `JsonInstant`
 * 开了 `encodeDefaults = true` —— 所以**每一次落盘**都把这个字段以显式 `false` 写进了
 * `display_setting` 的 JSON。只改 Kotlin 默认值对既有安装完全无效：反序列化时 JSON 里
 * 那个显式 `false` 会覆盖默认值，于是「设置里没有这个开关、助手回复也没有底色」。
 *
 * 这个迁移只做一件事：把一次性的旧值翻成 `true`。只跑一次（`VERSION` 记到 6），
 * 之后用户手动关掉就是这个选择，不会被再翻回来 —— 与 V5「工具调用默认开启」同一套写法。
 */
class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsRepository.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        // 键不存在 = 全新安装，走 Kotlin 默认值（已经是 true），无需处理；
        // JSON 解析失败同样跳过，别让一条设置把整份配置卡死在迁移里。
        prefs[SettingsRepository.DISPLAY_SETTING]?.let { json ->
            runCatching {
                JsonInstant.decodeFromString<DisplaySetting>(json)
            }.getOrNull()?.let { display ->
                if (!display.showAssistantBubble) {
                    prefs[SettingsRepository.DISPLAY_SETTING] = JsonInstant.encodeToString(
                        display.copy(showAssistantBubble = true)
                    )
                }
            }
        }
        prefs[SettingsRepository.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}
