package heizige.kk.khatkit.app.feature.workflow.data

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
