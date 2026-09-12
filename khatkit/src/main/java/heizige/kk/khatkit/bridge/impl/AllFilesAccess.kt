package heizige.kk.khatkit.bridge.impl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 共享存储「所有文件访问」授权入口。
 *
 * 卡片读写 /sdcard 等路径在 API 30+ 必须有此授权，否则 File API 直接失败。
 */
object AllFilesAccess {

    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** 打开系统授权页；优先应用专属页面，失败退回总列表。 */
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appPage = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${activity.packageName}"))
        runCatching { activity.startActivity(appPage) }.onFailure {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}
