/*
 * 选择器网格里的勾选标记（对应 ImageToolbox `core/ui/widget/buttons/MediaCheckBox`，
 * Apache-2.0, T8RIN）。
 *
 * 未选中 = 空心圆；选中且处于多选 = 填充圆 + 顺序序号；选中但只有一张 = 实心对勾圆。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun MediaCheckBox(
    modifier: Modifier = Modifier,
    isChecked: Boolean,
    selectionIndex: Int = -1,
    onCheck: (() -> Unit)? = null,
    checkedIcon: ImageVector = Icons.Filled.CheckCircle,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedColor: Color = MaterialTheme.colorScheme.onSurface,
    addContainer: Boolean = false,
) {
    val image = if (isChecked) checkedIcon else Icons.Outlined.Circle
    val color by animateColorAsState(
        targetValue = if (isChecked) checkedColor else uncheckedColor,
        label = "mediaCheckBoxColor",
    )

    if (onCheck != null) {
        KedgeIconButton(
            onClick = onCheck,
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (addContainer) suggestContainerColorBy(color) else Color.Transparent,
            ),
        ) {
            AnimatedContent(
                targetState = image,
                transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
                label = "mediaCheckBoxIcon",
            ) { icon ->
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
        }
    } else {
        AnimatedContent(
            targetState = Triple(isChecked, image, selectionIndex),
            transitionSpec = {
                if (initialState.third >= 0 && targetState.third >= 0) {
                    fadeIn() + scaleIn(initialScale = 0.85f) togetherWith
                        fadeOut() + scaleOut(targetScale = 0.85f)
                } else {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                }
            },
            label = "mediaCheckBox",
        ) { (checked, icon, index) ->
            if (index >= 0 && checked) {
                Box(
                    modifier = modifier
                        .size(24.dp)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = contentColorFor(color),
                        style = KedgeTextStyles.footnoteSmall().copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    modifier = modifier,
                    contentDescription = null,
                    tint = color,
                )
            }
        }
    }
}
