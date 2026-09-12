package heizige.kk.khatkit.app.ui.pages.backup.components

import heizige.kk.khatkit.app.ui.components.ui.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
            Button(
                onClick = {
                    exitProcess(0)
                }
            ) {
                Text(stringResource(R.string.backup_page_restart_app))
            }
        },
    )
}
