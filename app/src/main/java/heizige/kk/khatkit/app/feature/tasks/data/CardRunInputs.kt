package heizige.kk.khatkit.app.feature.tasks.data

import heizige.kk.khatkit.app.core.data.ai.tools.jsonToMap
import heizige.kk.khatkit.app.core.data.ai.tools.reusableCardArgs
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal fun CardRunEntity.savedInputs(): Map<String, Any?> {
    if (!canRerun() || argsTemplate.length !in 3..4096) return emptyMap()
    return try {
        reusableCardArgs(jsonToMap(Json.parseToJsonElement(argsTemplate)))
    } catch (_: SerializationException) {
        emptyMap()
    }
}
