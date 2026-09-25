package heizige.kk.khatkit.app.core.ui.components.message

import heizige.kk.kedge.theme.KedgeColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.util.copyMessageToClipboard
import heizige.kk.khatkit.app.core.ui.icons.contentCopy

@Composable
fun ChatMessageCopySheet(
    message: UIMessage,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.select_and_copy),
        imageVector = contentCopy,
        confirmText = stringResource(R.string.copy_all),
        onConfirm = {
            context.copyMessageToClipboard(message)
            onDismissRequest()
        },
        onDismiss = onDismissRequest,
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Content
            val textParts =
                message.parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }

            if (textParts.isEmpty()) {
                // No text content available
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_text_content_to_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KedgeColors.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        textParts.fastForEach { textPart ->
                            Text(
                                text = textPart.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
