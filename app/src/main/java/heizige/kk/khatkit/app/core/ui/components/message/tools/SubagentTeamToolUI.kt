package heizige.kk.khatkit.app.core.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.feature.chat.helper.DELEGATE_TEAM_TASKS
import heizige.kk.khatkit.app.feature.chat.helper.TEMPORARY_SUBAGENT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.theme.MochiTheme

internal object SubagentTeamToolUI : ToolUIRenderer {
    override val toolName = DELEGATE_TEAM_TASKS
    override fun icon(context: ToolUIContext) = groups

    @Composable
    override fun title(context: ToolUIContext) = "子代理任务"

    private fun tasks(context: ToolUIContext) =
        ((context.arguments as? JsonObject)?.get("tasks") as? JsonArray)
            ?.takeIf { tasks -> tasks.size in 1..3 && tasks.all { it.getStringContent("task") != null } }

    private fun results(context: ToolUIContext) =
        (context.content as? JsonObject)?.get("results") as? JsonArray

    override fun hasSummary(context: ToolUIContext) = tasks(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        tasks(context)?.forEachIndexed { index, task ->
            Assignment(index, task, results(context)?.getOrNull(index), compact = true)
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val tasks = tasks(context)
        val results = results(context)
        if (tasks == null || (context.tool.isExecuted && results == null)) {
            DefaultToolPreview(context)
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = MochiScrollDefaults.flingBehavior(),
        ) {
            itemsIndexed(tasks) { index, task ->
                SelectionContainer {
                    Assignment(index, task, results?.getOrNull(index), compact = false)
                }
            }
        }
    }

    @Composable
    private fun Assignment(index: Int, task: JsonElement, result: JsonElement?, compact: Boolean) {
        val uid = task.getStringContent("role_uid")
        val profile = (task as? JsonObject)?.get("temporary_role") as? JsonObject
        val name = result.getStringContent("role")?.takeIf { it.isNotBlank() }
            ?: profile.getStringContent("name")?.takeIf { it.isNotBlank() } ?: when (uid) {
            TEMPORARY_SUBAGENT -> "临时子代理 ${index + 1}"
            null -> "未指定角色"
            else -> LocalSettings.current.assistants.find { it.id.toString() == uid }
                ?.name?.takeIf { it.isNotBlank() } ?: uid
        }
        val maxLines = if (compact) 2 else Int.MAX_VALUE
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (profile != null) "$name（临时角色）" else name, style = MochiTheme.textStyles.titleSmall)
            profile.getStringContent("description")?.let { duties ->
                Text(duties, style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            }
            Text(task.getStringContent("task").orEmpty(), style = MochiTheme.textStyles.body,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            val error = result.getStringContent("error")
            val answer = result.getStringContent("text")
            if (error != null || answer != null) {
                Text(
                    text = if (error != null) "失败：$error" else "结果：$answer",
                    style = MochiTheme.textStyles.body,
                    color = if (error != null) MochiTheme.colorScheme.error else MochiTheme.colorScheme.onSurface,
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
