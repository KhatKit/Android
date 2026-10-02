package heizige.kk.khatkit.uikit

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormPickerRequestTest {

    @Test
    fun `media filters go to the in-app media picker`() {
        assertTrue(FormPickRequest.isMediaFilter(""))
        assertTrue(FormPickRequest.isMediaFilter("image/*"))
        assertTrue(FormPickRequest.isMediaFilter("video/mp4"))
        assertTrue(FormPickRequest.isMediaFilter("audio/*"))
        assertTrue(FormPickRequest.isMediaFilter("PNG"))
        assertTrue(FormPickRequest.isMediaFilter(".heic"))
    }

    @Test
    fun `wildcards and document filters go to the system picker`() {
        assertFalse(FormPickRequest.isMediaFilter("*/*"))
        assertFalse(FormPickRequest.isMediaFilter("application/pdf"))
        assertFalse(FormPickRequest.isMediaFilter("pdf"))
        assertFalse(FormPickRequest.isMediaFilter("text/plain"))
    }

    @Test
    fun `request built from form item keeps declarations`() {
        val request = FormPickRequest.from(
            mapOf("id" to "cover", "media" to "Video", "filter" to "mp4", "multiple" to "true"),
            FormPickKind.FILE,
        )
        requireNotNull(request)
        assertEquals("cover", request.id)
        assertEquals(FormPickKind.FILE, request.kind)
        assertEquals("video", request.media)
        assertEquals("mp4", request.filter)
        assertTrue(request.multiple)
        assertTrue(request.preferMediaPicker)
    }

    @Test
    fun `dir picker never prefers the media picker`() {
        val request = FormPickRequest.from(mapOf("id" to "dir"), FormPickKind.DIR)
        requireNotNull(request)
        assertEquals(FormPickKind.DIR, request.kind)
        assertFalse(request.multiple)
        assertFalse(request.preferMediaPicker)
    }

    @Test
    fun `item without id has no request`() {
        assertNull(FormPickRequest.from(mapOf("type" to "file_picker"), FormPickKind.FILE))
    }

    @Test
    fun `host exposes request and fills submitted value`() = runBlocking {
        val host = FormPickerHost()
        val values = mutableMapOf<String, Any?>()
        host.attach { id, value -> values[id] = value }

        val request = requireNotNull(FormPickRequest.from(mapOf("id" to "cover"), FormPickKind.FILE))
        host.request(request)
        assertEquals(request, withTimeout(1_000) { host.request.filterNotNull().first() })

        host.submit("cover", "/storage/emulated/0/DCIM/a.jpg")
        assertEquals("/storage/emulated/0/DCIM/a.jpg", values["cover"])
        assertNull(host.request.value)
    }

    @Test
    fun `blank submit clears the row and clearRequest keeps value`() {
        val host = FormPickerHost()
        val values = mutableMapOf<String, Any?>("cover" to "/手输/路径")
        host.attach { id, value -> values[id] = value }

        host.submit("cover", "")
        assertTrue(values["cover"] == null)

        values["cover"] = "/手输/路径"
        host.request(requireNotNull(FormPickRequest.from(mapOf("id" to "cover"), FormPickKind.FILE)))
        host.clearRequest()
        assertEquals("/手输/路径", values["cover"])
        assertNull(host.request.value)
    }

    @Test
    fun `detach drops the fill sink`() {
        val host = FormPickerHost()
        val values = mutableMapOf<String, Any?>()
        host.attach { id, value -> values[id] = value }
        host.detach()
        host.submit("cover", "/a")
        assertTrue(values.isEmpty())
    }
}