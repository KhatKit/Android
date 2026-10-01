package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import heizige.kk.kedge.overlays.KedgePrimaryBottomSheet
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khromia.components.PrimaryBottomSheet as KhromiaPrimaryBottomSheet

/**
 * 项目统一的底部弹层，**按风格分流**，签名对齐 Khromia 的 `PrimaryBottomSheet`。
 *
 * - Miuix：[KedgePrimaryBottomSheet]（Miuix WindowBottomSheet + KedgeButton 按钮行）
 * - MD3Exp：Khromia 原组件，行为与视觉不变
 *
 * 为什么不改 Khromia：Khromia 不依赖 Kedge，而 Kedge 已依赖 Khromia —— 在 Khromia 里
 * 引入 Kedge 会成环。所以分流放在这里，调用点只需把 import 从
 * `heizige.kk.khromia.components.PrimaryBottomSheet` 换成本文件的。
 *
 * @param dismissable 是否允许点击遮罩/返回手势关闭；[KhromiaPrimaryBottomSheet] 用
 *   `dismissible`，Kedge 版暂不支持，忽略该参数。
 * @param scrollable 内容是否可滚动；Kedge 版靠内容自身滚动，忽略该参数。
 */
@Composable
fun PrimaryBottomSheet(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    dismissible: Boolean = true,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> KedgePrimaryBottomSheet(
            visible = visible,
            title = title,
            imageVector = imageVector,
            confirmText = confirmText,
            onConfirm = onConfirm,
            dismissText = dismissText,
            onDismiss = onDismiss,
            modifier = modifier,
        ) {
            content(onDismiss)
        }

        // Khromia 有两个重载：painter 版和 imageVector 版，二选一必填，
        // 这里按实际给了哪个委派给对应重载。
        KedgeStyle.MD3Exp -> if (painter != null) {
            KhromiaPrimaryBottomSheet(
                modifier = modifier,
                visible = visible,
                title = title,
                painter = painter,
                dismissText = dismissText,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                scrollable = scrollable,
                dismissible = dismissible,
                content = content,
            )
        } else {
            KhromiaPrimaryBottomSheet(
                modifier = modifier,
                visible = visible,
                title = title,
                imageVector = requireNotNull(imageVector) {
                    "PrimaryBottomSheet 需要 painter 或 imageVector 之一"
                },
                dismissText = dismissText,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                scrollable = scrollable,
                dismissible = dismissible,
                content = content,
            )
        }
    }
}