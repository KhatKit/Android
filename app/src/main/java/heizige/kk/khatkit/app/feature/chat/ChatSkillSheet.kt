package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.feature.chat.helper.ChatSkillDraftHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows

@Composable
internal fun ChatSkillSheet(state: MochiSheetState, onLoad: (String) -> Unit) {
    MochiSheet(state = state) {
        val manager = rememberAppEntryPoint().skillManager()
        var skills by remember { mutableStateOf<List<SkillMetadata>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(manager) {
            try {
                skills = withContext(Dispatchers.IO) { manager.listSkills() }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                error = "无法读取已安装技能"
            }
        }
        MochiSheetHeader(title = "载入技能到草稿")
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            mochiListSection("installed-skills") {
                val available = skills
                error?.let { message -> row { MochiListRow(title = message) } }
                if (available == null && error == null) row { MochiListRow(title = "读取技能中", loading = true) }
                if (available?.isEmpty() == true) row { MochiListRow(title = stringResource(R.string.skills_page_empty_title)) }
                rows(available.orEmpty(), key = { it.skillDir.absolutePath }) { skill ->
                    MochiListRow(title = skill.name, subtitle = skill.description, enabled = loading == null,
                        loading = loading == skill.name, onClick = {
                            if (state.isVisible && loading == null) {
                                loading = skill.name
                                error = null
                                scope.launch {
                                    try {
                                        val text = withContext(Dispatchers.IO) {
                                            val root = if (skill.builtin) manager.getBuiltinSkillsDir() else manager.getSkillsDir()
                                            ChatSkillDraftHelper.read(skill, root)
                                        }
                                        if (state.isVisible) { onLoad(text); state.hide() }
                                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                        error = if (e is IllegalArgumentException) e.message else "无法读取所选技能"
                                    } finally { loading = null }
                                }
                            }
                        })
                }
            }
        }
    }
}
