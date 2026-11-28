package heizige.kk.khatkit.app.feature.chat.data

import kotlin.uuid.Uuid

/** 悬浮窗要显示的会话，expanded 为 true 时展开成面板。 */
data class FloatingChatWindow(val conversationId: Uuid, val expanded: Boolean)
