package heizige.kk.khatkit.app.core.data.ai.hub

import android.content.Context
import android.os.Build
import android.provider.Settings
import heizige.kk.khatkit.bridge.impl.FileStoreBridge
import heizige.kk.khatkit.hub.HubAccountInfo
import java.util.UUID

/**
 * 套餐账户的本机存储。
 *
 * - 激活令牌 / 会话密钥：走 [FileStoreBridge] 的 store 密钥通道（Android Keystore AES/GCM），
 *   明文不落盘，与卡片密钥同一套安全存储
 * - Hub 地址沿用 KhatKitToolProvider.hubBaseUrl 的 SharedPreferences
 * - 设备标识优先 Settings.Secure.ANDROID_ID，异常时退回持久化随机 UUID
 * - 额度缓存为普通 SharedPreferences（非敏感，用于离线展示）
 */
class HubAccountRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secretStore = FileStoreBridge(appContext, SECRET_STORE_NAME)

    /** 激活令牌（Bearer / 网关 API Key）；未激活返回 null。 */
    fun token(): String? = secretStore.secretGet(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    /** 服务端会话密钥（若 /api/activate 返回）。 */
    fun sessionKey(): String? = secretStore.secretGet(KEY_SESSION_KEY)?.takeIf { it.isNotBlank() }

    fun saveActivation(token: String, sessionKey: String) {
        if (token.isNotBlank()) secretStore.secretSet(KEY_TOKEN, token)
        if (sessionKey.isNotBlank()) secretStore.secretSet(KEY_SESSION_KEY, sessionKey)
    }

    /** 清除本机激活信息（令牌 + 额度缓存）。 */
    fun clear() {
        runCatching { secretStore.secretRemove(KEY_TOKEN) }
        runCatching { secretStore.secretRemove(KEY_SESSION_KEY) }
        prefs.edit().clear().apply()
    }

    /** 设备唯一标识：ANDROID_ID 优先，缺失时用持久化随机 UUID（重装应用才会变化）。 */
    fun deviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (!androidId.isNullOrBlank() && androidId != BAD_ANDROID_ID) {
            return "android:$androidId"
        }
        prefs.getString(KEY_FALLBACK_DEVICE_ID, null)?.let { return it }
        val generated = "uuid:${UUID.randomUUID()}"
        prefs.edit().putString(KEY_FALLBACK_DEVICE_ID, generated).apply()
        return generated
    }

    /** 设备名称：厂商 + 型号，便于用户在服务端识别设备。 */
    fun deviceName(): String = runCatching {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .distinct()
            .joinToString(" ")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android 设备"

    /** 缓存 /api/me 的额度，供离线展示。 */
    fun cacheAccount(info: HubAccountInfo) {
        prefs.edit()
            .putString(KEY_PLAN, info.plan)
            .putLong(KEY_AI_TOKENS, info.aiTokensRemaining ?: -1L)
            .putLong(KEY_TOOL_CALLS, info.toolCallsRemaining ?: -1L)
            .putLong(KEY_EXPIRES_AT, info.expiresAt ?: -1L)
            .apply()
    }

    /** 读取缓存的额度（未缓存的字段为 null）。 */
    fun cachedAccount(): HubAccountInfo = HubAccountInfo(
        active = token() != null,
        plan = prefs.getString(KEY_PLAN, null).orEmpty(),
        aiTokensRemaining = prefs.getLong(KEY_AI_TOKENS, -1L).takeIf { it >= 0 },
        toolCallsRemaining = prefs.getLong(KEY_TOOL_CALLS, -1L).takeIf { it >= 0 },
        expiresAt = prefs.getLong(KEY_EXPIRES_AT, -1L).takeIf { it > 0 },
    )

    private companion object {
        const val PREFS_NAME = "khatkit_hub_account"
        const val SECRET_STORE_NAME = "__hub_account__"
        const val KEY_TOKEN = "activation_token"
        const val KEY_SESSION_KEY = "session_key"
        const val KEY_FALLBACK_DEVICE_ID = "fallback_device_id"
        const val KEY_PLAN = "quota_plan"
        const val KEY_AI_TOKENS = "quota_ai_tokens"
        const val KEY_TOOL_CALLS = "quota_tool_calls"
        const val KEY_EXPIRES_AT = "quota_expires_at"

        /** 历史上部分模拟器/刷机设备返回的固定 ANDROID_ID，不具备唯一性。 */
        const val BAD_ANDROID_ID = "9774d56d682e549c"
    }
}
