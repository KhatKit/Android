package heizige.kk.khatkit.app.ui.components.message

import heizige.kk.kedge.theme.KedgeColors
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.data.model.MessageNode
import heizige.kk.khatkit.app.ui.icons.arrowBack
import heizige.kk.khatkit.app.ui.icons.arrowForward

@Composable
fun ChatMessageBranchSelector(
    node: MessageNode,
    modifier: Modifier = Modifier,
    onUpdate: (MessageNode) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (node.messages.size > 1) {
            val actionColor = KedgeColors.onSurfaceVariant

            Icon(
                imageVector = arrowBack,
                contentDescription = "Prev",
                modifier = Modifier
                    .clip(CircleShape)
                    .alpha(if (node.selectIndex == 0) 0.5f else 1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (node.selectIndex > 0) {
                                onUpdate(
                                    node.copy(
                                        selectIndex = node.selectIndex - 1
                                    )
                                )
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionColor
            )

            Text(
                text = "${node.selectIndex + 1}/${node.messages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = actionColor
            )

            Icon(
                imageVector = arrowForward,
                contentDescription = "Next",
                modifier = Modifier
                    .clip(CircleShape)
                    .alpha(if (node.selectIndex == node.messages.lastIndex) 0.5f else 1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (node.selectIndex < node.messages.lastIndex) {
                                onUpdate(
                                    node.copy(
                                        selectIndex = node.selectIndex + 1
                                    )
                                )
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionColor
            )
        }
    }
}
