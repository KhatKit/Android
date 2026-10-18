package heizige.kk.khatkit.app.feature.automation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.components.ToastDefaults
import heizige.kk.khromia.layout.FullscreenPopup
import heizige.kk.khatkit.app.feature.automation.data.AutomationBus
import kotlinx.coroutines.delay

/**
 * 应用前台的自动化提示；点击只隐藏提示，不取消脚本。
 *
 * 内容挂到 Activity decorView（[FullscreenPopup]），仅包裹内容。
 * 与软件外悬浮看板共用 [AutomationStatusBoard]，定位留白不参与点击。
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
    LaunchedEffect(status, approval) {
        val completed = status
        if (completed?.finished == true && approval == null) {
            delay(3_000L)
            if (AutomationBus.status.value === completed && AutomationBus.pendingApproval.value == null) {
                AutomationBus.clear()
            }
        }
    }
    if (status == null && approval == null) return

    FullscreenPopup(passThroughTouches = true) {
        // 仅内容可点击，定位用的底部留白触摸穿透到应用。
        Box(Modifier.navigationBarsPadding().padding(
            bottom = ToastDefaults.screenSizeMin() * ToastDefaults.BottomScreenFraction,
        )) {
            AutomationStatusBoard(
                status = status,
                approval = approval,
                visible = true,
                hidden = hidden,
                onApprove = { AutomationBus.approve() },
                onDeny = { AutomationBus.deny() },
                onRemember = { AutomationBus.approveAndRemember() },
                onDismiss = { AutomationBus.hideOverlayTemporarily() },
            )
        }
    }
}
