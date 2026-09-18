package heizige.kk.khatkit.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.BuiltinCards
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.trigger.TriggerCard
import heizige.kk.khatkit.trigger.TriggerEngine
import heizige.kk.khatkit.trigger.TriggerRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 事件触发控制器：把 khatkit 的 [TriggerEngine] 与真实卡片执行器接起来。
 *
 * - 复用 KhatKitToolProvider 的 CardRunManager/CardExecutor（同一并发闸门与 bridge）
 * - 卡片清单来自 CardCache（与卡片市场/工具用的是同一份本地缓存）
 * - 脚本失败只发通知 + 日志，不抛出，绝不打断宿主
 */
class TriggerController(
    context: Context,
    private val provider: KhatKitToolProvider,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "khatkit").apply { mkdirs() }
    private val cache = CardCache(rootDir)

    val settings = TriggerSettings(appContext)

    private val _cards = MutableStateFlow<List<TriggerCard>>(emptyList())

    /** 声明了 events 的已安装卡片（供设置页展示）。 */
    val cards: StateFlow<List<TriggerCard>> = _cards.asStateFlow()

    val engine = TriggerEngine(
        runner = TriggerRunner { name, args -> runCard(name, args) },
    )

    init {
        ensureChannel(appContext)
    }

    fun refreshCardsAsync() {
        scope.launch { refreshCards() }
    }

    /** 重新扫描本地已安装卡片并同步到引擎（只保留启用中的卡片）。 */
    suspend fun refreshCards(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            BuiltinCards.install(appContext, cache)
            val loaded = cache.installedVersions().mapNotNull { (name, version) ->
                runCatching { cache.load(cache.cardDir(name, version)) }.getOrNull()
            }
            _cards.value = loaded
                .filter { it.manifest.events.isNotEmpty() }
                .map { TriggerCard(it.manifest.name, it.manifest.events) }
            syncEngine()
        }.onFailure { Log.e(TAG, "refreshCards failed", it) }
    }

    /** 总开关/单卡开关变化后调用。 */
    fun syncEngine() {
        engine.updateCards(
            _cards.value.filter { settings.isCardEnabled(it.name) }
        )
    }

    private fun runCard(name: String, args: Map<String, Any?>) {
        if (!settings.masterEnabled) return
        scope.launch(Dispatchers.IO) {
            val card = cache.installedVersions()[name]?.let { version ->
                runCatching { cache.load(cache.cardDir(name, version)) }.getOrNull()
            }
            if (card == null) {
                Log.w(TAG, "card not installed: $name")
                return@launch
            }
            val result = provider.runManager.run(card, args)
            when (result) {
                is EngineResult.Err -> notifyFailure(name, "${result.code}: ${result.message}")
                is EngineResult.Ok -> {
                    val scriptError = result.value["error"]?.toString()
                    if (!scriptError.isNullOrBlank()) notifyFailure(name, scriptError)
                    else Log.i(TAG, "card executed: $name args=${args.keys}")
                }
            }
        }
    }

    private fun notifyFailure(name: String, message: String) {
        Log.e(TAG, "card trigger failed: $name: $message")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(appContext.getString(R.string.trigger_notification_failed_title))
                .setContentText(appContext.getString(R.string.trigger_notification_failed_text, name, message))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(appContext.getString(R.string.trigger_notification_failed_text, name, message))
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(appContext)
                .notify(NOTIFICATION_ID_BASE + (name.hashCode() and 0xFF), notification)
        }.onFailure { Log.e(TAG, "notifyFailure failed", it) }
    }

    companion object {
        private const val TAG = "TriggerController"
        const val CHANNEL_ID = "khatkit_trigger"
        private const val NOTIFICATION_ID_BASE = 2100

        /** 幂等创建通知渠道；前台服务 startForeground 前必须先有渠道。 */
        fun ensureChannel(context: Context) {
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.trigger_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(R.string.trigger_notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
