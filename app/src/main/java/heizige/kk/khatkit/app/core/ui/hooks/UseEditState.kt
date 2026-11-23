package heizige.kk.khatkit.app.core.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Composable
fun <T : Any> useEditState(onUpdate: (T) -> Unit): EditState<T> {
    val latestUpdate by rememberUpdatedState(onUpdate)
    return remember { EditState { latestUpdate(it) } }
}

internal class EditDraft<T : Any>(initial: T) {
    var value by mutableStateOf(initial)
}

class EditState<T : Any> internal constructor(private val onUpdate: (T) -> Unit) {
    internal var draft by mutableStateOf<EditDraft<T>?>(null)
        private set

    val isEditing: Boolean get() = draft != null
    var currentState: T?
        get() = draft?.value
        set(value) {
            if (value == null) dismiss() else draft?.value = value
        }

    fun open(initialState: T) { draft = EditDraft(initialState) }
    fun dismiss() { draft = null }
    fun confirm() {
        val value = currentState ?: return
        dismiss()
        onUpdate(value)
    }

    internal fun update(session: EditDraft<T>, value: T) {
        if (draft === session) session.value = value
    }
}

/** Compose inside the native overlay: retain its draft until the exit finishes, not for the page lifetime. */
@Composable
fun <T : Any> EditState<T>.EditStateContent(content: @Composable (T, (T) -> Unit) -> Unit) {
    val active = draft
    var last by remember(this) { mutableStateOf(active) }
    if (active != null && active !== last) SideEffect { last = active }
    val displayed = active ?: last ?: return
    // A new opening (even for the same value) must not inherit field state or a pager from the old one.
    key(displayed) { content(displayed.value) { update(displayed, it) } }
}
