package heizige.kk.khatkit.app.core.data.terminal

import android.content.Context
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.app.feature.extensions.TerminalPluginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TerminalPluginHub {
    suspend fun installLatest(context: Context, hubBaseUrl: String): Result<TerminalPluginRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val base = hubBaseUrl.trim().trimEnd('/')
            require(base.isNotBlank()) { "Hub 地址未配置，先在设置里填写" }
            val item = HubClient(base).extensionItems(kind = "dependency", appVersion = BuildConfig.VERSION_NAME)
                .firstOrNull { it.name == TerminalPluginInfo.MARKET_NAME }
                ?: error("服务器上还没有「${TerminalPluginInfo.DISPLAY_NAME}」插件条目")
            val tmp = appEntryPoint(context).khatKitToolProvider().downloadFile(
                "$base/api/items/${item.id}/download",
                "terminal-plugin-${item.id}.zip",
            ) ?: error("插件下载失败")
            try {
                TerminalPluginInstaller.install(context, tmp, null, item.id).getOrThrow()
            } finally { tmp.delete() }
        }
    }
}
