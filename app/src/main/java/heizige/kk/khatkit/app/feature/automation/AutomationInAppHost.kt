package heizige.kk.khatkit.app.feature.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khromia.components.ToastCardContainer
import heizige.kk.khromia.components.ToastDefaults
import heizige.kk.khromia.layout.FullscreenPopup

/**
 * 应用前台的自动化提示；点击只隐藏提示，不取消脚本。
 *
 * 宿主与 Khromia/上游 ImageToolbox 的 toast 一致：内容挂到 Activity decorView
 * （[FullscreenPopup]），动画面板是全屏内容、卡片贴底，`slideInVertically { it / 2 }`
 * 的位移全程都留在宿主内容内，不再出现「自建 WRAP_CONTENT Popup window 把入场动画
 * 裁掉」的问题。卡片视觉（形状/透明度/尺寸/阴影/留白/文字）由
 * [ToastCardContainer] + [ToastDefaults] 提供，与软件外悬浮看板共用同一份规格。
 *
 * 与 Khromia `GlobalToastHost` 不同的是：看板是长生命周期且实时重组的（AutomationBus
 * 状态、允许/拒绝、审批超时都会改内容），而 `Toast.showCustom(alwaysShow = true)` 走的是
 * 「单槽串行队列 + 全局 dismissRevision」语义——常驻项会一直占住队列，之后任何普通 toast
 * 都排在它后面永远不显示，且全局 dismiss 会误伤他人。因此这里直接复用同一套宿主
 * （[FullscreenPopup]）而不是排队进 toast 队列，看板状态始终由 AutomationBus 实时驱动。
 */
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

    FullscreenPopup(passThroughTouches = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = ToastDefaults.enter,
                exit = ToastDefaults.exit,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ToastCardContainer(
                        modifier = Modifier.clickable { AutomationBus.hideOverlayTemporarily() },
                    ) {
                        Row(
                            modifier = Modifier.padding(ToastDefaults.ContentPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (detail.isBlank()) label else "$label · $detail",
                                style = ToastDefaults.textStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (approval != null) {
                                Spacer(Modifier.width(8.dp))
                                KedgeTextButton(onClick = { AutomationBus.approve() }) { Text("允许") }
                                KedgeTextButton(onClick = { AutomationBus.deny() }) { Text("拒绝") }
                            }
                        }
                    }
                }
            }
        }
    }
}
