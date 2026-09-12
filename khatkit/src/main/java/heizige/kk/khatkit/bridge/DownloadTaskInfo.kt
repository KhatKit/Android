package heizige.kk.khatkit.bridge

/** 下载中心展示用的一条任务快照。 */
data class DownloadTaskInfo(
    val id: String,
    val name: String,
    val url: String,
    /** queued / running / paused / done / failed / cancelled */
    val state: String,
    val bytes: Long,
    val total: Long,
    val speedBps: Long,
    val file: String?,
    val error: String?,
) {
    val progress: Float
        get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = state == "queued" || state == "running"
}
