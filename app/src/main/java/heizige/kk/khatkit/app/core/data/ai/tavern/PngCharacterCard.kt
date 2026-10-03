package heizige.kk.khatkit.app.core.data.ai.tavern

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * PNG tEXt layout used by SillyTavern `src/character-card-parser.js`.
 * `chara` keeps the original JSON. `ccv3` is the same JSON with spec forced to v3.
 * Readers prefer `ccv3`, then `chara`. This file reimplements that layout; it does not copy the AGPL parser.
 */
object PngCharacterCard {
    private val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun embed(cardJson: String): ByteArray {
        val chara = base64(cardJson)
        val v3 = runCatching {
            val root = Json.parseToJsonElement(cardJson).jsonObject.toMutableMap()
            root["spec"] = JsonPrimitive(CharacterCardCodec.SPEC_V3)
            root["spec_version"] = JsonPrimitive("3.0")
            Json.encodeToString(JsonElement.serializer(), JsonObject(root))
        }.getOrDefault(cardJson)
        return write(listOf("chara" to chara, "ccv3" to base64(v3)))
    }

    fun readJson(png: ByteArray): String {
        val chunks = readText(png)
        val payload = chunks.firstPayload("ccv3") ?: chunks.firstPayload("chara")
            ?: error("No chara tEXt chunk")
        val base64 = Regex("""\[chara:\s*(.+?)]""").find(payload)?.groupValues?.get(1) ?: payload
        return String(decodeBase64(base64), StandardCharsets.UTF_8)
    }

    fun readBase64(png: ByteArray): String? = runCatching {
        val chunks = readText(png)
        val payload = chunks.firstPayload("ccv3") ?: chunks.firstPayload("chara") ?: return null
        Regex("""\[chara:\s*(.+?)]""").find(payload)?.groupValues?.get(1) ?: payload
    }.getOrNull()

    internal fun write(texts: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ihdr()))
        texts.forEach { (keyword, text) -> out.write(chunk("tEXt", textChunk(keyword, text))) }
        out.write(chunk("IDAT", idat()))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    internal fun readText(png: ByteArray): List<Pair<String, String>> {
        if (png.size < 8 || !png.copyOfRange(0, 8).contentEquals(signature)) error("Not a PNG")
        val texts = mutableListOf<Pair<String, String>>()
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0 || offset + 12 + length > png.size) break
            val type = String(png, offset + 4, 4, StandardCharsets.US_ASCII)
            val data = png.copyOfRange(offset + 8, offset + 8 + length)
            if (type == "tEXt") {
                val split = data.indexOf(0)
                if (split > 0) {
                    val keyword = String(data, 0, split, StandardCharsets.ISO_8859_1)
                    val value = String(data, split + 1, data.size - split - 1, StandardCharsets.ISO_8859_1)
                    texts += keyword to value
                }
            }
            offset += 12 + length
            if (type == "IEND") break
        }
        return texts
    }

    private fun List<Pair<String, String>>.firstPayload(keyword: String): String? =
        firstOrNull { it.first.equals(keyword, ignoreCase = true) }?.second

    private fun ihdr(): ByteArray = byteArrayOf(
        0, 0, 0, 1,
        0, 0, 0, 1,
        8, 2, 0, 0, 0,
    )

    private fun idat(): ByteArray {
        val deflater = Deflater()
        deflater.setInput(byteArrayOf(0, 0, 0, 0))
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(64)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) compressed.write(buffer, 0, count)
        }
        deflater.end()
        return compressed.toByteArray()
    }

    private fun textChunk(keyword: String, text: String): ByteArray {
        val key = keyword.toByteArray(StandardCharsets.ISO_8859_1)
        val value = text.toByteArray(StandardCharsets.ISO_8859_1)
        return ByteArray(key.size + 1 + value.size).also {
            key.copyInto(it)
            value.copyInto(it, key.size + 1)
        }
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val out = ByteArrayOutputStream()
        writeInt(out, data.size)
        out.write(typeBytes)
        out.write(data)
        writeInt(out, crc.value.toInt())
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeBase64(value: String): ByteArray {
        val cleaned = value.replace("\\s".toRegex(), "")
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return Base64.getMimeDecoder().decode(padded)
    }
}
