package heizige.kk.khatkit.app.feature.tasks.helper

import android.net.Uri
import heizige.kk.khatkit.app.core.data.db.entity.CardArtifactEntity
import java.io.File


internal fun CardArtifactEntity.isAvailable(): Boolean = when (kind) {
    CardArtifactEntity.TEXT -> !textContent.isNullOrEmpty()
    CardArtifactEntity.LINK -> available && Uri.parse(uri).scheme in setOf("http", "https")
    CardArtifactEntity.FILE -> fileOrNull()?.canRead() == true
    else -> false
}

internal fun CardArtifactEntity.fileOrNull(): File? =
    Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)?.takeIf { it.isFile }
