package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.uikit.KhatKitForm
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.icons.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.description
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

/**
 * 卡片 ui bridge 的宿主挂载点（设计文档 7.2）。
 *
 * 卡片执行时（可能在聊天流里），脚本线程阻塞等待；这里在 Compose 主线程
 * 渲染表单/确认/进度/结果卡片，交互后把值回传。渲染层在 :khatkit-ui。
 */
@Composable
fun KhatKitUiHost() {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val request by provider.uiRequest.collectAsStateWithLifecycle()
    val progress by provider.uiProgress.collectAsStateWithLifecycle()

    KhatKitTheme(style = provider.uiStyle) {
        when (val current = request) {
            is UiRequest.Form -> KhatKitForm(
                title = current.title,
                items = current.items,
                onSubmit = { values -> provider.submitForm(values) },
                onCancel = { provider.dismissUi() },
            )

            is UiRequest.Confirm -> PrimaryBottomSheet(
                visible = true,
                title = current.title,
                imageVector = if (current.danger) warning else checkCircle,
                confirmText = stringResource(
                    if (current.danger) R.string.khatkit_confirm_danger else R.string.khatkit_confirm
                ),
                onConfirm = { provider.answerConfirm(true) },
                onDismiss = { provider.answerConfirm(false) },
                scrollable = true,
            ) { dismiss ->
                ConfirmSheetContent(
                    message = current.message,
                    onCancel = { dismiss() },
                )
            }

            is UiRequest.Show -> ShowCardSheet(current.card) { provider.dismissUi() }

            null -> Unit
        }

        progress?.let { (ratio, label) ->
            Popup(alignment = Alignment.TopCenter) {
                KedgeSurface(
                    modifier = Modifier
                        .padding(top = 48.dp)
                        .widthIn(max = 320.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        KedgeProgressIndicator(
                            progress = ratio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmSheetContent(message: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            KedgeTextButton(onClick = onCancel) {
                Text(stringResource(R.string.khatkit_action_cancel))
            }
        }
    }
}

@Composable
private fun ShowCardSheet(card: Map<String, Any?>, onDismiss: () -> Unit) {
    val title = card["title"]?.toString()?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.khatkit_show_result_title)
    val content = (card["markdown"] ?: card["text"] ?: card["content"])?.toString()

    PrimaryBottomSheet(
        visible = true,
        title = title,
        imageVector = description,
        dismissText = stringResource(R.string.khatkit_close),
        onDismiss = onDismiss,
        scrollable = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            if (content != null) {
                MarkdownBlock(content)
            } else {
                Text(card.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            }
        }
    }
}
