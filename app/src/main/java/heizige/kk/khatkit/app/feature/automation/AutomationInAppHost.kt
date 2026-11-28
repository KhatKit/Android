package heizige.kk.khatkit.app.feature.automation

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.uikit.mochi.KkToastDefaults
import heizige.kk.khatkit.uikit.mochi.screenSizeMin
import heizige.kk.khatkit.app.feature.automation.helper.AutomationManager
import kotlinx.coroutines.delay

/** Live status/approval board; unlike a one-shot toast it can receive a new request while visible. */
@Composable
fun AutomationInAppHost() {
    val status by AutomationManager.status.collectAsStateWithLifecycle()
    val approval by AutomationManager.pendingApproval.collectAsStateWithLifecycle()
    val hidden by AutomationManager.overlayHidden.collectAsStateWithLifecycle()
    LaunchedEffect(status, approval) {
        val completed = status
        if (completed?.finished == true && approval == null) {
            delay(3_000L)
            if (AutomationManager.status.value === completed && AutomationManager.pendingApproval.value == null) {
                AutomationManager.clear()
            }
        }
    }
    if (status == null && approval == null) return

    InAppBoardLayer(approval?.id) {
        AutomationStatusBoard(
            status = status, approval = approval, visible = true, hidden = hidden,
            onApprove = AutomationManager::approve, onDeny = AutomationManager::deny,
            onRemember = AutomationManager::approveAndRemember,
            onDismiss = { AutomationManager.hideOverlayTemporarily() },
        )
    }
}

/** Same decor as Mochi card dialogs, but only the visible pill participates in Android hit testing. */
@Composable
private fun InAppBoardLayer(requestId: String?, content: @Composable () -> Unit) {
    val decor = LocalView.current.rootView as FrameLayout
    val parent = rememberCompositionContext()
    val latest by rememberUpdatedState(content)
    val board = remember(decor, parent) {
        ComposeView(decor.context).apply {
            id = View.generateViewId()
            setParentCompositionContext(parent)
            setContent { latest() }
        }
    }
    DisposableEffect(decor, board) {
        decor.addView(board, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        onDispose {
            decor.removeView(board)
            board.disposeComposition()
        }
    }
    val density = LocalDensity.current
    val spacing = screenSizeMin * KkToastDefaults.BottomScreenFraction
    val bottom = with(density) { spacing.roundToPx() } + WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(board, bottom) {
        val params = board.layoutParams as FrameLayout.LayoutParams
        params.bottomMargin = bottom
        board.layoutParams = params
    }
    LaunchedEffect(board, requestId) { if (requestId != null) board.bringToFront() }
}
