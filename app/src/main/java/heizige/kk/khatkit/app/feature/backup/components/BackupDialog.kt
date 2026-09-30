package heizige.kk.khatkit.app.feature.backup.components

import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import kotlin.system.exitProcess

@Composable
fun BackupDialog() {
    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_page_restart_app)) },
        text = { Text(stringResource(R.string.backup_page_restart_desc)) },
        confirmButton = {
            KedgeButton(
                onClick = {
                    exitProcess(0)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.backup_page_restart_app))
            }
        },
    )
}
