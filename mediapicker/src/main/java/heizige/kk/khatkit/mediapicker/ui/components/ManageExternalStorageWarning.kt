/*
 * 缺少「所有文件访问」权限时的提示条（对应 ImageToolbox
 * `ManageExternalStorageWarning`，Apache-2.0, T8RIN）。
 *
 * 没有 MANAGE_EXTERNAL_STORAGE 时，Android/data、SD 卡根目录等位置
 * 扫描不到；提示条插在网格第一项，告诉用户可以去开。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
internal fun ManageExternalStorageWarning(
    onRequestManagePermission: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.errorContainer, RectangleShape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.media_picker_manage_storage_extra_types),
                    color = MaterialTheme.colorScheme.error,
                    style = KedgeTextStyles.body(),
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.media_picker_manage_storage_extra_types_sub),
                color = MaterialTheme.colorScheme.onSurface,
                style = KedgeTextStyles.footnote(),
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        KedgeButton(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            onClick = onRequestManagePermission,
        ) {
            Text(stringResource(R.string.media_picker_request))
        }
    }
}
