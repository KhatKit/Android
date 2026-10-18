package heizige.kk.khatkit.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 「按添加顺序」排列的事实来源：[CardCache.installOrder]。
 *
 * 为什么不用 `installedVersions()` 的 map 顺序：那是 `listFiles()` 的目录列举序，
 * ext4 上是哈希序，卡片更新一次换个 inode 就会在欢迎页里跳位。所以顺序必须落盘，
 * 且**更新不改变原位置**——这份测试盯的就是这两条。
 */
class CardInstallOrderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeCard(root: File, name: String, version: String = "1.0.0", modifiedAt: Long? = null) {
        val dir = File(root, "cards/$name/$version").apply { mkdirs() }
        File(dir, "card.json").writeText("""{"name":"$name","version":"$version"}""")
        modifiedAt?.let { File(root, "cards/$name").setLastModified(it) }
    }

    /** 能被 [CardCache.load] 解析的最小卡片（引擎 + 入口脚本都在）。 */
    private fun writeLoadableCard(root: File, name: String, version: String = "1.0.0") {
        val dir = File(root, "cards/$name/$version").apply { mkdirs() }
        File(dir, "card.json").writeText(
            """{"name":"$name","version":"$version","engine":"lua","entry":{"lua":"main.lua"}}"""
        )
        File(dir, "main.lua").writeText("return { ok = true }")
    }

    @Test
    fun recordedOrderFollowsInstallSequenceAndSurvivesUpdates() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "alpha")
        writeCard(root, "beta")
        val cache = CardCache(root)

        cache.recordInstall("alpha")
        cache.recordInstall("beta")
        assertEquals(listOf("alpha", "beta"), cache.installOrder())

        // 更新（同一个名字再记一次）不能把卡片挤到末尾，也不能重复出现。
        writeCard(root, "alpha", version = "1.1.0")
        cache.recordInstall("alpha")
        assertEquals(listOf("alpha", "beta"), cache.installOrder())
    }

    @Test
    fun legacyCardsWithoutRecordAreBackfilledByDirectoryTime() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "late", modifiedAt = 1_600_000_003_000L)
        writeCard(root, "early", modifiedAt = 1_600_000_001_000L)
        writeCard(root, "mid", modifiedAt = 1_600_000_002_000L)
        val cache = CardCache(root)

        // 改动之前装的卡片没有记录，按卡片目录的修改时间补一次。
        assertEquals(listOf("early", "mid", "late"), cache.installOrder())
        // 补记录必须写回：第二次读不再依赖 mtime。
        File(root, "cards/early").setLastModified(1_700_000_000_000L)
        assertEquals(listOf("early", "mid", "late"), cache.installOrder())
    }

    @Test
    fun recordInstallKeepsBackfilledCardsAheadOfNewOnes() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "old", modifiedAt = 1_600_000_001_000L)
        val cache = CardCache(root)

        cache.installOrder()
        writeCard(root, "new")
        cache.recordInstall("new")
        assertEquals(listOf("old", "new"), cache.installOrder())
    }

    @Test
    fun uninstallDropsTheNameFromTheRecord() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "alpha")
        writeCard(root, "beta")
        val cache = CardCache(root)
        cache.recordInstall("alpha")
        cache.recordInstall("beta")

        assertTrue(cache.uninstall("alpha"))
        assertEquals(listOf("beta"), cache.installOrder())
        // 重装之后重新排到末尾，而不是继承旧位置。
        writeCard(root, "alpha")
        cache.recordInstall("alpha")
        assertEquals(listOf("beta", "alpha"), cache.installOrder())
    }


    @Test
    fun installedCardsFollowRecordedOrderNotDirectoryOrder() {
        val root = tempFolder.newFolder("cache")
        listOf("alpha", "beta", "gamma").forEach { writeLoadableCard(root, it) }
        val cache = CardCache(root)
        // 记录顺序特意和目录列举序/名字字典序都不一样。
        cache.recordInstall("gamma")
        cache.recordInstall("alpha")
        cache.recordInstall("beta")

        assertEquals(listOf("gamma", "alpha", "beta"), cache.installedCards().map { it.manifest.name })
    }

    @Test
    fun installedCardsKeepPositionAcrossUpdate() {
        val root = tempFolder.newFolder("cache")
        writeLoadableCard(root, "alpha")
        writeLoadableCard(root, "beta")
        val cache = CardCache(root)
        cache.recordInstall("alpha")
        cache.recordInstall("beta")

        writeLoadableCard(root, "alpha", version = "1.1.0")
        cache.recordInstall("alpha")
        assertEquals(listOf("alpha", "beta"), cache.installedCards().map { it.manifest.name })
    }

    @Test
    fun cardThatCannotBeParsedIsSkippedWithoutBreakingTheOrder() {
        val root = tempFolder.newFolder("cache")
        writeLoadableCard(root, "alpha")
        writeLoadableCard(root, "beta")
        val dir = File(root, "cards/broken/1.0.0").apply { mkdirs() }
        File(dir, "card.json").writeText("{ not json")
        val cache = CardCache(root)
        cache.recordInstall("alpha")
        cache.recordInstall("beta")

        assertEquals(listOf("alpha", "beta"), cache.installedCards().map { it.manifest.name })
    }

    @Test
    fun orderFileLivesOutsideTheCardsDirectory() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "alpha")
        val cache = CardCache(root)
        cache.recordInstall("alpha")

        // 放进 cards/ 里会被 installedVersions() 当成卡片目录扫到。
        assertTrue(File(root, "installed-order.txt").isFile)
        assertEquals(setOf("alpha"), cache.installedVersions().keys)
    }
}
