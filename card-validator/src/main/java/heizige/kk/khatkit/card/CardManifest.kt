package heizige.kk.khatkit.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
     * - schedule：`times`（HH:mm 列表）或 `intervalMinutes`（间隔分钟）二选一，`days` 限定星期
     * - notification：`package` / `titleContains` / `textContains` 任意组合（空 = 不限制）
     * - app_launch：`package` 为空表示任意应用进入前台
     * - charging：`state` 为 connected | disconnected
     */
    @Serializable
    data class Event(
        /** schedule | notification | app_launch | charging */
        val type: String,
        /** schedule：["08:00","21:30"] */
        val times: List<String> = emptyList(),
        /** schedule：重复间隔（分钟，>=1），与 times 二选一 */
        val intervalMinutes: Int = 0,
        /** schedule：1=周一 .. 7=周日，空 = 每天 */
        val days: List<Int> = emptyList(),
        /** notification / app_launch 的包名（空 = 任意），JSON 字段名为 package */
        @SerialName("package") val packageName: String = "",
        /** notification：标题包含 */
        val titleContains: String = "",
        /** notification：正文包含 */
        val textContains: String = "",
        /** charging：connected | disconnected */
        val state: String = "",
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

    val isCommand: Boolean get() = engine == "command"

    /** 卡片要求的能力集合，供能力协商过滤 */
    val requiredBridges: Set<String> get() = requires.bridges.toSet()

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
        const val EVENT_APP_LAUNCH = "app_launch"
        const val EVENT_CHARGING = "charging"
        val ALL_EVENT_TYPES = setOf(EVENT_SCHEDULE, EVENT_NOTIFICATION, EVENT_APP_LAUNCH, EVENT_CHARGING)

        const val CHARGING_CONNECTED = "connected"
        const val CHARGING_DISCONNECTED = "disconnected"
        val ALL_CHARGING_STATES = setOf(CHARGING_CONNECTED, CHARGING_DISCONNECTED)
    }
}
