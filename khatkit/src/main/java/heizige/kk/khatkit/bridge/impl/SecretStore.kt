package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 卡片密钥存储（设计文档 7.4 的「密钥分离」）。
 *
 * 敏感数据不进 kv：用 Android Keystore 的 AES/GCM 主密钥加密后落盘，
 * 明文永不落盘。密钥名单独维护索引，便于 UI 展示「该卡片存了哪些密钥」。
 */
internal class SecretStore(context: Context, cardName: String) {

    private val prefs = context.getSharedPreferences("khatkit_secret_$cardName", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(name: String): String? {
        val blob = prefs.getString(encodeKey(name), null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipherText = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    fun set(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
        prefs.edit()
            .putString(encodeKey(name), blob)
            .putString(INDEX_KEY, json.encodeToString(ListSerializer(String.serializer()), names() + name))
            .apply()
    }

    fun remove(name: String) {
        prefs.edit()
            .remove(encodeKey(name))
            .putString(INDEX_KEY, json.encodeToString(ListSerializer(String.serializer()), names() - name))
            .apply()
    }

    fun names(): List<String> {
        val raw = prefs.getString(INDEX_KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encodeKey(name: String): String =
        "secret_" + Base64.encodeToString(name.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "khatkit_secret_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val INDEX_KEY = "__khatkit_secret_index"
    }
}
