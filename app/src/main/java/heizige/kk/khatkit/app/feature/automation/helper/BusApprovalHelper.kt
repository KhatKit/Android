package heizige.kk.khatkit.app.feature.automation.helper

import heizige.kk.khatkit.app.core.data.automation.ApprovalCategory
import heizige.kk.khatkit.bridge.ApprovalGate
import kotlinx.coroutines.runBlocking

/** 将 app 的授权管理器适配为 bridge 的同步审批闸门。 */
internal class BusApprovalHelper(
    private val manager: AutomationManager = AutomationManager,
) : ApprovalGate {
    override fun request(title: String, detail: String, category: String): Boolean {
        val resolved = ApprovalCategory.fromId(category) ?: ApprovalCategory.CARD_RUN
        return runBlocking { manager.requestApproval(title, detail, resolved) }
    }
}
