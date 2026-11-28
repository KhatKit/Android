package heizige.kk.khatkit.app.feature.tools.data

import android.content.Context
import java.util.UUID

/** 桌面固定卡片 token 的落盘入口：prefs 名 / key / token 生成方式保持不变。 */
internal object ToolShortcutTokenRepository {
    private const val PREFS_NAME = "tool_shortcuts"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun tokenFor(context: Context, name: String): String? = preferences(context).getString(name, null)

    fun ensureToken(context: Context, name: String): String =
        tokenFor(context, name) ?: UUID.randomUUID().toString().also {
            preferences(context).edit().putString(name, it).apply()
        }

    fun matches(context: Context, name: String, token: String): Boolean =
        preferences(context).getString(name, null) == token
}
