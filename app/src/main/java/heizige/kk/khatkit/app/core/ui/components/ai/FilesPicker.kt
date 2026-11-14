package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
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
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.book4
import heizige.kk.khatkit.app.core.ui.icons.extension
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.derivedStateOf
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSectionHeader
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

    // 图标一律走 mochi-icons（接入规范：不要引 Material Icons / 手画 Path）：
    // 名字见 mochi-icons 的 ionicons 目录。
    val attachments = buildList {
        // 拍照去掉了：媒体选择器网格的第一格就是拍照。
        add(Attachment("images", stringResource(R.string.photo), onPickImage))
        if (provider is ProviderSetting.Google) {
            add(Attachment("videocam", stringResource(R.string.video), onPickVideo))
            add(Attachment("musical-notes", stringResource(R.string.audio), onPickAudio))
        }
        add(Attachment("document-attach", stringResource(R.string.tool_ui_file), onPickFile))
        onStartVoiceMode?.let { add(Attachment("mic", stringResource(R.string.chat_page_voice_title), it)) }
        add(
            Attachment(
                "shield-checkmark",
                stringResource(R.string.setting_page_permissions),
            ) {
                onDismiss()
                navController.navigate(Screen.SettingPermissions)
            },
        )
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
                leadingContent = { MochiIonicon("folder", size = 22.dp) },
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
                leadingContent = { MochiIonicon("albums", size = 22.dp) },
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
                leadingContent = { MochiIonicon("archive", size = 22.dp) },
                titleContent = { Text(stringResource(R.string.chat_page_compress_context)) },
                trailingContent = if (conversation.messageNodes.isNotEmpty()) {
                    { ToolCount(stringResource(R.string.chat_page_message_count, conversation.messageNodes.size)) }
                } else null,
            )
        }
    }

    // 附件（拍照排第一张）+ 会话工具：面板头「更多选项」已经说明这是什么，不再加分组小标题。
    // 左右各让出 RowPadding，底部留 SectionSpacing。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MochiListDefaults.RowPadding,
                end = MochiListDefaults.RowPadding,
                bottom = MochiListDefaults.SectionSpacing,
            ),
        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
    ) {
        AttachmentChips(attachments)
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

private data class Attachment(val icon: String, val label: String, val onClick: () -> Unit)

/**
 * 附件动作：一排 Mochi 芯片（工具台同款 [MochiChipGroup]：单行横向滚动、手指按在芯片上拖动时
 * 拖动交给滚动容器，不会误触选中）。
 *
 * 顺序照图片选择器：**拍照占第一张图片的位置**，然后才是相册选照片，往后是视频 / 音频 / 文件 / 语音。
 */
@Composable
private fun AttachmentChips(items: List<Attachment>) {
    // 外层已经使用 RowPadding；去掉 ChipGroup 默认的 16dp 内缩，让芯片背景
    // 与下方 OptionItem 卡片的左边缘对齐。
    MochiChipGroup(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        items.forEach { item ->
            MochiChip(
                label = item.label,
                selected = false,
                onClick = item.onClick,
                leadingIcon = { MochiIonicon(item.icon, size = 16.dp) },
            )
        }
    }
}

/** 工具行尾部的计数，和 MCP 那一行同一个写法。 */
@Composable
private fun ToolCount(text: String) {
    Text(
        text,
        style = MochiTheme.textStyles.footnote,
        color = MochiTheme.colorScheme.onSurfaceVariant,
    )
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
        leadingContent = { MochiIonicon("cube", size = 22.dp) },
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
                        MochiIonicon("settings", size = 20.dp)
                    }
                    if (boundWorkspace.shellStatus != WorkspaceShellStatus.DISABLED.name) {
                        MochiIconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }, shapes = IconButtonDefaults.shapes()) {
                            MochiIonicon("terminal", size = 20.dp)
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
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val navController = LocalNavController.current
    val toolProvider = rememberAppEntryPoint().khatKitToolProvider()
    var terminalAvailable by remember { mutableStateOf(false) }
    var terminalShortcutEnabled by remember {
        mutableStateOf(toolProvider.widgetEnabled("__khatkit_terminal"))
    }
    LaunchedEffect(toolProvider) {
        terminalAvailable = toolProvider.terminalPluginAvailable()
        terminalShortcutEnabled = toolProvider.widgetEnabled("__khatkit_terminal")
    }

    // U7b-6：直连 MochiSheet（state 由调用方持有）。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(R.string.assistant_page_tab_extensions))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (terminalAvailable) {
                CardGroup(
                    modifier = Modifier.fillMaxWidth(),
                    title = { Text("插件") },
                ) {
                    item(
                        onClick = {
                            val next = !terminalShortcutEnabled
                            terminalShortcutEnabled = next
                            toolProvider.setWidgetEnabled("__khatkit_terminal", next)
                        },
                        leadingContent = { Icon(terminal, null) },
                        headlineContent = { Text("欢迎页快捷方式") },
                        supportingContent = {
                            Text(
                                "终端插件",
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            KkSwitch(
                                checked = terminalShortcutEnabled,
                                onCheckedChange = {
                                    terminalShortcutEnabled = it
                                    toolProvider.setWidgetEnabled("__khatkit_terminal", it)
                                },
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CardGroup(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.extensions_page_section_extensions)) },
            ) {
                item(
                    onClick = {
                        onDismissAll()
                        navController.navigate(Screen.QuickMessages)
                    },
                    chevron = true,
                    leadingContent = { Icon(bolt, null) },
                    headlineContent = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                    supportingContent = {
                        Text(stringResource(R.string.extensions_page_quick_messages_desc))
                    },
                )
                item(
                    onClick = {
                        onDismissAll()
                        navController.navigate(Screen.Prompts)
                    },
                    chevron = true,
                    leadingContent = { Icon(book4, null) },
                    headlineContent = { Text(stringResource(R.string.extensions_page_prompts)) },
                    supportingContent = {
                        Text(stringResource(R.string.extensions_page_prompts_desc))
                    },
                )
                item(
                    onClick = {
                        onDismissAll()
                        navController.navigate(Screen.Skills)
                    },
                    chevron = true,
                    leadingContent = { Icon(extension, null) },
                    headlineContent = { Text(stringResource(R.string.extensions_page_agent_skills)) },
                    supportingContent = {
                        Text(stringResource(R.string.extensions_page_agent_skills_desc))
                    },
                )
                item(
                    onClick = {
                        onDismissAll()
                        navController.navigate(Screen.Workspaces)
                    },
                    chevron = true,
                    leadingContent = { Icon(folder, null) },
                    headlineContent = { Text(stringResource(R.string.extensions_page_workspace)) },
                    supportingContent = {
                        Text(stringResource(R.string.extensions_page_workspace_desc))
                    },
                )
                item(
                    onClick = {
                        onDismissAll()
                        navController.navigate(Screen.Extensions)
                    },
                    chevron = true,
                    leadingContent = { Icon(inventory2, null) },
                    headlineContent = { Text(stringResource(R.string.extensions_page_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.setting_page_extensions_desc))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
