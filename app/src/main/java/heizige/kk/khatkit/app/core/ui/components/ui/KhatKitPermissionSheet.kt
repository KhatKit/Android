package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.khromia.components.PrimaryBottomSheet

/**
 * 权限面板：只展示各项权限的授予状态，外加一个「放手模式」开关。
 */
@Composable
fun KhatKitPermissionSheet(
    provider: KhatKitToolProvider,
    onDismiss: () -> Unit,
) {
    val rootEnabled = provider.enableRoot
    val shizukuAvailable = remember { ShizukuPermission.isAvailable() }
    val shizukuGranted = remember { shizukuAvailable && ShizukuPermission.isGranted() }
    val accessibilityEnabled = AccessibilityBridgeHolder.current() != null
    val allFilesGranted = remember { AllFilesAccess.isGranted() }
    var handsOff by remember { mutableStateOf(provider.handsOffMode) }

    PrimaryBottomSheet(
        visible = true,
        title = "权限",
        imageVector = verifiedUser,
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PermissionRow(
                label = "Root 提权",
                granted = rootEnabled,
                grantedText = "已启用",
                deniedText = "未启用",
            )
            PermissionRow(
                label = "Shizuku",
                granted = shizukuGranted,
                grantedText = "已授权",
                deniedText = if (shizukuAvailable) "未授权" else "不可用",
            )
            PermissionRow(
                label = "无障碍服务",
                granted = accessibilityEnabled,
                grantedText = "已开启",
                deniedText = "未开启",
            )
            PermissionRow(
                label = "所有文件访问",
                granted = allFilesGranted,
                grantedText = "已授予",
                deniedText = "未授予",
            )
            PermissionRow(
                label = "脚本执行",
                granted = true,
                grantedText = "可用",
                deniedText = "不可用",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "放手模式",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "高权限 / 高风险卡片不再逐条确认，全部交给 AI 执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = handsOff,
                    onCheckedChange = {
                        handsOff = it
                        provider.handsOffMode = it
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (granted) grantedText else deniedText,
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}
