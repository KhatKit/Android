package heizige.kk.khatkit.uikit.mochi

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Page-local title state, also available to native scrolling content. */
val LocalMochiTitleScroll = staticCompositionLocalOf<MochiTitleScroll?> { null }

@Composable
fun rememberMochiTitleScroll(): MochiTitleScroll {
    val scope = rememberCoroutineScope()
    return remember(scope) { MochiTitleScroll(scope) }
}

@Composable
fun Modifier.mochiTitleScroll(state: MochiTitleScroll): Modifier {
    val gesture = rememberScrollableState(state::consumeDrag)
    return nestedScroll(state).scrollable(
        state = gesture,
        orientation = Orientation.Vertical,
        enabled = state.hasTitle,
        overscrollEffect = null,
    )
}

class MochiTitleScroll internal constructor(private val scope: CoroutineScope) : NestedScrollConnection {
    private val collapsedFraction = mutableFloatStateOf(0f)
    private val titleHeight = mutableFloatStateOf(0f)
    private var snapJob: Job? = null

    val progress: Float get() = collapsedFraction.floatValue
    internal val hasTitle: Boolean get() = titleHeight.floatValue > 0f

    internal fun updateHeight(height: Int) {
        titleHeight.floatValue = height.toFloat()
    }

    internal fun consumeDrag(deltaY: Float): Float {
        val heightPx = titleHeight.floatValue
        if (heightPx <= 0f || deltaY == 0f) return 0f
        val previous = progress
        val next = (previous - deltaY / heightPx).coerceIn(0f, 1f)
        if (next == previous) return 0f
        snapJob?.cancel()
        collapsedFraction.floatValue = next
        return ((previous - next) * heightPx).coerceIn(minOf(deltaY, 0f), maxOf(deltaY, 0f))
    }

    // Upward drag collapses the title first. Downward drag expands it at the top of the body.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, consumeDrag(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f) Offset(0f, consumeDrag(available.y)) else Offset.Zero

    fun onNativeDrag(deltaY: Float, canScrollUp: Boolean) {
        if (deltaY < 0f || !canScrollUp) consumeDrag(deltaY)
    }

    /** Finish a drag from a Compose or native scroll container. */
    fun settle() {
        val current = progress
        if (current > 0f && current < 1f) {
            snapJob?.cancel()
            snapJob = scope.launch {
                animate(current, if (current >= 0.5f) 1f else 0f) { value, _ ->
                    collapsedFraction.floatValue = value.coerceIn(0f, 1f)
                }
            }
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }
}
