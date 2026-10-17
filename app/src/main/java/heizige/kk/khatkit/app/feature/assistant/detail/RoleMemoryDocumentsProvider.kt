package heizige.kk.khatkit.app.feature.assistant.detail

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import heizige.kk.khatkit.app.R
import java.io.File
import kotlin.uuid.Uuid

/** User-granted, read-only SAF access. Edits go through the inspector's redaction and version check. */
class RoleMemoryDocumentsProvider : DocumentsProvider() {
    private val root get() = File(requireNotNull(context).noBackupFilesDir, "role-memory").also { it.mkdirs() }.canonicalFile
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)

    override fun onCreate() = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val defaults = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_MIME_TYPES)
        return MatrixCursor(projection ?: defaults).apply {
            newRow().apply {
                add(Root.COLUMN_ROOT_ID, "role-memory")
                add(Root.COLUMN_DOCUMENT_ID, "root")
                add(Root.COLUMN_TITLE, "KhatKit 记忆")
                add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(Root.COLUMN_MIME_TYPES, "text/markdown")
            }
        }
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: columns).apply { row(this, documentId, resolve(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: columns).apply {
            resolve(parentDocumentId).listFiles().orEmpty()
                .filter { !it.name.startsWith(".") && (it.isDirectory || it.extension == "md") }
                .sortedBy { it.name }.forEach { file ->
                    val id = file.relativeTo(root).invariantSeparatorsPath
                    if (runCatching { resolve(id) }.isSuccess) row(this, id, file)
                }
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        require(mode == "r") { "Use the memory inspector to edit with conflict protection" }
        val file = resolve(documentId)
        require(file.isFile)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolve(parentDocumentId).toPath()
        val child = resolve(documentId).toPath()
        return child != parent && child.startsWith(parent)
    }

    private fun resolve(id: String): File {
        if (id == "root") return root
        val role = id.substringBefore('/')
        Uuid.parse(role)
        val relative = id.substringAfter('/', "")
        require(relative.isEmpty() || relative == "MEMORY.md" || relative == "topics" || relative == "daily" ||
            Regex("(topics/[A-Za-z0-9_-]{1,80}|daily/\\d{4}-\\d{2}-\\d{2})\\.md").matches(relative))
        val target = File(root, id).canonicalFile
        val roleRoot = File(root, role).canonicalFile
        require(roleRoot.toPath().startsWith(root.toPath()) && target.toPath().startsWith(roleRoot.toPath()))
        return target
    }

    private fun row(cursor: MatrixCursor, id: String, file: File) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, id)
            add(Document.COLUMN_DISPLAY_NAME, if (id == "root") "KhatKit 记忆" else file.name)
            add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "text/markdown")
            add(Document.COLUMN_FLAGS, 0)
            add(Document.COLUMN_SIZE, if (file.isDirectory) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }
}
