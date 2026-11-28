package heizige.kk.khatkit.app.feature.lanshare.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LanShareDocument(val uri: Uri, val name: String, val directory: Boolean)

/** Traversal starts at the user's SAF grant. The HTTP layer never accepts raw document IDs. */
internal class LanShareDirectoryRepository(private val resolver: ContentResolver) {
    suspend fun root(tree: Uri): LanShareDocument = withContext(Dispatchers.IO) {
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree))
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = resolver.query(uri, columns, null, null, null)?.use {
            if (it.moveToFirst()) LanShareDocument(uri, it.getString(1).orEmpty(), it.getString(2) == Document.MIME_TYPE_DIR)
            else null
        }
        requireNotNull(document?.takeIf { it.directory })
    }

    suspend fun children(directory: LanShareDocument, unnamedFallback: String): List<LanShareDocument> = withContext(Dispatchers.IO) {
        require(directory.directory)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(directory.uri, DocumentsContract.getDocumentId(directory.uri))
        val entries = resolver.query(uri, columns, null, null, null) ?: error("directory unavailable")
        entries.use { cursor ->
            buildList {
                while (size < MAX_ENTRIES && cursor.moveToNext()) {
                    add(LanShareDocument(
                        DocumentsContract.buildDocumentUriUsingTree(directory.uri, cursor.getString(0)),
                        cursor.getString(1).orEmpty().ifBlank { unnamedFallback },
                        cursor.getString(2) == Document.MIME_TYPE_DIR,
                    ))
                }
            }.sortedWith(compareByDescending<LanShareDocument> { it.directory }.thenBy { it.name })
        }
    }

    suspend fun open(document: LanShareDocument) = withContext(Dispatchers.IO) {
        require(!document.directory)
        resolver.openInputStream(document.uri)
    }

    companion object {
        const val MAX_ENTRIES = 500
        private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    }
}
