package heizige.kk.khatkit.app.core.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.util.JsonInstant

/**
 * OA-4：账号类提供商（ChatGPT / Grok）落地后的一次性设置规范化。
 *
 * 为什么需要它：这版之前 providers 里只可能有 openai/google/claude；本版起
 * ProviderSetting 多了两个 Account 叶子（带 @SerialName("chatgpt"/"grok") 与 credential 字段），
 * DisplaySetting 也多了字段（如 effectsEnabled）。老安装的 JSON 里没有这些键 —— 反序列化能靠默认值
 * 兜住，但那些新键会一直停留在「缺省」状态，和「用户显式选的默认值」在语义上没法区分。
 * 所以这里跑一次「解码 -> 重新编码」，把新字段的默认值显式写下来，同时把版本记到 7。
 *
 * 兼容原则（与 V5/V6 同一套）：
 *  - 任何一段 JSON 解码失败都原样保留，绝不让一条老数据把整份设置卡死在迁移里；
 *  - 只补默认值，不翻任何用户已选的开关（V6 那种「默认值反了」的特例这里没有）；
 *  - 版本记到 7，之后不再跑。
 */
class PreferenceStoreV7Migration : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsRepository.VERSION]
        return version == null || version < 7
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        // 1) providers：解码成功才重编码（Account 叶子的 credential 默认 null 会被显式写入）
        prefs[SettingsRepository.PROVIDERS]?.let { json ->
            runCatching { JsonInstant.decodeFromString<List<ProviderSetting>>(json) }
                .getOrNull()
                ?.let { providers ->
                    prefs[SettingsRepository.PROVIDERS] = JsonInstant.encodeToString(providers)
                }
        }

        // 2) displaySetting：解码成功才重编码（effectsEnabled 等新字段落成显式默认值）
        prefs[SettingsRepository.DISPLAY_SETTING]?.let { json ->
            runCatching { JsonInstant.decodeFromString<DisplaySetting>(json) }
                .getOrNull()
                ?.let { display ->
                    prefs[SettingsRepository.DISPLAY_SETTING] = JsonInstant.encodeToString(display)
                }
        }

        prefs[SettingsRepository.VERSION] = 7
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}