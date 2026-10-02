package heizige.kk.khatkit.bridge.impl

/**
 * 卡片存储配额：files + JSONL + SQLite 合计的天花板（`card.json` 的 `store.quota_mb`）。
 *
 * SQLite 侧另有 `PRAGMA max_page_count` 硬顶（见 [CardSqlStore]），这里的校验负责
 * 拦住「多个存储同时逼近上限」的情况。
 */
class StoreQuota(quotaMb: Int) {

    /** 配额字节数；`quota_mb` 最小按 1MB 生效。 */
    val quotaBytes: Long = quotaMb.coerceAtLeast(1).toLong() * 1024L * 1024L

    /** SQLite 单库页数上限（按 [PAGE_BYTES] 估算），防止一次巨型写入吃满磁盘。 */
    val maxPageCount: Long = (quotaBytes / PAGE_BYTES).coerceAtLeast(MIN_PAGES)

    /** 校验「当前占用 + 本次新增」是否仍在配额内，越界抛中文错误。 */
    fun enforce(usedBytes: Long, additionalBytes: Long = 0L) {
        val used = usedBytes.coerceAtLeast(0)
        require(used + additionalBytes <= quotaBytes) {
            "store 配额超限：已用 ${used / 1024 / 1024}MB，本次 ${(additionalBytes / 1024).coerceAtLeast(1)}KB，" +
                "上限 ${quotaBytes / 1024 / 1024}MB；请清理数据或申请更高配额"
        }
    }

    /** 已用占比（0f–1f），供 UI 展示。 */
    fun usedRatio(usedBytes: Long): Float =
        (usedBytes.coerceAtLeast(0).toFloat() / quotaBytes).coerceIn(0f, 1f)

    companion object {
        /** SQLite 默认页大小 4KB；实际库页大小不同也只是估算，max_page_count 另有硬校验。 */
        const val PAGE_BYTES = 4096L

        /** 至少给 64 页（256KB），否则小配额连建表都放不下。 */
        const val MIN_PAGES = 64L
    }
}
