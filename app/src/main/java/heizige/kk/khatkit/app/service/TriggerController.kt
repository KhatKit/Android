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

    val logs = TriggerLogStore(appContext)

    private val _cards = MutableStateFlow<List<TriggerCard>>(emptyList())

    /** 已安装卡片及其生效事件（用户覆盖优先于 manifest，供设置页展示/编辑）。 */
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

    /** 重新扫描本地已安装卡片并同步到引擎（用户覆盖优先于 manifest 事件）。 */
    suspend fun refreshCards(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            BuiltinCards.install(appContext, cache)
            val loaded = cache.installedVersions().mapNotNull { (name, version) ->
                runCatching { cache.load(cache.cardDir(name, version)) }.getOrNull()
            }
            _cards.value = loaded.map { installed ->
                val name = installed.manifest.name
                val override = settings.overrideFor(name)
                TriggerCard(
                    name = name,
                    events = override.events ?: installed.manifest.events,
                    maxRetries = override.maxRetries.coerceIn(0, MAX_RETRIES),
                    retryDelaySeconds = override.retryDelaySeconds.coerceIn(0, MAX_RETRY_DELAY_SECONDS),
                    baseEvents = installed.manifest.events,
                )
            }
            syncEngine()
        }.onFailure { Log.e(TAG, "refreshCards failed", it) }
    }

    /** 总开关/单卡开关变化后调用。 */
    fun syncEngine() {
        engine.updateCards(
            _cards.value.filter { settings.isCardEnabled(it.name) && it.events.isNotEmpty() }
        )
    }

    /** 保存某卡片的覆盖事件与重试配置，并立即刷新引擎。 */
    fun saveOverride(name: String, override: CardTriggerOverride) {
        settings.setOverride(name, override)
        refreshCardsAsync()
    }

    private fun runCard(name: String, args: Map<String, Any?>) {
        if (!settings.masterEnabled) return
        scope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val outcome = executeCard(name, args)
            logs.append(
                TriggerLogEntry(
                    at = startedAt,
                    card = name,
                    type = eventTypeOf(args),
                    payload = briefPayload(args),
                    ok = outcome.ok,
                    message = outcome.message.maybeTruncate(),
                )
            )
            val retryQueued = engine.reportRunResult(name, args, outcome.ok)
            if (outcome.ok) {
                Log.i(TAG, "card executed: $name args=${args.keys}")
            } else if (!retryQueued) {
                // 重试次数已用尽（或未配置重试）：最终失败才通知用户
                notifyFailure(name, outcome.message)
            } else {
                Log.w(TAG, "card failed, retry queued: $name: ${outcome.message}")
            }
        }
    }

    private suspend fun executeCard(name: String, args: Map<String, Any?>): RunOutcome {
        val card = cache.installedVersions()[name]?.let { version ->
            runCatching { cache.load(cache.cardDir(name, version)) }.getOrNull()
        }
        if (card == null) {
            Log.w(TAG, "card not installed: $name")
            return RunOutcome(ok = false, message = "卡片未安装")
        }
        return when (val result = provider.runCardWithStatus(card, args)) {
            is EngineResult.Err -> RunOutcome(ok = false, message = "${result.code}: ${result.message}")
            is EngineResult.Ok -> {
                val scriptError = result.value["error"]?.toString()
                if (!scriptError.isNullOrBlank()) RunOutcome(ok = false, message = scriptError)
                else RunOutcome(ok = true)
            }
        }
    }

    private fun eventTypeOf(args: Map<String, Any?>): String =
        (args[TriggerEngine.ARG_EVENT] as? Map<*, *>)?.get("type")?.toString().orEmpty()
            .ifBlank { "unknown" }

    private fun briefPayload(args: Map<String, Any?>): String {
        val event = args[TriggerEngine.ARG_EVENT] as? Map<*, *> ?: return ""
        return event.entries
            .filter { it.key != "type" && it.value != null }
            .joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun String.maybeTruncate(): String =
        if (length <= MAX_MESSAGE_LENGTH) this else take(MAX_MESSAGE_LENGTH) + "…"

    private data class RunOutcome(
        val ok: Boolean,
        val message: String = "",
    )

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
        private const val MAX_RETRIES = 3
        private const val MAX_RETRY_DELAY_SECONDS = 3600
        private const val MAX_MESSAGE_LENGTH = 200

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
