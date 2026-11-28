package heizige.kk.khatkit.app.feature.workflow.helper

import heizige.kk.khatkit.app.feature.workflow.data.VisualNode
import heizige.kk.khatkit.app.feature.workflow.data.VisualWorkflow

/** 验收夹具：通知 → 分类 → 条件分支 → 回复或写文件。 */
fun notificationClassifySample(): VisualWorkflow = VisualWorkflow(
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

fun linearClassifySample(): VisualWorkflow = VisualWorkflow(
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

fun repeatNotifySample(): VisualWorkflow = VisualWorkflow(
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
