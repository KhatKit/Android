package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.media.addPhotoAlternate
import heizige.kk.khatkit.app.core.ui.icons.utility.delete
import heizige.kk.khatkit.app.core.ui.icons.media.image
import heizige.kk.khatkit.app.core.ui.icons.utility.link
import heizige.kk.khatkit.app.core.ui.icons.settings.palette
import heizige.kk.khatkit.app.core.ui.icons.media.photoCamera
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.navigation.MochiNavDefaults
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.theme.MochiDefaults

enum class AvatarEditSheetAction {
    PickImage,
    TakePhoto,
    PickIconFile,
    PickEmoji,
    Reset,
}

private sealed interface AvatarSheetExit {
    data object Dismiss : AvatarSheetExit
    data class Action(val action: AvatarEditSheetAction) : AvatarSheetExit
    data class SubmitUrl(val url: String) : AvatarSheetExit
}

/** Mochi avatar editor; actions dispatch exactly once after the sheet finishes hiding. */
@Composable
fun AvatarEditSheet(
    onDismissRequest: () -> Unit,
    onAction: (AvatarEditSheetAction) -> Unit,
    onSubmitUrl: (String) -> Unit,
) {
    val sheetState = rememberMochiSheetState()
    val navigator = rememberMochiNavigator("avatar.options")
    var urlInput by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    var exit by remember { mutableStateOf<AvatarSheetExit>(AvatarSheetExit.Dismiss) }

    LaunchedEffect(Unit) { sheetState.show() }

    val requestClose: (AvatarSheetExit) -> Unit = { target ->
        if (!closing) {
            closing = true
            exit = target
            sheetState.hide()
        }
    }

    MochiSheet(
        state = sheetState,
        navigator = navigator,
        onDismiss = {
            if (!delivered) {
                delivered = true
                when (val target = exit) {
                    is AvatarSheetExit.Action -> onAction(target.action)
                    is AvatarSheetExit.SubmitUrl -> onSubmitUrl(target.url)
                    AvatarSheetExit.Dismiss -> Unit
                }
                onDismissRequest()
            }
        },
    ) {
        val surface = MochiDefaults.sheetBackground()
        MochiNavHost(
            navigator = navigator,
            spec = MochiNavDefaults.spec(pushBackdrop = surface),
        ) { route ->
            val root = route == "avatar.options"
            val leadingIcon = if (root) "close" else "chevron-back"
            val controlsEnabled = !closing && sheetState.isVisible && !navigator.isTransitioning
            Column(Modifier.fillMaxSize().background(surface)) {
                MochiSheetHeader(
                    title = stringResource(
                        if (root) R.string.avatar_change_avatar else R.string.avatar_url_dialog_title,
                    ),
                    navigationIcon = {
                        MochiBarIconButton(
                            onClick = {
                                if (controlsEnabled) {
                                    if (root) requestClose(AvatarSheetExit.Dismiss) else navigator.pop()
                                }
                            },
                            enabled = controlsEnabled,
                            navId = "sheet.leading",
                            contentKey = leadingIcon,
                        ) {
                            MochiIonicon(
                                leadingIcon,
                                contentDescription = stringResource(if (root) R.string.avatar_cancel else R.string.back),
                            )
                        }
                    },
                    actions = if (!root) {
                        {
                            MochiBarIconButton(
                                onClick = {
                                    val url = urlInput.trim()
                                    if (controlsEnabled && url.isNotBlank()) {
                                        requestClose(AvatarSheetExit.SubmitUrl(url))
                                    }
                                },
                                enabled = controlsEnabled && urlInput.trim().isNotBlank(),
                                navId = "sheet.trailing",
                                contentKey = "checkmark",
                            ) {
                                MochiIonicon("checkmark", contentDescription = stringResource(R.string.avatar_url_confirm))
                            }
                        }
                    } else {
                        null
                    },
                )
                MochiSheetContent(modifier = Modifier.weight(1f, fill = false)) {
                    if (root) {
                        ActionListStage(
                            onPick = { action -> requestClose(AvatarSheetExit.Action(action)) },
                            onInputUrl = {
                                if (!closing && sheetState.isVisible && !navigator.isTransitioning) {
                                    navigator.push("avatar.url")
                                }
                            },
                        )
                    } else {
                        UrlInputStage(
                            urlInput = urlInput,
                            onUrlInputChange = { urlInput = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionListStage(
    onPick: (AvatarEditSheetAction) -> Unit,
    onInputUrl: () -> Unit,
) {
    val pickImageLabel = stringResource(R.string.avatar_pick_image)
    val pickEmojiLabel = stringResource(R.string.avatar_pick_emoji)
    val inputUrlLabel = stringResource(R.string.avatar_input_url)
    val resetLabel = stringResource(R.string.avatar_reset)
    val takePhotoLabel = stringResource(R.string.avatar_take_photo)
    val pickIconFileLabel = stringResource(R.string.avatar_pick_icon_file)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MochiListSection {
            AvatarActionRow(image, pickImageLabel) { onPick(AvatarEditSheetAction.PickImage) }
            AvatarActionRow(photoCamera, takePhotoLabel) { onPick(AvatarEditSheetAction.TakePhoto) }
            AvatarActionRow(addPhotoAlternate, pickIconFileLabel) { onPick(AvatarEditSheetAction.PickIconFile) }
            AvatarActionRow(palette, pickEmojiLabel) { onPick(AvatarEditSheetAction.PickEmoji) }
            AvatarActionRow(link, inputUrlLabel, onInputUrl)
            MochiListRow(
                title = resetLabel,
                destructive = true,
                leading = { Icon(delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { onPick(AvatarEditSheetAction.Reset) },
            )
        }
    }
}

@Composable
private fun AvatarActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    MochiListRow(
        title = title,
        leading = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun UrlInputStage(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Md3OutlinedTextFieldWithSlots(
            value = urlInput,
            onValueChange = onUrlInputChange,
            label = { Text(stringResource(R.string.avatar_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
