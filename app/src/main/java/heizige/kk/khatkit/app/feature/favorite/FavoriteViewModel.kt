package heizige.kk.khatkit.app.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import heizige.kk.khatkit.app.core.data.db.entity.FavoriteEntity
import heizige.kk.khatkit.app.feature.favorite.data.NodeFavoriteAdapter
import heizige.kk.khatkit.app.core.data.model.FavoriteType
import heizige.kk.khatkit.app.feature.favorite.data.FavoriteRepository
import kotlin.uuid.Uuid
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import heizige.kk.khatkit.app.feature.favorite.data.NodeFavoriteListItem
@HiltViewModel

class FavoriteViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    val nodeFavorites = favoriteRepository
        .listByType(FavoriteType.NODE)
        .map { favorites ->
            favorites.mapNotNull { entity ->
                val ref = NodeFavoriteAdapter.decodeRef(entity) ?: return@mapNotNull null
                val meta = NodeFavoriteAdapter.decodeMeta(entity)

                NodeFavoriteListItem(
                    id = entity.id,
                    refKey = entity.refKey,
                    conversationId = ref.conversationId,
                    nodeId = ref.nodeId,
                    conversationTitle = meta?.title.orEmpty(),
                    preview = meta?.previewText ?: "",
                    createdAt = entity.createdAt,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun removeFavorite(refKey: String) {
        favoriteRepository.deleteByRefKey(refKey)
    }

    suspend fun getEntityByRefKey(refKey: String): FavoriteEntity? {
        return favoriteRepository.getByRefKey(refKey)
    }

    suspend fun restoreFavorite(entity: FavoriteEntity) {
        favoriteRepository.upsert(entity)
    }
}
