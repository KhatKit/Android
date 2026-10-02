/*
 * 媒体读取权限的状态与申请（对应 ImageToolbox 的 `PermissionUtils` +
 * `ContextUtils` 那部分，Apache-2.0, T8RIN）。
 *
 * Android 13+ 用 READ_MEDIA_IMAGES，13 以下用 READ_EXTERNAL_STORAGE。
 * 另外单独跟踪「所有文件访问」——它不是读取媒体的前提，只影响
 * Android/data、SD 卡根目录这些位置能不能被扫到。
 */

package heizige.kk.khatkit.mediapicker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * [allowedMedia] 对应的读取权限。Android 13 起图片/视频是两个独立权限，
 * 只申请 `READ_MEDIA_IMAGES` 却选视频的话，视频库永远是空的。
 */
fun mediaReadPermissions(allowedMedia: AllowedMedia): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when (allowedMedia) {
            is AllowedMedia.Photos -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            AllowedMedia.Videos -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            AllowedMedia.Both -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        }
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun Context.hasMediaReadPermission(allowedMedia: AllowedMedia): Boolean =
    mediaReadPermissions(allowedMedia).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

fun Context.hasManageExternalStorage(): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        true
    } else {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false) ||
            // 有些 ROM（定制桌面 / 精简系统）根本没有这个开关，
            // 老老实实弹提示条只会让用户反复走进一个没有按钮的设置页。
            isExternalStorageManagerUnavailable()
    }

/**
 * ROM 是否压根不提供「所有文件访问」开关。
 *
 * 主要判据是宿主有没有在 manifest 里声明 `MANAGE_EXTERNAL_STORAGE`：从应用商店
 * 安装的包即使声明了也拿不到这个权限，这时提示条只会把用户送进一个没有开关的设置页。
 */
fun Context.isExternalStorageManagerUnavailable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val declared = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE) == true
    }.getOrDefault(false)
    return !declared
}

private fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }

@SuppressLint("InlinedApi")
private fun Context.manageAllFilesIntent(): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.fromParts("package", packageName, null)
    }

@Stable
class MediaPermissionState internal constructor(
    private val appContext: Context,
    allowedMedia: AllowedMedia,
) {
    /** 「所有文件访问」。缺它只是扫不到部分目录，不阻塞选择。 */
    var hasManagePermission by mutableStateOf(appContext.hasManageExternalStorage())
        internal set

    internal var allowedMedia: AllowedMedia = allowedMedia
        set(value) {
            field = value
            refresh()
        }

    private val _hasReadPermission =
        mutableStateOf(appContext.hasMediaReadPermission(allowedMedia))
    val hasReadPermission: Boolean get() = _hasReadPermission.value

    internal var readPermissionLauncher: (() -> Unit)? = null
    internal var managePermissionLauncher: (() -> Unit)? = null

    fun refresh() {
        _hasReadPermission.value = appContext.hasMediaReadPermission(allowedMedia)
        hasManagePermission = appContext.hasManageExternalStorage()
    }

    /** 拉起系统授权弹窗。 */
    fun requestReadPermission() {
        runCatching { readPermissionLauncher?.invoke() }.onFailure { refresh() }
    }

    /**
     * 跳到能开「所有文件访问」的设置页。
     *
     * `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 在部分 ROM 上没有对应 Activity，
     * 直接 launch 会抛 `ActivityNotFoundException`，所以包一层并退到应用详情页。
     */
    fun requestManagePermission() {
        val launch = managePermissionLauncher
        if (launch != null && runCatching { launch() }.isSuccess) return
        runCatching { appContext.startActivity(manageIntent()) }
            .onFailure { runCatching { appContext.startActivity(appContext.appDetailsIntent()) } }
    }

    internal fun manageIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.manageAllFilesIntent()
        } else {
            appContext.appDetailsIntent()
        }
}

@Composable
fun rememberMediaPermissionState(allowedMedia: AllowedMedia): MediaPermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember { MediaPermissionState(context.applicationContext, allowedMedia) }
    state.allowedMedia = allowedMedia

    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        state.refresh()
    }
    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        state.refresh()
    }

    state.readPermissionLauncher = { readLauncher.launch(mediaReadPermissions(allowedMedia)) }
    state.managePermissionLauncher = { manageLauncher.launch(state.manageIntent()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                state.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}
