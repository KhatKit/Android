package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.os.Build
import android.util.Log
import heizige.kk.khatkit.bridge.HostBridge
import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import java.util.concurrent.atomic.AtomicLong

class HostBridgeImpl(
    private val context: Context,
    private val registry: () -> BridgeRegistry?,
    private val manifest: () -> CardManifest? = { null },
) : HostBridge {
    private val startedAt = System.currentTimeMillis()
    private val timeoutAt = AtomicLong(0L)

    override fun info(): Map<String, Any?> = mapOf(
        "appVersion" to runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull(),
        "osVersion" to Build.VERSION.RELEASE,
        "sdkInt" to Build.VERSION.SDK_INT,
        "engine" to "rust",
    )

    override fun card(): Map<String, Any?> {
        val card = manifest()
        return mapOf(
            "name" to card?.name.orEmpty(),
            "version" to card?.version.orEmpty(),
            "author" to card?.author.orEmpty(),
            "tags" to listOfNotNull(card?.tags?.domain, card?.tags?.action, card?.tags?.scene),
        )
    }

    override fun health(): Map<String, Any?> = mapOf(
        "root" to RootBridgeImpl.isAvailable(),
        "shizuku" to ShizukuBridgeImpl.isAvailable(),
        "accessibility" to (AccessibilityBridgeHolder.current() != null),
        "allFiles" to AllFilesAccess.isGranted(),
        "overlay" to true,
        "notifications" to true,
        // store.sql 的全文检索能力：平台 SQLite 不带 FTS5 时卡片应退化为 LIKE
        "fts5" to CardSqlRepository.fts5Supported(context),
    )

    override fun capabilities(): List<String> = registry()?.availableBridges()?.sorted().orEmpty()

    override fun log(level: String, message: String) {
        when (level.lowercase()) {
            "error" -> Log.e("KhatKitCard", message)
            "warn", "warning" -> Log.w("KhatKitCard", message)
            "debug" -> Log.d("KhatKitCard", message)
            else -> Log.i("KhatKitCard", message)
        }
    }

    override fun setTimeout(ms: Int) {
        require(ms >= 0) { "超时毫秒数不能为负数" }
        timeoutAt.set(if (ms == 0) 0L else System.currentTimeMillis() + ms)
    }

    override fun elapsedMs(): Long = System.currentTimeMillis() - startedAt
}
