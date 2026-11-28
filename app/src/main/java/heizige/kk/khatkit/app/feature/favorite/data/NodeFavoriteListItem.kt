package heizige.kk.khatkit.app.feature.favorite.data

import kotlin.uuid.Uuid
import dagger.hilt.android.lifecycle.HiltViewModel


data class NodeFavoriteListItem(
    val id: String,
    val refKey: String,
    val conversationId: Uuid,
    val nodeId: Uuid,
    val conversationTitle: String,
    val preview: String,
    val createdAt: Long,
)
