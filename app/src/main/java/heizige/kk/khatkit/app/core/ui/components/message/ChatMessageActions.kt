package heizige.kk.khatkit.app.core.ui.components.message

import heizige.kk.kedge.theme.KedgeColors
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.context.LocalTTSState
import heizige.kk.khatkit.app.core.util.copyMessageToClipboard
import heizige.kk.khatkit.app.core.util.extractQuotedContentAsText
import heizige.kk.khatkit.app.core.util.removeBracketedContent
import heizige.kk.khatkit.app.core.util.toLocalString
import heizige.kk.khatkit.app.core.util.toMessageTimeString
import java.util.Locale
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.conversionPath
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.highlightAlt
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.share
import heizige.kk.khatkit.app.core.ui.icons.stopCircle
import heizige.kk.khatkit.app.core.ui.icons.sync
import heizige.kk.khatkit.app.core.ui.icons.translate
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.ui.icons.web

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val actionIconColor = KedgeColors.onSurfaceVariant

        Icon(
            imageVector = contentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        Icon(
            imageVector = sync,
            contentDescription = stringResource(R.string.regenerate),
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    if (message.role == MessageRole.USER) {
                        showRegenerateConfirm = true
                    } else {
                        onRegenerate()
                    }
                }
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) stopCircle else volumeUp,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                val text = message.toText()
                                var textToSpeak = text
                                if (settings.displaySetting.ttsOnlyReadQuoted) {
                                    textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                                }
                                if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                    textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                                }
                                tts.speak(textToSpeak)
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) actionIconColor else actionIconColor.copy(alpha = 0.38f)
            )

            // Translation button
            if (onTranslate != null) {
                Icon(
                    imageVector = translate,
                    contentDescription = stringResource(R.string.translate),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = {
                                showTranslateDialog = true
                            }
                        )
                        .padding(8.dp)
                        .size(16.dp),
                    tint = actionIconColor
                )
            }
        }

        Icon(
            imageVector = moreVert,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = {
                        onOpenActionSheet()
                    }
                )
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = KedgeColors.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }

    // Translation dialog
    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }

    // Regenerate confirmation dialog
    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.more_options),
        imageVector = moreVert,
        onDismiss = onDismissRequest,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Select and Copy
            Card(
                onClick = {
                    dismiss()
                    onSelectAndCopy()
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = highlightAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.select_and_copy),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // WebView Preview (only show if message has text content)
            val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
                .any { it.text.isNotBlank() }

            if (hasTextContent) {
                Card(
                    onClick = {
                        dismiss()
                        onWebViewPreview()
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = web,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp)
                        )
                        Text(
                            text = stringResource(R.string.render_with_webview),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            // Edit
            Card(
                onClick = {
                    dismiss()
                    onEdit()
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = edit,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Share
            Card(
                onClick = {
                    dismiss()
                    onShare()
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = share,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.share),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Create a Fork
            Card(
                onClick = {
                    dismiss()
                    onFork()
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = conversionPath,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.create_fork),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Delete
            Card(
                onClick = {
                    dismiss()
                    onDelete()
                },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = KedgeColors.errorContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = delete,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Message Info
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) {
                    Text(model.displayName)
                }
            }
        }
    }
}
