package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
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

@Composable
fun GroupChatPage(id: Uuid, vm: GroupChatViewModel = hiltViewModel()) {
    val conversation by vm.chat.getConversationFlow(id).collectAsStateWithLifecycle()
    val config = conversation.groupConfig
    var draft by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                        val raw = config?.let(GroupChat::encodeQr) ?: return@KedgeTextButton
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, raw)
                        }
                        context.startActivity(Intent.createChooser(send, "分享群配置"))
                    },
                ) { Text("分享") }
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
