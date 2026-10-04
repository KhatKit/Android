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
import heizige.kk.khatkit.app.core.data.model.GroupImportResult
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.QRCode
import heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheet
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

/**
 * 群聊页：发言、导出（二维码 / 文本分享）与导入（扫码 / 粘贴）。
 *
 * 导出与导入走同一个载荷：导出是 [GroupChat.encodeQr]，导入是 [GroupChat.importShare]。
 * 中间不经过 [GroupChat.decodeQr]——它不校验 `schema_version`、不跑密钥黑名单，还丢掉
 * `cards`，用它当导入入口等于把不安全的数据直接写进库。
 *
 * ## 遗留：导入的 cards 暂未落库
 *
 * [GroupChat.importShare] 成功时返回的 [GroupSharePayload.cards]（角色卡元数据）**只显示、
 * 不持久化**。原因：`Conversation` 只有 `groupConfig: GroupConfig?` 一个群相关字段
 * （`core/data/model/Conversation.kt:35`），数据库侧也只有 `conversationentity.group_config`
 * 一列（`ConversationEntity.kt:37`），`GroupConfigSerializer` 的契约里也没有 cards 的位置。
 * 要落库就得动 `Conversation` / `ConversationEntity` / `ConversationDAO` 与迁移脚本，
 * 超出本次改动范围。因此这里把 cards 列出来让用户至少能确认「导入了什么」，
 * 群配置的落库内容仍然是纯 `GroupConfig`。
 */
@Composable
fun GroupChatPage(id: Uuid, vm: GroupChatViewModel = hiltViewModel()) {
    val conversation by vm.chat.getConversationFlow(id).collectAsStateWithLifecycle()
    val config = conversation.groupConfig
    var draft by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<GroupImportResult?>(null) }
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

    // 扫码与粘贴共用这一个入口：两条路径的校验口径必须完全一致（decodeQr 既不校验
    // schema_version 也不跑密钥黑名单，还会丢掉 cards，绝不能用来导入）。
    // 只有 Accepted 才写库；Rejected 一律不落库，只把 reason 与逐条 fieldErrors 显示出来。
    val applyImport: (String) -> Unit = { raw ->
        when (val result = GroupChat.importShare(raw, id.toString())) {
            is GroupImportResult.Accepted -> {
                importResult = result
                scope.launch {
                    val current = vm.chat.getConversationFlow(id).value
                    vm.chat.saveConversation(
                        id,
                        current.copy(
                            type = GroupChat.TYPE_GROUP,
                            groupConfig = result.payload.config,
                            title = current.title.ifBlank { "群聊" },
                        ),
                    )
                }
            }

            is GroupImportResult.Rejected -> importResult = result
        }
    }

    if (showScanner) {
        // QrScannerSheet 自己用仓里的权限范式（rememberPermissionState + PermissionManager，
        // 见 QrScannerSheet.kt:191）申请相机权限；权限被拒或设备无相机时回调 onResult(null)，
        // 所以本页不重复申请权限、不硬编码 Manifest，只把 null 落回「手动粘贴」这条入口。
        QrScannerSheet(
            onResult = { scanned ->
                showScanner = false
                if (scanned != null) applyImport(scanned)
            },
            onDismiss = { showScanner = false },
        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KedgeTextButton(onClick = { applyImport(importText) }) { Text("恢复群配置") }
                KedgeTextButton(onClick = { showScanner = true }) { Text("扫码导入") }
            }
            when (val result = importResult) {
                null -> Unit

                is GroupImportResult.Rejected -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "导入被拒：${result.reason}",
                        style = KedgeTextStyles.footnoteSmall(),
                        color = MaterialTheme.colorScheme.error,
                    )
                    // field 已是契约 snake_case 键，直接展示，不做键名映射。
                    result.fieldErrors.forEach { error ->
                        Text(
                            text = "· ${error.field}：${error.message}",
                            style = KedgeTextStyles.footnoteSmall(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is GroupImportResult.Accepted -> {
                    val payload = result.payload
                    Text(
                        text = "已导入 ${payload.config.roles.size} 个角色 · " +
                            "${payload.cards.size} 张角色卡",
                        style = KedgeTextStyles.footnoteSmall(),
                    )
                    // cards 目前没有落库位置（见下方说明），至少要让用户看见导入了什么。
                    payload.cards.forEach { card ->
                        Text(
                            text = "· ${card.name.ifBlank { card.roleId }}（${card.roleId}）" +
                                if (card.persona.isBlank()) "" else " · 含 persona",
                            style = KedgeTextStyles.footnoteSmall(),
                        )
                    }
                }
            }
        }
    }
}
