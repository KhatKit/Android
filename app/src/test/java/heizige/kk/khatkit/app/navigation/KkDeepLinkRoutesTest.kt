package heizige.kk.khatkit.app.navigation

import heizige.kk.khatkit.app.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 深链 / Intent 入口抽样：2 条主样本 + 现有视图 URI 的「无页面」偏差。 */
class KkDeepLinkRoutesTest {

    @Test
    fun translateActionMapsToTranslatorRoute() {
        assertEquals(
            "translator",
            KkDeepLinkRoutes.routeOfIntentAction(KkDeepLinkRoutes.ACTION_TRANSLATE),
        )
        assertEquals(
            Screen.Translator,
            ScreenRoutes.screenOf(KkDeepLinkRoutes.routeOfIntentAction(KkDeepLinkRoutes.ACTION_TRANSLATE)!!),
        )
    }

    @Test
    fun conversationIdExtraFallsBackToChatRoute() {
        assertEquals(
            ScreenRoutes.routeOf(Screen.Chat("conv-1")),
            KkDeepLinkRoutes.routeOfIntentAction(action = "android.intent.action.MAIN", conversationId = "conv-1"),
        )
        assertEquals(
            Screen.Chat("conv-1"),
            ScreenRoutes.screenOf(
                KkDeepLinkRoutes.routeOfIntentAction(action = null, conversationId = "conv-1")!!,
            ),
        )
    }

    @Test
    fun shareActionsKeepTheirExtras() {
        assertEquals(
            ScreenRoutes.routeOf(Screen.ShareHandler(text = "hello", streamUri = "content://x/1")),
            KkDeepLinkRoutes.routeOfIntentAction(
                action = KkDeepLinkRoutes.ACTION_SEND,
                text = "hello",
                streamUri = "content://x/1",
            ),
        )
        assertEquals(
            ScreenRoutes.routeOf(Screen.ShareHandler(text = "picked")),
            KkDeepLinkRoutes.routeOfIntentAction(KkDeepLinkRoutes.ACTION_PROCESS_TEXT, text = "picked"),
        )
    }

    @Test
    fun shortcutViewUriHasNoPage() {
        // 现状：rikkahub://shortcut 拉起相机（ShortcutHandlerActivity），不压导航栈。
        assertNull(KkDeepLinkRoutes.routeOfViewUri(KkDeepLinkRoutes.VIEW_URI_SHORTCUT))
        assertNull(KkDeepLinkRoutes.routeOfViewUri("rikkahub://other"))
        // IMAGE_GEN action 在 Manifest 里登记了，但 RouteActivity.handleIntent 没有分支，保持现状。
        assertNull(KkDeepLinkRoutes.routeOfIntentAction(KkDeepLinkRoutes.ACTION_IMAGE_GEN))
    }
}
