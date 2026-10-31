package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.ui.KhatKitMediaPicker
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme

/**
 * 聊天背景分组：一张 Mochi 分组卡片（小标题 + 说明），里面依次是预览图、[adjustments]（调用方放透明度滑块等）、
 * 「选择/更换背景图片」与「移除」两个列表按钮。
 *
 * @param adjustments 放在预览图下面、按钮上面的行，与本组同一张卡片
 */
@Composable
fun BackgroundPicker(
    modifier: Modifier = Modifier,
    background: String?,
    backgroundOpacity: Float = 1.0f,
    onUpdate: (String?) -> Unit,
    adjustments: @Composable () -> Unit = {},
) {
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    var showPickOption by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    // 背景图走自研媒体网格选择器（对齐 ImageToolbox），单选
    var showImagePicker by remember { mutableStateOf(false) }

    val previewOpacity = backgroundOpacity.coerceIn(0f, 1f)

    MochiListSection(
        modifier = modifier,
        header = stringResource(R.string.assistant_page_chat_background),
        footer = stringResource(R.string.assistant_page_chat_background_desc),
    ) {
        if (background != null) {
            Box(
                modifier = Modifier
                    .mochiListDivider(MochiListDefaults.TextRowPadding)
                    .fillMaxWidth()
                    .padding(MochiListDefaults.RowPadding),
            ) {
                AsyncImage(
                    model = background,
                    contentDescription = stringResource(R.string.assistant_page_background_set),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiTheme.shapes.large)
                        .alpha(previewOpacity)
                )
            }
        }

        adjustments()

        MochiListButton(
            text = if (background != null) {
                stringResource(R.string.assistant_page_change_background)
            } else {
                stringResource(R.string.assistant_page_select_background)
            },
            onClick = {
                showPickOption = true
            },
        )

        if (background != null) {
            MochiListButton(
                text = stringResource(R.string.assistant_page_remove),
                onClick = {
                    onUpdate(null)
                },
                destructive = true,
            )
        }
    }

    if (showPickOption) {
        AppDialog(
            onDismissRequest = {
                showPickOption = false
            },
            title = {
                // AppDialog 给标题槽提供的是 Material 字阶，标题用 Material Text 才能吃到它
                androidx.compose.material3.Text(stringResource(R.string.assistant_page_select_background))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MochiButton(
                        onClick = {
                            showPickOption = false
                            showImagePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.assistant_page_select_from_gallery))
                    }
                    MochiButton(
                        onClick = {
                            showPickOption = false
                            urlInput = ""
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.assistant_page_enter_image_url))
                    }
                    if (background != null) {
                        MochiButton(
                            onClick = {
                                showPickOption = false
                                onUpdate(null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.assistant_page_remove_background))
                        }
                    }
                }
            },
            confirmButton = {
                MochiButton(
                    onClick = {
                        showPickOption = false
                    },
                    style = MochiButtonStyle.Text,
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    if (showUrlInput) {
        AppDialog(
            onDismissRequest = {
                showUrlInput = false
            },
            title = {
                androidx.compose.material3.Text(stringResource(R.string.assistant_page_enter_image_url))
            },
            text = {
                MochiTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "https://example.com/image.jpg",
                    singleLine = true,
                    supportingText = stringResource(R.string.assistant_page_image_url),
                )
            },
            confirmButton = {
                MochiButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onUpdate(urlInput.trim())
                            showUrlInput = false
                        }
                    },
                    style = MochiButtonStyle.Text,
                ) {
                    Text(stringResource(R.string.assistant_page_confirm))
                }
            },
            dismissButton = {
                MochiButton(
                    onClick = {
                        showUrlInput = false
                    },
                    style = MochiButtonStyle.Text,
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
