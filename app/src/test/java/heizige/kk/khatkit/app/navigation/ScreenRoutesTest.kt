package heizige.kk.khatkit.app.navigation

import heizige.kk.khatkit.app.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScreenRoutes] 的护栏：
 * - 67 个 [Screen] 子类全都有路由，且全表唯一（新增子类忘了映射时，main 里的 `when` 先编不过；
 *   这里再从「名字集合 == routeNames」和「逐个可逆」两头兜住）；
 * - `routeOf` / `screenOf` 互为逆运算，route 字符串本身稳定（重复编码不漂）。
 */
class ScreenRoutesTest {

    /** 每个 [Screen] 子类一个代表实例。 */
    private val samples: List<Screen> = listOf(
        Screen.Greeting,
        Screen.Chat(id = "c-1", text = "hi", files = listOf("/sdcard/a.png"), nodeId = "n-1", messageId = "m-1"),
        Screen.ShareHandler(text = "hello", streamUri = "content://x/1"),
        Screen.RoleChatPanel(roleId = "role-1", conversationId = "conv-1"),
        Screen.TraceAudit,
        Screen.Assistant,
        Screen.AssistantDetail(id = "a-1"),
        Screen.AssistantBasic(id = "a-1"),
        Screen.AssistantPrompt(id = "a-1"),
        Screen.AssistantMemory(id = "a-1"),
        Screen.AssistantRequest(id = "a-1"),
        Screen.AssistantMcp(id = "a-1"),
        Screen.AssistantLocalTool(id = "a-1"),
        Screen.AssistantInjections(id = "a-1"),
        Screen.Translator,
        Screen.Setting,
        Screen.Backup,
        Screen.ImageGen,
        Screen.WebView(url = "https://example.com/a?b=c&d=e", contentId = "web-1"),
        Screen.SettingPreferencesTheme,
        Screen.SettingThemeColors,
        Screen.SettingThemeMaterials,
        Screen.SettingThemeListCards,
        Screen.SettingPreferencesNotification,
        Screen.SettingPreferencesGeneral,
        Screen.SettingPreferencesUI,
        Screen.SettingPreferencesUIMessage,
        Screen.SettingPreferencesUIFont,
        Screen.SettingPreferencesUICode,
        Screen.SettingPermissions,
        Screen.SettingPreferencesNetwork,
        Screen.SettingProvider,
        Screen.SettingProviderDetail(providerId = "p-1"),
        Screen.SettingModels,
        Screen.SettingAbout,
        Screen.SettingSearch,
        Screen.SettingSearchDetail(serviceId = "s-1"),
        Screen.SettingSpeech,
        Screen.SettingMcp,
        Screen.ExploreMarket,
        Screen.ToolCatalogue,
        Screen.ExploreCards(initialQuery = "moon"),
        Screen.ExploreLocal,
        Screen.ExploreCardDetail(name = "moon-card"),
        Screen.CardRuns,
        Screen.CardWidget(name = "daily_note", title = "Daily & note/1"),
        Screen.SettingDonate,
        Screen.SettingFiles,
        Screen.SettingMemory,
        Screen.SettingWeb,
        Screen.SettingTriggers,
        Screen.CreateWorkflow(workflowId = "w-1"),
        Screen.SettingPackage,
        Screen.Debug,
        Screen.Log,
        Screen.Extensions,
        Screen.Plugin("remote-ssh"),
        Screen.QuickMessages,
        Screen.Prompts,
        Screen.Skills,
        Screen.Workspaces,
        Screen.WorkspaceDetail(id = "w-1"),
        Screen.WorkspaceTerminal(id = "w-1"),
        Screen.WorkspaceFileEditor(id = "w-1", area = "workspace", path = "src/a.kt"),
        Screen.SkillDetail(skillName = "skill-1"),
        Screen.MessageSearch,
        Screen.DownloadCenter,
        Screen.Stats,
        Screen.AboutTerminal,
    )

    @Test
    fun everyScreenHasARouteAndRoundTrips() {
        assertEquals("Screen 子类代表实例数量", 69, samples.size)
        for (screen in samples) {
            val route = ScreenRoutes.routeOf(screen)
            assertEquals("route 解析回原型: $route", screen, ScreenRoutes.screenOf(route))
            assertEquals("route 稳定: $route", route, ScreenRoutes.routeOf(ScreenRoutes.screenOf(route)!!))
        }
    }

    @Test
    fun routesAreUniqueAndNamesMatchTheTable() {
        val routes = samples.map { ScreenRoutes.routeOf(it) }
        assertEquals("route 两两不同", routes.size, routes.toSet().size)
        assertEquals("名字集合与 routeNames 一致", ScreenRoutes.routeNames, routes.map { it.substringBefore('?') }.toSet())
        assertEquals("routeNames 条数", 69, ScreenRoutes.routeNames.size)
    }

    @Test
    fun routeCharsetIsEncoded() {
        val allowed = Regex("^[A-Za-z0-9?=&,._~%\\-]+$")
        for (screen in samples) {
            val route = ScreenRoutes.routeOf(screen)
            assertTrue("route 只含编码后字符: $route", allowed.matches(route))
            assertTrue("route 不含裸空格: $route", ' ' !in route)
        }
    }

    @Test
    fun specialCharactersSurviveTheRoundTrip() {
        val screen = Screen.Chat(
            id = "id /?#&=+% \"quoted\" 中文",
            text = "a&b=c?d#e%f",
            files = listOf("/a b/c?.png", "content://x/y%20z"),
        )
        val route = ScreenRoutes.routeOf(screen)
        assertEquals(screen, ScreenRoutes.screenOf(route))
        assertEquals(route, ScreenRoutes.routeOf(ScreenRoutes.screenOf(route)!!))
    }

    @Test
    fun nullAndEmptyParametersStayDistinct() {
        val nullText = Screen.Chat(id = "c-1")
        val emptyText = Screen.Chat(id = "c-1", text = "")
        assertNotEquals(ScreenRoutes.routeOf(nullText), ScreenRoutes.routeOf(emptyText))
        assertEquals(nullText, ScreenRoutes.screenOf(ScreenRoutes.routeOf(nullText)))
        assertEquals(emptyText, ScreenRoutes.screenOf(ScreenRoutes.routeOf(emptyText)))

        assertEquals(Screen.Chat(id = "c-1"), ScreenRoutes.screenOf("chat?id=c-1"))
        assertEquals(Screen.CreateWorkflow(), ScreenRoutes.screenOf("create-workflow"))
        assertEquals(Screen.CreateWorkflow("w-1"), ScreenRoutes.screenOf("create-workflow?workflowId=w-1"))
    }

    @Test
    fun unknownOrBrokenRoutesReturnNull() {
        assertNull(ScreenRoutes.screenOf(""))
        assertNull(ScreenRoutes.screenOf("no-such-screen"))
        assertNull(ScreenRoutes.screenOf("chat"))
        assertNull(ScreenRoutes.screenOf("chat?id=%ZZ"))
        assertNull(ScreenRoutes.screenOf("web-view?url=x"))
        assertNull(ScreenRoutes.screenOf("chat?id=c-1&id=c-2"))
    }
}
