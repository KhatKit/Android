package heizige.kk.khatkit.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeImpl
import heizige.kk.khatkit.app.record.KhatKitOperationRecorder

/**
 * L1 无障碍服务：连接后把 [AccessibilityBridgeImpl] 注册到 bridge 宿主，
 * 卡片即可通过 `accessibility` bridge 执行找节点/点击/输入/手势等自动化。
 */
class KhatKitAccessibilityService : AccessibilityService() {

    private var bridge: AccessibilityBridgeImpl? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val impl = AccessibilityBridgeImpl(this)
        bridge = impl
        AccessibilityBridgeHolder.attach(impl)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (current.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            current.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { pkg ->
                bridge?.foregroundPackage = pkg
            }
        }
        // 录制人工操作：未在录制时只是一次 volatile 读，开销可忽略
        KhatKitOperationRecorder.onAccessibilityEvent(current)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridgeHolder.detach()
        bridge = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridgeHolder.detach()
        bridge = null
        super.onDestroy()
    }

    companion object {
        /** 系统设置里本服务是否已勾选（不等价于已连接，仅用于引导页展示）。 */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${KhatKitAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
