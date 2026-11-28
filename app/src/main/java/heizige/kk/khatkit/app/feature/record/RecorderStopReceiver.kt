package heizige.kk.khatkit.app.feature.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import heizige.kk.khatkit.app.feature.record.helper.OperationRecordManager

/** 录制通知里的「停止」按钮：停止录制并取消通知，步骤留在内存里等 App 回来保存。 */
class RecorderStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP) return
        OperationRecordManager.stop(context)
    }

    companion object {
        const val ACTION_STOP = "heizige.kk.khatkit.app.feature.record.action.STOP"
    }
}
