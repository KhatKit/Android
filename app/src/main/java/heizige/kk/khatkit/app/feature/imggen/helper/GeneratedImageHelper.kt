package heizige.kk.khatkit.app.feature.imggen.helper

import heizige.kk.khatkit.app.feature.imggen.data.GeneratedImage

import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.core.data.db.entity.GenMediaEntity
import heizige.kk.khatkit.app.core.data.files.FilesManager
import java.io.File

internal fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val fullPath = File(imagesDir, this.path.removePrefix("images/")).absolutePath

    return GeneratedImage(
        id = this.id,
        prompt = this.prompt,
        filePath = fullPath,
        timestamp = this.createAt,
        model = this.modelId
    )
}
