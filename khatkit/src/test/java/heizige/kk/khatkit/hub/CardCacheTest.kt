package heizige.kk.khatkit.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CardCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeCard(root: File, name: String, version: String) {
        val dir = File(root, "cards/$name/$version").apply { mkdirs() }
        File(dir, "card.json").writeText("""{"name":"$name","version":"$version"}""")
    }

    @Test
    fun installedVersionsPicksHighestSemver() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "pdf_merge", "1.0.0")
        writeCard(root, "pdf_merge", "1.2.0")
        writeCard(root, "image_compress", "0.9.0")

        val versions = CardCache(root).installedVersions()
        assertEquals("1.2.0", versions["pdf_merge"])
        assertEquals("0.9.0", versions["image_compress"])
    }

    @Test
    fun uninstallRemovesAllVersionsOfCard() {
        val root = tempFolder.newFolder("cache")
        writeCard(root, "pdf_merge", "1.0.0")
        writeCard(root, "pdf_merge", "1.2.0")
        writeCard(root, "other", "1.0.0")

        val cache = CardCache(root)
        assertTrue(cache.uninstall("pdf_merge"))
        assertFalse(File(root, "cards/pdf_merge").exists())
        assertTrue(File(root, "cards/other/1.0.0/card.json").exists())
        assertFalse(cache.uninstall("missing"))
    }

    @Test
    fun loadReadsManifestAndScript() {
        val root = tempFolder.newFolder("cache")
        val dir = File(root, "cards/demo/1.0.0").apply { mkdirs() }
        File(dir, "card.json").writeText(
            """{"name":"demo","version":"1.0.0","engine":"lua","entry":{"lua":"main.lua"}}"""
        )
        File(dir, "main.lua").writeText("return {}")

        val loaded = CardCache(root).load(dir)
        assertEquals("demo", loaded.manifest.name)
        assertEquals("return {}", loaded.scriptText)
    }
}
