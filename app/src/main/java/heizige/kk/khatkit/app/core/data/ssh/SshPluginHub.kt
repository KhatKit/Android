package heizige.kk.khatkit.app.core.data.ssh

import android.content.Context
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.localization.localizedMessage
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 KhatKitHub 下载并安装远程 SSH 插件。
 *
 * 条目来自 `GET /api/items?kind=dependency`（与 OCR 插件同一通道），安装包地址是
 * `GET /api/items/{id}/download`，响应头 `X-Content-SHA256` 必须与包内计算值一致。
 */
object SshPluginHub {
    /** 按扩展页/市场里拿到的条目 id 安装（市场行已经带 id，不用再查列表）。 */
    suspend fun install(
        context: Context,
        hubBaseUrl: String,
        itemId: String,
    ): Result<SshPluginRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val base = hubBaseUrl.trim().trimEnd('/')
            require(base.isNotEmpty()) { context.getString(R.string.plugin_hub_url_missing) }
            require(itemId.isNotBlank()) { context.getString(R.string.plugin_item_id_empty) }
            val tmp = appEntryPoint(context).khatKitToolProvider().downloadFile(
                "$base/api/items/$itemId/download",
                "ssh_plugin_$itemId.zip",
            ) ?: error(context.getString(R.string.plugin_download_failed))
            try {
                SshPluginInstaller.install(context, tmp, null, itemId).getOrThrow()
            } finally {
                tmp.delete()
            }
        }
    }

    /** 按名字在 `kind=dependency` 里找最近的条目再安装（扩展页只需要名字）。 */
    suspend fun installLatest(
        context: Context,
        hubBaseUrl: String,
    ): Result<SshPluginRecord> = withContext(Dispatchers.IO) {
        val base = hubBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext Result.failure(IllegalStateException(context.getString(R.string.plugin_hub_url_missing)))
        val appVersion = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("")
        val item = runCatching {
            HubClient(base, messageResolver = { it.localizedMessage(context) })
                .extensionItems(kind = "dependency", appVersion = appVersion)
                .filter { it.name == SshPluginInfo.MARKET_NAME }
                .sortedByDescending { it.preferred }
                .firstOrNull()
        }.getOrNull() ?: return@withContext Result.failure(
            IllegalStateException(context.getString(R.string.plugin_entry_missing, SshPluginInfo.displayName(context))),
        )
        install(context, base, item.id)
    }
}
