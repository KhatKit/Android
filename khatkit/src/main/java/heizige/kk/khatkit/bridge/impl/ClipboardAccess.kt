package heizige.kk.khatkit.bridge.impl

import android.content.Context

/**
 * 剪贴板读取开关（设置 → 权限 → 剪贴板读取）。
 *
 * Android 10+ 起没有「常驻剪贴板权限」这一说，系统只在应用处于前台时允许读取；
 * 这里提供的是应用自己的一层开关：关掉后卡片/工具调用 [AndroidToolBridge.getClipboard]
 * 会直接报错，而不是把剪贴板内容交出去。默认开启。
 */
object ClipboardAccess {
    private const val PREFS = "khatkit_permissions"
    private const val KEY = "clipboard_read_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}
