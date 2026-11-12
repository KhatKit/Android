package heizige.kk.khatkit.app.core.data.ssh

import android.content.Context
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
            require(base.isNotEmpty()) { "Hub 地址未配置，先在设置里填写" }
            require(itemId.isNotBlank()) { "插件条目 id 为空" }
            val url = URL(base + "/api/items/" + itemId + "/download")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            val tmp = File(context.cacheDir, "ssh_plugin_" + System.currentTimeMillis() + ".zip")
            try {
                conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                val sha = conn.getHeaderField("X-Content-SHA256")
                SshPluginInstaller.install(context, tmp, sha, itemId).getOrThrow()
            } finally {
                tmp.delete()
                runCatching { conn.disconnect() }
            }
        }
    }

    /** 按名字在 `kind=dependency` 里找最近的条目再安装（扩展页只需要名字）。 */
    suspend fun installLatest(
        context: Context,
        hubBaseUrl: String,
    ): Result<SshPluginRecord> = withContext(Dispatchers.IO) {
        val base = hubBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext Result.failure(IllegalStateException("Hub 地址未配置，先在设置里填写"))
        val appVersion = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("")
        val item = runCatching {
            HubClient(base)
                .extensionItems(kind = "dependency", appVersion = appVersion)
                .filter { it.name == SshPluginInfo.MARKET_NAME }
                .sortedByDescending { it.preferred }
                .firstOrNull()
        }.getOrNull() ?: return@withContext Result.failure(
            IllegalStateException("服务器上还没有「" + SshPluginInfo.DISPLAY_NAME + "」插件条目"),
        )
        install(context, base, item.id)
    }
}
