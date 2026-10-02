package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.ApprovalGate

/** 宿主侧的向量化能力；`khatkit` 不依赖 `app`，由宿主实现本接口并注入。 */
fun interface EmbeddingEngine {
    /** 批量向量化，返回与 texts 等长、等维的向量；失败抛中文异常。 */
    fun embed(texts: List<String>): List<FloatArray>
}

/** `store` bridge 按卡片构造时的入参（manifest 派生的策略上下文）。 */
data class StoreRequest(
    val cardName: String,
    val quotaMb: Int,
    /** 方法级权限：向量检索复用 `ai.chat` 的审批策略（同样是花钱的模型调用）。 */
    val permissions: Map<String, String> = emptyMap(),
    val approvalGate: ApprovalGate? = null,
    val deadlineAt: Long = 0L,
)

/** 一条向量命中（`store.embedSearch` 的返回行）。 */
data class VectorHit(
    val rowId: String,
    val score: Float,
    val text: String?,
)

/**
 * 卡片向量索引：存在卡片自己的 SQLite 库里（`__khatkit_embeddings`），
 * 检索用**暴力余弦相似度**——卡片数据量级（几千条）足够，规模上来再谈索引。
 *
 * 向量以紧凑字符串（`0.1,0.2,…`）存 `vector` 列；维度不一致的旧向量在检索时跳过。
 */
class CardVectorIndex(private val sql: CardSqlStore) {

    /** 写入或覆盖一条向量；同 `(ns, row_id)` 覆盖。 */
    fun upsert(namespace: String, rowId: String, text: String?, vector: FloatArray): Boolean {
        require(vector.isNotEmpty()) { "向量不能为空" }
        sql.exec(
            "CREATE TABLE IF NOT EXISTS $TABLE (ns TEXT NOT NULL, row_id TEXT NOT NULL, " +
                "text TEXT, vector TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (ns, row_id))",
            emptyList(),
        )
        sql.exec("DELETE FROM $TABLE WHERE ns = ? AND row_id = ?", listOf(namespace, rowId))
        return sql.exec(
            "INSERT INTO $TABLE(ns, row_id, text, vector, updated_at) VALUES (?, ?, ?, ?, ?)",
            listOf(namespace, rowId, text, encode(vector), System.currentTimeMillis()),
        ).let { true }
    }

    /** 暴力余弦检索；维度不匹配的向量跳过。 */
    fun search(namespace: String, query: FloatArray, topK: Int): List<VectorHit> {
        val rows = sql.exec(
            "SELECT row_id, text, vector FROM $TABLE WHERE ns = ? LIMIT $MAX_SCAN",
            listOf(namespace),
        )
        return rows.mapNotNull { row ->
            val vector = decode(row["vector"] as? String ?: return@mapNotNull null)
            val score = cosine(query, vector) ?: return@mapNotNull null
            VectorHit(
                rowId = row["row_id"]?.toString().orEmpty(),
                score = score,
                text = row["text"] as? String,
            )
        }.sortedByDescending { it.score }.take(topK.coerceIn(1, MAX_TOP_K))
    }

    /** 该命名空间的向量条数（卡片可在提示里展示规模）。 */
    fun count(namespace: String): Long =
        sql.exec("SELECT count(*) AS n FROM $TABLE WHERE ns = ?", listOf(namespace))
            .firstOrNull()?.get("n")?.toString()?.toLongOrNull() ?: 0L

    /** 移除一条向量；返回是否命中。 */
    fun remove(namespace: String, rowId: String): Boolean =
        sql.exec("DELETE FROM $TABLE WHERE ns = ? AND row_id = ?", listOf(namespace, rowId)).let { true }

    companion object {
        /** 索引表名；带双下划线前缀，避开卡片自建表。 */
        const val TABLE = "__khatkit_embeddings"

        /** 单次检索最多扫描的向量条数；超量的库请卡片自己分片。 */
        const val MAX_SCAN = 2000

        /** topK 上限。 */
        const val MAX_TOP_K = 50

        /** 余弦相似度；维度不一致返回 null（向量可能来自换了模型的旧数据）。 */
        fun cosine(a: FloatArray, b: FloatArray): Float? {
            if (a.size != b.size || a.isEmpty()) return null
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            if (normA <= 0.0 || normB <= 0.0) return 0f
            return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
        }

        /** 向量编码为紧凑字符串；`Float.toString()` 保证可无损 parse 回原值。 */
        fun encode(vector: FloatArray): String = vector.joinToString(",")

        fun decode(encoded: String): FloatArray =
            encoded.split(',').mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
    }
}