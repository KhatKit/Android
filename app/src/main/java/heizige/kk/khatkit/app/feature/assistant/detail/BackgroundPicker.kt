package heizige.kk.khatkit.app.feature.assistant.detail

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormRow
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.ui.KhatKitMediaPicker
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun BackgroundPicker(
    modifier: Modifier = Modifier,
    background: String?,
    backgroundOpacity: Float = 1.0f,
    onUpdate: (String?) -> Unit
) {
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    var showPickOption by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    // 背景图走自研媒体网格选择器（对齐 ImageToolbox），单选
    var showImagePicker by remember { mutableStateOf(false) }

    val previewOpacity = backgroundOpacity.coerceIn(0f, 1f)

    KedgeFormRow(
        modifier = modifier,
        label = {
            Text(stringResource(R.string.assistant_page_chat_background))
        },
        description = {
            Text(stringResource(R.string.assistant_page_chat_background_desc))
        }
    ) {
        KedgeButton(
            onClick = {
                showPickOption = true
            },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = if (background != null) {
                    stringResource(R.string.assistant_page_change_background)
                } else {
                    stringResource(R.string.assistant_page_select_background)
                }
            )
        }

        if (background != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_background_set),
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KedgeTextButton(
                    onClick = {
                        onUpdate(null)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_remove))
                }
            }

            AsyncImage(
                model = background,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(previewOpacity)
            )
        }
    }

    if (showPickOption) {
        AppAlertDialog(
            onDismissRequest = {
                showPickOption = false
            },
            title = {
                Text(stringResource(R.string.assistant_page_select_background))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KedgeButton(
                        onClick = {
                            showPickOption = false
                            showImagePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.assistant_page_select_from_gallery))
                    }
                    KedgeButton(
                        onClick = {
                            showPickOption = false
                            urlInput = ""
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.assistant_page_enter_image_url))
                    }
                    if (background != null) {
                        KedgeButton(
                            onClick = {
                                showPickOption = false
                                onUpdate(null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.assistant_page_remove_background))
                        }
                    }
                }
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        showPickOption = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    if (showUrlInput) {
        AppAlertDialog(
            onDismissRequest = {
                showUrlInput = false
            },
            title = {
                Text(stringResource(R.string.assistant_page_enter_image_url))
            },
            text = {
                KedgeOutlinedTextFieldWithSlots(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(R.string.assistant_page_image_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/image.jpg") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onUpdate(urlInput.trim())
                            showUrlInput = false
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_confirm))
                }
            },
            dismissButton = {
                KedgeTextButton(
                    onClick = {
                        showUrlInput = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    KhatKitMediaPicker(
        visible = showImagePicker,
        allowedMedia = AllowedMedia.Photos(null),
        allowMultiple = false,
        onDismiss = { showImagePicker = false },
        onPicked = { uris ->
            showImagePicker = false
            uris.firstOrNull()?.let { picked ->
                val localUris = filesManager.createChatFilesByContents(listOf(picked))
                localUris.firstOrNull()?.let { localUri -> onUpdate(localUri.toString()) }
            }
        },
    )
}
