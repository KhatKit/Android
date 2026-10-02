package heizige.kk.khatkit.bridge.impl

import android.util.Base64
import heizige.kk.khatkit.bridge.CryptoBridge
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoBridgeImpl : CryptoBridge {
    private val random = SecureRandom()

    override fun sha256(text: String): String = digest("SHA-256", text.toByteArray())
    override fun sha256File(path: String): String = digest("SHA-256", File(path).readBytes())
    override fun md5(text: String): String = digest("MD5", text.toByteArray())

    override fun hmacSha256(text: String, key: String, encoding: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val result = mac.doFinal(text.toByteArray())
        return if (encoding.lowercase() == "base64") {
            Base64.encodeToString(result, Base64.NO_WRAP)
        } else {
            result.toHex()
        }
    }

    override fun uuid(): String = UUID.randomUUID().toString()
    override fun randomInt(min: Int, max: Int): Int {
        require(min <= max) { "随机数范围无效：min 必须小于等于 max" }
        return if (min == max) min else random.nextInt(max - min + 1) + min
    }

    override fun randomToken(bytes: Int): String {
        require(bytes >= 0) { "随机 token 长度不能为负数" }
        val data = ByteArray(bytes)
        random.nextBytes(data)
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    override fun urlEncode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
    override fun urlDecode(text: String): String = URLDecoder.decode(text, StandardCharsets.UTF_8.name())
    override fun base64(text: String): String = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
    override fun base64Decode(text: String): String = String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
    override fun hexEncode(data: String): String = data.toByteArray().toHex()
    override fun hexDecode(text: String): String = String(text.hexBytes(), Charsets.UTF_8)

    override fun aesEncrypt(text: String, key: String, iv: String): String =
        crypt(Cipher.ENCRYPT_MODE, text.toByteArray(), key, iv).let { Base64.encodeToString(it, Base64.NO_WRAP) }

    override fun aesDecrypt(text: String, key: String, iv: String): String =
        String(crypt(Cipher.DECRYPT_MODE, Base64.decode(text, Base64.DEFAULT), key, iv), Charsets.UTF_8)

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).toHex()

    private fun crypt(mode: Int, data: ByteArray, key: String, iv: String): ByteArray {
        require(key.toByteArray().size in setOf(16, 24, 32)) { "AES key 长度必须是 16/24/32 字节" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = if (iv.isEmpty()) ByteArray(16) else iv.toByteArray()
        require(ivBytes.size == 16) { "AES iv 长度必须是 16 字节" }
        cipher.init(mode, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(ivBytes))
        return cipher.doFinal(data)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0) { "hex 文本长度必须为偶数" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
