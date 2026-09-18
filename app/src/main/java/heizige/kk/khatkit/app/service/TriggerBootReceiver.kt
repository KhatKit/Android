package heizige.kk.khatkit.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机 / 应用升级后重新拉起触发器服务，恢复 schedule 计时与前台应用轮询。
 */
class TriggerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                if (!TriggerSettings(context).masterEnabled) return
                Log.i(TAG, "re-arming trigger service after ${intent.action}")
                TriggerService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "TriggerBootReceiver"
    }
}
