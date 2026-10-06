package heizige.kk.khatkit.app.feature.settings

import android.content.Context
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Serializable
data class WorkflowNode(
    val category: String,
    val title: String,
    val detail: String,
    val params: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val kind: String = "",
    val id: String = "",
)

@Serializable
data class WorkflowDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "新工作流",
    val enabled: Boolean = true,
    val nodes: List<WorkflowNode> = listOf(
        WorkflowNode("触发器", "手动启动", "点击运行或绑定到自动化触发器", kind = "trigger", id = "t1"),
    ),
    val graphJson: String = "",
)

/**
 * 本地工作流编排存储。节点定义与现有卡片执行器解耦，便于后续把模块注册表接入运行时。
 */
object WorkflowPreferencesRepository {
    private const val PREFS = "khatkit_workflows"
    private const val KEY = "definitions"

    fun load(context: Context): List<WorkflowDefinition> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                WorkflowDefinition(
                    id = item.optString("id"),
                    name = item.optString("name", "新工作流"),
                    enabled = item.optBoolean("enabled", true),
                    nodes = item.optJSONArray("nodes").toNodeList(),
                    graphJson = item.optString("graphJson"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, definition: WorkflowDefinition) {
        val definitions = load(context).toMutableList()
        val index = definitions.indexOfFirst { it.id == definition.id }
        if (index >= 0) definitions[index] = definition else definitions += definition
        write(context, definitions)
    }

    fun delete(context: Context, id: String) {
        write(context, load(context).filterNot { it.id == id })
    }

    private fun write(context: Context, definitions: List<WorkflowDefinition>) {
        val array = JSONArray()
        definitions.forEach { definition ->
            array.put(
                JSONObject()
                    .put("id", definition.id)
                    .put("name", definition.name)
                    .put("enabled", definition.enabled)
                    .put("graphJson", definition.graphJson)
                    .put(
                        "nodes",
                        JSONArray().apply {
                            definition.nodes.forEach { node ->
                                put(
                                    JSONObject()
                                        .put("category", node.category)
                                        .put("title", node.title)
                                        .put("detail", node.detail)
                                        .put("enabled", node.enabled)
                                        .put("kind", node.kind)
                                        .put("id", node.id)
                                        .put("params", JSONObject(node.params)),
                                )
                            }
                        },
                    ),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}

private fun JSONArray?.toNodeList(): List<WorkflowNode> {
    if (this == null) return listOf(
        WorkflowNode("触发器", "手动启动", "点击运行或绑定到自动化触发器"),
    )
    return List(length()) { index ->
        val item = getJSONObject(index)
        WorkflowNode(
            category = item.optString("category", "动作"),
            title = item.optString("title", "未命名节点"),
            detail = item.optString("detail", "执行自动化操作"),
            params = item.optJSONObject("params").toStringMap(),
            enabled = item.optBoolean("enabled", true),
            kind = item.optString("kind"),
            id = item.optString("id"),
        )
    }.ifEmpty {
        listOf(WorkflowNode("触发器", "手动启动", "点击运行或绑定到自动化触发器"))
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optString(it) }
}
