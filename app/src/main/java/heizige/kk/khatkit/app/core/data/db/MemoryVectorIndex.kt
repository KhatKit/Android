package heizige.kk.khatkit.app.core.data.db

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * sqlite-vector 索引管理。
 *
 * - 通过 [SQLiteConfiguration] 在连接打开时加载 `libvector` / `vector` 扩展
 * - [ensure] 在 onOpen 中对 `memory_chunks.embedding` 注册向量列
 * - 任何失败都静默降级：检索层会退回 Kotlin 暴力余弦（[MemoryVectorIndex.isAvailable]）
 */
internal object MemoryVectorIndex {
    private const val TAG = "MemoryVectorIndex"
    const val DEFAULT_DIM = 768

    @Volatile
    private var available: Boolean = false

    fun isAvailable(): Boolean = available

    /** 注册向量列；成功置 [isAvailable] = true。 */
    fun ensure(db: SupportSQLiteDatabase, context: Context) {
        try {
            val ver = db.query("SELECT vector_version()").use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            Log.i(TAG, "vector_version=$ver")
            // 维度按已存 embedding 行探测；无数据时用默认维度
            val dim = detectDimension(db) ?: DEFAULT_DIM
            db.execSQL("SELECT vector_init('memory_chunks', 'embedding', 'type=FLOAT32,dimension=$dim,distance=COSINE')")
            available = true
        } catch (e: Throwable) {
            Log.w(TAG, "sqlite-vector unavailable, falling back to in-memory cosine: ${e.message}")
            available = false
        }
    }

    /** 从已有 embedding BLOB 长度推断维度（Float32 = 4 bytes/dim）。 */
    private fun detectDimension(db: SupportSQLiteDatabase): Int? {
        return try {
            db.query("SELECT embedding FROM memory_chunks WHERE embedding IS NOT NULL LIMIT 1").use { c ->
                if (c.moveToFirst()) {
                    val blob = c.getBlob(0)
                    if (blob != null && blob.size >= 4 && blob.size % 4 == 0) blob.size / 4 else null
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** 供 SQLiteConfiguration 探测扩展文件是否存在。 */
    fun hasNativeExtension(context: Context): Boolean {
        val dir = context.applicationInfo.nativeLibraryDir ?: return false
        return File(dir, "vector.so").exists() || File(dir, "libvector.so").exists() ||
            File(dir, "vector").exists() || File(dir, "libvector").exists()
    }

    /** 推荐加载路径：与 libsimple 同约定（扩展名解析），优先官方 README 的 `/vector`。 */
    fun nativeExtensionPath(context: Context): String {
        val dir = context.applicationInfo.nativeLibraryDir
        return if (File(dir, "vector.so").exists() || File(dir, "vector").exists()) {
            "$dir/vector"
        } else {
            "$dir/libvector"
        }
    }

    fun floatArrayToBlob(v: FloatArray): ByteArray {
        val out = ByteArray(v.size * 4)
        for (i in v.indices) {
            val bits = java.lang.Float.floatToIntBits(v[i])
            out[i * 4] = (bits and 0xFF).toByte()
            out[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            out[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            out[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return out
    }

    fun blobToFloatArray(blob: ByteArray): FloatArray {
        val n = blob.size / 4
        val out = FloatArray(n)
        for (i in 0 until n) {
            val bits = (blob[i * 4].toInt() and 0xFF) or
                ((blob[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((blob[i * 4 + 2].toInt() and 0xFF) shl 16) or
                ((blob[i * 4 + 3].toInt() and 0xFF) shl 24)
            out[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return out
    }
}
