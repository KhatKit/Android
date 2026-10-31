package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Prepare the same installed card for publishing or a versioned fork. */
object CardPublicationHelper {
    fun prepare(card: LoadedCard, version: String, license: String): Pair<String, String> {
        if (card.engineKind != EngineKind.WASM) {
            return CardParser.serialize(card.manifest.copy(version = version, license = license)) to ""
        }
        val source = Json.parseToJsonElement(requireNotNull(card.wasmManifestJson)).jsonObject
        val manifest = JsonObject(source + mapOf(
            "version" to JsonPrimitive(version), "license" to JsonPrimitive(license),
        )).toString()
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            var expanded = 0L
            var entries = 0
            fun entry(path: String, bytes: ByteArray) {
                require(++entries <= 256) { "卡片文件过多" }
                expanded += bytes.size
                require(expanded <= 32L * 1024 * 1024) { "卡片展开后超过 32 MiB" }
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("manifest.json", manifest.toByteArray(Charsets.UTF_8))
            entry("card.wasm", requireNotNull(card.wasmBytes))
            for (directory in listOf("assets", "locales")) {
                val root = File(card.dir, directory)
                if (!root.isDirectory) continue
                for (file in root.walkTopDown().maxDepth(32).filter { it.isFile }.sortedBy { it.path }) {
                    require(file.canonicalPath.startsWith(card.dir.canonicalPath + File.separator)) {
                        "卡片资源路径无效"
                    }
                    require(file.length() <= 32L * 1024 * 1024 - expanded) { "卡片资源过大" }
                    entry(file.relativeTo(card.dir).invariantSeparatorsPath, file.readBytes())
                }
            }
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= 8 * 1024 * 1024) { "卡片归档超过 8 MiB" }
        return manifest to Base64.getEncoder().encodeToString(bytes)
    }
}
