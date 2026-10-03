package heizige.kk.khatkit.app.feature.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 可视化工作流的规范 JSON。执行时编译成既有 FlowSpec，不另写卡片执行器。 */
val WorkflowJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 与 `khatkit__run_flow` 的步骤上限一致，见 docs/flow.md。 */
const val WORKFLOW_FLOW_STEP_LIMIT = 20

/** TriggerEngine 已有的 18 种事件。触发器节点只引用这些名字，不重新实现调度。 */
val WORKFLOW_TRIGGER_EVENTS = listOf(
    "schedule",
    "notification",
    "notification_click",
    "notification_reply",
    "app_launch",
    "app_exit",
    "app_install",
    "app_uninstall",
    "charging",
    "wifi",
    "network",
    "battery",
    "screen",
    "clipboard",
    "bluetooth",
    "location",
    "shortcut",
    "tile",
)

val WORKFLOW_NODE_KINDS = listOf(
    "trigger",
    "llm",
    "card_tool",
    "condition",
    "loop",
    "delay",
    "data_extract",
    "http",
    "notify",
    "sub_flow",
)

@Serializable
data class VisualNode(
    val id: String,
    val kind: String,
    val title: String = "",
    val params: Map<String, String> = emptyMap(),
    val dependsOn: List<String> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class VisualWorkflow(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val nodes: List<VisualNode> = emptyList(),
)

@Serializable
data class StepTrace(
    val nodeId: String,
    val kind: String,
    val status: String,
    val input: Map<String, String> = emptyMap(),
    val output: Map<String, String> = emptyMap(),
)

@Serializable
data class WorkflowExecution(
    val steps: List<StepTrace>,
    val status: String,
    val error: String? = null,
) {
    fun successes(): List<StepTrace> = steps.filter { it.status == "success" && it.output["resumed"] != "true" }
}

fun VisualWorkflow.encode(): String = WorkflowJson.encodeToString(this)

fun decodeWorkflow(raw: String): VisualWorkflow? =
    runCatching { WorkflowJson.decodeFromString<VisualWorkflow>(raw) }.getOrNull()

object WorkflowSamples {
    /** 验收夹具：通知 → 分类 → 条件分支 → 回复或写文件。 */
    fun notificationClassify(): VisualWorkflow = VisualWorkflow(
        id = "sample-notification-classify",
        name = "通知分类",
        nodes = listOf(
            VisualNode("t1", "trigger", "通知触发", mapOf("event" to "notification")),
            VisualNode(
                "llm1",
                "llm",
                "分类",
                mapOf("prompt" to "分类这条通知", "fallback" to "other"),
                dependsOn = listOf("t1"),
            ),
            VisualNode(
                "c1",
                "condition",
                "是否紧急",
                mapOf("field" to "text", "equals" to "urgent"),
                dependsOn = listOf("llm1"),
            ),
            VisualNode(
                "n1",
                "notify",
                "发送回复",
                mapOf("title" to "回复", "when" to "then"),
                dependsOn = listOf("c1"),
            ),
            VisualNode(
                "f1",
                "card_tool",
                "写入文件",
                mapOf("card" to "write_file", "path" to "out.txt", "when" to "else"),
                dependsOn = listOf("c1"),
            ),
        ),
    )

    fun linearClassify(): VisualWorkflow = VisualWorkflow(
        id = "sample-linear",
        name = "线性分类",
        nodes = listOf(
            VisualNode("t1", "trigger", "通知触发", mapOf("event" to "notification")),
            VisualNode(
                "llm1",
                "llm",
                "分类",
                mapOf("prompt" to "分类", "fallback" to "other"),
                dependsOn = listOf("t1"),
            ),
            VisualNode(
                "n1",
                "notify",
                "发送通知",
                mapOf("title" to "已分类"),
                dependsOn = listOf("llm1"),
            ),
        ),
    )

    fun repeatNotify(): VisualWorkflow = VisualWorkflow(
        id = "sample-loop",
        name = "重复通知",
        nodes = listOf(
            VisualNode("t1", "trigger", "手动启动", mapOf("event" to "manual")),
            VisualNode("loop1", "loop", "重复三次", mapOf("count" to "3"), dependsOn = listOf("t1")),
            VisualNode(
                "body1",
                "notify",
                "滴答",
                mapOf("title" to "tick", "when" to "body"),
                dependsOn = listOf("loop1"),
            ),
        ),
    )
}
