package heizige.kk.khatkit.app.feature.automation

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
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.BuiltinCards
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.trigger.TriggerCard
import heizige.kk.khatkit.trigger.TriggerEngine
import heizige.kk.khatkit.trigger.TriggerRunner
import heizige.kk.khatkit.trigger.WorkdayCalendar
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
 * - 触发运行经过 [TriggerRunQueue] 串行排队（并发上限见 TriggerSettings.maxParallel）
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

    private val runQueue = TriggerRunQueue(
        capacity = QUEUE_CAPACITY,
        maxParallel = { settings.maxParallel },
    )

    /** 节假日日历（schedule 的 calendar 过滤），懒加载后缓存；[reloadCalendar] 可刷新覆盖文件。 */
    @Volatile
    private var workdayCalendar: WorkdayCalendar? = null

    init {
        ensureChannel(appContext)
    }

    /** 重新加载节假日日历（内置资产 + 用户覆盖文件）并注入引擎。 */
    fun reloadCalendar() {
        val loaded = WorkdayCalendar.load(appContext)
        workdayCalendar = loaded
        engine.calendar = loaded
    }

    private fun calendar(): WorkdayCalendar =
        workdayCalendar ?: WorkdayCalendar.load(appContext).also {
            workdayCalendar = it
            engine.calendar = it
        }

    fun refreshCardsAsync() {
        scope.launch { refreshCards() }
    }

    /** 重新扫描本地已安装卡片并同步到引擎（用户覆盖优先于 manifest 事件）。 */
    suspend fun refreshCards(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            BuiltinCards.install(appContext, cache)
            reloadCalendar()
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
                    disabledIndexes = override.disabledEvents.filter { it >= 0 }.toSet(),
                )
            }
            syncEngine()
            syncExternalBindings()
        }.onFailure { Log.e(TAG, "refreshCards failed", it) }
    }

    /** 总开关/单卡开关/事件变化后调用；同步引擎卡片与精确闹钟。 */
    fun syncEngine() {
        val enabled = automationCards()
        engine.updateCards(enabled)
        TriggerExactAlarmScheduler.schedule(appContext, enabled, settings.masterEnabled, calendar())
    }

    /** 闹钟投递后由 TriggerService 调用，重排下一次精确闹钟。 */
    fun rescheduleExactAlarm() {
        TriggerExactAlarmScheduler.schedule(appContext, automationCards(), settings.masterEnabled, calendar())
    }

    /** 全局并发上限（1..3），即时生效（队列每次调度都会重新读取）。 */
    fun setMaxParallel(value: Int) {
        settings.setMaxParallel(value)
    }

    private fun automationCards(): List<TriggerCard> =
        _cards.value.filter { settings.isCardEnabled(it.name) && it.events.isNotEmpty() }

    /** 按当前启用卡片同步桌面快捷方式与磁贴槽位（仅受单卡开关影响，不受总开关影响）。 */
    private fun syncExternalBindings() {
        val configured = _cards.value.filter { settings.isCardEnabled(it.name) && it.events.isNotEmpty() }
        runCatching { TriggerShortcutPublisher.sync(appContext, configured) }
            .onFailure { Log.e(TAG, "sync shortcuts failed", it) }
        runCatching {
            val tileCards = configured
                .filter { card ->
                    card.events.indices.any { index ->
                        index !in card.disabledIndexes &&
                            card.events[index].type == CardManifest.EVENT_TILE
                    }
                }
                .map { it.name }
            TriggerTileRegistry(appContext).sync(tileCards)
        }.onFailure { Log.e(TAG, "sync tiles failed", it) }
    }

    /** 保存某卡片的覆盖事件与重试配置，并立即刷新引擎。 */
    fun saveOverride(name: String, override: CardTriggerOverride) {
        settings.setOverride(name, override)
        refreshCardsAsync()
    }

    /** 单卡开关变化后异步重同步桌面快捷方式 / 磁贴（不必重扫卡片目录）。 */
    fun syncExternalBindingsAsync() {
        scope.launch(Dispatchers.IO) { syncExternalBindings() }
    }

    private fun runCard(name: String, args: Map<String, Any?>) {
        if (!settings.masterEnabled) return
        submitRun(TriggerRunQueue.Item(card = name, args = args))
    }

    /**
     * 用户主动触发（桌面快捷方式 / 快捷设置磁贴）：不受总开关影响，
     * 但仍要求卡片已启用且声明了对应事件（未被单独停用）。
     */
    fun runExternal(name: String, eventType: String) {
        scope.launch(Dispatchers.IO) {
            if (_cards.value.none { it.name == name }) refreshCards()
            val card = _cards.value.firstOrNull { it.name == name } ?: return@launch
            if (!settings.isCardEnabled(name)) return@launch
            val index = card.events.indices.firstOrNull { i ->
                i !in card.disabledIndexes && card.events[i].type == eventType
            } ?: return@launch
            val label = card.events[index].name.ifBlank { card.name }
            submitRun(
                TriggerRunQueue.Item(
                    card = name,
                    args = TriggerEngine.eventArgs(type = eventType, name = label),
                    external = true,
                )
            )
        }
    }

    /** 提交到串行队列；队列满时丢弃最旧任务并写日志。 */
    private fun submitRun(item: TriggerRunQueue.Item) {
        val batch = runQueue.submit(item)
        batch.dropped?.let { dropped ->
            logs.append(
                TriggerLogEntry(
                    at = System.currentTimeMillis(),
                    card = dropped.card,
                    type = eventTypeOf(dropped.args),
                    payload = briefPayload(dropped.args),
                    ok = false,
                    message = "触发队列已满，丢弃最早排队任务".maybeTruncate(),
                )
            )
        }
        batch.starts.forEach(::launchRun)
    }

    private fun launchRun(item: TriggerRunQueue.Item) {
        scope.launch(Dispatchers.IO) {
            try {
                executeAndLog(item)
            } finally {
                runQueue.complete().forEach(::launchRun)
            }
        }
    }

    private suspend fun executeAndLog(item: TriggerRunQueue.Item) {
        // 排队期间总开关被关闭：自动触发直接放弃（用户主动触发继续）
        if (!item.external && !settings.masterEnabled) return
        val startedAt = System.currentTimeMillis()
        val outcome = executeCard(item.card, item.args)
        logs.append(
            TriggerLogEntry(
                at = startedAt,
                card = item.card,
                type = eventTypeOf(item.args),
                payload = briefPayload(item.args),
                ok = outcome.ok,
                message = outcome.message.maybeTruncate(),
            )
        )
        val retryQueued = engine.reportRunResult(item.card, item.args, outcome.ok)
        if (outcome.ok) {
            Log.i(TAG, "card executed: ${item.card} args=${item.args.keys}")
        } else if (!retryQueued) {
            // 重试次数已用尽（或未配置重试）：最终失败才通知用户
            notifyFailure(item.card, outcome.message)
        } else {
            Log.w(TAG, "card failed, retry queued: ${item.card}: ${outcome.message}")
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
        return when (val result = provider.runCardWithStatus(card, args, trigger = "事件触发")) {
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
        private const val QUEUE_CAPACITY = 10

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
