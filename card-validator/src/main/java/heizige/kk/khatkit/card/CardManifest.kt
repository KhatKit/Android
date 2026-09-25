package heizige.kk.khatkit.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

/**
 * 卡片 manifest 规范（KhatKit 设计文档第 5 节）。
 *
 * 刻意与 MCP tool schema 对齐：`parameters` 就是标准 JSON Schema，
 * 将来把卡片暴露成 MCP tool 时零成本。
 */
@Serializable
data class CardManifest(
    /** 全局唯一，小写下划线 */
    val name: String,
    val version: String = "0.0.0",
    val description: String = "",
    val author: String = "",
    val license: String = "",

    /** lua | js | auto | command */
    val engine: String = "auto",
    val entry: Entry = Entry(),

    /** none | elevated */
    val privilege: String = "none",

    val requires: Requires = Requires(),
    val network: Network = Network(),

    /** 标准 JSON Schema */
    val parameters: JsonObject = JsonObject(emptyMap()),

    /** 声明哪些参数必须由用户 UI 填写：{ 参数名: { source, widget, ... } } */
    val ui: Map<String, JsonObject> = emptyMap(),

    /** 触发方式：ai（AI 工具调用）/ user（用户在卡片界面手动运行），默认两者都允许 */
    val triggers: List<String> = DEFAULT_TRIGGERS,

    /** 事件触发：定时 / 通知 / 应用启动 / 充电，空数组 = 不参与自动触发 */
    val events: List<Event> = emptyList(),

    val tags: Tags? = null,
    val compliance: Compliance? = null,
    val store: Store = Store(),

    /** 调用计费（可选）：缺省表示免费，由 Hub 的套餐/工具次数量化 */
    val pricing: Pricing? = null,

    /** engine == command 时的命令模板，`{}` 占位符由宿主白名单转义 */
    val command: String? = null,
) {
    @Serializable
    data class Entry(
        val lua: String? = null,
        val js: String? = null,
    )

    @Serializable
    data class Requires(
        val bridges: List<String> = emptyList(),
        val libs: List<LibRequirement> = emptyList(),
        val bins: List<String> = emptyList(),
        val env: List<String> = emptyList(),

        /**
         * 原生依赖包：依赖不随应用编译，而是与云端卡片脚本一起从 Hub 下载，
         * 校验 sha256 后由宿主 DexClassLoader 加载。见 docs/dependency-system.md。
         *
         * 兼容：旧 manifest 字段 `requires.plugins` 仍可解析（反序列化回退），新序列化只写 `dependencies`。
         */
        @JsonNames("plugins")
        val dependencies: List<DependencyReq> = emptyList(),
    )

    /**
     * 原生依赖包需求。依赖产物是含 `classes.dex` 的 jar，加载后注册成同名 bridge
     * （如 `imageToolbox`），脚本调用方式与内置 bridge 一致。
     */
    @Serializable
    data class DependencyReq(
        /** 依赖名（同时是注册进 BridgeRegistry 的 bridge 名），小写下划线 */
        val name: String,
        /** 依赖版本，SemVer */
        val version: String,
        /** 产物下载地址；留空时用 Hub 约定地址 /api/dependencies/<name>/<version> */
        val url: String? = null,
        /** 产物 sha256（64 位小写十六进制），加载前必须校验通过 */
        val sha256: String = "",
    )

    @Serializable
    data class LibRequirement(
        val name: String,
        /** lua | js */
        val lang: String = "js",
        val version: String = "*",
    )

    @Serializable
    data class Network(
        /** 域名白名单，空数组 = 禁止联网 */
        val allow: List<String> = emptyList(),
    )

    /**
     * 事件触发器声明。一个事件 = 一组匹配条件：
     * - schedule：`times`（HH:mm 列表）或 `intervalMinutes`（间隔分钟）二选一，`days` 限定星期，
     *   `calendar`（any|workday|weekend|holiday）按中国节假日日历过滤
     * - notification：`package` / `titleContains` / `textContains` 任意组合（空 = 不限制）
     * - notification_click：`package` / `titleContains` / `textContains` 组合匹配用户点击的通知（需通知使用权）
     * - notification_reply：`package`（必填）用户对通知直接回复后的同包名新通知（启发式，见 TriggerEngine KDoc）
     * - app_launch：`package` 为空表示任意应用进入前台
     * - app_exit：`package`（必填）离开前台时触发
     * - app_install / app_uninstall：`package`（可选，空 = 任意应用）安装 / 卸载
     * - shortcut：`name`（必填）在桌面创建动态快捷方式，点击即运行卡片
     * - tile：`name`（必填）注册到快捷设置磁贴槽位，点击磁贴运行卡片
     * - charging：`state` 为 connected | disconnected
     * - wifi：`ssid`（可选，空 = 任意热点）、`state` connected | disconnected（可选，空 = 任意变化）
     * - network：`state` online | offline（网络通断）
     * - battery：`levelBelow` / `levelAbove`（0-100，可只写其一）与可选 `state` charging | discharging
     * - screen：`state` on | off | unlocked | locked
     * - clipboard：`textContains`（新剪贴板文本包含该子串）
     * - bluetooth：`state` connected | disconnected，可选 `device`（设备名包含，空 = 任意）
     * - location：`lat` / `lon` / `radiusM` + `state` enter | exit（进入/离开圆形区域）
     */
    @Serializable
    data class Event(
        /** schedule | notification | notification_click | notification_reply | app_launch | app_exit | app_install | app_uninstall | charging | wifi | network | battery | screen | clipboard | bluetooth | location | shortcut | tile */
        val type: String,
        /** schedule：["08:00","21:30"] */
        val times: List<String> = emptyList(),
        /** schedule：重复间隔（分钟，>=1），与 times 二选一 */
        val intervalMinutes: Int = 0,
        /** schedule：1=周一 .. 7=周日，空 = 每天 */
        val days: List<Int> = emptyList(),
        /** schedule：日历过滤 any | workday | weekend | holiday（节假日数据来自用户可编辑的 holidays_cn.json） */
        val calendar: String = "any",
        /** notification / notification_click / notification_reply / app_launch / app_exit / app_install / app_uninstall 的包名（空 = 任意；app_exit 与 notification_reply 必填），JSON 字段名为 package */
        @SerialName("package") val packageName: String = "",
        /** shortcut / tile：快捷方式或磁贴的显示名称（必填） */
        val name: String = "",
        /** notification / notification_click / notification_reply：标题包含（兼容 title_contains 写法） */
        @JsonNames("title_contains") val titleContains: String = "",
        /** notification / notification_click / notification_reply：正文/回复文本包含；clipboard：新剪贴板文本包含（兼容 text_contains 写法） */
        @JsonNames("text_contains") val textContains: String = "",
        /** charging | wifi | network | battery | screen | bluetooth | location：状态 / 转换方向 */
        val state: String = "",
        /** wifi：目标热点 SSID（空 = 任意） */
        val ssid: String = "",
        /** bluetooth：设备名包含（空 = 任意） */
        val device: String = "",
        /** battery：低于该电量（0-100）触发；-1 = 未设置 */
        @SerialName("level_below") val levelBelow: Int = -1,
        /** battery：高于该电量（0-100）触发；-1 = 未设置 */
        @SerialName("level_above") val levelAbove: Int = -1,
        /** location：圆心纬度 [-90,90] */
        val lat: Double? = null,
        /** location：圆心经度 [-180,180] */
        val lon: Double? = null,
        /** location：半径（米，>=1） */
        @SerialName("radius_m") val radiusM: Int = 0,
    )

    /** 两个正交维度 + 一个可选场景，见 TagVocabulary */
    @Serializable
    data class Tags(
        val domain: String,
        val action: String,
        val scene: String? = null,
    )

    @Serializable
    data class Compliance(
        /** low | medium | high */
        val risk: String = "low",
        val note: String = "",
    )

    @Serializable
    data class Store(
        @SerialName("quota_mb") val quotaMb: Int = 50,
        val secret: Boolean = false,
    )

    /** 调用计费：price 为每次调用价格（>= 0，0 = 免费），currency 为币种（默认 CNY）。 */
    @Serializable
    data class Pricing(
        val price: Double = 0.0,
        val currency: String = "CNY",
    )

    val isCommand: Boolean get() = engine == "command"

    /** 卡片要求的能力集合，供能力协商过滤 */
    val requiredBridges: Set<String> get() = requires.bridges.toSet()

    /** 卡片依赖的原生依赖包 */
    val requiredDependencies: List<DependencyReq> get() = requires.dependencies

    /** 是否允许 AI 工具调用 */
    fun supportsAi(): Boolean = TRIGGER_AI in triggers

    /** 是否允许用户在卡片界面手动运行 */
    fun supportsUser(): Boolean = TRIGGER_USER in triggers

    /** 卡片是否声明了至少一个事件触发器 */
    fun supportsEvents(): Boolean = events.isNotEmpty()

    companion object {
        const val TRIGGER_AI = "ai"
        const val TRIGGER_USER = "user"
        val DEFAULT_TRIGGERS = listOf(TRIGGER_AI, TRIGGER_USER)
        val ALL_TRIGGERS = setOf(TRIGGER_AI, TRIGGER_USER)

        const val EVENT_SCHEDULE = "schedule"
        const val EVENT_NOTIFICATION = "notification"
        const val EVENT_NOTIFICATION_CLICK = "notification_click"
        const val EVENT_NOTIFICATION_REPLY = "notification_reply"
        const val EVENT_APP_LAUNCH = "app_launch"
        const val EVENT_APP_EXIT = "app_exit"
        const val EVENT_APP_INSTALL = "app_install"
        const val EVENT_APP_UNINSTALL = "app_uninstall"
        const val EVENT_CHARGING = "charging"
        const val EVENT_WIFI = "wifi"
        const val EVENT_NETWORK = "network"
        const val EVENT_BATTERY = "battery"
        const val EVENT_SCREEN = "screen"
        const val EVENT_CLIPBOARD = "clipboard"
        const val EVENT_BLUETOOTH = "bluetooth"
        const val EVENT_LOCATION = "location"
        const val EVENT_SHORTCUT = "shortcut"
        const val EVENT_TILE = "tile"
        val ALL_EVENT_TYPES = setOf(
            EVENT_SCHEDULE,
            EVENT_NOTIFICATION,
            EVENT_NOTIFICATION_CLICK,
            EVENT_NOTIFICATION_REPLY,
            EVENT_APP_LAUNCH,
            EVENT_APP_EXIT,
            EVENT_APP_INSTALL,
            EVENT_APP_UNINSTALL,
            EVENT_CHARGING,
            EVENT_WIFI,
            EVENT_NETWORK,
            EVENT_BATTERY,
            EVENT_SCREEN,
            EVENT_CLIPBOARD,
            EVENT_BLUETOOTH,
            EVENT_LOCATION,
            EVENT_SHORTCUT,
            EVENT_TILE,
        )

        /** schedule 日历过滤：不限 / 工作日 / 休息日 / 法定节假日 */
        const val CALENDAR_ANY = "any"
        const val CALENDAR_WORKDAY = "workday"
        const val CALENDAR_WEEKEND = "weekend"
        const val CALENDAR_HOLIDAY = "holiday"
        val ALL_CALENDARS = setOf(
            CALENDAR_ANY,
            CALENDAR_WORKDAY,
            CALENDAR_WEEKEND,
            CALENDAR_HOLIDAY,
        )

        /** 通断类状态（charging / wifi / bluetooth 共用） */
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val CHARGING_CONNECTED = STATE_CONNECTED
        const val CHARGING_DISCONNECTED = STATE_DISCONNECTED
        val ALL_CONNECTION_STATES = setOf(STATE_CONNECTED, STATE_DISCONNECTED)
        val ALL_CHARGING_STATES = ALL_CONNECTION_STATES

        /** network 状态 */
        const val NETWORK_ONLINE = "online"
        const val NETWORK_OFFLINE = "offline"
        val ALL_NETWORK_STATES = setOf(NETWORK_ONLINE, NETWORK_OFFLINE)

        /** battery 充放电状态 */
        const val BATTERY_CHARGING = "charging"
        const val BATTERY_DISCHARGING = "discharging"
        val ALL_BATTERY_STATES = setOf(BATTERY_CHARGING, BATTERY_DISCHARGING)

        /** screen 状态 */
        const val SCREEN_ON = "on"
        const val SCREEN_OFF = "off"
        const val SCREEN_UNLOCKED = "unlocked"
        const val SCREEN_LOCKED = "locked"
        val ALL_SCREEN_STATES = setOf(SCREEN_ON, SCREEN_OFF, SCREEN_UNLOCKED, SCREEN_LOCKED)

        /** location 转换方向 */
        const val LOCATION_ENTER = "enter"
        const val LOCATION_EXIT = "exit"
        val ALL_LOCATION_STATES = setOf(LOCATION_ENTER, LOCATION_EXIT)
    }
}
