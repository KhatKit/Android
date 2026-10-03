package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.engine.ScriptEngine
import org.junit.Assert.*
import org.junit.Test

class BrowserBridgeRegistryTest {
    private class Browser : BrowserBridge, AutoCloseable {
        var released = false
        override fun open(url: String) = url
        override fun snapshot(sessionId: String, selector: String?, offset: Int) = sessionId
        override fun act(sessionId: String, action: String, arguments: Map<String, Any?>?) = action
        override fun close(sessionId: String) = sessionId
        override fun close() { released = true }
    }

    private class Engine : ScriptEngine {
        val bridges = mutableMapOf<String, Any>()
        override fun define(name: String, value: Any) { bridges[name] = value }
        override fun defineModule(name: String, source: String) = Unit
        override fun eval(script: String, args: Map<String, Any?>) = EngineResult.Ok(emptyMap())
        override fun close() = Unit
    }

    @Test
    fun browserIsOptionalAndEachInjectionOwnsAResource() {
        assertFalse(BridgeRegistry().availableBridges().contains("browser"))
        val created = mutableListOf<Browser>()
        val contexts = mutableListOf<BridgeContext>()
        val registry = BridgeRegistry(browserProvider = { context ->
            contexts += context
            Browser().also { created += it }
        })
        val resources = mutableListOf<AutoCloseable>()
        val manifest = CardManifest(
            name = "browser_test",
            requires = CardManifest.Requires(bridges = listOf("browser")),
            network = CardManifest.Network(allow = listOf("example.com")),
        )
        val first = Engine()
        val second = Engine()
        assertTrue(registry.inject(first, manifest, runResources = resources))
        assertTrue(registry.inject(second, manifest, runResources = resources))
        assertEquals(2, resources.size)
        assertNotSame(contexts[0], contexts[1])
        assertEquals(setOf("example.com"), contexts[0].networkAllow)
        assertTrue(first.bridges.getValue("browser") is ContextAwareBridge)
        assertFalse(first.bridges.getValue("browser") is AutoCloseable) // Cleanup is not a script method.
        resources.forEach { it.close() }
        assertTrue(created.all { it.released })
    }
}
