package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.hooks.EditStateContent
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.chat
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.sms
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

/**
 * 角色聊天面板：聊天页右上角三横杠的落地页。
 *
 * 三块内容全部是 option item（[CardGroup] + `item(...)` 或逐行 [KedgeOptionItem]，
 * Miuix/MD3 由 Kedge 组件分流）：
 * ① 当前角色配置信息（头像 / 名字 / 模型 / 系统提示摘要 + 角色设置入口）；
 * ② 该角色的历史聊天（`assistantId == roleId` 且 direct，**每行是外层 LazyColumn 的独立
 *    item**，不再整段塞进一个 `item {}`）；
 * ③ 「新建聊天」与「重命名当前聊天」。
 *
 * 「重命名当前聊天」是从 ChatPage 顶栏「长按标题改名」搬过来的：会话标题已经不上顶栏，
 * 但改名能力不能跟着消失，所以放在这里，仍然用 [useEditState] + [AppAlertDialog] 那套弹窗。
 * 该行**常驻**，会话未落库/还没有消息时不可点，并用一条与旧文案等价的说明代替动作说明。
 *
 * @param roleId 当前角色（assistant id）。由聊天页顶栏已解析出的角色 id 传入 —— 顶栏显示谁，
 *   面板就查谁，不在面板里二次猜测。
 * @param conversationId 打开面板时所在会话的 id，只用于「重命名当前聊天」。
 */
@Composable
fun ChatRolePanelPage(roleId: String, conversationId: String? = null) {
    val vm: ChatRolePanelViewModel = hiltViewModel<ChatRolePanelViewModel, ChatRolePanelViewModel.Factory>(
        creationCallback = { it.create(roleId) }
    )
    val appSettings by vm.settings.collectAsStateWithLifecycle()
    val roleChats by vm.roleChats.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val cards = listCardStyle()

    val roleUuid = remember(roleId) { runCatching { Uuid.parse(roleId) }.getOrNull() }
    val currentConversationUuid = remember(conversationId) {
        conversationId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    }
    val role = roleUuid?.let { appSettings.getAssistantById(it) }

    // 角色不存在时给**可见提示**，不静默回退到「当前角色」（那样面板显示的角色和顶栏不一致）。
    if (role == null) {
        KedgeSettingsPageScaffold(
            title = stringResource(R.string.chat_role_panel_title),
            scrollBehavior = scrollBehavior,
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(8.dp),
            ) {
                item {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                        item(
                            leadingContent = { Icon(settings, null) },
                            headlineContent = { Text(stringResource(R.string.chat_role_panel_role_missing)) },
                            supportingContent = { Text(stringResource(R.string.chat_role_panel_role_missing_desc)) },
                        )
                    }
                }
            }
        }
        return
    }

    val model = appSettings.findModelById(role.chatModelId ?: appSettings.chatModelId)
    val roleName = role.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
    val modelName = model?.displayName ?: stringResource(R.string.chat_role_panel_no_model)

    // 「重命名当前聊天」的可用性/初始标题：取 DB 全量会话（带 messageNodes），
    // 历史列表那些行没有节点，判断不了「有没有消息」。
    var renameTarget by remember(currentConversationUuid) {
        mutableStateOf<RolePanelRenameTarget?>(null)
    }
    LaunchedEffect(currentConversationUuid) {
        renameTarget = currentConversationUuid?.let { vm.loadRenameTarget(it) }
    }
    val renameState = useEditState<String> { title ->
        currentConversationUuid?.let { id ->
            vm.renameConversation(id, title)
            renameTarget = renameTarget?.copy(title = title.trim())
        }
    }
    renameState.EditStateContent { value, onUpdate ->
        AppAlertDialog(
            onDismissRequest = { renameState.dismiss() },
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = {
                KedgeOutlinedTextField(
                    value = value,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = { renameState.confirm() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                KedgeTextButton(
                    onClick = { renameState.dismiss() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    // 「新建聊天」时角色已被删除（设置里没了）：不静默失败，落一条可见提示。
    var roleGoneHint by remember { mutableStateOf(false) }

    KedgeSettingsPageScaffold(
        title = roleName,
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(bottom = 24.dp),
        ) {
            // ① 当前角色配置信息
            item {
                RolePanelHeader(
                    roleName = roleName,
                    avatar = role.avatar,
                    avatarShape = role.avatarShape,
                    avatarColor = role.avatarColor,
                    providerName = appSettings.findModelProviderName(
                        role.chatModelId ?: appSettings.chatModelId
                    ),
                    modelName = modelName,
                    systemPrompt = role.systemPrompt,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp),
                    title = { Text(stringResource(R.string.chat_role_panel_section_role)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.AssistantDetail(roleId)) },
                        leadingContent = { Icon(settings, null) },
                        headlineContent = { Text(stringResource(R.string.chat_role_panel_role_settings)) },
                        supportingContent = { Text(stringResource(R.string.chat_role_panel_role_settings_desc)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantPrompt(roleId)) },
                        leadingContent = { Icon(sms, null) },
                        headlineContent = { Text(stringResource(R.string.chat_role_panel_prompt)) },
                        supportingContent = { Text(stringResource(R.string.chat_role_panel_prompt_desc)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMemory(roleId)) },
                        leadingContent = { Icon(psychology, null) },
                        headlineContent = { Text(stringResource(R.string.chat_role_panel_memory)) },
                        supportingContent = { Text(stringResource(R.string.chat_role_panel_memory_desc)) },
                        trailingContent = { Icon(arrowForward, null) },
                    )
                }
            }

            // ② 该角色历史聊天：分段标题单独一行，每个会话是外层 LazyColumn 的独立 item
            //    （按需组合/回收），行的形状仍由 listCardStyle().indexedShape 算，与 CardGroup
            //    里的 option item 逐像素一致。
            item {
                RolePanelSectionTitle(stringResource(R.string.chat_role_panel_section_history))
            }
            if (roleChats.isEmpty()) {
                item {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        item(
                            leadingContent = { Icon(chat, null) },
                            headlineContent = { Text(stringResource(R.string.chat_role_panel_history_empty)) },
                            supportingContent = { Text(stringResource(R.string.chat_role_panel_history_empty_desc)) },
                        )
                    }
                }
            } else {
                itemsIndexed(roleChats, key = { _, conversation -> conversation.id.toString() }) { index, conversation ->
                    KedgeOptionItem(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .padding(bottom = if (index == roleChats.lastIndex) 0.dp else cards.gap),
                        onClick = { navigateToChatPage(navController, conversation.id) },
                        shape = cards.indexedShape(index, roleChats.size),
                        leadingContent = { Icon(chat, null) },
                        titleContent = {
                            Text(
                                text = conversation.title.ifBlank {
                                    stringResource(R.string.chat_page_new_chat)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(rolePanelTimeLabel(conversation.updateAt)) },
                    )
                }
            }

            // ③ 新建聊天 + 重命名当前聊天（原顶栏长按标题的入口）
            if (roleGoneHint) {
                item {
                    Text(
                        text = stringResource(R.string.chat_role_panel_role_missing),
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
            }
            item {
                val canRename = renameTarget?.canRename == true
                CardGroup(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp),
                    title = { Text(stringResource(R.string.chat_role_panel_section_actions)) },
                ) {
                    item(
                        onClick = {
                            vm.startNewChat { started ->
                                if (started) {
                                    // 新会话由 ChatPage 按「当前角色」现造（见 VM 注释），
                                    // 这里只负责跳一个全新 id。
                                    navigateToChatPage(navController)
                                } else {
                                    roleGoneHint = true
                                }
                            }
                        },
                        leadingContent = { Icon(add, null) },
                        headlineContent = { Text(stringResource(R.string.chat_role_panel_new_chat)) },
                        supportingContent = { Text(stringResource(R.string.chat_role_panel_new_chat_desc)) },
                    )
                    // 改名入口**常驻**：不可改时只去掉点击（onClick = null 会让
                    // KedgeOptionItem 关掉按压回弹），并用与旧文案等价的说明代替动作说明。
                    item(
                        onClick = if (canRename) {
                            { renameState.open(renameTarget?.title.orEmpty()) }
                        } else {
                            null
                        },
                        leadingContent = { Icon(edit, null) },
                        headlineContent = { Text(stringResource(R.string.chat_role_panel_rename_chat)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (canRename) {
                                        R.string.chat_role_panel_rename_chat_desc
                                    } else {
                                        R.string.chat_role_panel_rename_chat_unavailable
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 分段标题：与 `CardGroup` 的 title 同一套字阶/取色（那边由 KedgeSegmentedListSlots 画）。 */
@Composable
private fun RolePanelSectionTitle(text: String) {
    Box(
        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.54f),
            style = KedgeTextStyles.title().copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            ),
        )
    }
}

/** 面板头部：头像 + 角色名 + 模型名 + 系统提示摘要（与控制台里的助手详情页同一套写法）。 */
@Composable
private fun RolePanelHeader(
    roleName: String,
    avatar: Avatar,
    avatarShape: String,
    avatarColor: String?,
    providerName: String?,
    modelName: String,
    systemPrompt: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UIAvatar(
            name = roleName,
            value = avatar,
            avatarShape = avatarShape,
            avatarColor = avatarColor,
            defaultIconName = providerName,
            onUpdate = null,
            modifier = Modifier.size(80.dp),
        )
        Text(
            text = roleName,
            style = KedgeTextStyles.displayTitle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = modelName,
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (systemPrompt.isNotBlank()) {
            Text(
                text = systemPrompt.take(120) + if (systemPrompt.length > 120) "..." else "",
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 历史聊天行右侧的时间：今天只显示时分，跨天显示 `月/日`。
 *
 * 不复用 ChatDrawer 里那份私有实现（`drawerSearchTimeLabel`）—— 它在另一个正在改的
 * 文件里，且带「昨天」这种只有中文的硬编码。
 */
private fun rolePanelTimeLabel(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (local.toLocalDate()) {
        today -> local.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> local.format(DateTimeFormatter.ofPattern("M/d"))
    }
}
