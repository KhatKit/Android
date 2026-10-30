package heizige.kk.khatkit.app.navigation

import heizige.kk.khatkit.app.Screen

/**
 * 深链 / Intent 入口 → 路由。**逐条照抄 `RouteActivity.handleIntent` 现有映射**（U7a-2 接线时
 * 用它替换同一个 when，保证行为等价），不新增任何 scheme。
 *
 * 现有入口一共四条：
 * - `heizige.kk.khatkit.app.action.TRANSLATE` → [Screen.Translator]；
 * - `android.intent.action.SEND` → [Screen.ShareHandler]（EXTRA_TEXT / EXTRA_STREAM）；
 * - `android.intent.action.PROCESS_TEXT` → [Screen.ShareHandler]（EXTRA_PROCESS_TEXT）；
 * - 其他 action 且带 `conversationId` extra → [Screen.Chat]。
 *
 * 唯一注册过的 VIEW URI `rikkahub://shortcut`（`ShortcutHandlerActivity`）拉起的是相机，
 * **没有页面**，所以这里刻意不映射（[routeOfViewUri] 返回 null，与现状一致）。
 *
 * 纯函数：不解析 Android [android.content.Intent]，参数由调用方从 Intent 取出来再传，
 * 这样 JVM 单测能直接覆盖全部映射。
 */
object KkDeepLinkRoutes {

    const val ACTION_TRANSLATE: String = "heizige.kk.khatkit.app.action.TRANSLATE"
    const val ACTION_IMAGE_GEN: String = "heizige.kk.khatkit.app.action.IMAGE_GEN"
    const val ACTION_SEND: String = "android.intent.action.SEND"
    const val ACTION_PROCESS_TEXT: String = "android.intent.action.PROCESS_TEXT"
    const val EXTRA_CONVERSATION_ID: String = "conversationId"
    const val VIEW_URI_SHORTCUT: String = "rikkahub://shortcut"

    /**
     * 按 [android.content.Intent] 的 action + extras 得到目标路由，语义与
     * `RouteActivity.handleIntent` 完全一致（含「其他 action 时看 conversationId」的兜底）。
     *
     * @param text ACTION_SEND 的 EXTRA_TEXT / ACTION_PROCESS_TEXT 的 EXTRA_PROCESS_TEXT
     * @param streamUri ACTION_SEND 的 EXTRA_STREAM
     */
    fun routeOfIntentAction(
        action: String?,
        conversationId: String? = null,
        text: String? = null,
        streamUri: String? = null,
    ): String? = when (action) {
        ACTION_TRANSLATE -> ScreenRoutes.routeOf(Screen.Translator)
        ACTION_SEND -> ScreenRoutes.routeOf(
            Screen.ShareHandler(text = text.orEmpty(), streamUri = streamUri),
        )
        ACTION_PROCESS_TEXT -> ScreenRoutes.routeOf(Screen.ShareHandler(text = text.orEmpty()))
        else -> conversationId?.let { ScreenRoutes.routeOf(Screen.Chat(it)) }
    }

    /**
     * VIEW URI → 路由。`rikkahub://shortcut` 是相机快捷方式，没有对应页面，
     * 保持现状返回 null；未知 URI 同样返回 null。
     */
    fun routeOfViewUri(uri: String?): String? = when (uri) {
        VIEW_URI_SHORTCUT -> null
        else -> null
    }
}
