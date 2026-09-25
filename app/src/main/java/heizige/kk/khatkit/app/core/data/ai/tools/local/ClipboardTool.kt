package heizige.kk.khatkit.app.core.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.util.readClipboardText
import heizige.kk.khatkit.app.core.util.writeClipboardText

internal fun buildClipboardTool(context: Context): Tool = Tool(
    name = "clipboard_tool",
    description = """
        Read or write plain text from the device clipboard.
        Use action: read or write. For write, provide text.
        Do NOT write to the clipboard unless the user has explicitly requested it.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("read")
                            add("write")
                        }
                    )
                    put("description", "Operation to perform: read or write")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to write to the clipboard (required for write)")
                })
            },
            required = listOf("action")
        )
    },
    execute = {
        val params = it.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        when (action) {
            "read" -> {
                val payload = buildJsonObject {
                    put("text", context.readClipboardText())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }

            "write" -> {
                val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                context.writeClipboardText(text)
                val payload = buildJsonObject {
                    put("success", true)
                    put("text", text)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }

            else -> error("unknown action: $action, must be one of [read, write]")
        }
    }
)
