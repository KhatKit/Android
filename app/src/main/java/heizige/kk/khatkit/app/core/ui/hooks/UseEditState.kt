package heizige.kk.khatkit.app.core.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.feice.mochi.basic.MochiSheetState

@Composable
fun <T> useEditState(
    onUpdate: (T) -> Unit
): EditState<T> {
    var isEditing by remember { mutableStateOf(false) }
    var currentState by remember { mutableStateOf<T?>(null) }

    return object : EditState<T> {
        override var isEditing: Boolean
            get() = isEditing
            set(value) {
                isEditing = value
            }

        override var currentState: T?
            get() = currentState
            set(value) {
                currentState = value
            }

        override fun open(initialState: T) {
            isEditing = true
            currentState = initialState
        }

        override fun confirm() {
            if (currentState != null) {
                onUpdate(currentState!!)
                isEditing = false
                currentState = null
            }
        }

        override fun dismiss() {
            isEditing = false
            currentState = null
        }
    }
}

interface EditState<T> {
    var isEditing: Boolean
    var currentState: T?

    fun open(initialState: T)

    fun confirm()

    fun dismiss()
}

@Composable
fun <T> EditState<T>.EditStateContent(
    content: @Composable (value: T, updateValue: (T) -> Unit) -> Unit
) {
    if (this.isEditing) {
        this.currentState?.let {
            content(it) { newState ->
                this.currentState = newState
            }
        }
    }
}

/**
 * U7b-5：编辑面板的 state 驱动版本。
 *
 * [EditStateContent] 只在 isEditing 时组合内容 —— 这样面板没有收起动画、也拿不到 [MochiSheetState]
 * 去做页根 backdrop。这里把「最后一份待编辑对象」留成快照：isEditing 为真时 show() 并刷新快照，
 * 为假时只 hide()，面板在收起动画期间继续渲染快照内容，不会解引用空值。
 * 旧调用点（另外 3 个页面）继续用 [EditStateContent]，行为不变。
 */
@Composable
fun <T> EditState<T>.EditStateSheetHost(
    sheetState: MochiSheetState,
    content: @Composable (value: T, updateValue: (T) -> Unit) -> Unit,
) {
    var lastValue by remember { mutableStateOf(this.currentState) }
    LaunchedEffect(this.isEditing) {
        if (this.isEditing) {
            this.currentState?.let { lastValue = it }
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }
    lastValue?.let { value ->
        content(value) { newState ->
            this.currentState = newState
        }
    }
}
