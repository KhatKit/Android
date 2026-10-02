package heizige.kk.khatkit.app.core.ui.components.khatkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TreeUriPathsTest {

    private val roots = mapOf("primary" to "/storage/emulated/0", "1A2B-3C4D" to "/storage/1A2B-3C4D")

    @Test
    fun `primary volume maps to emulated root`() {
        assertEquals("/storage/emulated/0/Download", TreeUriPaths.resolve("primary", "Download", roots))
        assertEquals("/storage/emulated/0", TreeUriPaths.resolve("primary", "", roots))
        assertEquals("/storage/emulated/0/DCIM/Camera", TreeUriPaths.resolve("PRIMARY", "/DCIM/Camera/", emptyMap()))
    }

    @Test
    fun `secondary volume uses the discovered root`() {
        assertEquals("/storage/1A2B-3C4D/Android/data", TreeUriPaths.resolve("1A2B-3C4D", "Android/data", roots))
    }

    @Test
    fun `unknown volume has no real path`() {
        assertNull(TreeUriPaths.resolve("9999-9999", "Download", roots))
    }

    @Test
    fun `primary volume resolves without a discovered root`() {
        assertEquals("/storage/emulated/0/Pictures", TreeUriPaths.resolve("primary", "Pictures", emptyMap()))
    }
}