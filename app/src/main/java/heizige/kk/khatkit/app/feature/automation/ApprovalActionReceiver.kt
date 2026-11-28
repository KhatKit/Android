package heizige.kk.khatkit.app.feature.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import heizige.kk.khatkit.app.feature.automation.helper.AutomationManager

/**
 * 授权通知的「允许 / 拒绝」按钮接收器：把通知上的点击回灌给 [AutomationManager] 的
 * 待决议请求。仅在无悬浮窗能力、走通知回退时使用；无待决议请求时是 no-op。
 */
class ApprovalActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AutomationManager.EXTRA_APPROVAL_ID) ?: return
        when (intent.action) {
            AutomationManager.ACTION_APPROVAL_ALLOW -> AutomationManager.approve(id)
            AutomationManager.ACTION_APPROVAL_DENY -> AutomationManager.deny(id)
        }
    }
}
