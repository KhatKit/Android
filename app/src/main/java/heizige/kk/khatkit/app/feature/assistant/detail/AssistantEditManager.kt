package heizige.kk.khatkit.app.feature.assistant.detail

import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主线程上的编辑状态；磁盘回写中的旧快照不覆盖尚未保存完的输入。 */
internal class AssistantEditManager(initial: Assistant) {
    private val current = MutableStateFlow(initial)
    val state = current.asStateFlow()
    private var pendingWrites = 0

    fun beginEdit(assistant: Assistant) {
        require(assistant.id == current.value.id)
        pendingWrites++
        current.value = assistant
    }

    fun acceptStored(assistant: Assistant?) {
        if (pendingWrites == 0 && assistant != null) current.value = assistant
    }

    fun finishEdit(stored: Assistant?) {
        check(pendingWrites > 0)
        pendingWrites--
        acceptStored(stored)
    }
}
