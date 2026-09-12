package heizige.kk.khatkit.hub

import android.content.Context
import heizige.kk.khatkit.card.CardParser
import java.io.File

/**
 * 宿主内置卡片层（设计文档 8.2 的思路）。
 *
 * 把打包进 assets/cards 的示例卡片铺到本地缓存，首次运行即可用，
 * 不依赖网络。已存在的卡片目录不覆盖。
 */
object BuiltinCards {

    private const val ASSET_ROOT = "cards"

    fun install(context: Context, cache: CardCache): List<String> {
        val installed = mutableListOf<String>()
        val cardNames = context.assets.list(ASSET_ROOT).orEmpty()
        for (dirName in cardNames) {
            val assetDir = "$ASSET_ROOT/$dirName"
            val manifestText = runCatching {
                context.assets.open("$assetDir/card.json").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            val manifest = CardParser.parse(manifestText).getOrNull() ?: continue

            val target = cache.cardDir(manifest.name, manifest.version)
            if (File(target, "card.json").exists()) continue

            target.mkdirs()
            context.assets.list(assetDir).orEmpty().forEach { fileName ->
                runCatching {
                    context.assets.open("$assetDir/$fileName").use { input ->
                        File(target, fileName).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            installed.add(manifest.name)
        }
        return installed
    }
}
