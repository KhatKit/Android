package heizige.kk.khatkit.app.feature.automation

import heizige.kk.khatkit.bridge.ApprovalGate
import kotlinx.coroutines.runBlocking

/** 将 app 的审批总线适配为 bridge 的同步审批闸门。 */
internal class BusApprovalGate(
    private val bus: AutomationBus = AutomationBus,
) : ApprovalGate {
    override fun request(title: String, detail: String, category: String): Boolean {
        val resolved = ApprovalCategory.fromId(category) ?: ApprovalCategory.CARD_RUN
        return runBlocking { bus.requestApproval(title, detail, resolved) }
    }
}
