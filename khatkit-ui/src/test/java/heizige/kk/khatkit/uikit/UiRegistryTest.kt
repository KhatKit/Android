package heizige.kk.khatkit.uikit

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRegistryTest {

    @Test
    fun resolvesRegisteredRenderer() {
        UiRegistry.register("test.renderer") { _ -> }
        assertNotNull(UiRegistry.resolve("test.renderer"))
        assertNull(UiRegistry.resolve("missing.renderer"))
        assertNull(UiRegistry.resolve(null))
    }

    @Test
    fun builtinRenderersAreRegistered() {
        assertTrue("json.pretty" in UiRegistry.registeredNames())
        assertTrue("keyvalue" in UiRegistry.registeredNames())
    }
}
