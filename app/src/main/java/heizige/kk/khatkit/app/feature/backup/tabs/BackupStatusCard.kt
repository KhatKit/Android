package heizige.kk.khatkit.app.feature.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.kedge.theme.KedgeTextStyles

/**
 * 备份状态卡：S3Tab 与 WebDavTab 共用的同一份实现。
 */
@Composable
internal fun BackupStatusCard(
    title: String,
    lastBackupText: String,
    fileSummaryText: String,
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = title,
                    style = KedgeTextStyles.title()
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lastBackupText,
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSummaryText,
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
        )
    }
}
