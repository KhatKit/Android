package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.search.SearchOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import heizige.kk.khatkit.app.feature.settings.data.replacingSearchServices

/**
 * 搜索服务列表变动后，`Settings.searchServiceSelected` 必须继续指着**同一条服务**。
 *
 * 这个下标被 `SearchPicker` 与 `SearchTools` 直接用来取当前搜索源，所以只要它跟着列表位置
 * 漂移，用户就会在毫无提示的情况下换掉聊天里用的搜索引擎。
 */
class SearchServiceOrderTest {
    private val exa = SearchOptions.ExaOptions()
    private val zhipu = SearchOptions.ZhipuOptions()
    private val deepSeek = SearchOptions.DeepSeekOptions()

    private fun settings(vararg services: SearchOptions, selected: Int) = Settings(
        searchServices = services.toList(),
        searchServiceSelected = selected,
    )

    @Test
    fun `插入到队首后选中项仍指向同一条服务`() {
        val before = settings(exa, zhipu, selected = 1) // 选的是 zhipu

        val after = before.replacingSearchServices(listOf(deepSeek) + before.searchServices)

        assertEquals(listOf<SearchOptions>(deepSeek, exa, zhipu), after.searchServices)
        assertEquals(zhipu, after.searchServices[after.searchServiceSelected])
    }

    @Test
    fun `拖动排序后选中项仍指向同一条服务`() {
        val before = settings(exa, zhipu, deepSeek, selected = 2) // 选的是 deepSeek

        // deepSeek 拖到队首
        val moved = before.searchServices.toMutableList().apply { add(0, removeAt(2)) }
        val after = before.replacingSearchServices(moved)

        assertEquals(deepSeek, after.searchServices[after.searchServiceSelected])
        assertEquals(0, after.searchServiceSelected)
    }

    @Test
    fun `删掉别的服务时选中下标跟着前移`() {
        val before = settings(exa, zhipu, deepSeek, selected = 2) // 选的是 deepSeek

        val after = before.replacingSearchServices(before.searchServices - exa)

        assertEquals(deepSeek, after.searchServices[after.searchServiceSelected])
        assertEquals(1, after.searchServiceSelected)
    }

    @Test
    fun `删掉正在用的那条时下标退回合法范围`() {
        val before = settings(exa, zhipu, selected = 1) // 选的是 zhipu

        val after = before.replacingSearchServices(before.searchServices - zhipu)

        assertEquals(listOf<SearchOptions>(exa), after.searchServices)
        assertEquals(0, after.searchServiceSelected)
    }
}
