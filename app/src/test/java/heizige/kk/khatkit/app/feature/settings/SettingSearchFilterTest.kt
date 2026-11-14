package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.icons.search
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置搜索「能搜到最小选项」的过滤口径护栏。
 *
 * 这两条今天看起来都理所当然，但都是本次改造才成立的约定，而且退化时**不报错**：
 * - 加空格搜「气泡 透明」应该收敛到气泡不透明度，而不是把所有含「气泡」的行都捞出来；
 * - 选项级结果要带归属页标题，用户才知道这个开关在哪一页。
 *
 * 用真实中文标题（与 strings.xml 同字）构造条目，不依赖资源系统 —— 单测没有 Android 资源。
 */
class SettingSearchFilterTest {

    private fun entry(
        title: String,
        keywords: String,
        description: String = "",
        anchor: String? = null,
        pageTitle: String = "",
        destination: Screen = Screen.SettingPreferencesUIMessage,
    ) = SettingSearchEntry(
        title = title,
        description = description,
        keywords = keywords,
        icon = search as ImageVector,
        destination = destination,
        anchor = anchor,
        pageTitle = pageTitle,
    )

    private val pages = listOf(
        entry("界面", "ui appearance 界面", description = "主题、消息、字体、代码", destination = Screen.SettingPreferencesUI),
        entry("通知", "notification 通知", description = "更新提醒、消息通知", destination = Screen.SettingPreferencesNotification),
    )

    private val leaves = listOf(
        entry(
            title = "气泡不透明度",
            keywords = "bubble opacity alpha 气泡 透明度",
            anchor = "r1001",
            pageTitle = "消息显示",
        ),
        entry(
            title = "显示时间",
            keywords = "datetime time timestamp 时间 日期",
            anchor = "r1002",
            pageTitle = "消息显示",
        ),
        entry(
            title = "显示气泡",
            keywords = "bubble assistant 气泡 助手",
            anchor = "r1003",
            pageTitle = "消息显示",
        ),
    )

    @Test
    fun `empty query returns only page level entries`() {
        val result = filteredSettingResults("", pages, leaves)
        assertEquals("空关键词只给一级入口，不预先摊开所有选项", pages, result)
    }

    @Test
    fun `whitespace only query returns only page level entries`() {
        assertEquals(pages, filteredSettingResults("   ", pages, leaves))
    }

    @Test
    fun `single keyword finds the minimal option`() {
        val result = filteredSettingResults("datetime", pages, leaves)
        assertEquals(1, result.size)
        assertEquals("显示时间", result.single().title)
        assertEquals("这个结果必须带锚点，否则跳过去定位不到", "r1002", result.single().anchor)
    }

    @Test
    fun `multiple keywords are ANDed`() {
        // 「气泡」单独命中两行，「透明」只属于其中一行 → AND 后必须收敛到一行。
        val result = filteredSettingResults("气泡 透明", pages, leaves)
        assertEquals(1, result.size)
        assertEquals("气泡不透明度", result.single().title)
    }

    @Test
    fun `page level results come before leaf results`() {
        // 「通知」既是页面级入口，也出现在页面级候选里；选项级里没有任何行命中它。
        val result = filteredSettingResults("通知", pages, leaves)
        assertEquals(1, result.size)
        assertEquals("通知", result.single().title)
        assertTrue("页面级结果在前", result.indexOfFirst { it.anchor == null } < result.size)
    }

    @Test
    fun `chinese and english keywords both work`() {
        assertTrue(filteredSettingResults("时间", pages, leaves).isNotEmpty())
        assertTrue(filteredSettingResults("time", pages, leaves).isNotEmpty())
    }

    @Test
    fun `leaf result carries its owning page title`() {
        val hit = filteredSettingResults("datetime", pages, leaves).single()
        assertEquals("选项级结果的第二行是归属页，用户要知道它在哪一页", "消息显示", hit.supportingLine())
    }

    @Test
    fun `page result keeps its own description`() {
        val hit = filteredSettingResults("notification", pages, leaves).single()
        assertEquals("更新提醒、消息通知", hit.supportingLine())
    }

    @Test
    fun `unknown query returns nothing`() {
        assertTrue(filteredSettingResults("zzzz-not-a-setting", pages, leaves).isEmpty())
    }
}
