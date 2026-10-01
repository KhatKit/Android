package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.khatkit.app.R
import androidx.compose.material3.DropdownMenuItem
import heizige.kk.khatkit.app.core.ui.icons.moreVert

/**
 * 列表 item 的次要操作（复制、导出、重命名、删除等）。
 *
 * 约定：点击 item 本身执行主操作（进入详情/编辑），其余操作统一放进 [ItemActionMenu]；
 * 破坏性操作（删除）放在最后并标记 [destructive]，由调用方负责二次确认或提供撤销。
 */
@Immutable
data class ItemAction(
    val text: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Miuix 下用 OverlayListPopup 拼菜单行（KedgeDropdownItemSlot 内部已按风格分派），
 * MD3Exp 下保持 DropdownMenuItem。破坏性操作的文字与图标统一取 error 色。
 */
@Composable
fun ItemActionMenu(
    actions: List<ItemAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        KedgeIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = moreVert,
                contentDescription = stringResource(R.string.more_options),
            )
        }
        KedgeDropdownMenuSlots(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                val errorColor = MaterialTheme.colorScheme.error
                val tint = if (action.destructive) errorColor else MaterialTheme.colorScheme.onSurface
                KedgeDropdownItemSlot(
                    text = {
                        Text(
                            text = action.text,
                            color = if (action.destructive) errorColor else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = tint,
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}