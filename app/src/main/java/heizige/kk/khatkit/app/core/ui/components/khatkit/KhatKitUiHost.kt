package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeAlertDialog
import heizige.kk.kedge.overlays.KedgeDialog
import heizige.kk.kedge.overlays.KedgeProgressIndicator
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

            is UiRequest.Confirm -> KedgeAlertDialog(
                show = true,
                onDismissRequest = { provider.answerConfirm(false) },
                title = current.title,
                text = current.message,
                confirmText = stringResource(
                    if (current.danger) R.string.khatkit_confirm_danger else R.string.khatkit_confirm
                ),
                onConfirm = { provider.answerConfirm(true) },
                dismissText = stringResource(R.string.khatkit_action_cancel),
                onDismiss = { provider.answerConfirm(false) },
            )

            is UiRequest.Show -> ShowCardDialog(current.card) { provider.dismissUi() }

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
private fun ShowCardDialog(card: Map<String, Any?>, onDismiss: () -> Unit) {
    val title = card["title"]?.toString()?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.khatkit_show_result_title)
    val content = (card["markdown"] ?: card["text"] ?: card["content"])?.toString()

    KedgeDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
    ) {
        if (content != null) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MarkdownBlock(content)
            }
        } else {
            Text(card.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            KedgeTextButton(onClick = onDismiss) { Text(stringResource(R.string.khatkit_close)) }
        }
    }
}
