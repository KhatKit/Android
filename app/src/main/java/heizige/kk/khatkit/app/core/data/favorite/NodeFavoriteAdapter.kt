package heizige.kk.khatkit.app.core.data.favorite

import heizige.kk.khatkit.app.core.data.db.entity.FavoriteEntity
import heizige.kk.khatkit.app.core.data.model.FavoriteMeta
import heizige.kk.khatkit.app.core.data.model.FavoriteType
import heizige.kk.khatkit.app.core.data.model.NodeFavoriteRef
import heizige.kk.khatkit.app.core.data.model.NodeFavoriteTarget
import heizige.kk.khatkit.app.core.data.model.buildFavoritePreview
import heizige.kk.khatkit.app.core.util.JsonInstant

object NodeFavoriteAdapter : FavoriteAdapter<NodeFavoriteTarget> {
    override val type: FavoriteType = FavoriteType.NODE

    override fun buildRefKey(target: NodeFavoriteTarget): String {
        return buildRefKey(target.conversationId.toString(), target.nodeId.toString())
    }

    fun buildRefKey(conversationId: String, nodeId: String): String = "node:$conversationId:$nodeId"

    override fun buildFavoriteEntity(
        target: NodeFavoriteTarget,
        existing: FavoriteEntity?,
        now: Long
    ): FavoriteEntity {
        val ref = NodeFavoriteRef(
            conversationId = target.conversationId,
            nodeId = target.nodeId,
        )
        val meta = FavoriteMeta(
            title = target.conversationTitle.ifBlank { null },
            subtitle = target.nodeId.toString(),
            previewText = target.node.buildFavoritePreview(),
        )

        return FavoriteEntity(
            id = existing?.id ?: buildRefKey(target),
            type = type.value,
            refKey = buildRefKey(target),
            refJson = JsonInstant.encodeToString(ref),
            snapshotJson = "",
            metaJson = JsonInstant.encodeToString(meta),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    fun decodeRef(entity: FavoriteEntity): NodeFavoriteRef? {
        if (entity.type != type.value) return null
        return runCatching {
            JsonInstant.decodeFromString<NodeFavoriteRef>(entity.refJson)
        }.getOrNull()
    }

    fun decodeMeta(entity: FavoriteEntity): FavoriteMeta? {
        if (entity.type != type.value) return null
        val rawMeta = entity.metaJson ?: return null
        return runCatching {
            JsonInstant.decodeFromString<FavoriteMeta>(rawMeta)
        }.getOrNull()
    }
}
