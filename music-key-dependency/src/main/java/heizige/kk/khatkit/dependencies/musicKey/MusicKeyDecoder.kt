package heizige.kk.khatkit.dependencies.musicKey

import java.io.File
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlin.math.abs
import kotlin.math.tan

/** Local streaming decryption for NCM and legacy KWM files. */
internal object MusicKeyDecoder {
    private val ncmMagic = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val coreKey = "hzHRAmso5kInbaxW".toByteArray(Charsets.US_ASCII)
    private val qmcV1Key = byteArrayOf(
        0xC3.toByte(),0x4A,0xD6.toByte(),0xCA.toByte(),0x90.toByte(),0x67,0xF7.toByte(),0x52,0xD8.toByte(),0xA1.toByte(),0x66,0x62,0x9F.toByte(),0x5B,0x09,0x00,0xC3.toByte(),0x5E,0x95.toByte(),0x23,0x9F.toByte(),0x13,0x11,0x7E,0xD8.toByte(),0x92.toByte(),0x3F,0xBC.toByte(),0x90.toByte(),0xBB.toByte(),0x74,0x0E,
        0xC3.toByte(),0x47,0x74,0x3D,0x90.toByte(),0xAA.toByte(),0x3F,0x51,0xD8.toByte(),0xF4.toByte(),0x11,0x84.toByte(),0x9F.toByte(),0xDE.toByte(),0x95.toByte(),0x1D,0xC3.toByte(),0xC6.toByte(),0x09,0xD5.toByte(),0x9F.toByte(),0xFA.toByte(),0x66,0xF9.toByte(),0xD8.toByte(),0xF0.toByte(),0xF7.toByte(),0xA0.toByte(),0x90.toByte(),0xA1.toByte(),0xD6.toByte(),0xF3.toByte(),
        0xC3.toByte(),0xF3.toByte(),0xD6.toByte(),0xA1.toByte(),0x90.toByte(),0xA0.toByte(),0xF7.toByte(),0xF0.toByte(),0xD8.toByte(),0xF9.toByte(),0x66,0xFA.toByte(),0x9F.toByte(),0xD5.toByte(),0x09,0xC6.toByte(),0xC3.toByte(),0x1D,0x95.toByte(),0xDE.toByte(),0x9F.toByte(),0x84.toByte(),0x11,0xF4.toByte(),0xD8.toByte(),0x51,0x3F,0xAA.toByte(),0x90.toByte(),0x3D,0x74,0x47,
        0xC3.toByte(),0x0E,0x74,0xBB.toByte(),0x90.toByte(),0xBC.toByte(),0x3F,0x92.toByte(),0xD8.toByte(),0x7E,0x11,0x13,0x9F.toByte(),0x23,0x95.toByte(),0x5E,0xC3.toByte(),0x00,0x09,0x5B,0x9F.toByte(),0x62,0x66,0xA1.toByte(),0xD8.toByte(),0x52,0xF7.toByte(),0x67,0x90.toByte(),0xCA.toByte(),0xD6.toByte(),0x4A
    )
    private val qmcV1Extensions = setOf("tkm", "bkcmp3", "bkcm4a", "bkcflac", "bkcwav", "bkcape", "bkcogg", "bkcwma", "m4a")
    private val qmcV2Extensions = setOf("mflac", "mgg", "mgg0", "mgg1", "mggl", "qmcflac", "qmcogg", "qmc0", "qmc2", "qmc3", "qmc4", "qmc6", "qmc8")

    fun decrypt(path: String, output: String = ""): String {
        val source = File(path)
        require(source.isFile) { "输入文件不存在：$path" }
        val target = if (output.isBlank()) File(source.parentFile, source.nameWithoutExtension + ".decoded") else File(output)
        target.parentFile?.mkdirs()
        when {
            source.extension.equals("ncm", true) -> decryptNcm(source, target)
            source.extension.equals("kwm", true) -> decryptKwm(source, target)
            qmcV1Extensions.contains(source.extension.lowercase()) -> decryptQmcV1(source, target)
            qmcV2Extensions.contains(source.extension.lowercase()) -> decryptQmcV2(source, target)
            source.extension.lowercase() in setOf("kgm", "kgma", "vpr") -> decryptKgm(source, target)
            else -> error("当前解密器暂不支持 ${source.extension}；已支持 NCM、KWM、QQ 音乐 QMC v1")
        }
        return target.absolutePath
    }

    private fun decryptQmcV1(source: File, target: File) {
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(1024 * 1024)
                var position = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        val absolute = position + i
                        val phase = if (absolute <= 0x7fff) absolute % 128 else (absolute % 0x7fff) % 128
                        buffer[i] = (buffer[i].toInt() xor qmcV1Key[phase.toInt()].toInt()).toByte()
                    }
                    out.write(buffer, 0, count)
                    position += count
                }
            }
        }
    }

    private fun decryptQmcV2(source: File, target: File) {
        val tail = source.inputStream().buffered().use { input ->
            input.skipFully((source.length() - minOf(source.length(), 4096L)).coerceAtLeast(0L))
            input.readBytes()
        }
        val marker = "QTag".toByteArray(Charsets.US_ASCII)
        val at = tail.indexOfSlice(marker)
        require(at >= 4) { "QMC 文件缺少 QTag/EKey 尾包；需要带内嵌 EKey 的文件" }
        val size = readBeInt(tail, at - 4)
        require(size in 1..1280 && at >= size + 4) { "QMC EKey 尾包长度非法" }
        val ekeyText = tail.copyOfRange(at - 4 - size, at - 4).toString(Charsets.US_ASCII).trim('\u0000')
        val key = deriveQmcMaster(ekeyText)
        val audioLength = source.length() - (tail.size - at + size + 4)
        require(audioLength > 0) { "QMC 音频区为空" }
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(1024 * 1024); var position = 0L; var remaining = audioLength
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(count > 0) { "QMC 音频提前结束" }
                    qmcStreamXor(buffer, count, position, key)
                    out.write(buffer, 0, count); position += count; remaining -= count
                }
            }
        }
    }


    private fun deriveQmcMaster(text: String): ByteArray {
        val v2Prefix = Base64.getEncoder().encodeToString("QQMusic EncV2,Key:".toByteArray(Charsets.US_ASCII))
        val isV2 = text.startsWith(v2Prefix)
        val encoded = if (isV2) text.removePrefix(v2Prefix) else text
        val raw = try { Base64.getDecoder().decode(encoded) } catch (_: Throwable) { error("QMC EKey 不是合法 Base64") }
        require(raw.size > 8) { "QMC EKey 太短" }
        val header = raw.copyOfRange(0, 8)
        val cipher = raw.copyOfRange(8, raw.size)
        val simple = ByteArray(8) { abs(tan(106.0 + it * 0.1) * 100.0).toInt().coerceIn(0, 255).toByte() }
        val teaKey = ByteArray(16) { if (it and 1 == 0) simple[it / 2] else header[it / 2] }
        var plain = teaDecryptCbc(cipher, teaKey)
        if (isV2) {
            plain = teaDecryptCbc(plain, byteArrayOf(0x2A,0x2A,0x23,0x21,0x28,0x23,0x24,0x25,0x26,0x5E,0x61,0x31,0x63,0x5A,0x2C,0x54))
            plain = teaDecryptCbc(plain, byteArrayOf(0x33,0x38,0x36,0x5A,0x4A,0x59,0x21,0x40,0x23,0x2A,0x24,0x25,0x5E,0x26,0x29,0x28))
        }
        return plain.dropWhile { it == 0.toByte() }.toByteArray()
    }

    private fun decryptKgm(source: File, target: File) {
        val magic = byteArrayOf(0x7c,0xd5.toByte(),0x32,0xeb.toByte(),0x86.toByte(),0x02,0x7f,0x4b,0xa8.toByte(),0xaf.toByte(),0xa6.toByte(),0x8e.toByte(),0x0f,0xff.toByte(),0x99.toByte(),0x14)
        val vpr = byteArrayOf(0x05,0x28,0xbc.toByte(),0x96.toByte(),0xe9.toByte(),0xe4.toByte(),0x5a,0x43,0x91.toByte(),0xaa.toByte(),0xbd.toByte(),0xd0.toByte(),0x7a,0xf5.toByte(),0x36,0x31)
        source.inputStream().buffered().use { input ->
            val header = input.readFully(1024)
            require(header.copyOfRange(0, 16).contentEquals(magic) || header.copyOfRange(0, 16).contentEquals(vpr)) { "不是合法的 KGM/VPR 文件" }
            val offset = readLeInt(header, 0x10); val version = readLeInt(header, 0x14)
            require(version != 5) { "KGG v5 需要客户端密钥库，当前不支持" }
            require(offset in 1024..source.length()) { "KGM 音频偏移非法" }
            val pub = MusicKeyDecoder::class.java.classLoader?.getResourceAsStream("kugou_key.bin")?.use { it.readBytes() } ?: error("缺少酷狗公钥表")
            val own = header.copyOfRange(0x1c, 0x2c) + byteArrayOf(0)
            input.skipFully((offset - 1024).toLong())
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(1024 * 1024); var pos = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; for (i in 0 until n) { val g = pos + i; val packed = (buffer[i].toInt() xor own[(g % 17).toInt()].toInt()) and 255; val a = packed xor ((packed and 15) shl 4); val publicByte = pub[(g / 16).toInt()].toInt() and 255; val m = mendTable[(g % mendTable.size).toInt()].toInt() and 255; val b = publicByte xor m; buffer[i] = (a xor (b xor ((b and 15) shl 4))).toByte() }; out.write(buffer,0,n); pos += n }
            }
        }
    }

    private fun qmcStreamXor(data: ByteArray, count: Int, offset: Long, key: ByteArray) {
        if (key.size <= 300) {
            val map = ByteArray(128) { i ->
                val idx = (i * i + 71214) % key.size; val shift = (idx + 4) % 8
                ((key[idx].toInt() and 255 shl shift) or ((key[idx].toInt() and 255) ushr shift)).toByte()
            }
            for (i in 0 until count) { val p = offset + i; val phase = if (p <= 0x7fff) p % 128 else (p % 0x7fff) % 128; data[i] = (data[i].toInt() xor map[phase.toInt()].toInt()).toByte() }
        } else error("QMC 长 EKey RC4 流暂不支持")
    }

    private fun teaDecryptCbc(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size >= 16 && data.size % 8 == 0) { "QMC TEA 密文长度非法" }
        val k = IntArray(4) { readBeInt(key, it * 4) }; val out = ByteArray(data.size); var prev = 0L
        for (p in data.indices step 8) { val block = readBeLong(data, p) xor prev; val dec = teaBlock(block, k); writeBeLong(out, p, dec); prev = readBeLong(data, p) }
        val pad = out[0].toInt() and 7; val start = 1 + pad + 2; val end = out.size - 7
        require(start <= end) { "QMC TEA 填充非法" }; return out.copyOfRange(start, end)
    }

    private fun teaBlock(block: Long, k: IntArray): Long { var y = (block ushr 32).toInt(); var z = block.toInt(); var sum = -0x61C88647
        repeat(16) { z -= (((y shl 4) + k[2]) xor (y + sum) xor ((y ushr 5) + k[3])); y -= (((z shl 4) + k[0]) xor (z + sum) xor ((z ushr 5) + k[1])); sum += 0x61C88647 }; return (y.toLong() shl 32) or (z.toLong() and 0xffffffffL) }

    private fun readBeInt(a: ByteArray, p: Int) = ((a[p].toInt() and 255) shl 24) or ((a[p+1].toInt() and 255) shl 16) or ((a[p+2].toInt() and 255) shl 8) or (a[p+3].toInt() and 255)
    private fun readLeInt(a: ByteArray, p: Int) = (a[p].toInt() and 255) or ((a[p+1].toInt() and 255) shl 8) or ((a[p+2].toInt() and 255) shl 16) or ((a[p+3].toInt() and 255) shl 24)
    private fun readBeLong(a: ByteArray, p: Int) = (readBeInt(a,p).toLong() shl 32) or (readBeInt(a,p+4).toLong() and 0xffffffffL)
    private fun writeBeLong(a: ByteArray,p:Int,v:Long) { for(i in 0..7) a[p+i]=(v ushr (56-i*8)).toByte() }
    private fun ByteArray.indexOfSlice(s: ByteArray): Int = (0..size-s.size).firstOrNull { copyOfRange(it,it+s.size).contentEquals(s) } ?: -1
    private val mendTable = Base64.getDecoder().decode("uNU9sumveIyDM3FRdqDNNy8+NY2pvpi354wizlph32hpif6ltt6pd/zIvb3lbT5aNu9pTr7h6WYc89kCtvISm0TQb7k1ibZGbXOCBmnB7deFwjDfomK+eS1iYj0Nfr5IiSMCoOTVdVEyAlP9FjohOxYPw7K7s+K6Oj0T7PYBRYSlcA+TSQxkzTHVzEwHAZ4AGiOQv4geO6umPsRzRxB+O1684wCE/wnU4IkPW1hwT/tl2FxTG9PIxr/vmLBQTw/q5YNYjCgshGfN0J5H2ydQyvRjY+iXfxtLDMLBIUzMWPWUUqPz0+Bo9AAj814Ke5PdqxKyE+iE16efDzJMVR0ENlLcA/P5TkLpPWHvfLazk1A=")

    private fun decryptNcm(source: File, target: File) {
        source.inputStream().buffered().use { input ->
            require(input.readFully(8).contentEquals(ncmMagic)) { "不是合法的 NCM 文件" }
            input.skipFully(2)
            val keyLength = input.readLeInt()
            require(keyLength in 1..(1 shl 20)) { "NCM 密钥长度非法" }
            val keyBlob = input.readFully(keyLength).map { (it.toInt() xor 0x64).toByte() }.toByteArray()
            val keyPlain = aesEcb(coreKey, keyBlob)
            require(keyPlain.size > 17) { "NCM 核心密钥为空" }
            val rc4Key = keyPlain.copyOfRange(17, keyPlain.size)
            val box = ncmBox(rc4Key)
            val metaLength = input.readLeInt()
            require(metaLength in 0..(64 shl 20)) { "NCM 元数据过大" }
            input.skipFully(metaLength.toLong())
            input.skipFully(5)
            val coverSpace = input.readLeInt()
            val coverSize = input.readLeInt()
            require(coverSpace >= 0 && coverSize >= 0 && coverSize <= coverSpace) { "NCM 封面区长度非法" }
            require(coverSpace <= (128 shl 20)) { "NCM 封面过大" }
            input.skipFully(coverSpace.toLong())
            target.outputStream().buffered().use { out ->
                var position = 0L
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        buffer[i] = (buffer[i].toInt() xor box[((position + i + 1) and 0xff).toInt()].toInt()).toByte()
                    }
                    out.write(buffer, 0, count)
                    position += count
                }
            }
        }
    }

    private fun decryptKwm(source: File, target: File) {
        require(source.length() >= 1056L) { "KWM 文件过短" }
        source.inputStream().buffered().use { input ->
            input.skipFully(1024)
            val scan = input.readFully(minOf(64 * 1024, (source.length() - 1024).toInt()))
            val key = recoverKwmKey(scan)
            target.outputStream().buffered().use { out ->
                for (i in scan.indices) scan[i] = (scan[i].toInt() xor key[i and 31].toInt()).toByte()
                out.write(scan)
                var position = scan.size
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) buffer[i] = (buffer[i].toInt() xor key[(position + i) and 31].toInt()).toByte()
                    out.write(buffer, 0, count)
                    position += count
                }
            }
        }
    }

    private fun recoverKwmKey(data: ByteArray): ByteArray {
        val candidates = ArrayList<ByteArray>()
        var previous: ByteArray? = null
        var offset = 0
        while (offset + 32 <= data.size) {
            val chunk = data.copyOfRange(offset, offset + 32)
            if (chunk.contentEquals(previous)) candidates += chunk
            previous = chunk
            offset += 32
        }
        previous?.let { candidates += it.copyOfRange(16, 32) + it.copyOfRange(0, 16) }
        for (i in 0 until minOf(64, data.size / 32)) candidates += data.copyOfRange(i * 32, i * 32 + 32)
        return candidates.firstOrNull { candidate ->
            val probe = ByteArray(minOf(64, data.size)) { i -> (data[i].toInt() xor candidate[i and 31].toInt()).toByte() }
            val signature = probe.copyOfRange(0, minOf(4, probe.size))
            probe.toString(Charsets.US_ASCII).startsWith("ID3") || signature.contentEquals(byteArrayOf(0x66, 0x4c, 0x61, 0x43)) ||
                signature.contentEquals(byteArrayOf(0x4f, 0x67, 0x67, 0x53)) || signature.contentEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46))
        } ?: candidates.firstOrNull() ?: error("无法从 KWM 数据恢复解密密钥")
    }

    private fun aesEcb(key: ByteArray, encrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        val plain = cipher.doFinal(encrypted)
        val pad = plain.lastOrNull()?.toInt()?.and(0xff) ?: 0
        require(pad in 1..16 && pad <= plain.size) { "NCM AES 填充非法" }
        return plain.copyOf(plain.size - pad)
    }

    private fun ncmBox(key: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 255)) and 255
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        return ByteArray(256) { i -> s[(s[i] + s[(i + s[i]) and 255]) and 255].toByte() }
    }

    private fun InputStream.readFully(size: Int): ByteArray {
        require(size >= 0) { "读取长度非法" }
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(result, offset, size - offset)
            require(count > 0) { "文件提前结束" }
            offset += count
        }
        return result
    }

    private fun InputStream.readLeInt(): Int = readFully(4).let {
        (it[0].toInt() and 255) or ((it[1].toInt() and 255) shl 8) or
            ((it[2].toInt() and 255) shl 16) or ((it[3].toInt() and 255) shl 24)
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped > 0) left -= skipped else require(read() >= 0) { "文件提前结束" }
        }
    }
}
