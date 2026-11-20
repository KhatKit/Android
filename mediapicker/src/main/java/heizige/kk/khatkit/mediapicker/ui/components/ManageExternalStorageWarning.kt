/* Copyright ImageToolbox contributors (Apache-2.0); adapted for KhatKit. */
package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.mediapicker.R
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.LocalContentColor

@Composable
internal fun ManageExternalStorageWarning(onRequestManagePermission: () -> Unit) {
    MochiListSection(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        footer = stringResource(R.string.media_picker_manage_storage_extra_types_sub),
    ) {
        MochiListRow(
            title = stringResource(R.string.media_picker_manage_storage_extra_types),
            trailing = {
                MochiButton(
                    onClick = onRequestManagePermission,
                ) { Text(stringResource(R.string.media_picker_request), color = LocalContentColor.current) }
            },
        )
    }
}
