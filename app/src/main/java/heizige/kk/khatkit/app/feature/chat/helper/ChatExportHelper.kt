package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.core.util.toLocalString
import java.time.LocalDateTime
import heizige.kk.khatkit.ai.util.encodeBase64

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.FileProvider
import heizige.kk.khatkit.app.core.util.JsonInstantPretty
import java.io.FileOutputStream
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.app.feature.chat.data.ExportOptions



internal fun exportToMarkdown(
    context: Context,
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ExportOptions,
) {
    val filename = "chat-export-${LocalDateTime.now().format(ExportTimeFormatter)}.md"

    val sb = buildAnnotatedString {
        append("# ${conversation.title}\n\n")
        append("*Exported on ${LocalDateTime.now().toLocalString()}*\n\n")

        messages.forEach { message ->
            val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
            append("$role:\n\n")
            message.parts.forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        append(part.text)
                        appendLine()
                    }

                    is UIMessagePart.Image -> {
                        append("![Image](${part.encodeBase64().getOrNull()?.base64})")
                        appendLine()
                    }

                    is UIMessagePart.Reasoning -> {
                        if (!options.includeReasoning) return@forEach
                        part.reasoning.lines()
                            .filter { it.isNotBlank() }
                            .forEach {
                                append("> $it")
                                appendLine()
                            }
                        appendLine()
                    }

                    is UIMessagePart.Tool -> {
                        append("**Tool**: `${part.toolName}`")
                        appendLine()
                        if (part.toolCallId.isNotBlank()) {
                            append("- Call ID: `${part.toolCallId}`")
                            appendLine()
                        }

                        append("Input:")
                        appendLine()
                        append("```json")
                        appendLine()
                        append(JsonInstantPretty.encodeToString(part.inputAsJson()))
                        appendLine()
                        append("```")
                        appendLine()

                        if (part.output.isNotEmpty()) {
                            append("Output:")
                            appendLine()
                            part.output.forEach { outputPart ->
                                when (outputPart) {
                                    is UIMessagePart.Text -> {
                                        append("```text")
                                        appendLine()
                                        append(outputPart.text)
                                        appendLine()
                                        append("```")
                                        appendLine()
                                    }

                                    is UIMessagePart.Reasoning -> {
                                        outputPart.reasoning.lines()
                                            .filter { it.isNotBlank() }
                                            .forEach {
                                                append("> $it")
                                                appendLine()
                                            }
                                    }

                                    is UIMessagePart.Image -> {
                                        append("![Tool Image](${outputPart.encodeBase64().getOrNull()?.base64})")
                                        appendLine()
                                    }

                                    is UIMessagePart.Document -> {
                                        append("[Document: ${outputPart.fileName}](${outputPart.url})")
                                        appendLine()
                                    }

                                    is UIMessagePart.Video -> {
                                        append("[Video](${outputPart.url})")
                                        appendLine()
                                    }

                                    is UIMessagePart.Audio -> {
                                        append("[Audio](${outputPart.url})")
                                        appendLine()
                                    }

                                    else -> {}
                                }
                            }
                        }
                        appendLine()
                    }

                    else -> {}
                }
            }
            appendLine()
            append("---")
            appendLine()
        }
    }

    try {
        val uri = writeExportTempFile(context, filename) { it.write(sb.toString().toByteArray()) }
        shareFile(context, uri, "text/markdown")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * 导出文件名里的时间戳格式。三条导出（Markdown / PNG / Tavern 群聊）共用这一份，
 * 免得同一批导出文件因为格式各写一遍而在磁盘上看起来不是同一批。
 */
internal val ExportTimeFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/**
 * 把导出内容写进临时目录并返回可分享的 content uri。三条导出路径（Markdown / PNG /
 * Tavern 群聊）共用这一份，不再各自写一遍 `delete + createNewFile + FileOutputStream +
 * FileProvider`——那种四行样板复制到第三份就会开始漂。
 *
 * 覆盖写：同名文件先删再建。临时目录（[appTempFolder]）由系统按缓存回收，重复导出同名
 * 文件是常态（同一秒内点两次），所以这里不生成随机后缀，导出文件名保持可预期。
 */
internal fun writeExportTempFile(
    context: Context,
    fileName: String,
    write: (java.io.OutputStream) -> Unit,
): Uri {
    val file = context.appTempFolder.resolve(fileName)
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
    FileOutputStream(file).use(write)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

/**
 * 调起系统分享面板。三条导出路径共用。
 *
 * 不是 `private`：群聊导出那张卡在 [GroupExportCard.kt] 里，同模块可见即可，
 * 分享 Intent 的拼法只此一份。
 */
internal fun shareFile(context: Context, uri: Uri, mimeType: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent,
            context.getString(R.string.chat_page_export_share_via)
        )
    )
}
