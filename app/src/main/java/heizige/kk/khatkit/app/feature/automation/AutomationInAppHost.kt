package heizige.kk.khatkit.app.feature.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.theme.KedgeTextStyles

/** 应用前台的自动化提示；点击只隐藏提示，不取消脚本。 */
@Composable
fun AutomationInAppHost() {
    val status by AutomationBus.status.collectAsStateWithLifecycle()
    val approval by AutomationBus.pendingApproval.collectAsStateWithLifecycle()
    val hidden by AutomationBus.overlayHidden.collectAsStateWithLifecycle()
    if (hidden || (status == null && approval == null)) return

    val label = approval?.title ?: status?.label ?: return
    val detail = approval?.detail.orEmpty().ifBlank { status?.detail.orEmpty() }
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { visibleState.targetState = true }
    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(300)) +
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                    ) +
                    slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        initialOffsetY = { it / 2 },
                    ),
                exit = fadeOut(tween(250)) +
                    slideOutVertically(
                        animationSpec = tween(500),
                        targetOffsetY = { it / 2 },
                    ) +
                    scaleOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                    ),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .alpha(0.96f)
                        .clickable { AutomationBus.hideOverlayTemporarily() },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (detail.isBlank()) label else "$label · $detail",
                            style = KedgeTextStyles.body(),
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            maxLines = 2,
                        )
                        if (approval != null) {
                            TextButton(onClick = { AutomationBus.approve() }) { Text("允许") }
                            TextButton(onClick = { AutomationBus.deny() }) { Text("拒绝") }
                        }
                    }
                }
            }
        }
    }
}
