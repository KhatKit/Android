/*
 * 网格里的日期粘性头，可整组勾选（对应 ImageToolbox `MediaStickyHeader`，
 * Apache-2.0, T8RIN）。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun MediaStickyHeader(
    modifier: Modifier = Modifier,
    date: String,
    isChecked: Boolean,
    onChecked: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(start = 8.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        Text(
            text = date,
            style = KedgeTextStyles.title(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer
                        .blend(MaterialTheme.colorScheme.primary, 0.1f)
                        .copy(0.5f)
                )
                .hapticsCombinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onLongClick = onChecked,
                    onClick = { onChecked?.invoke() },
                )
                .padding(vertical = 6.dp, horizontal = 12.dp),
        )

        AnimatedVisibility(
            visible = onChecked != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            MediaCheckBox(
                isChecked = isChecked,
                onCheck = { onChecked?.invoke() },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
