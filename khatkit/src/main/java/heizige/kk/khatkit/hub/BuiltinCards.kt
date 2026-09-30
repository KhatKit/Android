package heizige.kk.khatkit.hub

import android.content.Context


/**
 * 宿主内置卡片层（设计文档 8.2 的思路）。
 *
 * 卡片已移到独立 Hub cards 目录，不再随 APK 打包。
 * 保留兼容入口，旧调用不会因为缺少 assets 而失败。
 */
object BuiltinCards {

    fun install(context: Context, cache: CardCache): List<String> = emptyList()
}
