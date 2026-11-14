package heizige.kk.khatkit.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 设置搜索的选项级索引（SettingLeafIndex）与各设置页的行锚点必须**两边对齐**。
 *
 * ## 为什么需要这条护栏
 *
 * 「让设置搜索能搜到最小选项」是两份声明合作的结果：
 * - `SettingLeafIndex.kt` 的登记表决定**搜得到什么**；
 * - 各个 `Setting*.kt` 里的 `anchor = settingAnchor(R.string.xxx)` 决定**跳过去能不能定位到**。
 *
 * 两边只改一边不会编译失败（常量各自合法），运行时也不会崩 —— 结果只是「搜到了、点进去
 * 却停在页首没有高亮」，是最难被自测发现的那种回归。所以这里在源码层面双向核对：
 *
 * 1. 登记表里每个条目，仓库里必须至少有一处 `settingAnchor(R.string.<同一个常量>)`；
 * 2. 反过来，页面里出现的每个 `settingAnchor(R.string.x)` 也必须在登记表里（否则那个锚点
 *    永远不可能被命中，是死代码）；
 * 3. 登记表内部锚点唯一（同一个锚点对应两行会让高亮同时点亮两处）。
 *
 * 扫的是源码文本，不是运行时对象：仓库 testImplementation 只有 junit，没有 Robolectric /
 * compose-ui-test，Composable 跑不起来。同 `PrimaryBottomSheetIconSourceGuardTest` 的做法。
 */
class SettingLeafIndexTest {

    private companion object {
        /** 登记表本身。 */
        const val INDEX_FILE = "src/main/java/heizige/kk/khatkit/app/feature/settings/SettingLeafIndex.kt"

        /** 页面源码根（锚点写在这些文件里）。 */
        const val SETTINGS_DIR = "src/main/java/heizige/kk/khatkit/app/feature/settings"

        /** 扫出来的 `settingAnchor(R.string.x)` 常量名。 */
        fun anchorsIn(file: File): Set<String> {
            if (!file.isFile) return emptySet()
            val text = file.readText()
            return Regex("settingAnchor\\(R\\.string\\.([A-Za-z0-9_]+)\\)")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet()
        }

        /** 登记表里声明过的标题常量（只认 Leaf(...) 行的第一个参数）。 */
        fun registeredTitles(indexText: String): List<String> =
            Regex("Leaf\\(R\\.string\\.([A-Za-z0-9_]+)")
                .findAll(indexText)
                .map { it.groupValues[1] }
                .toList()
    }

    @Test
    fun `every registered leaf has a page anchor`() {
        val index = File(INDEX_FILE)
        assertTrue("找不到登记表：${index.absolutePath}（工作目录=${File(".").absolutePath}）", index.isFile)
        val titles = registeredTitles(index.readText())
        assertTrue("登记表是空的，护栏等于没检查", titles.isNotEmpty())

        val anchors = File(SETTINGS_DIR)
            .listFiles { f -> f.name.startsWith("Setting") && f.name.endsWith(".kt") }
            ?.flatMap { anchorsIn(it) }
            ?.toSet()
            .orEmpty()
        assertTrue("一个页面锚点都没扫到，扫描路径可能写窄了", anchors.isNotEmpty())

        val missing = titles.filterNot { it in anchors }
        assertEquals(
            "这些设置项登记进搜索了，但页面上没有对应的 anchor = settingAnchor(R.string.xxx)，" +
                "搜到点进去不会定位：$missing",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `every page anchor is registered`() {
        val index = File(INDEX_FILE)
        assertTrue("找不到登记表", index.isFile)
        val titles = registeredTitles(index.readText()).toSet()
        val anchors = File(SETTINGS_DIR)
            .listFiles { f -> f.name.startsWith("Setting") && f.name.endsWith(".kt") }
            ?.flatMap { anchorsIn(it) }
            ?.toSet()
            .orEmpty()

        val orphans = anchors.filterNot { it in titles }
        assertEquals(
            "这些行声明了 anchor，但登记表里没有对应条目 —— 这个锚点永远不会被命中，是死代码：$orphans",
            emptyList<String>(),
            orphans,
        )
    }

    @Test
    fun `registered anchors are unique`() {
        val index = File(INDEX_FILE)
        val titles = registeredTitles(index.readText())
        val duplicated = titles.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals(
            "同一个设置项在登记表里出现了多次，高亮会同时命中两处：$duplicated",
            emptySet<String>(),
            duplicated,
        )
    }
}
