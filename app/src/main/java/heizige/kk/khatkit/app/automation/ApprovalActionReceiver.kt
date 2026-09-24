package heizige.kk.khatkit.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 授权通知的「允许 / 拒绝」按钮接收器：把通知上的点击回灌给 [AutomationBus] 的
 * 待决议请求。仅在无悬浮窗能力、走通知回退时使用；无待决议请求时是 no-op。
 */
class ApprovalActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutomationBus.ACTION_APPROVAL_ALLOW -> AutomationBus.approve()
            AutomationBus.ACTION_APPROVAL_DENY -> AutomationBus.deny()
        }
    }
}
