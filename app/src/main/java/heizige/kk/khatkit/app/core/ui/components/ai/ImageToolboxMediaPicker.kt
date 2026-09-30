package heizige.kk.khatkit.app.core.ui.components.ai

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PickerImage(val uri: android.net.Uri, val id: Long)

@Composable
internal fun ImageToolboxMediaPicker(
    onDismiss: () -> Unit,
    onConfirm: (List<android.net.Uri>) -> Unit,
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf(emptyList<PickerImage>()) }
    val selected = remember { mutableStateListOf<PickerImage>() }

    LaunchedEffect(Unit) {
        images = withContext(Dispatchers.IO) { queryImages(context) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KedgeIconButton(onClick = onDismiss) { Icon(close, "关闭") }
                    Text("选择图片", style = MaterialTheme.typography.titleLarge)
                    SpacerWeight()
                    Text("${selected.size}/50", color = MaterialTheme.colorScheme.primary)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(images, key = { it.id }) { image ->
                        val index = selected.indexOf(image)
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (index >= 0) selected.removeAt(index)
                                    else if (selected.size < 50) selected.add(image)
                                },
                        ) {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (index >= 0) {
                                Box(
                                    modifier = Modifier.padding(6.dp).size(26.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KedgeButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { onConfirm(selected.map { it.uri }) },
                    ) {
                        Icon(check, null)
                        Text("  添加图片")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SpacerWeight() {
    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
}

private fun queryImages(context: Context): List<PickerImage> = runCatching {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        buildList {
            while (cursor.moveToNext() && size < 500) {
                val id = cursor.getLong(idIndex)
                add(PickerImage(ContentUris.withAppendedId(collection, id), id))
            }
        }
    }.orEmpty()
}.getOrDefault(emptyList())
