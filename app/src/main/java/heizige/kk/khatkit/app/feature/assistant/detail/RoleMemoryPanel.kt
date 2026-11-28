package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.app.LocalPageRoute
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.MochiTooltipIconButton
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryConflict
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryDocument
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryRepository
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.icons.navigation.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import heizige.kk.khatkit.app.core.ui.icons.files.folder
import heizige.kk.khatkit.app.core.ui.icons.navigation.refresh
import heizige.kk.khatkit.app.core.ui.icons.files.save as saveIcon
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import heizige.kk.khatkit.app.PageBackInterceptor
import ui.feice.mochi.basic.MochiDialogTextField
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.basic.Text as MochiText
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun RoleMemoryPanel(
    vm: AssistantDetailViewModel,
    assistant: Assistant,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var files by remember(assistant.id) { mutableStateOf(listOf("MEMORY.md")) }
    var document by remember(assistant.id) { mutableStateOf<RoleMemoryDocument?>(null) }
    var draft by remember(assistant.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<RoleMemoryDocument?>(null) }
    var newTopic by remember { mutableStateOf<String?>(null) }
    var pendingOpen by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val navigator = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dirty = document != null && draft != document?.content
    // U7a-6：改用 PageBackInterceptor 登记（有未保存改动/正忙时先收返回），不再挂页面级 BackHandler。
    // 登记时带上所在页：只拦本页的返回，压在下面的页里还开着的浮层不会吞掉栈顶页的返回
    val memoryBackTokenRoute = LocalPageRoute.current
    val memoryBackToken = remember { Any() }
    DisposableEffect(dirty || busy) {
        if (dirty || busy) {
            PageBackInterceptor.push(memoryBackToken, memoryBackTokenRoute) {
                if (!busy) confirmExit = true
                true
            }
        } else {
            PageBackInterceptor.pop(memoryBackToken)
        }
        onDispose { PageBackInterceptor.pop(memoryBackToken) }
    }

    suspend fun open(path: String) {
        try {
            document = vm.readRoleMemory(path)
            draft = document!!.content
        } catch (e: Exception) {
            Toast.show(resources.getString(R.string.role_memory_read_failed, e.message))
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
                Toast.show(resources.getString(R.string.role_memory_saved))
            } catch (e: RoleMemoryConflict) {
                conflict = e.current
            } catch (e: Exception) {
                Toast.show(resources.getString(R.string.role_memory_save_failed, e.message))
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(assistant.id) {
        files = vm.roleMemoryFiles()
        open("MEMORY.md")
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.role_memory_page_title),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            MochiIconButton(onClick = {
                if (dirty) confirmExit = true else navigator.popBackStack()
            }, enabled = !busy) { Icon(arrowBack, stringResource(R.string.back)) }
        },
        actions = {
            MochiTooltipIconButton(stringResource(R.string.role_memory_new_topic), onClick = { newTopic = "" }, enabled = !busy) { Icon(add, null) }
            MochiTooltipIconButton(stringResource(R.string.role_memory_open_folder), onClick = {
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
                        Toast.show(resources.getString(R.string.role_memory_open_folder_failed))
                    }
                }
            }) { Icon(folder, null) }
        },
    ) { innerPadding ->
        val current = document
        val options = remember(files, current?.path) {
            (files + listOfNotNull(current?.path)).distinct().toImmutableList()
        }
        CompositionLocalProvider(LocalMochiListItemSpacing provides MemorySectionSpacing) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = innerPadding + PaddingValues(
                    start = 16.dp,
                    top = PageMetrics.TopContentPadding,
                    end = 16.dp,
                    bottom = 8.dp + PageMetrics.BottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(MemorySectionSpacing),
            ) {
                // 两个开关成一组：点整行即切换
                item(key = "switches") {
                    MochiListSection {
                        MochiListRow(
                            title = stringResource(R.string.role_memory_section_title),
                            onClick = { vm.update(assistant.copy(enableMemory = !assistant.enableMemory, useGlobalMemory = false)) },
                            trailing = {
                                MochiSwitch(checked = assistant.enableMemory, onCheckedChange = {
                                    vm.update(assistant.copy(enableMemory = it, useGlobalMemory = false))
                                })
                            },
                        )
                        MochiListRow(
                            title = stringResource(R.string.role_memory_auto_organize),
                            onClick = { vm.update(assistant.copy(autoExtractMemory = !assistant.autoExtractMemory)) },
                            trailing = {
                                MochiSwitch(checked = assistant.autoExtractMemory, onCheckedChange = {
                                    vm.update(assistant.copy(autoExtractMemory = it))
                                })
                            },
                        )
                    }
                }
                // 文件：菜单式选择行切换记忆文件，下面两行是重新加载 / 保存
                item(key = "file") {
                    MochiListSection {
                        MochiListPickerRow(
                            title = stringResource(R.string.role_memory_files),
                            options = options,
                            selectedIndex = options.indexOf(current?.path ?: "MEMORY.md"),
                            onSelect = { index ->
                                val path = options[index]
                                if (dirty) pendingOpen = path else scope.launch { open(path) }
                            },
                            leading = { MemoryEntryIcon(folder) },
                            enabled = !busy,
                        )
                        MochiListRow(
                            title = stringResource(R.string.role_memory_reload),
                            leading = { MemoryEntryIcon(refresh) },
                            enabled = !busy,
                            onClick = {
                                val path = document?.path ?: "MEMORY.md"
                                if (dirty) pendingOpen = path
                                else scope.launch { files = vm.roleMemoryFiles(); open(path) }
                            },
                        )
                        MochiListRow(
                            title = stringResource(R.string.role_memory_save),
                            leading = { MemoryEntryIcon(saveIcon) },
                            enabled = !busy && dirty,
                            onClick = { save() },
                        )
                    }
                }
                if (current != null) {
                    if (current.path == "MEMORY.md") {
                        item(key = "budget") {
                            val loaded = minOf(draft.length, RoleMemoryRepository.CURATED_BUDGET)
                            MochiListCard {
                                Column(
                                    Modifier.padding(MochiListDefaults.RowPadding),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth()) {
                                        MochiText(
                                            stringResource(R.string.role_memory_budget),
                                            style = MochiTheme.textStyles.body,
                                            color = MochiTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f).alignByBaseline(),
                                        )
                                        MochiText(
                                            stringResource(R.string.role_memory_budget_chars, loaded, RoleMemoryRepository.CURATED_BUDGET),
                                            style = MochiTheme.textStyles.subhead,
                                            color = MochiTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                    }
                                    MochiProgressBar(loaded.toFloat() / RoleMemoryRepository.CURATED_BUDGET)
                                }
                            }
                        }
                    }
                    item(key = "editor") {
                        val label = when {
                            current.path == "MEMORY.md" -> stringResource(R.string.role_memory_curated)
                            current.path.startsWith("daily/") -> stringResource(R.string.role_memory_daily_log)
                            else -> stringResource(R.string.role_memory_topic)
                        }
                        // 小标题 = 原输入框的 label，说明 = 保存状态 / 字数；输入框底色交给卡片
                        MochiListSection(
                            header = label,
                            footer = if (busy) stringResource(R.string.role_memory_saving) else if (dirty) stringResource(R.string.role_memory_unsaved) else stringResource(R.string.role_memory_chars, current.content.length),
                        ) {
                            MochiTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = EditorMinHeight)
                                    .padding(vertical = 6.dp),
                                placeholder = label,
                                enabled = !busy,
                                singleLine = false,
                                maxLines = 22,
                                clearButton = false,
                                textStyle = MochiTheme.textStyles.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                colors = MochiTextFieldDefaults.colors(container = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmExit) {
        AppDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.role_memory_discard_changes)) },
            confirmButton = { MochiTextButton(onClick = {
                confirmExit = false
                navigator.popBackStack()
            }) { Text(stringResource(R.string.role_memory_discard_and_back)) } },
            dismissButton = { MochiTextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.role_memory_keep_editing)) } },
        )
    }
    pendingOpen?.let { target ->
        AppDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text(stringResource(R.string.role_memory_discard_changes)) },
            confirmButton = { MochiTextButton(onClick = {
                pendingOpen = null
                scope.launch { open(target) }
            }) { Text(stringResource(R.string.role_memory_discard_and_open)) } },
            dismissButton = { MochiTextButton(onClick = { pendingOpen = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    conflict?.let { latest ->
        AppDialog(
            onDismissRequest = { conflict = null },
            title = { Text(stringResource(R.string.role_memory_file_changed)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.role_memory_disk_version), style = MaterialTheme.typography.titleMedium)
                    Text(latest.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { MochiTextButton(onClick = {
                document = latest; draft = latest.content; conflict = null
            }) { Text(stringResource(R.string.role_memory_reload)) } },
            dismissButton = { MochiTextButton(onClick = { save(overwrite = true) }, enabled = !busy) { Text(stringResource(R.string.role_memory_overwrite_disk)) } },
        )
    }
    newTopic?.let { name ->
        AppDialog(
            onDismissRequest = { newTopic = null },
            title = { Text(stringResource(R.string.role_memory_new_topic)) },
            text = {
                MochiDialogTextField(value = name, onValueChange = { newTopic = it },
                    placeholder = stringResource(R.string.role_memory_filename), autoFocus = false)
            },
            confirmButton = { MochiTextButton(onClick = {
                val path = "topics/$name.md"
                newTopic = null
                if (document?.content != draft) pendingOpen = path else scope.launch { open(path) }
            }, enabled = Regex("[A-Za-z0-9_-]{1,80}").matches(name)) { Text(stringResource(R.string.role_memory_create)) } },
            dismissButton = { MochiTextButton(onClick = { newTopic = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 分组之间的间距；mochiListSection / 懒加载里相邻卡片的间距，同值提供给 LocalMochiListItemSpacing */
private val MemorySectionSpacing = 16.dp

/** 编辑框最小高度：约 12 行等宽正文（原 minLines = 12） */
private val EditorMinHeight = 240.dp

@Composable
private fun MemoryEntryIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MochiTheme.colorScheme.primary,
    )
}
