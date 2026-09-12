package heizige.kk.khatkit.app.data.favorite

import heizige.kk.khatkit.app.data.db.entity.FavoriteEntity
import heizige.kk.khatkit.app.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
