package heizige.kk.khatkit.app.core.data.terminal

import android.content.Context
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.localization.localizedMessage
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TerminalPluginHub {
    suspend fun installLatest(context: Context, hubBaseUrl: String): Result<TerminalPluginRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val base = hubBaseUrl.trim().trimEnd('/')
            require(base.isNotBlank()) { context.getString(R.string.plugin_hub_url_missing) }
            val item = HubClient(base, messageResolver = { it.localizedMessage(context) })
                .extensionItems(kind = "dependency", appVersion = BuildConfig.VERSION_NAME)
                .firstOrNull { it.name == TerminalPluginInfo.MARKET_NAME }
                ?: error(context.getString(R.string.plugin_entry_missing, TerminalPluginInfo.DISPLAY_NAME))
            val tmp = appEntryPoint(context).khatKitToolProvider().downloadFile(
                "$base/api/items/${item.id}/download",
                "terminal-plugin-${item.id}.zip",
            ) ?: error(context.getString(R.string.plugin_download_failed))
            try {
                TerminalPluginInstaller.install(context, tmp, null, item.id).getOrThrow()
            } finally { tmp.delete() }
        }
    }
}
