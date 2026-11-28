package heizige.kk.khatkit.app.core.data.datastore

import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ConversationTeam(
    val managers: Set<Uuid> = emptySet(),
    val delegates: Set<Uuid> = emptySet(),
    val temporaryRoles: List<Assistant> = emptyList(),
)

internal fun Settings.team(chat: Uuid?): ConversationTeam = conversationTeams[chat] ?: ConversationTeam()

internal fun Settings.withTeam(chat: Uuid, team: ConversationTeam): Settings =
    copy(conversationTeams = if (team == ConversationTeam()) conversationTeams - chat else conversationTeams + (chat to team))

internal fun Settings.promoteTemporaryRole(chat: Uuid, roleId: Uuid): Settings {
    val team = team(chat)
    val role = team.temporaryRoles.firstOrNull { it.id == roleId } ?: return this
    return withTeam(chat, team.copy(temporaryRoles = team.temporaryRoles - role))
        .copy(assistants = (assistants + role).distinctBy { it.id })
}

/** Only used to evaluate existing role-management rules. Never persist this permission projection. */
internal fun Settings.forTeam(chat: Uuid?): Settings {
    val team = team(chat)
    return copy(assistants = assistants.map { role ->
        role.copy(canManageRoles = role.id in team.managers, enableSubagentTeam = role.id in team.delegates)
    })
}
