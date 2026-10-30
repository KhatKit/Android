package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiContextMenu
import ui.feice.mochi.basic.MochiItem

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
 * U3c：菜单换成 mochi-compose 的 MochiContextMenu（锚点=右上角更多按钮）。
 *
 * 对外签名不变、调用点零改动。ItemAction.enabled 在 MochiItem 里没有对应能力，
 * 现有调用点都传 enabled=true，先按全可点击渲染（已列入 MochiItem 表达不了的清单）。
 */
@Composable
fun ItemActionMenu(
    actions: List<ItemAction>,
    modifier: Modifier = Modifier,
) {
    val items: ImmutableList<MochiItem> = actions.map { action ->
        MochiItem(
            label = action.text,
            destructive = action.destructive,
            icon = { color ->
                androidx.compose.material3.Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = color,
                )
            },
        )
    }.toImmutableList()

    Box(modifier = modifier) {
        MochiContextMenu(
            items = items,
            onItemClick = { index -> actions.getOrNull(index)?.onClick?.invoke() },
        ) {
            MochiIconButton(onClick = { }) {
                androidx.compose.material3.Icon(
                    imageVector = moreVert,
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        }
    }
}
