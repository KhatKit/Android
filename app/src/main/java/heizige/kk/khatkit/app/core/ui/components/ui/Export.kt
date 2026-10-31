package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.KkCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.export.ExporterState
import heizige.kk.khatkit.app.core.ui.icons.description
import heizige.kk.khatkit.app.core.ui.icons.share
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

@Composable
fun <T> ExportDialog(
    exporter: ExporterState<T>,
    title: String? = null,
    onDismiss: () -> Unit
) {
    // DG1：material3 AlertDialog → MochiDialog。两个导出口卡片作为内容，取消是 Cancel 胶囊按钮。
    // 可见性仍由调用方 if 门控（PromptPage），本批不动调用方签名。
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = title ?: stringResource(R.string.export_title),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.export_cancel),
                role = MochiDialogActionRole.Cancel,
            ),
        ),
        dismissOnOutsideTap = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KkCard(
                onClick = {
                    exporter.exportToFile()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(description, null)
                    Column {
                        Text(
                            text = stringResource(R.string.export_to_file),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.export_to_file_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            KkCard(
                onClick = {
                    exporter.exportAndShare()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(share, null)
                    Column {
                        Text(
                            text = stringResource(R.string.export_share),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.export_share_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
