package heizige.kk.khatkit.app.feature.tools.helper

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.feature.tools.data.ToolShortcutTokenRepository

internal object ToolShortcutHelper {
    const val ACTION = "heizige.kk.khatkit.app.action.OPEN_PINNED_TOOL"
    private const val EXTRA_NAME = "tool_name"
    private const val EXTRA_TOKEN = "tool_token"
    private val cardName = Regex("[a-z][a-z0-9_-]{0,63}")

    fun pin(context: Context, name: String, title: String): Boolean {
        if (!cardName.matches(name)) return false
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        return runCatching {
            if (!manager.isRequestPinShortcutSupported) return false
            val token = ToolShortcutTokenRepository.ensureToken(context, name)
            val launch = Intent(context, RouteActivity::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val shortcut = ShortcutInfo.Builder(context, id(name))
                .setShortLabel(title.trim().ifBlank { name }.take(32))
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launch)
                .build()
            manager.requestPinShortcut(shortcut, null)
        }.getOrDefault(false)
    }

    fun resolve(context: Context, intent: Intent): Screen.CardWidget? {
        val name = intent.getStringExtra(EXTRA_NAME)?.takeIf(cardName::matches) ?: return null
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return null
        if (!ToolShortcutTokenRepository.matches(context, name, token)) return null
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return null
        val shortcut = runCatching { manager.pinnedShortcuts.firstOrNull { it.id == id(name) && it.isEnabled } }
            .getOrNull() ?: return null
        return Screen.CardWidget(name, shortcut.shortLabel?.toString().orEmpty())
    }

    private fun id(name: String) = "tool-card:$name"
}
