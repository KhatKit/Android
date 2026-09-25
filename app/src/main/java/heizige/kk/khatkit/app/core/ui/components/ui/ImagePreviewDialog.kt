package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.compose.rememberAsyncImagePainter
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import kotlinx.coroutines.launch
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.download

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val state = rememberZoomablePagerState { images.size }
    val toaster = LocalToaster.current
    val lifecycleOwner = LocalLifecycleOwner.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box {
            ImagePager(
                modifier = Modifier.fillMaxSize(),
                pagerState = state,
                imageLoader = { index ->
                    val painter = rememberAsyncImagePainter(images[index])
                    return@ImagePager Pair(painter, painter.intrinsicSize)
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                Toast.show("正在保存")
                                val imgUrl = images[state.currentPage]
                                filesManager.saveMessageImage(context, imgUrl)
                                Toast.show(message = "已保存图片", isError = false)
                            }.onFailure {
                                it.printStackTrace()
                                Toast.show(
                                    message = it.toString(),
                                    isError = true
                                )
                            }
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(download, null, tint = Color.White)
                }
            }
        }
    }
}
