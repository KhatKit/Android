package heizige.kk.khatkit.app.core.ui.components.ai

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import heizige.kk.khatkit.common.android.Logging
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.uikit.mochi.Toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SZ-E：返回一个打开裁剪对话框的函数（替代原来的 uCrop Activity 跳转）。
 *
 * 裁好的图片先写成临时 JPEG 交给 onCroppedImageReady，回调结束后删除；不管完成还是取消都会调用 onCleanup。
 * 原参数 aspectRatio（宽/高 Pair）与 freeStyleCropEnabled 保留，行为映射到 ImageCropDialog。
 */
@Composable
internal fun useCropLauncher(
    onCroppedImageReady: (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null,
    aspectRatio: Pair<Float, Float>? = null,
    freeStyleCropEnabled: Boolean = true,
): (Uri) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    // 写文件期间对话框还开着，防止连点确认存出两张
    var saving by remember { mutableStateOf(false) }

    fun finish() {
        source = null
        onCleanup?.invoke()
    }

    source?.let { uri ->
        ImageCropDialog(
            image = uri,
            aspectRatio = aspectRatio?.let { (w, h) -> if (h > 0f) w / h else null },
            freeStyleCropEnabled = freeStyleCropEnabled,
            onDismiss = ::finish,
            onConfirm = { bitmap ->
                if (saving) return@ImageCropDialog
                saving = true
                scope.launch {
                    val outputFile = File(context.appTempFolder, "crop_output_" + System.currentTimeMillis() + ".jpg")
                    runCatching {
                        withContext(Dispatchers.IO) {
                            outputFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        }
                        onCroppedImageReady(outputFile.toUri())
                    }.onFailure { error ->
                        Logging.log("CropLauncher", "crop failed: " + error.message + " | " + error.stackTraceToString())
                        Toast.show(
                            "Failed to crop image: " + (error.message ?: "unknown error"),
                            isError = true,
                        )
                    }
                    outputFile.delete()
                    saving = false
                    finish()
                }
            },
        )
    }

    return { source = it }
}
