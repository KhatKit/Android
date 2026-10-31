package heizige.kk.khatkit.hub

import com.sun.net.httpserver.HttpServer
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CardPackageInstallTest {
    @get:Rule val temp = TemporaryFolder()
    private val manifest = """{"name":"demo","version":"1.0.0","engine":"wasm","runtime":"wasm","abi_version":1,"capabilities":["ui:render"],"widget":{"entry":{"wasm":"card.wasm"}}}"""
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { buffer -> ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        } }.toByteArray()
    private fun archive() = zip("manifest.json" to manifest.toByteArray(),
        "card.wasm" to byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0))

    private suspend fun serve(bytes: ByteArray, block: suspend (HubClient, AtomicInteger) -> Unit) {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/card") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(HubClient("http://127.0.0.1:${server.address.port}"), requests) }
        finally { server.stop(0) }
    }

    private fun entry() = CardIndexEntry("demo", "1.0.0", url = "/card")

    @Test fun completeWasmPackageInstallsAndConcurrentCallersShareTheInstall() = runBlocking {
        val root = temp.newFolder()
        val cache = CardCache(root)
        serve(archive()) { hub, requests ->
            val dirs = coroutineScope { (1..5).map { async { cache.ensure(entry(), hub) } }.awaitAll() }
            assertEquals(1, requests.get())
            assertEquals(1, dirs.distinct().size)
            val loaded = cache.load(dirs.first())
            assertEquals(EngineKind.WASM, loaded.engineKind)
            assertTrue(loaded.manifest.hasUi)
            assertNotNull(loaded.wasmBytes)
            val widget = cache.loadWidget(loaded)
            assertTrue(widget.widgetExecution)
            assertEquals(setOf("ui"), widget.bridgeSubset)
        }
    }

    @Test fun failedUpdateKeepsTheInstalledVersionAndRejectsSiblingTraversal() = runBlocking {
        val root = temp.newFolder()
        val cache = CardCache(root)
        val existing = cache.cardDir("demo", "1.0.0").apply { mkdirs() }
        File(existing, "card.json").writeText("""{"name":"demo","version":"1.0.0","engine":"lua","entry":{"lua":"main.lua"}}""")
        File(existing, "main.lua").writeText("return { old = true }")
        // Prefix sibling traversal bypassed startsWith(destDir) without a separator.
        val malicious = zip("card.json" to """{"name":"wrong","version":"1.0.0"}""".toByteArray(),
            "../outside" to byteArrayOf(1))
        serve(malicious) { hub, _ ->
            assertTrue(runCatching { cache.ensure(entry(), hub, force = true) }.isFailure)
            assertEquals("return { old = true }", cache.load(existing).scriptText)
            assertFalse(File(root, "staging/outside").exists())
        }
    }

    @Test fun rejectsMissingExecutableInsteadOfInstallingAnEmptyCard() = runBlocking {
        val cache = CardCache(temp.newFolder())
        serve(zip("card.json" to """{"name":"demo","version":"1.0.0","engine":"lua","entry":{"lua":"main.lua"}}""".toByteArray())) { hub, _ ->
            assertTrue(runCatching { cache.ensure(entry(), hub) }.isFailure)
            assertFalse(cache.isCached(entry()))
        }
    }
}
