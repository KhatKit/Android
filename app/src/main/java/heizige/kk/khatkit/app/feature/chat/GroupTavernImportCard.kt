package heizige.kk.khatkit.app.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import heizige.kk.khromia.helper.Toast as KhromiaToast
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeListItem
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.CodingErrorAction

/**
 * 群聊「导入 Tavern 群聊文件」卡片——[GroupTavernExportCard] 的**回导**那一半。
 *
 * ## 补的是哪个缺口
 *
 * 导出卡片（[GroupTavernExportCard]）的 supporting 文案写着「可在 SillyTavern 打开并回导」，
 * 但 `TavernChatCodec.importGroup` 在 `app/src/main` 里**零调用方**——回导这一步点不到。
 * 本卡片就是那个生产入口：选 `.jsonl` 文件（SAF `OpenDocument`）或粘贴文本，
 * 交给 [resolveTavernGroupImport] 判定，再按 [TavernGroupImportOutcome] 分支如实呈现。
 *
 * ## 判定与 UI 的分工
 *
 * **判定全在 [resolveTavernGroupImport] 里**（纯函数，可 JVM 测），本文件只负责：
 * 取字节 → 调它 → 按 `headline` / `detailLines` / `isError` 渲染 → 落库或弹 Toast。
 * 文案不在本文件另写一份，所以「`Normalized` 的 fixes 有没有被显示出来」是能被单测断言的。
 *
 * 四个报告分支的 UI 行为（判定见 [TavernGroupImportOutcome]）：
 *
 * | 报告 | UI 行为 | 落库 |
 * |---|---|---|
 * | `Clean` | 结果视图显示成功行 + 角色卡摘要 | 落库 |
 * | `Normalized` | 结果视图显示成功行**并逐条列出 `fixes`**（改前→改后） | 落库 |
 * | `NoConfig` | 结果视图显示「这份文件没有群配置块，沿用当前群配置」，**非错误配色** | 只落消息，沿用当前群配置 |
 * | `Rejected` | 结果视图显示原因 + 逐条字段级错误，错误配色 | **绝不落库** |
 *
 * ## 落库
 *
 * 只有 [TavernGroupImportOutcome.Accepted] 分支会走到 [applyTavernGroupImport]，而该函数
 * 的入参类型就是 `Accepted`——被拒收的分支根本没有可落库的载荷，密钥黑名单这道闸门
 * 绕不过不靠自觉，靠类型。判定里的 `GroupChat.validate(config, conversationId)` 已在
 * 构造 `Accepted` 之前跑过（见 `GroupTavernImport.kt` 的 ②）。
 *
 * ## 形状
 *
 * 卡片形状照 [GroupTavernExportCard]（`KedgeCard(onClick = …) { KedgeListItem(...) }`），
 * 按钮样式照 `GroupChatPage.kt:736` 的 `KedgeTextButton`，提示照 [KhromiaToast]。
 * 结果视图是**自己的一份**，不覆盖 `GroupChatPage` 里 `importShare` 的 `ImportResultView`
 * ——那是扫码/分享载荷那条路径的展示，两条路径的标题与前置条件不同。
 *
 * ⚠️ **本卡片在真机上没有被验证过**：文件选择器交互、SAF 读字节、Toast 呈现、
 * 落库后的真实 UI 刷新全部只有 JVM 层的判定测试覆盖。`docs/eval/c1-group-chat.md`
 * 十条用例的态列仍是 `unverified`。
 */

/**
 * 选文件用的 mime 类型。沿用仓库关于 JSON 的**唯一**既有约定 `application/json`
 * （`core/data/export/ExportHooks.kt:57` 的 `ACTION_SEND` 与同文件 `:85` 的
 * `ActivityResultContracts.CreateDocument`），直接复用导出侧 [GROUP_EXPORT_MIME_TYPE]
 * 这一个常量，**不引入新 mime**。
 *
 * 读文件用 [ActivityResultContracts.OpenDocument]（与 `ExportHooks.kt:139-140` 的
 * `rememberImporter` 同一份约定）而不是 `GetContent` / `ACTION_GET_CONTENT`：前者是
 * `ACTION_OPEN_DOCUMENT`，返回可长期持有的 `content://` uri，且系统会按 mime 过滤。
 * `.jsonl` 在 `MimeTypeMap` 里没有条目（见 [GROUP_EXPORT_MIME_TYPE] 的 KDoc），
 * 所以这里给的是 `application/json` 而不是 `application/x-ndjson`：过滤条件太窄会让
 * 一部分文件在选择器里根本不出现。
 */
internal const val TAVERN_IMPORT_MIME_TYPE = GROUP_EXPORT_MIME_TYPE

/** 单个酒馆群聊文件的大小上限（字节）。8 MiB 对群聊记录来说是极大的量，再大就不是聊天记录了。 */
internal const val TAVERN_IMPORT_MAX_BYTES = 8 * 1024 * 1024

/** UTF-8 BOM。`Json.parseToJsonElement` 见到它会直接抛，而 Windows 上的编辑器很容易留一个。 */
private const val UTF8_BOM = "\uFEFF"

/**
 * SAF 读回来的字节 → 文本。
 *
 * **非法 UTF-8 替换而不是抛异常**：SillyTavern 写出的文件里可能有截断的多字节序列。
 * 用 [CodingErrorAction.REPLACE] 解码能让坏字节变成 U+FFFD，之后由
 * [resolveTavernGroupImport] 判成 `Unsupported`（「不是酒馆群聊文件」）——用户看到一句
 * 能读懂的话，而不是一个从文件选择器里崩出来的 `MalformedInputException`。
 *
 * @return 解码后的文本；字节数超过 [TAVERN_IMPORT_MAX_BYTES] 时回 null（不当成合法文件）。
 */
internal fun decodeTavernImportBytes(bytes: ByteArray): String? {
    if (bytes.size > TAVERN_IMPORT_MAX_BYTES) return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix(UTF8_BOM)
}

/**
 * 「导入 Tavern 群聊文件」卡片。
 *
 * @param conversation 当前群聊会话。**卡片只在群聊会话出现**，判定用 [isGroupConversation]
 *   的严格口径（`group_config` 非空 **且** `type == GROUP`），与导出卡片、气泡层、
 *   轮次内核对同一个会话的判断不会漂移（`GroupExportCard.kt:32-34`）。
 * @param onImported 落库回调。收到的一定是 [TavernGroupImportOutcome.Accepted]——
 *   被拒收的分支根本走不到这里。
 */
@Composable
internal fun TavernGroupImportCard(
    conversation: Conversation,
    onImported: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    // isGroupConversation 严格口径。单聊会话恒为 false，面板与 C1 之前逐字相同。
    if (!isGroupConversation(conversation)) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedText by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<TavernGroupImportOutcome?>(null) }

    // 选文件与粘贴两条路径共用这一个入口：判定与落库口径必须完全一致。
    fun runImport(raw: String) {
        val result = resolveTavernGroupImport(raw, conversation.id.toString())
        outcome = result
        when (result) {
            is TavernGroupImportOutcome.Accepted -> {
                onImported(conversation.applyTavernGroupImport(result))
                KhromiaToast.show(message = result.headline, isError = false)
            }
            // Rejected / Unsupported：只报话术，绝不落库。
            is TavernGroupImportOutcome.Rejected ->
                KhromiaToast.show(message = result.headline, isError = true)

            is TavernGroupImportOutcome.Unsupported ->
                KhromiaToast.show(message = result.headline, isError = true)
        }
    }

    // SAF 选文件：读字节走 IO 线程，判定与落库回到主线程（更新会话状态）。
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    .getOrNull()
            }
            if (bytes == null) {
                KhromiaToast.show(message = "读取酒馆群聊文件失败", isError = true)
                return@launch
            }
            val text = decodeTavernImportBytes(bytes)
            if (text == null) {
                KhromiaToast.show(message = "酒馆群聊文件太大，无法导入", isError = true)
                return@launch
            }
            runImport(text)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KedgeCard(
            onClick = { openDocument.launch(arrayOf(TAVERN_IMPORT_MIME_TYPE)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            KedgeListItem(
                headlineContent = { Text("导入 Tavern 群聊文件") },
                supportingContent = {
                    Text("选择酒馆群聊文件（.jsonl）回导到当前群聊，或在下面粘贴文件内容")
                },
                leadingContent = { Icon(groups, contentDescription = null) },
            )
        }

        Text(
            text = "粘贴酒馆群聊文件内容（.jsonl / JSON 数组）",
            style = MaterialTheme.typography.labelLarge,
        )
        KedgeOutlinedTextFieldWithSlots(
            value = pastedText,
            onValueChange = { pastedText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KedgeTextButton(onClick = { runImport(pastedText) }) { Text("恢复酒馆群聊") }
        }

        TavernGroupImportResultView(outcome)
    }
}

/**
 * 酒馆群聊文件回导的结果视图。
 *
 * **不是** `GroupChatPage` 里 `ImportResultView`（那个是 `importShare` 的展示，不覆盖它）：
 * 这条路径的标题要写明走的是酒馆群聊文件，且 `Normalized` 分支必须把 `fixes` 逐条列出来。
 *
 * 文案全部来自 [TavernGroupImportOutcome]，本函数只负责渲染与配色。
 */
@Composable
private fun TavernGroupImportResultView(outcome: TavernGroupImportOutcome?) {
    val result = outcome ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = result.headline,
            style = MaterialTheme.typography.bodySmall,
            color = if (result.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // detailLines：归一化的 fixes / 卡片去重记录（Accepted），或字段级错误（Rejected）。
        result.detailLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        val accepted = result as? TavernGroupImportOutcome.Accepted
        if (accepted != null && accepted.cards.isNotEmpty()) {
            Text(
                text = "随本次导入带进的角色卡 ${accepted.cards.size} 张（已落库快照）",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
