package heizige.kk.khatkit.app.core.ui.components.ui

/**
 * ⚠️ 临时编译垫片，**用完立刻删**。
 *
 * 另一会话的 `feature/tasks/CardRunsPage.kt`（未跟踪的新文件）写着
 * `import heizige.kk.khatkit.app.core.ui.components.ui.Toast`，但那个转发对象还没落地，
 * 整个 `:app` 编译不过，挡住了本次 UI 改动的真机验证。这里先补一个同签名转发，
 * 不碰对方的文件；验证完删除。
 */
object Toast {
    fun show(message: String, isError: Boolean = false) =
        heizige.kk.khromia.helper.Toast.show(message, isError)
}
