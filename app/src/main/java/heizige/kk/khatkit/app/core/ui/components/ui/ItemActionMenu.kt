package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem

/**
 * 列表 item 的次要操作（复制、导出、重命名、删除等）。
 *
 * 约定：点击 item 本身执行主操作（进入详情/编辑），其余操作统一放进 [ItemActionMenu]；
 * 破坏性操作（删除）放在最后并标记 [destructive]。
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
 * 更多操作菜单：**点击**触发的 MochiDropdown（U3c-fix）。
 *
 * 上一版误用 MochiContextMenu（长按菜单）且按钮是空 onClick，点击永远弹不出来；
 * 现在按钮槽交给 buttonContent，点击/展开状态由 MochiDropdown 统一管理。
 * 对外签名不变；ItemAction.enabled 在 MochiItem 里无对应能力（现有调用点都为 true）。
 */
@Composable
fun ItemActionMenu(
    actions: List<ItemAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val items: ImmutableList<MochiItem> = actions.map { action ->
        MochiItem(
            label = action.text,
            destructive = action.destructive,
            icon = { color ->
                Icon(imageVector = action.icon, contentDescription = null, tint = color)
            },
        )
    }.toImmutableList()

    Box(modifier = modifier) {
        MochiDropdown(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            items = items,
            onItemClick = { index ->
                expanded = false
                actions.getOrNull(index)?.onClick?.invoke()
            },
            onClick = { expanded = !expanded },
            buttonContent = {
                Icon(imageVector = moreVert, contentDescription = stringResource(R.string.more_options))
            },
        )
    }
}
