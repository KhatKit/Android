package heizige.kk.khatkit.app.feature.assistant.detail

import heizige.kk.khatkit.app.LocalPageRoute
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
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.MochiTextTooltipBox
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryConflict
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryDocument
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryRepository
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.save as saveIcon
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

    MochiSettingsPageScaffold(
        title = "记忆",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            MochiIconButton(onClick = {
                if (dirty) confirmExit = true else navigator.popBackStack()
            }, enabled = !busy) { Icon(arrowBack, "返回") }
        },
        actions = {
                MochiTextTooltipBox("新建主题笔记") {
                    MochiIconButton(onClick = { newTopic = "" }, enabled = !busy) { Icon(add, "新建主题笔记") }
                }
                MochiTextTooltipBox("打开记忆文件夹") {
                    MochiIconButton(onClick = {
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
                            title = "角色记忆",
                            onClick = { vm.update(assistant.copy(enableMemory = !assistant.enableMemory, useGlobalMemory = false)) },
                            trailing = {
                                MochiSwitch(checked = assistant.enableMemory, onCheckedChange = {
                                    vm.update(assistant.copy(enableMemory = it, useGlobalMemory = false))
                                })
                            },
                        )
                        MochiListRow(
                            title = "自动整理",
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
                            title = "记忆文件",
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
                            title = "重新加载",
                            leading = { MemoryEntryIcon(refresh) },
                            enabled = !busy,
                            onClick = {
                                val path = document?.path ?: "MEMORY.md"
                                if (dirty) pendingOpen = path
                                else scope.launch { files = vm.roleMemoryFiles(); open(path) }
                            },
                        )
                        MochiListRow(
                            title = "保存",
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
                                            "加载预算",
                                            style = MochiTheme.textStyles.body,
                                            color = MochiTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f).alignByBaseline(),
                                        )
                                        MochiText(
                                            "$loaded / ${RoleMemoryRepository.CURATED_BUDGET} 字符",
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
                            current.path == "MEMORY.md" -> "精选记忆"
                            current.path.startsWith("daily/") -> "每日日志"
                            else -> "主题笔记"
                        }
                        // 小标题 = 原输入框的 label，说明 = 保存状态 / 字数；输入框底色交给卡片
                        MochiListSection(
                            header = label,
                            footer = if (busy) "正在保存…" else if (dirty) "未保存" else "${current.content.length} 字符",
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
            title = { Text("放弃未保存的更改？") },
            confirmButton = { MochiTextButton(onClick = {
                confirmExit = false
                navigator.popBackStack()
            }) { Text("放弃并返回") } },
            dismissButton = { MochiTextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
        )
    }
    pendingOpen?.let { target ->
        AppDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text("放弃未保存的更改？") },
            confirmButton = { MochiTextButton(onClick = {
                pendingOpen = null
                scope.launch { open(target) }
            }) { Text("放弃并打开") } },
            dismissButton = { MochiTextButton(onClick = { pendingOpen = null }) { Text("取消") } },
        )
    }
    conflict?.let { latest ->
        AppDialog(
            onDismissRequest = { conflict = null },
            title = { Text("文件已被修改") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("磁盘版本", style = MaterialTheme.typography.titleMedium)
                    Text(latest.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { MochiTextButton(onClick = {
                document = latest; draft = latest.content; conflict = null
            }) { Text("重新加载") } },
            dismissButton = { MochiTextButton(onClick = { save(overwrite = true) }, enabled = !busy) { Text("覆盖磁盘版本") } },
        )
    }
    newTopic?.let { name ->
        AppDialog(
            onDismissRequest = { newTopic = null },
            title = { Text("新建主题笔记") },
            text = {
                MochiDialogTextField(value = name, onValueChange = { newTopic = it },
                    placeholder = "文件名", autoFocus = false)
            },
            confirmButton = { MochiTextButton(onClick = {
                val path = "topics/$name.md"
                newTopic = null
                if (document?.content != draft) pendingOpen = path else scope.launch { open(path) }
            }, enabled = Regex("[A-Za-z0-9_-]{1,80}").matches(name)) { Text("创建") } },
            dismissButton = { MochiTextButton(onClick = { newTopic = null }) { Text("取消") } },
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
