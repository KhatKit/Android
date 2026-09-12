package heizige.kk.khatkit.bridge.impl

import android.app.Activity
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Shizuku 授权状态与申请入口（给 UI 用，避免 app 直接依赖 shizuku）。 */
object ShizukuPermission {

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun request(activity: Activity, requestCode: Int, onResult: (Boolean) -> Unit) {
        if (isGranted()) {
            onResult(true)
            return
        }
        var listener: Shizuku.OnRequestPermissionResultListener? = null
        listener = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == requestCode) {
                listener?.let { Shizuku.removeRequestPermissionResultListener(it) }
                onResult(result == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(requestCode) }.onFailure {
            listener?.let { active -> Shizuku.removeRequestPermissionResultListener(active) }
            onResult(false)
        }
    }
}
