package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import java.time.Instant
import java.time.ZoneId

/** 可参与事件触发的已安装卡片（name + manifest 中声明的事件）。 */
data class TriggerCard(
    val name: String,
    val events: List<CardManifest.Event>,
)

/**
 * 卡片执行回调。实现方负责把执行切到 IO 线程；
 * 引擎只保证匹配、冷却与 args 组装，不关心执行细节。
 */
fun interface TriggerRunner {
    fun runCard(name: String, args: Map<String, Any?>)
}

/**
 * 事件触发引擎（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * - schedule：宿主定时 tick，按本地时间匹配 times（HH:mm）或 intervalMinutes
 * - notification / app_launch / charging：宿主投递事件，引擎匹配包名/内容/状态
 * - 同一卡片同类型事件有冷却时间，避免通知风暴/前后台抖动导致连发
 */
class TriggerEngine(
    private val runner: TriggerRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var cards: List<TriggerCard> = emptyList()

    /** "card|type" -> 最近一次运行时间 */
    private val lastRunAt = mutableMapOf<String, Long>()

    /** "card|eventIndex" -> 已触发的 "epochDay HH:mm"，防止同一分钟重复触发 */
    private val firedSlots = mutableMapOf<String, String>()

    /** "card|eventIndex" -> 间隔模式的上一轮起点 */
    private val intervalAnchors = mutableMapOf<String, Long>()

    fun updateCards(cards: List<TriggerCard>) {
        synchronized(lock) {
            this.cards = cards
            val alive = cards.mapTo(mutableSetOf()) { it.name }
            lastRunAt.keys.removeAll { it.substringBefore('|') !in alive }
            firedSlots.keys.removeAll { it.substringBefore('|') !in alive }
            intervalAnchors.keys.removeAll { it.substringBefore('|') !in alive }
        }
    }

    fun cards(): List<TriggerCard> = synchronized(lock) { cards }

    /** 宿主每 60s 调用一次；返回本次实际派发的卡片次数。 */
    fun tickSchedule(now: Long = clock(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        val epochDay = zoned.toLocalDate().toEpochDay()
        val hhmm = "%02d:%02d".format(zoned.hour, zoned.minute)
        val isoDay = zoned.dayOfWeek.value

        val dispatches = synchronized(lock) {
            val out = mutableListOf<Pair<String, Map<String, Any?>>>()
            cards.forEach { card ->
                card.events.forEachIndexed { index, event ->
                    if (event.type != CardManifest.EVENT_SCHEDULE) return@forEachIndexed
                    if (event.days.isNotEmpty() && isoDay !in event.days) return@forEachIndexed

                    when {
                        event.times.isNotEmpty() -> {
                            if (hhmm !in event.times) return@forEachIndexed
                            val slot = "$epochDay $hhmm"
                            if (firedSlots["${card.name}|$index"] == slot) return@forEachIndexed
                            if (!tryAcquireRun(card.name, event.type, now)) return@forEachIndexed
                            firedSlots["${card.name}|$index"] = slot
                        }

                        event.intervalMinutes > 0 -> {
                            val key = "${card.name}|$index"
                            val anchor = intervalAnchors.getOrPut(key) { now }
                            if (now - anchor < event.intervalMinutes * 60_000L) return@forEachIndexed
                            if (!tryAcquireRun(card.name, event.type, now)) return@forEachIndexed
                            intervalAnchors[key] = now
                        }

                        else -> return@forEachIndexed
                    }
                    out += card.name to eventArgs(type = event.type, time = hhmm)
                }
            }
            out
        }
        dispatch(dispatches)
        return dispatches.size
    }

    /** 通知到达；空包名事件表示“不限制包名”。 */
    fun onNotification(
        packageName: String,
        title: String = "",
        text: String = "",
        now: Long = clock(),
    ): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_NOTIFICATION) { event ->
            event.type == CardManifest.EVENT_NOTIFICATION &&
                (event.packageName.isBlank() || event.packageName == packageName) &&
                (event.titleContains.isBlank() || title.contains(event.titleContains, ignoreCase = true)) &&
                (event.textContains.isBlank() || text.contains(event.textContains, ignoreCase = true))
        }.map { it to eventArgs(
            type = CardManifest.EVENT_NOTIFICATION,
            packageName = packageName,
            title = title,
            text = text,
        ) }
    )

    /** 应用进入前台。空包名事件视为“任意应用”。 */
    fun onAppLaunch(packageName: String, now: Long = clock()): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_APP_LAUNCH) { event ->
            event.type == CardManifest.EVENT_APP_LAUNCH &&
                (event.packageName.isBlank() || event.packageName == packageName)
        }.map { it to eventArgs(
            type = CardManifest.EVENT_APP_LAUNCH,
            packageName = packageName,
        ) }
    )

    /** 充电状态变化：connected | disconnected。 */
    fun onCharging(state: String, now: Long = clock()): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_CHARGING) { event ->
            event.type == CardManifest.EVENT_CHARGING && event.state == state
        }.map { it to eventArgs(
            type = CardManifest.EVENT_CHARGING,
            state = state,
        ) }
    )

    /** 命中匹配条件且通过冷却的卡片名；命中即标记运行，避免重入重复派发。 */
    private fun matchAndAcquire(
        now: Long,
        type: String,
        predicate: (CardManifest.Event) -> Boolean,
    ): List<String> = synchronized(lock) {
        cards
            .filter { card -> card.events.any(predicate) }
            .filter { card -> tryAcquireRun(card.name, type, now) }
            .map { it.name }
    }

    private fun tryAcquireRun(name: String, type: String, now: Long): Boolean {
        val key = "$name|$type"
        val last = lastRunAt[key]
        if (last != null && now - last < cooldownFor(type)) return false
        lastRunAt[key] = now
        return true
    }

    private fun dispatch(dispatches: List<Pair<String, Map<String, Any?>>>): Int {
        dispatches.forEach { (name, args) -> runner.runCard(name, args) }
        return dispatches.size
    }

    private fun cooldownFor(type: String): Long = when (type) {
        CardManifest.EVENT_NOTIFICATION -> COOLDOWN_NOTIFICATION_MS
        CardManifest.EVENT_APP_LAUNCH -> COOLDOWN_APP_LAUNCH_MS
        CardManifest.EVENT_CHARGING -> COOLDOWN_CHARGING_MS
        else -> 0L
    }

    companion object {
        /** 通知防风暴冷却 */
        const val COOLDOWN_NOTIFICATION_MS = 3_000L

        /** 前后台抖动冷却 */
        const val COOLDOWN_APP_LAUNCH_MS = 60_000L

        /** 充电抖动冷却 */
        const val COOLDOWN_CHARGING_MS = 5_000L

        /** 脚本 args 中事件负载的键名：`args.event.type` 等 */
        const val ARG_EVENT = "event"

        fun eventArgs(
            type: String,
            packageName: String = "",
            title: String = "",
            text: String = "",
            state: String = "",
            time: String = "",
        ): Map<String, Any?> = mapOf(
            ARG_EVENT to buildMap {
                put("type", type)
                if (packageName.isNotEmpty()) put("package", packageName)
                if (title.isNotEmpty()) put("title", title)
                if (text.isNotEmpty()) put("text", text)
                if (state.isNotEmpty()) put("state", state)
                if (time.isNotEmpty()) put("time", time)
            }
        )
    }
}
