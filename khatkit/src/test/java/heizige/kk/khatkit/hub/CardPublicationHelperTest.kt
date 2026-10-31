package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipInputStream

class CardPublicationHelperTest {
    @Test fun wasmForkPreservesBinaryAssetsWidgetAndAbiLimits() {
        val dir = Files.createTempDirectory("publish-wasm-").toFile()
        try {
            dir.resolve("assets").mkdirs()
            dir.resolve("assets/note.txt").writeText("keep me")
            dir.resolve("locales").mkdirs()
            dir.resolve("locales/zh.json").writeText("""{"save":"保存"}""")
            dir.resolve("card.json").writeText("cache metadata must not enter the package")
            val manifest = """{"name":"note","version":"1.0.0","license":"MIT","engine":"wasm","runtime":"wasm","abi_version":1,"capabilities":["ui:render","store"],"limits":{"fuel":123456},"widget":{"entry":{"wasm":"card.wasm"}}}"""
            val wasm = byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0)
            val loaded = LoadedCard(CardParser.parse(manifest).getOrThrow(), dir, "", EngineKind.WASM, wasm, manifest)
            val (publishedManifest, base64) = CardPublicationHelper.prepare(loaded, "1.0.1", "Apache-2.0")
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(Base64.getDecoder().decode(base64))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            assertEquals(setOf("manifest.json", "card.wasm", "assets/note.txt", "locales/zh.json"), entries.keys)
            assertArrayEquals(wasm, entries["card.wasm"])
            assertEquals(publishedManifest, entries.getValue("manifest.json").decodeToString())
            val parsed = Json.parseToJsonElement(publishedManifest).jsonObject
            assertEquals("1.0.1", parsed.getValue("version").jsonPrimitive.content)
            assertEquals("Apache-2.0", parsed.getValue("license").jsonPrimitive.content)
            assertEquals(123456, parsed.getValue("limits").jsonObject.getValue("fuel").jsonPrimitive.int)
            assertEquals("card.wasm", parsed.getValue("widget").jsonObject.getValue("entry").jsonObject.getValue("wasm").jsonPrimitive.content)
            assertEquals("keep me", entries.getValue("assets/note.txt").decodeToString())
        } finally {
            dir.deleteRecursively()
        }
    }
}
