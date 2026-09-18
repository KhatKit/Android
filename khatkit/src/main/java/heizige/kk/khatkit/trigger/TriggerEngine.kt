package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import java.time.Instant
import java.time.ZoneId

/** 可参与事件触发的已安装卡片（name + 生效事件 + 失败重试配置）。 */
data class TriggerCard(
    val name: String,
    val events: List<CardManifest.Event>,
    /** 失败后的最大重试次数（0 = 不重试） */
    val maxRetries: Int = 0,
    /** 每次重试前的等待秒数 */
    val retryDelaySeconds: Int = 0,
    /** 卡片 manifest 原始事件（用户覆盖前的默认值，仅 UI 使用） */
    val baseEvents: List<CardManifest.Event> = events,
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
 * - notification / app_launch / charging / wifi / network / battery / screen /
 *   clipboard / bluetooth：宿主投递事件，引擎匹配包名/内容/状态
 * - location：宿主轮询位置，引擎按 haversine 距离做 enter/exit 边界判定（首次采样只记基线）
 * - 同一卡片同类型事件有冷却时间，避免通知风暴/前后台抖动导致连发
 * - 失败重试：执行方通过 [reportRunResult] 回报结果，[tickRetries] 派发到期的重试
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

    /** "card|eventIndex" -> 上一次是否在围栏内（location enter/exit 边界判定） */
    private val locationInside = mutableMapOf<String, Boolean>()

    /** card -> 已发生的连续失败次数 */
    private val failureCounts = mutableMapOf<String, Int>()

    /** card -> 待派发的失败重试 */
    private val pendingRetries = mutableMapOf<String, PendingRetry>()

    private data class PendingRetry(
        val args: Map<String, Any?>,
        val dueAt: Long,
    )

    fun updateCards(cards: List<TriggerCard>) {
        synchronized(lock) {
            this.cards = cards
            val alive = cards.mapTo(mutableSetOf()) { it.name }
            lastRunAt.keys.removeAll { it.substringBefore('|') !in alive }
            firedSlots.keys.removeAll { it.substringBefore('|') !in alive }
            intervalAnchors.keys.removeAll { it.substringBefore('|') !in alive }
            locationInside.keys.removeAll { it.substringBefore('|') !in alive }
            failureCounts.keys.removeAll { it !in alive }
            pendingRetries.keys.removeAll { it !in alive }
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
                // 空条件事件会匹配所有通知，显式拒绝（manifest 校验层也会拦截）
                (event.packageName.isNotBlank() ||
                    event.titleContains.isNotBlank() ||
                    event.textContains.isNotBlank()) &&
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

    /** Wi-Fi 连接变化：ssid 为空表示不限制热点，state 留空表示任意变化。 */
    fun onWifi(ssid: String, connected: Boolean, now: Long = clock()): Int {
        val state = if (connected) CardManifest.STATE_CONNECTED else CardManifest.STATE_DISCONNECTED
        return dispatch(
            matchAndAcquire(now, CardManifest.EVENT_WIFI) { event ->
                event.type == CardManifest.EVENT_WIFI &&
                    (event.state.isBlank() || event.state == state) &&
                    (event.ssid.isBlank() || event.ssid.equals(ssid, ignoreCase = true))
            }.map { it to eventArgs(
                type = CardManifest.EVENT_WIFI,
                ssid = ssid,
                state = state,
            ) }
        )
    }

    /** 网络通断：online | offline。 */
    fun onNetwork(online: Boolean, now: Long = clock()): Int {
        val state = if (online) CardManifest.NETWORK_ONLINE else CardManifest.NETWORK_OFFLINE
        return dispatch(
            matchAndAcquire(now, CardManifest.EVENT_NETWORK) { event ->
                event.type == CardManifest.EVENT_NETWORK && event.state == state
            }.map { it to eventArgs(
                type = CardManifest.EVENT_NETWORK,
                state = state,
            ) }
        )
    }

    /**
     * 电量/充放电变化。匹配规则：
     * - state 留空 = 不限制；否则必须是 charging | discharging
     * - levelBelow >= 0 且电量 <= levelBelow，或 levelAbove >= 0 且电量 >= levelAbove
     * - 只有 state 没有阈值时按 state 匹配
     */
    fun onBattery(level: Int, charging: Boolean, now: Long = clock()): Int {
        val state = if (charging) CardManifest.BATTERY_CHARGING else CardManifest.BATTERY_DISCHARGING
        return dispatch(
            matchAndAcquire(now, CardManifest.EVENT_BATTERY) { event ->
                event.type == CardManifest.EVENT_BATTERY &&
                    (event.state.isBlank() || event.state == state) &&
                    when {
                        event.levelBelow >= 0 && level <= event.levelBelow -> true
                        event.levelAbove >= 0 && level >= event.levelAbove -> true
                        event.levelBelow < 0 && event.levelAbove < 0 -> true
                        else -> false
                    }
            }.map { it to eventArgs(
                type = CardManifest.EVENT_BATTERY,
                state = state,
                level = level,
            ) }
        )
    }

    /** 屏幕状态：on | off | locked | unlocked。 */
    fun onScreen(state: String, now: Long = clock()): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_SCREEN) { event ->
            event.type == CardManifest.EVENT_SCREEN && event.state == state
        }.map { it to eventArgs(
            type = CardManifest.EVENT_SCREEN,
            state = state,
        ) }
    )

    /** 新剪贴板文本（去重由调用方负责）。 */
    fun onClipboard(text: String, now: Long = clock()): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_CLIPBOARD) { event ->
            event.type == CardManifest.EVENT_CLIPBOARD &&
                event.textContains.isNotBlank() &&
                text.contains(event.textContains, ignoreCase = true)
        }.map { it to eventArgs(
            type = CardManifest.EVENT_CLIPBOARD,
            text = text,
        ) }
    )

    /** 蓝牙连接变化：connected | disconnected，device 为空表示任意设备。 */
    fun onBluetooth(state: String, deviceName: String = "", now: Long = clock()): Int = dispatch(
        matchAndAcquire(now, CardManifest.EVENT_BLUETOOTH) { event ->
            event.type == CardManifest.EVENT_BLUETOOTH &&
                event.state == state &&
                (event.device.isBlank() || deviceName.contains(event.device, ignoreCase = true))
        }.map { it to eventArgs(
            type = CardManifest.EVENT_BLUETOOTH,
            state = state,
            device = deviceName,
        ) }
    )

    /**
     * 位置更新。每个 location 事件独立维护内外状态：
     * 首次采样只记录基线；之后 enter 在圈外→圈内、exit 在圈内→圈外时派发。
     */
    fun onLocation(lat: Double, lon: Double, now: Long = clock()): Int {
        val acquired = synchronized(lock) {
            val out = mutableListOf<Pair<String, Map<String, Any?>>>()
            cards.forEach { card ->
                card.events.forEachIndexed { index, event ->
                    if (event.type != CardManifest.EVENT_LOCATION) return@forEachIndexed
                    val centerLat = event.lat ?: return@forEachIndexed
                    val centerLon = event.lon ?: return@forEachIndexed
                    val radius = event.radiusM.coerceAtLeast(1)
                    val inside = distanceMeters(lat, lon, centerLat, centerLon) <= radius
                    val key = "${card.name}|$index"
                    val previous = locationInside[key]
                    locationInside[key] = inside
                    if (previous == null) return@forEachIndexed
                    val transitioned = when (event.state) {
                        CardManifest.LOCATION_ENTER -> !previous && inside
                        CardManifest.LOCATION_EXIT -> previous && !inside
                        else -> false
                    }
                    if (!transitioned) return@forEachIndexed
                    if (!tryAcquireRun(card.name, CardManifest.EVENT_LOCATION, now)) return@forEachIndexed
                    out += card.name to eventArgs(
                        type = CardManifest.EVENT_LOCATION,
                        state = event.state,
                        lat = lat,
                        lon = lon,
                    )
                }
            }
            out
        }
        return dispatch(acquired)
    }

    /**
     * 执行方回报一次运行结果。失败且卡片配置了重试时，排入 [tickRetries] 队列；
     * 成功或重试次数用尽则清空计数。
     *
     * @return 是否已排入一次重试（调用方可据此决定是否提示最终失败）
     */
    fun reportRunResult(
        name: String,
        args: Map<String, Any?>,
        success: Boolean,
        now: Long = clock(),
    ): Boolean = synchronized(lock) {
        if (success) {
            failureCounts.remove(name)
            pendingRetries.remove(name)
            return false
        }
        val card = cards.firstOrNull { it.name == name }
        val maxRetries = card?.maxRetries ?: 0
        val attempt = failureCounts[name] ?: 0
        if (maxRetries <= 0 || attempt >= maxRetries) {
            failureCounts.remove(name)
            pendingRetries.remove(name)
            return false
        }
        failureCounts[name] = attempt + 1
        val delay = (card?.retryDelaySeconds ?: 0).coerceAtLeast(0) * 1_000L
        pendingRetries[name] = PendingRetry(args = args, dueAt = now + delay)
        true
    }

    /** 宿主高频调用（如每秒）；派发到期的失败重试，返回派发数量。 */
    fun tickRetries(now: Long = clock()): Int {
        val due = synchronized(lock) {
            val ready = pendingRetries.filterValues { it.dueAt <= now }
            ready.keys.forEach { pendingRetries.remove(it) }
            ready.map { (name, retry) -> name to retry.args }
        }
        return dispatch(due)
    }

    /** 该卡片是否有等待派发的失败重试（用于决定是否提示最终失败）。 */
    fun hasPendingRetry(name: String): Boolean =
        synchronized(lock) { name in pendingRetries }

    /** 两次坐标间的 haversine 距离（米）。 */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

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
        CardManifest.EVENT_WIFI -> COOLDOWN_WIFI_MS
        CardManifest.EVENT_NETWORK -> COOLDOWN_NETWORK_MS
        CardManifest.EVENT_BATTERY -> COOLDOWN_BATTERY_MS
        CardManifest.EVENT_SCREEN -> COOLDOWN_SCREEN_MS
        CardManifest.EVENT_CLIPBOARD -> COOLDOWN_CLIPBOARD_MS
        CardManifest.EVENT_BLUETOOTH -> COOLDOWN_BLUETOOTH_MS
        CardManifest.EVENT_LOCATION -> COOLDOWN_LOCATION_MS
        else -> 0L
    }

    companion object {
        /** 通知防风暴冷却 */
        const val COOLDOWN_NOTIFICATION_MS = 3_000L

        /** 前后台抖动冷却 */
        const val COOLDOWN_APP_LAUNCH_MS = 60_000L

        /** 充电抖动冷却 */
        const val COOLDOWN_CHARGING_MS = 5_000L

        /** Wi-Fi 抖动冷却 */
        const val COOLDOWN_WIFI_MS = 15_000L

        /** 网络通断抖动冷却 */
        const val COOLDOWN_NETWORK_MS = 15_000L

        /** 电量变化冷却（电量 1% 级抖动很频繁） */
        const val COOLDOWN_BATTERY_MS = 60_000L

        /** 屏幕开关/锁屏冷却 */
        const val COOLDOWN_SCREEN_MS = 5_000L

        /** 剪贴板轮询冷却（轮询 2s 一次） */
        const val COOLDOWN_CLIPBOARD_MS = 2_000L

        /** 蓝牙连接抖动冷却 */
        const val COOLDOWN_BLUETOOTH_MS = 30_000L

        /** 位置轮询冷却（轮询 60s 一次） */
        const val COOLDOWN_LOCATION_MS = 60_000L

        /** 脚本 args 中事件负载的键名：`args.event.type` 等 */
        const val ARG_EVENT = "event"

        fun eventArgs(
            type: String,
            packageName: String = "",
            title: String = "",
            text: String = "",
            state: String = "",
            time: String = "",
            ssid: String = "",
            device: String = "",
            level: Int? = null,
            lat: Double? = null,
            lon: Double? = null,
        ): Map<String, Any?> = mapOf(
            ARG_EVENT to buildMap {
                put("type", type)
                if (packageName.isNotEmpty()) put("package", packageName)
                if (title.isNotEmpty()) put("title", title)
                if (text.isNotEmpty()) put("text", text)
                if (state.isNotEmpty()) put("state", state)
                if (time.isNotEmpty()) put("time", time)
                if (ssid.isNotEmpty()) put("ssid", ssid)
                if (device.isNotEmpty()) put("device", device)
                if (level != null) put("level", level)
                if (lat != null) put("lat", lat)
                if (lon != null) put("lon", lon)
            }
        )
    }
}
