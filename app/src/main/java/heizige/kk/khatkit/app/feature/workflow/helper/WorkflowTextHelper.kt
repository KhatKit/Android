package heizige.kk.khatkit.app.feature.workflow.helper

import heizige.kk.khatkit.app.R

// These labels are presentation aliases for legacy serialized data. Never write them back.
// Unknown labels, names, parameter values and model prompts are user/runtime content; return them verbatim.
// The raw keys below are protocol values and WorkflowLegacy migration aliases, not UI text.
internal fun workflowLabel(context: android.content.Context, value: String): String {
    val resource = when (value) {
        "触发器", "trigger" -> R.string.setting_workflow_label_trigger
        "模型", "llm" -> R.string.setting_workflow_label_llm
        "卡片", "card_tool" -> R.string.setting_workflow_label_card_tool
        "条件分支", "condition" -> R.string.setting_workflow_label_condition
        "重复", "loop" -> R.string.setting_workflow_label_loop
        "等待", "delay" -> R.string.setting_workflow_label_delay
        "提取字段", "data_extract" -> R.string.setting_workflow_label_data_extract
        "HTTP 请求", "http" -> R.string.setting_workflow_label_http
        "发送通知", "notify" -> R.string.setting_workflow_label_notify
        "子流程", "sub_flow" -> R.string.setting_workflow_label_sub_flow
        "逻辑" -> R.string.setting_workflow_label_logic
        "数据" -> R.string.setting_workflow_label_data
        "网络" -> R.string.setting_workflow_label_network
        "系统" -> R.string.setting_workflow_label_system
        "流程" -> R.string.setting_workflow_label_flow
        "动作" -> R.string.setting_workflow_label_action
        "手动启动", "manual" -> R.string.setting_workflow_label_manual
        "通知触发", "notification" -> R.string.setting_workflow_label_notification
        "分类", "classify" -> R.string.setting_workflow_label_classify
        "写文件", "write_file" -> R.string.setting_workflow_label_write_file
        "点击运行或绑定到自动化触发器" -> R.string.setting_workflow_label_manual_detail
        "未命名节点" -> R.string.setting_workflow_label_unnamed_node
        "执行自动化操作" -> R.string.setting_workflow_label_execute_action
        "是否紧急" -> R.string.setting_workflow_label_urgent
        else -> return value
    }
    return context.getString(resource)
}

internal fun workflowStatusLabel(context: android.content.Context, value: String): String = when (value) {
    "success" -> context.getString(R.string.setting_workflow_status_success)
    "failed" -> context.getString(R.string.setting_workflow_status_failed)
    "skipped" -> context.getString(R.string.setting_workflow_status_skipped)
    "cancelled" -> context.getString(R.string.setting_workflow_status_cancelled)
    "completed" -> context.getString(R.string.setting_workflow_status_completed)
    else -> value
}

internal fun workflowParameterLabel(context: android.content.Context, value: String): String = when (value) {
    "event" -> context.getString(R.string.setting_workflow_parameter_event)
    "prompt" -> context.getString(R.string.setting_workflow_parameter_prompt)
    "fallback" -> context.getString(R.string.setting_workflow_parameter_fallback)
    "field" -> context.getString(R.string.setting_workflow_parameter_field)
    "equals" -> context.getString(R.string.setting_workflow_parameter_equals)
    "count" -> context.getString(R.string.setting_workflow_parameter_count)
    "ms" -> context.getString(R.string.setting_workflow_parameter_ms)
    "url" -> context.getString(R.string.setting_workflow_parameter_url)
    "method" -> context.getString(R.string.setting_workflow_parameter_method)
    "title" -> context.getString(R.string.setting_workflow_parameter_title)
    "card" -> context.getString(R.string.setting_workflow_parameter_card)
    "path" -> context.getString(R.string.setting_workflow_parameter_path)
    "workflow" -> context.getString(R.string.setting_workflow_parameter_workflow)
    "depends_on" -> context.getString(R.string.setting_workflow_parameter_depends_on)
    "when" -> context.getString(R.string.setting_workflow_parameter_when)
    "text" -> context.getString(R.string.setting_workflow_parameter_text)
    else -> value
}
