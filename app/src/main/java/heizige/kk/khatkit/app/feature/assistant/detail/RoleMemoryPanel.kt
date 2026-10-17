package heizige.kk.khatkit.app.feature.assistant.detail

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextTooltipBox
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryConflict
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryDocument
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryRepository
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.save as saveIcon
import heizige.kk.khromia.helper.Toast
import kotlinx.coroutines.launch

@Composable
internal fun RoleMemoryPanel(
    vm: AssistantDetailViewModel,
    assistant: Assistant,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember(assistant.id) { mutableStateOf(listOf("MEMORY.md")) }
    var document by remember(assistant.id) { mutableStateOf<RoleMemoryDocument?>(null) }
    var draft by remember(assistant.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<RoleMemoryDocument?>(null) }
    var newTopic by remember { mutableStateOf<String?>(null) }
    var pendingOpen by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    val navigator = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dirty = document != null && draft != document?.content
    BackHandler(enabled = dirty || busy) { if (!busy) confirmExit = true }

    suspend fun open(path: String) {
        try {
            document = vm.readRoleMemory(path)
            draft = document!!.content
        } catch (e: Exception) {
            Toast.show("读取失败：${e.message}")
        }
    }
    fun save(overwrite: Boolean = false) {
        val current = document ?: return
        busy = true
        scope.launch {
            try {
                val saved = vm.saveRoleMemory(current.path, draft, current.version, overwrite)
                document = saved
                draft = saved.content
                files = vm.roleMemoryFiles()
                conflict = null
                Toast.show("已保存")
            } catch (e: RoleMemoryConflict) {
                conflict = e.current
            } catch (e: Exception) {
                Toast.show("保存失败：${e.message}")
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(assistant.id) {
        files = vm.roleMemoryFiles()
        open("MEMORY.md")
    }

    KedgeSettingsPageScaffold(
        title = "记忆",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            KedgeIconButton(onClick = {
                if (dirty) confirmExit = true else navigator.popBackStack()
            }, enabled = !busy) { Icon(arrowBack, "返回") }
        },
        actions = {
                KedgeTextTooltipBox("新建主题笔记") {
                    KedgeIconButton(onClick = { newTopic = "" }, enabled = !busy) { Icon(add, "新建主题笔记") }
                }
                KedgeTextTooltipBox("打开记忆文件夹") {
                    KedgeIconButton(onClick = {
                        val uri = DocumentsContract.buildDocumentUri(context.packageName + ".memory.documents", assistant.id.toString())
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri))
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.show("未找到系统文件管理器")
                            }
                        }
                    }) { Icon(folder, "打开记忆文件夹") }
                }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("角色记忆", style = KedgeTextStyles.body(), modifier = Modifier.weight(1f))
                KedgeSwitch(checked = assistant.enableMemory, onCheckedChange = {
                    vm.update(assistant.copy(enableMemory = it, useGlobalMemory = false))
                })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自动整理", style = KedgeTextStyles.body(), modifier = Modifier.weight(1f))
                KedgeSwitch(checked = assistant.autoExtractMemory, onCheckedChange = {
                    vm.update(assistant.copy(autoExtractMemory = it))
                })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    KedgeTextButton(onClick = { showFiles = true }, enabled = !busy) {
                        Text(document?.path ?: "MEMORY.md", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
                        Icon(keyboardArrowDown, "选择记忆文件", Modifier.size(18.dp))
                    }
                    KedgeDropdownMenuSlots(expanded = showFiles, onDismissRequest = { showFiles = false }) {
                        files.forEach { path ->
                            KedgeDropdownItemSlot(
                                text = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = if (path == document?.path) { { Icon(check, null) } } else null,
                                onClick = {
                                    showFiles = false
                                    if (dirty) pendingOpen = path else scope.launch { open(path) }
                                },
                            )
                        }
                    }
                }
                KedgeTextTooltipBox("重新加载") {
                    KedgeIconButton(onClick = {
                        val path = document?.path ?: "MEMORY.md"
                        if (dirty) pendingOpen = path
                        else scope.launch { files = vm.roleMemoryFiles(); open(path) }
                    }, enabled = !busy) { Icon(refresh, "重新加载") }
                }
                KedgeTextTooltipBox("保存") {
                    KedgeIconButton(onClick = { save() }, enabled = !busy && dirty) { Icon(saveIcon, "保存") }
                }
            }
            val current = document
            if (current != null) {
                if (current.path == "MEMORY.md") {
                    val loaded = minOf(draft.length, RoleMemoryRepository.CURATED_BUDGET)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("加载预算", style = KedgeTextStyles.footnoteSmall())
                        Text("$loaded / ${RoleMemoryRepository.CURATED_BUDGET} 字符", style = KedgeTextStyles.footnoteSmall())
                    }
                    LinearProgressIndicator(
                        progress = { loaded.toFloat() / RoleMemoryRepository.CURATED_BUDGET },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val editorStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                CompositionLocalProvider(LocalTextStyle provides editorStyle) {
                    KedgeTextFieldWithSlots(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(), textStyle = editorStyle,
                        minLines = 12, maxLines = 22, enabled = !busy, shape = RoundedCornerShape(8.dp),
                        label = { Text(when {
                            current.path == "MEMORY.md" -> "精选记忆"
                            current.path.startsWith("daily/") -> "每日日志"
                            else -> "主题笔记"
                        }) },
                    )
                }
                Text(
                    if (busy) "正在保存…" else if (dirty) "未保存" else "${current.content.length} 字符",
                    style = KedgeTextStyles.footnoteSmall(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmExit) {
        AppAlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("放弃未保存的更改？") },
            confirmButton = { KedgeTextButton(onClick = {
                confirmExit = false
                navigator.popBackStack()
            }) { Text("放弃并返回") } },
            dismissButton = { KedgeTextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
        )
    }
    pendingOpen?.let { target ->
        AppAlertDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text("放弃未保存的更改？") },
            confirmButton = { KedgeTextButton(onClick = {
                pendingOpen = null
                scope.launch { open(target) }
            }) { Text("放弃并打开") } },
            dismissButton = { KedgeTextButton(onClick = { pendingOpen = null }) { Text("取消") } },
        )
    }
    conflict?.let { latest ->
        AppAlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("文件已被修改") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("磁盘版本", style = KedgeTextStyles.title())
                    Text(latest.content, style = KedgeTextStyles.body())
                }
            },
            confirmButton = { KedgeTextButton(onClick = {
                document = latest; draft = latest.content; conflict = null
            }) { Text("重新加载") } },
            dismissButton = { KedgeTextButton(onClick = { save(overwrite = true) }, enabled = !busy) { Text("覆盖磁盘版本") } },
        )
    }
    newTopic?.let { name ->
        AppAlertDialog(
            onDismissRequest = { newTopic = null },
            title = { Text("新建主题笔记") },
            text = { KedgeTextFieldWithSlots(value = name, onValueChange = { newTopic = it },
                singleLine = true, label = { Text("文件名") }) },
            confirmButton = { KedgeTextButton(onClick = {
                val path = "topics/$name.md"
                newTopic = null
                if (document?.content != draft) pendingOpen = path else scope.launch { open(path) }
            }, enabled = Regex("[A-Za-z0-9_-]{1,80}").matches(name)) { Text("创建") } },
            dismissButton = { KedgeTextButton(onClick = { newTopic = null }) { Text("取消") } },
        )
    }
}
