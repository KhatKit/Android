package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.card.CardManifest

/** 普通查询、UI 卡片和小组件不显示运行看板；设备操作及自动触发任务显示。 */
internal fun showsAutomationStatus(manifest: CardManifest, trigger: String): Boolean =
    manifest.isCommand ||
        manifest.privilege == "elevated" ||
        manifest.requiredBridges.any { it in setOf("accessibility", "root", "shizuku") } ||
        trigger in setOf("定时任务", "事件触发", "flow")
