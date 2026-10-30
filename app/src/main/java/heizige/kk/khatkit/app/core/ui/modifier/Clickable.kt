package heizige.kk.khatkit.app.core.ui.modifier

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import heizige.kk.khatkit.uikit.mochi.kkPressState

/**
 * 共享的可点修饰符：按下态跟 Mochi 示例一致（轻微缩放 + 单层高光），
 * 不再只是 MD3 的水波。缩放用 [kkPressState]（Mochi 弹簧、减弱动效时直接切终态），
 * indication 仍取 [LocalIndication]（主题里换成了 Mochi 风格的浅高光，见 KhatKitTheme），
 * 所以不会出现「水波 + 缩放」叠两层互相打架。
 */
@Composable
fun Modifier.onClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .kkPressState(interaction, enabled)
        .clickable(
            onClick = onClick,
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            enabled = enabled,
        )
}
