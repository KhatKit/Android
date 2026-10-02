package heizige.kk.khatkit.bridge.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StoreQuotaTest {

    private val mb = 1024L * 1024L

    @Test
    fun `quota m floored at 1mb`() {
        assertEquals(mb, StoreQuota(0).quotaBytes)
        assertEquals(mb, StoreQuota(-5).quotaBytes)
        assertEquals(50 * mb, StoreQuota(50).quotaBytes)
    }

    @Test
    fun `max page count derived from quota and never below the minimum`() {
        assertEquals(StoreQuota(1).quotaBytes / StoreQuota.PAGE_BYTES, StoreQuota(1).maxPageCount)
        assertTrue(StoreQuota(1).maxPageCount >= StoreQuota.MIN_PAGES)
        assertEquals(StoreQuota(64).quotaBytes / StoreQuota.PAGE_BYTES, StoreQuota(64).maxPageCount)
        assertTrue(StoreQuota(64).maxPageCount > StoreQuota(1).maxPageCount)
    }

    @Test
    fun `write inside quota passes`() {
        val quota = StoreQuota(10)
        quota.enforce(usedBytes = 5 * mb, additionalBytes = 1024)
        quota.enforce(usedBytes = 0, additionalBytes = 0)
    }

    @Test
    fun `write beyond quota rejected with actionable message`() {
        val quota = StoreQuota(10)
        val error = try {
            quota.enforce(usedBytes = 9 * mb, additionalBytes = 2 * mb)
            fail("应当超限")
            ""
        } catch (e: IllegalArgumentException) {
            e.message.orEmpty()
        }
        assertTrue(error.contains("配额超限"))
        assertTrue(error.contains("上限 10MB"))
    }

    @Test
    fun `used ratio clamped for ui`() {
        val quota = StoreQuota(10)
        assertEquals(0f, quota.usedRatio(0), 1e-6f)
        assertEquals(0.5f, quota.usedRatio(5 * mb), 1e-4f)
        assertEquals(1f, quota.usedRatio(50 * mb), 1e-6f)
    }
}