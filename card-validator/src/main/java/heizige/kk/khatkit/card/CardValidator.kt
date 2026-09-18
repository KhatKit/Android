package heizige.kk.khatkit.card

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 校验严重级别。 */
enum class Severity { ERROR, WARNING }

data class CardIssue(
    val code: String,
    val message: String,
    val severity: Severity,
)

/**
 * 卡片审核 CI 的静态校验（设计文档 13.1 + MANIFEST.md 第 5 节）。
 *
 * 只做宿主侧能确定性判断的检查；越权/破坏性副作用仍靠人工。
 * 传入脚本正文后，会扫描脚本里出现的 bridge 调用与域名是否已声明。
 */
object CardValidator {
    val ENGINES = setOf("lua", "js", "auto", "command")
    val PRIVILEGES = setOf("none", "elevated")
    val BRIDGES = setOf("tool", "ui", "download", "store", "shizuku", "root", "accessibility")

    private val NAME_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")
    private val SEMVER_REGEX = Regex("^\\d+\\.\\d+\\.\\d+([-+].*)?$")
    private val URL_REGEX = Regex("""https?://([A-Za-z0-9.-]+)""")
    private val BRIDGE_CALL_REGEX = Regex("""\b(tool|ui|download|store|shizuku|root|accessibility)\s*[.:]""")
    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private const val ALL_EVENT_TYPE_TEXT =
        "schedule|notification|app_launch|charging|wifi|network|battery|screen|clipboard|bluetooth|location"

    /**
     * @param manifest 解析后的卡片
     * @param scripts  脚本正文，key 为入口文件名（如 main.lua / main.js）
     */
    fun validate(
        manifest: CardManifest,
        scripts: Map<String, String> = emptyMap(),
    ): List<CardIssue> = buildList {
        if (!NAME_REGEX.matches(manifest.name)) {
            error("NAME_INVALID", "name 必须是小写字母开头的小写下划线：${manifest.name}")
        }
        if (!SEMVER_REGEX.matches(manifest.version)) {
            warning("VERSION_NOT_SEMVER", "version 建议使用 SemVer：${manifest.version}")
        }
        if (manifest.engine !in ENGINES) {
            error("ENGINE_UNKNOWN", "engine 必须是 $ENGINES 之一：${manifest.engine}")
        }
        if (manifest.privilege !in PRIVILEGES) {
            error("PRIVILEGE_UNKNOWN", "privilege 必须是 none|elevated：${manifest.privilege}")
        }

        // 触发方式：只允许 ai/user，不允许为空或重复
        if (manifest.triggers.isEmpty()) {
            error("TRIGGER_EMPTY", "triggers 不能为空数组")
        }
        val unknownTriggers = manifest.triggers.toSet() - CardManifest.ALL_TRIGGERS
        if (unknownTriggers.isNotEmpty()) {
            error("TRIGGER_UNKNOWN", "triggers 只支持 ai|user：$unknownTriggers")
        }
        if (manifest.triggers.distinct().size != manifest.triggers.size) {
            error("TRIGGER_DUPLICATE", "triggers 不允许重复：${manifest.triggers}")
        }

        // 事件触发器（events）：类型/参数必须自洽，见 card.json 文档
        manifest.events.forEachIndexed { index, event ->
            val where = "events[$index]"
            if (event.type !in CardManifest.ALL_EVENT_TYPES) {
                error("EVENT_TYPE_UNKNOWN", "$where 未知事件类型 ${event.type}，支持 $ALL_EVENT_TYPE_TEXT")
                return@forEachIndexed
            }
            when (event.type) {
                CardManifest.EVENT_SCHEDULE -> {
                    if (event.intervalMinutes < 0) {
                        error("EVENT_INTERVAL_INVALID", "$where intervalMinutes 不能为负数：${event.intervalMinutes}")
                    }
                    if (event.times.isEmpty() && event.intervalMinutes <= 0) {
                        error("EVENT_SCHEDULE_EMPTY", "$where schedule 需要 times 或 intervalMinutes（>=1）")
                    }
                    event.times.forEach { time ->
                        if (!TIME_REGEX.matches(time)) {
                            error("EVENT_TIME_INVALID", "$where 时间格式必须是 HH:mm：$time")
                        }
                    }
                    event.days.forEach { day ->
                        if (day !in 1..7) {
                            error("EVENT_DAY_INVALID", "$where days 取值必须是 1..7（1=周一）：$day")
                        }
                    }
                }

                CardManifest.EVENT_NOTIFICATION -> {
                    if (event.packageName.isBlank() &&
                        event.titleContains.isBlank() &&
                        event.textContains.isBlank()
                    ) {
                        error("EVENT_NOTIFICATION_EMPTY", "$where 通知触发器至少需要一个匹配条件（package/titleContains/textContains）")
                    }
                }

                CardManifest.EVENT_APP_LAUNCH -> {
                    // package 留空 = 任意应用进入前台，合法
                }

                CardManifest.EVENT_CHARGING -> {
                    if (event.state !in CardManifest.ALL_CHARGING_STATES) {
                        error("EVENT_CHARGING_STATE_INVALID", "$where charging 的 state 必须是 connected|disconnected：${event.state}")
                    }
                }

                CardManifest.EVENT_WIFI -> {
                    if (event.state.isNotBlank() && event.state !in CardManifest.ALL_CONNECTION_STATES) {
                        error("EVENT_WIFI_STATE_INVALID", "$where wifi 的 state 必须是 connected|disconnected 或留空：${event.state}")
                    }
                }

                CardManifest.EVENT_NETWORK -> {
                    if (event.state !in CardManifest.ALL_NETWORK_STATES) {
                        error("EVENT_NETWORK_STATE_INVALID", "$where network 的 state 必须是 online|offline：${event.state}")
                    }
                }

                CardManifest.EVENT_BATTERY -> {
                    val hasLevel = event.levelBelow >= 0 || event.levelAbove >= 0
                    if (!hasLevel && event.state.isBlank()) {
                        error("EVENT_BATTERY_EMPTY", "$where battery 至少需要 level_below / level_above / state 之一")
                    }
                    if (event.state.isNotBlank() && event.state !in CardManifest.ALL_BATTERY_STATES) {
                        error("EVENT_BATTERY_STATE_INVALID", "$where battery 的 state 必须是 charging|discharging 或留空：${event.state}")
                    }
                    if (event.levelBelow < -1 || event.levelBelow > 100) {
                        error("EVENT_BATTERY_LEVEL_INVALID", "$where level_below 必须在 0..100：${event.levelBelow}")
                    }
                    if (event.levelAbove < -1 || event.levelAbove > 100) {
                        error("EVENT_BATTERY_LEVEL_INVALID", "$where level_above 必须在 0..100：${event.levelAbove}")
                    }
                    if (event.levelBelow >= 0 && event.levelAbove >= 0 && event.levelBelow >= event.levelAbove) {
                        error("EVENT_BATTERY_LEVEL_RANGE", "$where level_below 必须小于 level_above：${event.levelBelow} >= ${event.levelAbove}")
                    }
                }

                CardManifest.EVENT_SCREEN -> {
                    if (event.state !in CardManifest.ALL_SCREEN_STATES) {
                        error("EVENT_SCREEN_STATE_INVALID", "$where screen 的 state 必须是 on|off|unlocked|locked：${event.state}")
                    }
                }

                CardManifest.EVENT_CLIPBOARD -> {
                    if (event.textContains.isBlank()) {
                        error("EVENT_CLIPBOARD_EMPTY", "$where clipboard 需要 text_contains 匹配条件")
                    }
                }

                CardManifest.EVENT_BLUETOOTH -> {
                    if (event.state !in CardManifest.ALL_CONNECTION_STATES) {
                        error("EVENT_BLUETOOTH_STATE_INVALID", "$where bluetooth 的 state 必须是 connected|disconnected：${event.state}")
                    }
                }

                CardManifest.EVENT_LOCATION -> {
                    if (event.state !in CardManifest.ALL_LOCATION_STATES) {
                        error("EVENT_LOCATION_STATE_INVALID", "$where location 的 state 必须是 enter|exit：${event.state}")
                    }
                    val lat = event.lat
                    val lon = event.lon
                    if (lat == null || lat !in -90.0..90.0) {
                        error("EVENT_LOCATION_LAT_INVALID", "$where location 的 lat 必须在 -90..90：$lat")
                    }
                    if (lon == null || lon !in -180.0..180.0) {
                        error("EVENT_LOCATION_LON_INVALID", "$where location 的 lon 必须在 -180..180：$lon")
                    }
                    if (event.radiusM < 1) {
                        error("EVENT_LOCATION_RADIUS_INVALID", "$where location 的 radius_m 必须 >=1：${event.radiusM}")
                    }
                }
            }
        }

        val declared = manifest.requires.bridges
        val unknown = declared.filterNot { it in BRIDGES }
        if (unknown.isNotEmpty()) {
            error("BRIDGE_UNKNOWN", "声明了未知 bridge：$unknown")
        }

        // engine 与 entry 一致
        when (manifest.engine) {
            "lua" -> if (manifest.entry.lua.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=lua 需要 entry.lua")
            }
            "js" -> if (manifest.entry.js.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=js 需要 entry.js")
            }
            "auto" -> if (manifest.entry.lua.isNullOrBlank() && manifest.entry.js.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=auto 至少需要 entry.lua 或 entry.js 之一")
            }
            "command" -> if (manifest.command.isNullOrBlank()) {
                error("COMMAND_MISSING", "engine=command 需要 command 字段")
            }
        }

        // 标签：受控词表
        val tags = manifest.tags
        if (tags == null) {
            error("TAGS_MISSING", "tags 为必填（domain/action）")
        } else {
            if (!TagVocabulary.isValidDomain(tags.domain)) {
                error("TAG_DOMAIN_INVALID", "domain 不在词表内：${tags.domain}")
            }
            if (!TagVocabulary.isValidAction(tags.action)) {
                error("TAG_ACTION_INVALID", "action 不在词表内：${tags.action}")
            }
            if (!TagVocabulary.isValidScene(tags.scene)) {
                error("TAG_SCENE_INVALID", "scene 不在词表内：${tags.scene}")
            }
        }

        // game 域强制 compliance
        if (tags?.domain == "game" && manifest.compliance == null) {
            error("GAME_COMPLIANCE_MISSING", "domain=game 必须带 compliance（合规风险声明）")
        }

        // UI 声明：widget 必须在宿主白名单内，source 只能是 ai/ui（设计 7.2/13.1）
        manifest.ui.forEach { (param, spec) ->
            val widget = (spec["widget"] as? JsonPrimitive)?.contentOrNull
            if (widget != null && widget !in UiWidgetVocabulary.ALLOWED) {
                error("UI_WIDGET_UNKNOWN", "参数 $param 使用了未实现的组件：$widget")
            }
            val source = (spec["source"] as? JsonPrimitive)?.contentOrNull
            if (source != null && source !in UiWidgetVocabulary.SOURCES) {
                error("UI_SOURCE_INVALID", "参数 $param 的 source 必须是 ai|ui：$source")
            }
        }

        // 脚本正文扫描：bridge 调用 ⊆ 声明；域名 ⊆ network.allow
        if (scripts.isNotEmpty()) {
            val body = scripts.values.joinToString("\n")
            val usedBridges = BRIDGE_CALL_REGEX.findAll(body)
                .map { it.groupValues[1] }
                .toSet()
            val undeclared = usedBridges - declared
            if (undeclared.isNotEmpty()) {
                error("BRIDGE_UNDECLARED", "脚本调用了未声明的 bridge：$undeclared")
            }

            val declaredHosts = manifest.network.allow.map { it.trim().lowercase() }.toSet()
            val usedHosts = URL_REGEX.findAll(body).map { it.groupValues[1].lowercase() }.toSet()
            val undeclaredHosts = usedHosts.filter { host ->
                declaredHosts.none { allowed -> host == allowed || host.endsWith(".$allowed") }
            }
            if (undeclaredHosts.isNotEmpty()) {
                error("NETWORK_UNDECLARED", "脚本访问了未在白名单内的域名：$undeclaredHosts")
            }
        }
    }

    fun isValid(manifest: CardManifest, scripts: Map<String, String> = emptyMap()): Boolean =
        validate(manifest, scripts).none { it.severity == Severity.ERROR }

    private fun MutableList<CardIssue>.error(code: String, message: String) {
        add(CardIssue(code, message, Severity.ERROR))
    }

    private fun MutableList<CardIssue>.warning(code: String, message: String) {
        add(CardIssue(code, message, Severity.WARNING))
    }
}
