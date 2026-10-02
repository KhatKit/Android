package heizige.kk.khatkit.bridge.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVectorIndexTest {

    @Test
    fun `cosine of identical vectors is one`() {
        val vector = floatArrayOf(0.3f, -0.2f, 0.9f)
        assertEquals(1f, CardVectorIndex.cosine(vector, vector.copyOf())!!, 1e-5f)
    }

    @Test
    fun `cosine of orthogonal vectors is zero`() {
        val cosine = CardVectorIndex.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        assertEquals(0f, cosine!!, 1e-6f)
    }

    @Test
    fun `cosine of opposite vectors is minus one`() {
        val cosine = CardVectorIndex.cosine(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f))
        assertEquals(-1f, cosine!!, 1e-5f)
    }

    @Test
    fun `dimension mismatch and zero vectors are skipped`() {
        assertNull(CardVectorIndex.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f)))
        assertNull(CardVectorIndex.cosine(floatArrayOf(), floatArrayOf()))
        assertEquals(0f, CardVectorIndex.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f))!!, 1e-6f)
    }

    @Test
    fun `encode decode round trip keeps values`() {
        val vector = floatArrayOf(0.1234567f, -0.5f, 1f, 0f)
        val restored = CardVectorIndex.decode(CardVectorIndex.encode(vector))
        assertEquals(vector.size, restored.size)
        vector.forEachIndexed { index, expected ->
            assertEquals(expected, restored[index], 1e-6f)
        }
    }

    @Test
    fun `decode tolerates malformed entries`() {
        assertEquals(0, CardVectorIndex.decode("").size)
        // 坏值被丢弃，不会让整条向量不可用
        assertEquals(1, CardVectorIndex.decode("0.5,abc").size)
        assertEquals(0.5f, CardVectorIndex.decode("0.5,abc")[0], 1e-6f)
    }

    @Test
    fun `scan and topk limits are sane`() {
        assertTrue(CardVectorIndex.MAX_SCAN > CardVectorIndex.MAX_TOP_K)
        assertEquals(50, CardVectorIndex.MAX_TOP_K)
        assertEquals("__khatkit_embeddings", CardVectorIndex.TABLE)
    }
}