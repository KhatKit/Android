package heizige.kk.khatkit.engine

import org.junit.Assert.*
import org.junit.Test

class WasmCapabilityTest {
    private val dispatcher = RustBridgeDispatcher(emptyMap())

    @Test fun capabilityCannotBeUsedToCallAnotherBridge() {
        assertFalse(dispatcher.wasmAllowed("host", "root.exec", "[]"))
        assertFalse(dispatcher.wasmAllowed("ui:render", "store.kvSet", "[]"))
        assertTrue(dispatcher.wasmAllowed("host", "time.parse", "[]"))
        assertTrue(dispatcher.wasmAllowed("host", "host.locale", "[]"))
        assertTrue(dispatcher.wasmAllowed("ui:render", "ui.screen", "[]"))
    }

    @Test fun networkCapabilityBindsTheRequestedDomainAndProtocol() {
        assertTrue(dispatcher.wasmAllowed("net:example.com", "net.get", """["https://api.example.com/a"]"""))
        assertFalse(dispatcher.wasmAllowed("net:example.com", "net.get", """["https://example.com.evil.org/a"]"""))
        assertFalse(dispatcher.wasmAllowed("net:example.com", "net.get", """["https://example.com@evil.org/a"]"""))
        assertFalse(dispatcher.wasmAllowed("net:*", "net.get", """["file:///tmp/a"]"""))
        assertFalse(dispatcher.wasmAllowed("net:*", "root.exec", """["https://example.com"]"""))
    }
}
