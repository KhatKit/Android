package heizige.kk.khatkit.app.feature.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import heizige.kk.khatkit.trigger.TriggerCard
import heizige.kk.khatkit.trigger.TriggerSchedulePlanner
import heizige.kk.khatkit.trigger.WorkdayCalendar
import java.time.ZoneId

/**
 * 分钟级 schedule 的精确闹钟调度。
 *
 * - 存在启用的 `times` 事件且已获精确闹钟权限时用 [AlarmManager.setExactAndAllowWhileIdle]
 * - 权限缺失 / 无分钟级事件时取消闹钟，继续由 TriggerService 的 60s ticker 兜底
 * - 闹钟投递到 TriggerService（ACTION_ALARM），服务 tick 一次后重新排下一次
 */
object TriggerExactAlarmScheduler {

    const val ACTION_ALARM = "heizige.kk.khatkit.app.action.TRIGGER_ALARM"

    private const val TAG = "TriggerExactAlarm"
    private const val REQUEST_CODE = 3100

    /** 仅存在分钟级定时事件时保留闹钟；[masterEnabled] 为 false 或权限缺失则取消。 */
    fun schedule(
        context: Context,
        cards: List<TriggerCard>,
        masterEnabled: Boolean,
        calendar: WorkdayCalendar = WorkdayCalendar.EMPTY,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context)
        val allowed = canScheduleExact(alarmManager)
        val next = if (masterEnabled && allowed) {
            TriggerSchedulePlanner.nextTriggerAt(
                cards,
                System.currentTimeMillis(),
                ZoneId.systemDefault(),
                calendar,
            )
        } else {
            null
        }
        if (next == null) {
            runCatching { alarmManager.cancel(pendingIntent) }
            return
        }
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
        }.onFailure { Log.e(TAG, "setExactAndAllowWhileIdle failed", it) }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

    /** API 31+ 需要用户在系统设置里授予「闹钟和提醒」权限；以下版本默认允许。 */
    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExact(alarmManager)
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_CODE,
        Intent(context, TriggerService::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
