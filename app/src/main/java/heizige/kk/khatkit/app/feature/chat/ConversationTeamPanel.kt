package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.ConversationTeam
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextField
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiSwitch
import kotlin.uuid.Uuid

@Composable
internal fun ConversationTeamPanel(
    actor: Uuid,
    team: ConversationTeam,
    update: ((ConversationTeam) -> ConversationTeam) -> Unit,
    save: (Uuid, String, String, Boolean) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        title = { Text(stringResource(R.string.conversation_team)) },
    ) {
        item(
            headlineContent = { Text(stringResource(R.string.team_manager)) },
            supportingContent = { Text(stringResource(R.string.team_manager_detail)) },
            trailingContent = {
                MochiSwitch(checked = actor in team.managers, onCheckedChange = { enabled ->
                    update { it.copy(managers = if (enabled) it.managers + actor else it.managers - actor) }
                })
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.team_delegate)) },
            supportingContent = { Text(stringResource(R.string.team_delegate_detail)) },
            trailingContent = {
                MochiSwitch(checked = actor in team.delegates, onCheckedChange = { enabled ->
                    update { it.copy(delegates = if (enabled) it.delegates + actor else it.delegates - actor) }
                })
            },
        )
        team.temporaryRoles.forEach { role ->
            item(
                headlineContent = { Text(role.name) },
                supportingContent = { Text(stringResource(R.string.team_temporary_label, role.description)) },
                onClick = { selected = role.id.toString() },
                chevron = true,
            )
        }
    }
    val role = team.temporaryRoles.find { it.id.toString() == selected } ?: return
    var name by rememberSaveable(selected) { mutableStateOf(role.name) }
    var duties by rememberSaveable(selected) { mutableStateOf(role.description) }
    val valid = name.isNotBlank() && duties.isNotBlank()
    MochiDialog(
        visible = true, onDismissRequest = { selected = null }, dismissOnOutsideTap = true,
        title = stringResource(R.string.team_edit),
        actions = persistentListOf(
            MochiDialogAction(label = stringResource(R.string.chat_page_save), role = MochiDialogActionRole.Primary,
                enabled = valid, onClick = { save(role.id, name, duties, false) }),
            MochiDialogAction(label = stringResource(R.string.team_promote),
                enabled = valid, onClick = { save(role.id, name, duties, true) }),
            MochiDialogAction(label = stringResource(R.string.team_remove), role = MochiDialogActionRole.Destructive,
                onClick = { update { it.copy(temporaryRoles = it.temporaryRoles.filterNot { member -> member.id == role.id }) } }),
        ),
    ) {
        Column {
            Md3OutlinedTextField(value = name, onValueChange = { name = it.take(100) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = stringResource(R.string.assistant_page_name))
            Md3OutlinedTextField(value = duties, onValueChange = { duties = it.take(4000) },
                modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.team_duties))
        }
    }
}
