package heizige.kk.khatkit.app.core.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageAttachmentPromptTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file uri note contains absolute path mime and size`() {
        val file = tempFolder.newFile("photo.jpg")
        file.writeBytes(ByteArray(2048))

        val note = imageAttachmentNote(file.toURI().toString())

        assertTrue(note!!.startsWith("[用户附带图片: "))
        assertTrue(note.contains(file.absolutePath))
        assertTrue(note.contains("类型 image/jpeg"))
        assertTrue(note.contains("大小 2.0 KB"))
    }

    @Test
    fun `bare absolute path is treated as local file`() {
        val file = tempFolder.newFile("shot.png")
        file.writeBytes(ByteArray(10))

        assertEquals(file, localImageFile(file.absolutePath))
        val note = imageAttachmentNote(file.absolutePath)
        assertTrue(note!!.contains(file.absolutePath))
        assertTrue(note.contains("类型 image/png"))
    }

    @Test
    fun `assistant image note uses neutral label`() {
        val file = tempFolder.newFile("gen.jpg")
        file.writeBytes(ByteArray(10))

        val note = imageAttachmentNote(file.toURI().toString(), fromUser = false)
        assertTrue(note!!.startsWith("[图片: "))
    }

    @Test
    fun `remote and missing files return null`() {
        assertNull(imageAttachmentNote("https://example.com/a.jpg"))
        assertNull(imageAttachmentNote("data:image/png;base64,AAAA"))
        assertNull(imageAttachmentNote("file:///definitely/not/exists.jpg"))
    }

    @Test
    fun `data url label omits raw base64 payload`() {
        val payload = "A".repeat(4096)
        val label = imageAttachmentLabel("data:image/png;base64,$payload")

        assertTrue(label.startsWith("[用户附带图片: "))
        assertTrue(label.contains("image/png"))
        assertTrue(label.contains("内联 base64 已省略"))
        assertFalse(label.contains(payload))
    }

    @Test
    fun `remote image label keeps short url and assistant label is neutral`() {
        assertEquals(
            "[用户附带图片: https://example.com/a.jpg]",
            imageAttachmentLabel("https://example.com/a.jpg"),
        )
        assertEquals("[图片: https://example.com/a.jpg]", imageAttachmentLabel("https://example.com/a.jpg", fromUser = false))
    }

    @Test
    fun `estimated base64 bytes accounts for padding`() {
        assertEquals(3L, estimatedBase64Bytes("data:image/png;base64,AAAA"))
        assertEquals(1L, estimatedBase64Bytes("data:image/png;base64,TQ=="))
        assertEquals(0L, estimatedBase64Bytes("data:image/png;base64,"))
    }

    @Test
    fun `mime type resolves by extension`() {
        assertEquals("image/heic", imageMimeType("a.heic"))
        assertEquals("image/webp", imageMimeType("a.WEBP"))
        assertNull(imageMimeType("a.txt"))
    }

    @Test
    fun `file size formats bytes kb and mb`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("1.00 MB", formatFileSize(1024L * 1024L))
    }
}
