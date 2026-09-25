package heizige.kk.khatkit.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalImagesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `bare path and file uri resolve to existing image`() {
        val file = tempFolder.newFile("photo.jpg")

        assertEquals(file, localImageFileOf(file.absolutePath))
        assertEquals(file, localImageFileOf(file.toURI().toString()))
        assertNull(localImageFileOf("https://example.com/a.jpg"))
        assertNull(localImageFileOf("data:image/png;base64,AAAA"))
        assertNull(localImageFileOf("content://media/external/images/1"))
        assertNull(localImageFileOf(file.absolutePath + ".missing"))
    }

    @Test
    fun `non image files and missing files are ignored`() {
        val text = tempFolder.newFile("note.txt")

        assertNull(localImageFileOf(text.absolutePath))
        assertNull(localImageFileOf(tempFolder.root.resolve("ghost.png").absolutePath))
    }

    @Test
    fun `collect scans nested maps lists and json shapes`() {
        val first = tempFolder.newFile("first.png")
        val second = tempFolder.newFile("second.webp")
        val value = mapOf(
            "outputs" to listOf(first.absolutePath, "https://example.com/remote.jpg"),
            "meta" to mapOf("preview" to second.toURI().toString()),
            "note" to "结果已保存：${first.absolutePath}",
        )

        assertEquals(
            listOf(first.absolutePath, second.absolutePath),
            collectLocalImagePaths(value),
        )
    }

    @Test
    fun `collect deduplicates and respects limit`() {
        val files = (1..3).map { tempFolder.newFile("img$it.png") }
        val value = listOf(files[0].absolutePath, files[0].absolutePath, files[1].absolutePath, files[2].absolutePath)

        assertEquals(listOf(files[0].absolutePath, files[1].absolutePath), collectLocalImagePaths(value, limit = 2))
    }

    @Test
    fun `standalone existing path line becomes markdown image`() {
        val file = tempFolder.newFile("result.png")

        val expanded = expandLocalImagePathLines("已处理完成\n${file.absolutePath}\n请查收")

        assertTrue(expanded.contains("![result.png](${file.toURI()})"))
        assertTrue(expanded.startsWith("已处理完成"))
        assertTrue(expanded.endsWith("请查收"))
    }

    @Test
    fun `backticked file uri line is also expanded`() {
        val file = tempFolder.newFile("shot.jpg")

        val expanded = expandLocalImagePathLines("`${file.toURI()}`")

        assertEquals("![shot.jpg](${file.toURI()})", expanded)
    }

    @Test
    fun `inline path mixed with text urls and missing files stay untouched`() {
        val file = tempFolder.newFile("keep.jpg")
        val content = buildString {
            appendLine("结果见 ${file.absolutePath}")
            appendLine("https://example.com/a.jpg")
            appendLine(tempFolder.root.resolve("missing.png").absolutePath)
        }.trimEnd()

        assertEquals(content, expandLocalImagePathLines(content))
    }

    @Test
    fun `code fence content is not expanded`() {
        val file = tempFolder.newFile("fenced.png")

        val content = "```\n${file.absolutePath}\n```"

        assertEquals(content, expandLocalImagePathLines(content))
    }
}
