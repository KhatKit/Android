package heizige.kk.khatkit.app.feature.workflow.helper

import heizige.kk.khatkit.app.feature.workflow.data.WorkflowDefinition
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowNode
import heizige.kk.khatkit.app.feature.workflow.data.VisualNode
import heizige.kk.khatkit.app.feature.workflow.data.VisualWorkflow
import heizige.kk.khatkit.app.feature.workflow.data.WORKFLOW_NODE_KINDS
import heizige.kk.khatkit.app.feature.workflow.data.WORKFLOW_TRIGGER_EVENTS
import heizige.kk.khatkit.app.feature.workflow.data.decodeWorkflow

/**
 * 旧列表编辑器节点 → 规范 JSON 的纯映射（无状态、无副作用）。
 *
 * 已有 graphJson 时直接用，不重写用户图；旧中文判定值与 depends_on 参数保持兼容。
 */
fun WorkflowDefinition.toVisualWorkflow(): VisualWorkflow {
    decodeWorkflow(graphJson)?.let { return it }
    val visualNodes = nodes.mapIndexed { index, node -> toVisual(node, index) }
    val chained = visualNodes.mapIndexed { index, node ->
        if (node.dependsOn.isNotEmpty() || index == 0) node
        else node.copy(dependsOn = listOf(visualNodes[index - 1].id))
    }
    return VisualWorkflow(
        id = id.ifBlank { "workflow" },
        name = name.ifBlank { "未命名" },
        enabled = enabled,
        nodes = chained,
    )
}

/** 规范 JSON → 旧列表编辑器节点（保留 depends_on 参数）。 */
fun VisualWorkflow.toStoredNodes(): List<WorkflowNode> =
    nodes.map { node ->
        val params = node.params.toMutableMap()
        if (node.dependsOn.isNotEmpty()) params["depends_on"] = node.dependsOn.joinToString(",")
        WorkflowNode(
            category = kindLabel(node.kind),
            title = node.title.ifBlank { kindLabel(node.kind) },
            detail = node.kind,
            params = params,
            enabled = node.enabled,
            kind = node.kind,
            id = node.id,
        )
    }

private fun toVisual(node: WorkflowNode, index: Int): VisualNode {
    val depends = node.params["depends_on"].orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val params = node.params - "depends_on"
    return VisualNode(
        id = node.id.ifBlank { "n$index" },
        kind = kindOf(node),
        title = node.title,
        params = params + triggerEvent(node),
        dependsOn = depends,
        enabled = node.enabled,
    )
}

private fun kindOf(node: WorkflowNode): String {
    if (node.kind in WORKFLOW_NODE_KINDS) return node.kind
    val title = node.title
    return when {
        node.category == "触发器" || title.contains("触发") || title.contains("启动") -> "trigger"
        title.startsWith("AI") || node.category == "AI" -> "llm"
        title.contains("条件") || title.contains("否则") -> "condition"
        title.contains("重复") || title.contains("遍历") -> "loop"
        title == "等待" || title.contains("等待") && node.category == "逻辑" -> "delay"
        title.contains("提取") || title.contains("JSON") || title.contains("格式化") -> "data_extract"
        title.contains("HTTP") || title.contains("下载") || title.contains("上传") -> "http"
        title.contains("通知") && node.category != "触发器" -> "notify"
        title.contains("子流程") -> "sub_flow"
        else -> "card_tool"
    }
}

private fun triggerEvent(node: WorkflowNode): Map<String, String> {
    if (kindOf(node) != "trigger" || node.params["event"] in WORKFLOW_TRIGGER_EVENTS || node.params["event"] == "manual") {
        return emptyMap()
    }
    val event = when {
        node.title.contains("通知") -> "notification"
        node.title.contains("定时") -> "schedule"
        node.title.contains("剪贴板") -> "clipboard"
        node.title.contains("充电") -> "charging"
        node.title.contains("Wi-Fi") || node.title.contains("WiFi") -> "wifi"
        node.title.contains("位置") -> "location"
        node.title.contains("应用") -> "app_launch"
        else -> "manual"
    }
    return mapOf("event" to event)
}

private fun kindLabel(kind: String): String = when (kind) {
    "trigger" -> "触发器"
    "llm" -> "模型"
    "card_tool" -> "卡片"
    "condition" -> "逻辑"
    "loop" -> "逻辑"
    "delay" -> "逻辑"
    "data_extract" -> "数据"
    "http" -> "网络"
    "notify" -> "系统"
    "sub_flow" -> "流程"
    else -> "动作"
}
