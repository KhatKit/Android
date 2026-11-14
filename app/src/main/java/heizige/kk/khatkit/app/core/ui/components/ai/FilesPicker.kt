package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.core.ui.components.ui.ExtensionSelector
import heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup
import heizige.kk.khatkit.app.core.ui.components.khatkit.TreeUriPaths
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.workspace.WorkspaceShellStatus
import heizige.kk.khatkit.app.feature.workspace.EditWorkspaceDialog
import java.io.File
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.ui.icons.folderCopy
import heizige.kk.khatkit.app.core.ui.icons.graphicEq
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.inventory2
import heizige.kk.khatkit.app.core.ui.icons.musicNote
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.terminal
import heizige.kk.khatkit.app.core.ui.icons.videocam
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

/**
 * 输入栏「+」弹出的面板。上下两块：
 *
 * - 附件：一排（多了就两排）等宽的方块，图标坐在主色淡底的圆里，按下是 Mochi 的按压形变；
 *   相机 / 照片 / 文件常驻，视频和音频只在当前模型是 Gemini 时出现，语音模式在 ASR 支持时出现。
 * - 会话工具：一张分组卡片（和设置页同一套 option item），工作区、工作目录、MCP、扩展、压缩历史，
 *   条目按当前条件增减，整组圆角跟着首末行走。
 */
@Composable
internal fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    mcpManager: McpManager,
    onCompressContext: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    showInjectionSheet: Boolean,
    onShowInjectionSheetChange: (Boolean) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
    onStartVoiceMode: (() -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val provider = settings.getCurrentChatModel()?.findProvider(providers = settings.providers)
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = rememberAppEntryPoint().workspaceRepository()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }
    var showCwdSheet by remember { mutableStateOf(false) }

    val attachments = buildList {
        add(Attachment(photoCamera, stringResource(R.string.take_picture), onTakePic))
        add(Attachment(image, stringResource(R.string.photo), onPickImage))
        if (provider is ProviderSetting.Google) {
            add(Attachment(videocam, stringResource(R.string.video), onPickVideo))
            add(Attachment(musicNote, stringResource(R.string.audio), onPickAudio))
        }
        add(Attachment(folderCopy, stringResource(R.string.upload_file), onPickFile))
        onStartVoiceMode?.let { add(Attachment(graphicEq, stringResource(R.string.chat_page_voice_title), it)) }
    }

    // 工具行按条件增减；先把要出现的行排好，再按序号给首末行圆角。
    // 即使还没有工作区，也保留入口，用户可以直接从设备选择文件夹。
    val showWorkspace = true
    val showCwd = boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name
    val showMcp = settings.mcpServers.isNotEmpty()
    val tools = buildList<@Composable (Shape) -> Unit> {
        if (showWorkspace) add { shape ->
            WorkspacePickerListItem(
                shape = shape,
                assistant = assistant,
                conversation = conversation,
                workspaces = workspaces,
                workspaceRepository = workspaceRepository,
                boundWorkspace = boundWorkspace,
                onUpdateAssistant = onUpdateAssistant,
                onUpdateConversation = onUpdateConversation,
                onNavigateToDetail = { id ->
                    onDismiss()
                    navController.navigate(Screen.WorkspaceDetail(id))
                },
                onNavigateToTerminal = { id ->
                    onDismiss()
                    navController.navigate(Screen.WorkspaceTerminal(id))
                },
                onNavigateToManage = {
                    onDismiss()
                    navController.navigate(Screen.Workspaces)
                },
            )
        }
        if (showCwd) add { shape ->
            MochiOptionItem(
                onClick = { showCwdSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                leadingContent = { Icon(folder, null) },
                titleContent = { Text(stringResource(R.string.chat_page_working_directory)) },
                supportingContent = {
                    Text(
                        conversation.workspaceCwd ?: "/workspace",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (showMcp) add { shape ->
            McpPickerListItem(
                shape = shape,
                assistant = assistant,
                servers = settings.mcpServers,
                mcpManager = mcpManager,
                onUpdateAssistant = onUpdateAssistant,
            )
        }
        add { shape ->
            // 扩展 = 快捷消息 + 提示注入（模式 / 世界书）+ 技能；尾部是已启用的个数。
            val modeAndLorebookCount =
                if (assistant.allowConversationPromptInjection) {
                    conversation.modeInjectionIds.size + conversation.lorebookIds.size
                } else {
                    assistant.modeInjectionIds.size + assistant.lorebookIds.size
                }
            val activeCount = assistant.quickMessageIds.size + modeAndLorebookCount + assistant.enabledSkills.size
            MochiOptionItem(
                onClick = { onShowInjectionSheetChange(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                leadingContent = { Icon(inventory2, null) },
                titleContent = { Text(stringResource(R.string.assistant_page_tab_extensions)) },
                trailingContent = if (activeCount > 0) {
                    { ToolCount(activeCount.toString()) }
                } else null,
            )
        }
        add { shape ->
            MochiOptionItem(
                onClick = { onShowCompressDialogChange(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                leadingContent = { Icon(package2, null) },
                titleContent = { Text(stringResource(R.string.chat_page_compress_context)) },
                trailingContent = if (conversation.messageNodes.isNotEmpty()) {
                    { ToolCount(stringResource(R.string.chat_page_message_count, conversation.messageNodes.size)) }
                } else null,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AttachmentGrid(attachments)
        val cards = listCardStyle()
        InlineCardGroup(Modifier.fillMaxWidth()) {
            tools.forEachIndexed { index, row -> row(cards.indexedShape(index, tools.size)) }
        }
    }

    if (boundWorkspace != null && showCwd) {
        // U7b-6：宿主已改 MochiSheet，state 由这里持有；不再用 if 门控（否则退场动画不播）。
        val cwdSheet = rememberMochiSheetState()
        LaunchedEffect(showCwdSheet) { if (showCwdSheet) cwdSheet.show() else cwdSheet.hide() }
        WorkspaceCwdPickerSheet(
            sheetState = cwdSheet,
            workspaceId = boundWorkspace.id,
            currentCwd = conversation.workspaceCwd,
            onSelectCwd = { newCwd ->
                onUpdateConversation(conversation.copy(workspaceCwd = newCwd))
            },
            onDismiss = { showCwdSheet = false },
        )
    }

    // Injection Bottom Sheet：U7b-6 起 state 由这里持有，去 if 门控。
    val injectionSheet = rememberMochiSheetState()
    LaunchedEffect(showInjectionSheet) { if (showInjectionSheet) injectionSheet.show() else injectionSheet.hide() }
    InjectionQuickConfigSheet(
            sheetState = injectionSheet,
            conversation = conversation,
            assistant = assistant,
            settings = settings,
            onUpdateAssistant = onUpdateAssistant,
            onUpdateConversation = onUpdateConversation,
            onDismiss = { onShowInjectionSheetChange(false) },
            onDismissAll = onDismiss,
        )

    // Compress Context Dialog
    if (showCompressDialog) {
        CompressContextDialog(onDismiss = {
            onShowCompressDialogChange(false)
            onDismiss()
        }, onConfirm = { additionalPrompt, targetTokens, keepRecentMessages ->
            onCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
        })
    }
}

private data class Attachment(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** 附件方块：四个以内一排排满；再多就分两排（5 → 3 + 2，6 → 3 + 3），末排不足的位置留空，方块宽度不变。 */
@Composable
private fun AttachmentGrid(items: List<Attachment>) {
    val columns = if (items.size <= 4) items.size else (items.size + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { AttachmentTile(it, Modifier.weight(1f)) }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AttachmentTile(item: Attachment, modifier: Modifier = Modifier) {
    val shape = MochiRoundedRectangle(listCardStyle().largeCorner)
    val accent = MochiTheme.colorScheme.primary
    Column(
        modifier
            .mochiPressDeform()
            .clip(shape)
            .background(MochiListDefaults.colors().container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = item.onClick,
            )
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).background(accent.copy(alpha = 0.12f), MochiCapsule), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, Modifier.size(22.dp), tint = accent)
        }
        Text(
            item.label,
            style = MochiTheme.textStyles.footnote,
            color = MochiTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 工具行尾部的计数，和 MCP 那一行同一个写法。 */
@Composable
private fun ToolCount(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun WorkspacePickerListItem(
    shape: Shape,
    assistant: Assistant,
    conversation: Conversation,
    workspaces: List<WorkspaceEntity>,
    workspaceRepository: WorkspaceRepository,
    boundWorkspace: WorkspaceEntity?,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var pendingDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val path = uri?.let { TreeUriPaths.fromTreeUri(context, it) } ?: return@rememberLauncherForActivityResult
        pendingDirectory = path
        showCreateDialog = true
    }

    fun selectWorkspace(workspaceId: String?) {
        val newId = workspaceId?.let { Uuid.parse(it) }
        if (newId != assistant.workspaceId) {
            onUpdateAssistant(assistant.copy(workspaceId = newId))
            if (conversation.workspaceCwd != null) {
                onUpdateConversation(conversation.copy(workspaceCwd = null))
            }
        }
        showSheet = false
    }

    MochiOptionItem(
        onClick = { showSheet = true },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = { Icon(deployedCode, null) },
        titleContent = { Text(stringResource(R.string.assistant_page_workspace)) },
        supportingContent = {
            Text(
                text = boundWorkspace?.name ?: stringResource(R.string.assistant_page_workspace_unbound),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (boundWorkspace != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MochiIconButton(onClick = { onNavigateToDetail(boundWorkspace.id) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(settings, contentDescription = stringResource(R.string.workspace_detail))
                    }
                    if (boundWorkspace.shellStatus != WorkspaceShellStatus.DISABLED.name) {
                        MochiIconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(terminal, contentDescription = stringResource(R.string.workspace_terminal))
                        }
                    }
                }
            }
        } else null,
    )

    // U7b-6：宿主已改 MochiSheet，state 由这里持有，去掉 if 门控让退场动画能播。
    val workspaceSheet = rememberMochiSheetState()
    LaunchedEffect(showSheet) { if (showSheet) workspaceSheet.show() else workspaceSheet.hide() }
    WorkspaceSelectSheet(
            sheetState = workspaceSheet,
            assistant = assistant,
            workspaces = workspaces,
            onSelect = ::selectWorkspace,
            onChooseDirectory = {
                showSheet = false
                directoryLauncher.launch(null)
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )

    pendingDirectory?.let { directory ->
        if (showCreateDialog) {
            EditWorkspaceDialog(
                title = stringResource(R.string.workspace_page_create),
                initialName = File(directory).name,
                existingNames = workspaces.map { it.name.trim() }.toSet(),
                directoryPath = directory,
                onDismiss = {
                    showCreateDialog = false
                    pendingDirectory = null
                },
                onConfirm = { name ->
                    scope.launch {
                        runCatching { workspaceRepository.create(name, directory) }
                            .onSuccess { workspace ->
                                showCreateDialog = false
                                pendingDirectory = null
                                selectWorkspace(workspace.id)
                            }
                    }
                },
            )
        }
    }
}

@Composable
private fun InjectionQuickConfigSheet(
    sheetState: ui.feice.mochi.basic.MochiSheetState,
    conversation: Conversation,
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val navController = LocalNavController.current

    // U7b-6：直连 MochiSheet（state 由调用方持有）。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(R.string.assistant_page_tab_extensions))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp),
        ) {
            ExtensionSelector(
                assistant = assistant,
                settings = settings,
                onUpdate = onUpdateAssistant,
                conversation = conversation,
                onUpdateConversation = onUpdateConversation,
                modifier = Modifier.weight(1f),
                onNavigateToQuickMessages = {
                    onDismissAll()
                    navController.navigate(Screen.QuickMessages)
                },
                onNavigateToPrompts = {
                    onDismissAll()
                    navController.navigate(Screen.Prompts)
                },
                onNavigateToSkills = {
                    onDismissAll()
                    navController.navigate(Screen.Skills)
                })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
