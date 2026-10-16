package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local-only index of files, links and bounded text produced by a card run. */
@Entity(
    tableName = "card_artifacts",
    indices = [
        Index(value = ["run_id"]),
        Index(value = ["created_at"]),
    ],
)
data class CardArtifactEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "run_id")
    val runId: String,
    val kind: String,
    val uri: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,
    val available: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "text_content")
    val textContent: String? = null,
) {
    companion object {
        const val FILE = "file"
        const val LINK = "link"
        const val TEXT = "text"
    }
}
