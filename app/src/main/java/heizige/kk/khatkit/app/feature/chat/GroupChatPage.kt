package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.QRCode
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlin.uuid.Uuid

@Composable
fun GroupOrDirectPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid?,
    messageId: Uuid?,
) {
    val repo = rememberAppEntryPoint().conversationRepository()
    var type by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        type = repo.getConversationById(id)?.type ?: GroupChat.TYPE_DIRECT
    }
    when (type) {
        null -> Unit
        GroupChat.TYPE_GROUP -> GroupChatPage(id)
        else -> ChatPage(id = id, text = text, files = files, nodeId = nodeId, messageId = messageId)
    }
}

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    val chat: ChatManager,
) : ViewModel()

/**
 * 把群成员摊成角色卡元数据（[RoleCardMeta]），随 [GroupChat.encodeQr] 一起写进分享载荷。
 *
 * **实际取到什么**（逐字段）：
 * - `role_id` / `assistant_id` / `card_id`：直接来自群配置的 `GroupRole`，不加工；
 * - `name`：优先群配置里的 `role.name`，为空时退回它指向的助手的 `name`；
 * - `persona`：取 `role.assistantId` 指向的助手的 `systemPrompt`。解析方式与 `ChatManager`
 *   发言时挑角色助手完全一致（`Settings.getAssistantById(Uuid.parse(role.assistantId))`），
 *   所以二维码里的 persona 就是该角色真正会用的那段系统提示词；
 * - `avatar_ref`：留 null——助手头像存的是 `Avatar`（本地文件 URI 或远程 URL），
 *   不是可移植引用，写进载荷会让对方拿到一个指不到东西的路径。
 *
 * 取不到真实角色卡时（`assistantId` 不是合法 Uuid、助手已被删除）**照样生成二维码**，
 * 退化成 `role.name` + 空 `persona`，不因为缺角色卡就把整个二维码入口禁掉。
 */
private fun roleCards(roles: List<GroupRole>, settings: Settings): List<RoleCardMeta> =
    roles.map { role ->
        val assistant = runCatching { Uuid.parse(role.assistantId) }.getOrNull()
            ?.let { settings.getAssistantById(it) }
        RoleCardMeta(
            roleId = role.id,
            name = role.name.ifBlank { assistant?.name.orEmpty() },
            assistantId = role.assistantId,
            cardId = role.cardId,
            persona = assistant?.systemPrompt.orEmpty(),
        )
    }

@Composable
fun GroupChatPage(id: Uuid, vm: GroupChatViewModel = hiltViewModel()) {
    val conversation by vm.chat.getConversationFlow(id).collectAsStateWithLifecycle()
    val config = conversation.groupConfig
    var draft by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 分享载荷只算一次：二维码与 ACTION_SEND 文本分享共用同一份，不各算各的。
    val settings by rememberAppEntryPoint().settingsStore().settingsFlow
        .collectAsStateWithLifecycle()
    val sharePayload = remember(config, settings) {
        config?.let { groupConfig ->
            // encodeQr 命中密钥黑名单会抛 IllegalStateException（check），这里兜住不让页面崩。
            runCatching { GroupChat.encodeQr(groupConfig, roleCards(groupConfig.roles, settings)) }.getOrNull()
        }
    }

    KedgeSettingsPageScaffold(
        title = conversation.title.ifBlank { "群聊" },
        navigationIcon = { BackButton() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildString {
                    append(config?.mode ?: GroupChat.MODE_PIPELINE)
                    append(" · 预算 ")
                    append(config?.tokenBudgetPerRound ?: 0)
                },
                style = KedgeTextStyles.footnoteSmall(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                config?.roles.orEmpty().forEach { role ->
                    KedgeFilterChip(
                        selected = false,
                        onClick = { draft = draft + "@${role.name} " },
                        label = { Text(if (role.chair) "${role.name} 议长" else role.name) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(conversation.currentMessages, key = { it.id.toString() }) { message ->
                    val who = config?.roles?.firstOrNull { it.id == message.roleId }?.name
                        ?: if (message.roleId == GroupChat.SUMMARY_ID) "多数决" else message.role.name
                    Text(who, style = KedgeTextStyles.footnoteSmall(), fontWeight = FontWeight.SemiBold)
                    Text(message.toText().ifBlank { "…" }, style = KedgeTextStyles.body())
                }
            }
            KedgeOutlinedTextFieldWithSlots(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("消息") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KedgeTextButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isEmpty()) return@KedgeTextButton
                        vm.chat.sendMessage(id, listOf(UIMessagePart.Text(text)))
                        draft = ""
                    },
                ) { Text("发送") }
                KedgeTextButton(
                    onClick = {
                        val raw = sharePayload ?: return@KedgeTextButton
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, raw)
                        }
                        context.startActivity(Intent.createChooser(send, "分享群配置"))
                    },
                    enabled = sharePayload != null,
                ) { Text("分享") }
                KedgeTextButton(
                    onClick = { showQr = !showQr },
                    enabled = sharePayload != null,
                ) { Text(if (showQr) "收起二维码" else "生成二维码") }
            }
            if (showQr) {
                val payload = sharePayload
                if (payload == null) {
                    Text("当前群配置无法编码为分享载荷", style = KedgeTextStyles.footnoteSmall())
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        QRCode(
                            value = payload,
                            modifier = Modifier.size(220.dp),
                        )
                        Text(
                            text = "载荷 ${payload.length} 字节 · ${config?.roles?.size ?: 0} 个角色",
                            style = KedgeTextStyles.footnoteSmall(),
                        )
                    }
                }
            }
            KedgeOutlinedTextFieldWithSlots(
                value = importText,
                onValueChange = { importText = it },
                label = { Text("导入群配置 JSON") },
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeTextButton(
                onClick = {
                    val restored = GroupChat.decodeQr(importText) ?: return@KedgeTextButton
                    scope.launch {
                        val current = vm.chat.getConversationFlow(id).value
                        vm.chat.saveConversation(
                            id,
                            current.copy(
                                type = GroupChat.TYPE_GROUP,
                                groupConfig = restored,
                                title = current.title.ifBlank { "群聊" },
                            ),
                        )
                    }
                },
            ) { Text("恢复群配置") }
        }
    }
}
