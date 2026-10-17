package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.kedge.theme.KedgeTextStyles

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
        val cards = listCardStyle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            PermissionRow(
                label = "Root 提权",
                granted = rootEnabled,
                grantedText = "已启用",
                deniedText = "未启用",
                shape = cards.indexedShape(0, 5),
            )
            PermissionRow(
                label = "Shizuku",
                granted = shizukuGranted,
                grantedText = "已授权",
                deniedText = if (shizukuAvailable) "未授权" else "不可用",
                shape = cards.indexedShape(1, 5),
            )
            PermissionRow(
                label = "无障碍服务",
                granted = accessibilityEnabled,
                grantedText = "已开启",
                deniedText = "未开启",
                shape = cards.indexedShape(2, 5),
            )
            PermissionRow(
                label = "所有文件访问",
                granted = allFilesGranted,
                grantedText = "已授予",
                deniedText = "未授予",
                shape = cards.indexedShape(3, 5),
            )
            PermissionRow(
                label = "脚本执行",
                granted = true,
                grantedText = "可用",
                deniedText = "不可用",
                shape = cards.indexedShape(4, 5),
            )

            // 权限清单与「放手模式」是两组。行现在自带卡片底，原来那条分隔线会悬在
            // 两张卡中间，改成组间留白。
            Spacer(modifier = Modifier.height(8.dp))

            KedgeOptionItem(
                onClick = null,
                shape = cards.indexedShape(0, 1),
                titleContent = {
                    Text(
                        text = "放手模式",
                        style = KedgeTextStyles.title(),
                    )
                },
                supportingContent = {
                    Text(
                        text = "高权限 / 高风险卡片不再逐条确认，全部交给 AI 执行",
                        style = KedgeTextStyles.footnoteSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = handsOff,
                        onCheckedChange = {
                            handsOff = it
                            provider.handsOffMode = it
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    shape: Shape,
) {
    KedgeOptionItem(
        onClick = null,
        shape = shape,
        titleContent = {
            Text(
                text = label,
                style = KedgeTextStyles.body(),
            )
        },
        trailingContent = {
            Text(
                text = if (granted) grantedText else deniedText,
                style = KedgeTextStyles.body(),
                color = if (granted) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
    )
}
