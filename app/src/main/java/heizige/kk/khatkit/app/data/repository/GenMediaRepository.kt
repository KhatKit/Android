package heizige.kk.khatkit.app.data.repository

import androidx.paging.PagingSource
import heizige.kk.khatkit.app.data.db.dao.GenMediaDAO
import heizige.kk.khatkit.app.data.db.entity.GenMediaEntity

class GenMediaRepository(private val dao: GenMediaDAO) {
    fun getAllMedia(): PagingSource<Int, GenMediaEntity> = dao.getAll()

    suspend fun insertMedia(media: GenMediaEntity) = dao.insert(media)

    suspend fun deleteMedia(id: Int) = dao.delete(id)
}
