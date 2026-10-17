package heizige.kk.khatkit.app.core.network.routes

import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.network.BadRequestException

internal fun String?.toUuid(name: String = "id"): Uuid {
    if (this == null) throw BadRequestException("Missing $name")
    return runCatching { Uuid.parse(this) }.getOrNull()
        ?: throw BadRequestException("Invalid $name")
}
