package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** U4d：旧 Kedge 滑动删除的停靠位置，语义同 material3 SwipeToDismissBoxValue。 */
enum class KkSwipeToDismissValue { StartToEnd, EndToStart, Settled }

private fun KkSwipeToDismissValue.toMd3(): SwipeToDismissBoxValue = when (this) {
    KkSwipeToDismissValue.StartToEnd -> SwipeToDismissBoxValue.StartToEnd
    KkSwipeToDismissValue.EndToStart -> SwipeToDismissBoxValue.EndToStart
    KkSwipeToDismissValue.Settled -> SwipeToDismissBoxValue.Settled
}

private fun SwipeToDismissBoxValue.toKk(): KkSwipeToDismissValue = when (this) {
    SwipeToDismissBoxValue.StartToEnd -> KkSwipeToDismissValue.StartToEnd
    SwipeToDismissBoxValue.EndToStart -> KkSwipeToDismissValue.EndToStart
    SwipeToDismissBoxValue.Settled -> KkSwipeToDismissValue.Settled
}

/**
 * 滑动删除状态：内部包一个 material3 [androidx.compose.material3.SwipeToDismissBoxState]，
 * 由 [KkSwipeToDismissBox] 在组合时创建并回填。对外只暴露 currentValue / settledValue。
 */
@Stable
class KkSwipeToDismissState(
    initialValue: KkSwipeToDismissValue = KkSwipeToDismissValue.Settled,
    internal val positionalThreshold: (Float) -> Float = { it * 0.5f },
    internal val confirmValueChange: (KkSwipeToDismissValue) -> Boolean = { true },
) {
    internal val initial: KkSwipeToDismissValue = initialValue
    internal var inner: androidx.compose.material3.SwipeToDismissBoxState? by mutableStateOf(null)

    val currentValue: KkSwipeToDismissValue
        get() = inner?.currentValue?.toKk() ?: initial

    val settledValue: KkSwipeToDismissValue
        get() = inner?.settledValue?.toKk() ?: initial

    suspend fun reset() {
        inner?.reset()
    }

    suspend fun dismiss(direction: KkSwipeToDismissValue) {
        inner?.dismiss(direction.toMd3())
    }
}

@Composable
fun rememberKkSwipeToDismissState(
    initialValue: KkSwipeToDismissValue = KkSwipeToDismissValue.Settled,
    confirmValueChange: (KkSwipeToDismissValue) -> Boolean = { true },
    positionalThreshold: (totalDistance: Float) -> Float = KkSwipeToDismissDefaults.positionalThreshold,
): KkSwipeToDismissState = remember {
    KkSwipeToDismissState(initialValue, positionalThreshold, confirmValueChange)
}

object KkSwipeToDismissDefaults {
    val positionalThreshold: (totalDistance: Float) -> Float = { it * 0.5f }

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.errorContainer

    val onContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.onErrorContainer
}

@Composable
fun KkSwipeToDismissBox(
    state: KkSwipeToDismissState,
    backgroundContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    content: @Composable () -> Unit,
) {
    val inner = rememberSwipeToDismissBoxState(
        initialValue = state.initial.toMd3(),
        confirmValueChange = { state.confirmValueChange(it.toKk()) },
        positionalThreshold = state.positionalThreshold,
    )
    state.inner = inner
    SwipeToDismissBox(
        state = inner,
        modifier = modifier,
        enableDismissFromStartToEnd = enableDismissFromStartToEnd,
        enableDismissFromEndToStart = enableDismissFromEndToStart,
        backgroundContent = { Box { backgroundContent() } },
        content = { Box { content() } },
    )
}
