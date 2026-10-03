package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.feature.workflow.WorkflowEngine
import heizige.kk.khatkit.app.feature.workflow.WorkflowExecution
import heizige.kk.khatkit.app.feature.workflow.WorkflowLegacy
import heizige.kk.khatkit.app.feature.workflow.WorkflowSamples
import heizige.kk.khatkit.app.feature.workflow.encode
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khromia.helper.Toast

@Composable
fun CreateWorkflowPage(workflowId: String? = null) {
    val nav = LocalNavController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val loaded = remember(workflowId) {
        workflowId?.let { id -> WorkflowStore.load(context).firstOrNull { it.id == id } }
    }
    var editing by remember(workflowId) { mutableStateOf(loaded) }
    var name by remember(workflowId) { mutableStateOf(loaded?.name ?: "新工作流") }
    val nodes = remember {
        mutableStateListOf<WorkflowNode>().apply {
            addAll(
                loaded?.nodes ?: listOf(
                    WorkflowNode("触发器", "手动启动", "点击运行或绑定到自动化触发器"),
                )
            )
        }
    }
    var expandedIndex by remember { mutableStateOf<Int?>(0) }
    var classification by remember { mutableStateOf("urgent") }
    var preview by remember { mutableStateOf<WorkflowExecution?>(null) }
    var expandedStep by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val repository = rememberAppEntryPoint().workflowRepository()
    val presets = listOf(
        WorkflowPreset("trigger", "触发器", "通知触发", mapOf("event" to "notification")),
        WorkflowPreset("trigger", "触发器", "手动启动", mapOf("event" to "manual")),
        WorkflowPreset("llm", "模型", "分类", mapOf("prompt" to "分类这条通知", "fallback" to "other")),
        WorkflowPreset("condition", "逻辑", "条件分支", mapOf("field" to "text", "equals" to "urgent")),
        WorkflowPreset("loop", "逻辑", "重复", mapOf("count" to "3")),
        WorkflowPreset("delay", "逻辑", "等待", mapOf("ms" to "1000")),
        WorkflowPreset("data_extract", "数据", "提取字段", mapOf("field" to "text")),
        WorkflowPreset("http", "网络", "HTTP 请求", mapOf("url" to "https://example.com", "method" to "GET")),
        WorkflowPreset("notify", "系统", "发送通知", mapOf("title" to "回复")),
        WorkflowPreset("card_tool", "卡片", "写文件", mapOf("card" to "write_file", "path" to "out.txt")),
        WorkflowPreset("sub_flow", "流程", "子流程", mapOf("workflow" to "")),
    )

    KedgeSettingsPageScaffold(
        title = "创建工作流",
        navigationIcon = { BackButton() },
        actions = {
            KedgeTextButton(
                onClick = {
                    val saved = currentDefinition(editing?.id, name, nodes.toList())
                    WorkflowStore.save(context, saved)
                    scope.launch {
                        runCatching {
                            repository.save(WorkflowLegacy.fromStored(saved))
                        }
                    }
                    Toast.show("工作流「${name.ifBlank { "未命名" }}」已保存")
                    nav.navigate(Screen.SettingTriggers) {
                        popUpTo(Screen.CreateWorkflow()) { inclusive = true }
                    }
                },
            ) {
                Text("保存")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KedgeOutlinedTextFieldWithSlots(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("工作流名称") },
                    supportingText = { Text("给流程一个容易识别的名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                Text("流程节点", style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
            }
            itemsIndexed(nodes, key = { index, node -> "${node.title}-$index" }) { index, node ->
                WorkflowNodeCard(
                    index = index,
                    node = node,
                    canMoveUp = index > 0,
                    canMoveDown = index < nodes.lastIndex,
                    onUp = {
                        nodes[index] = nodes[index - 1].also { nodes[index - 1] = nodes[index] }
                    },
                    onDown = {
                        nodes[index] = nodes[index + 1].also { nodes[index + 1] = nodes[index] }
                    },
                    onDelete = { if (nodes.size > 1) nodes.removeAt(index) },
                    expanded = expandedIndex == index,
                    onExpand = { expandedIndex = if (expandedIndex == index) null else index },
                    onChange = { nodes[index] = it },
                    onDuplicate = { nodes.add(index + 1, node.copy(params = node.params.toMap())) },
                )
            }
            item {
                Text("添加模块", style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
            }
            item {
                KedgeTextButton(
                    onClick = {
                        val sample = WorkflowSamples.notificationClassify()
                        name = sample.name
                        nodes.clear()
                        nodes.addAll(WorkflowLegacy.toStoredNodes(sample))
                    },
                ) { Text("加载通知分类示例") }
            }
            presets.groupBy { it.category }.forEach { (category, actions) ->
                item(key = category) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(category, style = KedgeTextStyles.body(), color = MaterialTheme.colorScheme.primary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            actions.forEach { action ->
                                KedgeFilterChip(
                                    selected = false,
                                    onClick = {
                                        val previous = nodes.lastOrNull()?.id.orEmpty()
                                        nodes.add(
                                            WorkflowNode(
                                                category = action.category,
                                                title = action.title,
                                                detail = action.kind,
                                                params = action.params + if (previous.isNotEmpty()) {
                                                    mapOf("depends_on" to previous)
                                                } else {
                                                    emptyMap()
                                                },
                                                kind = action.kind,
                                                id = "n${nodes.size}",
                                            ),
                                        )
                                    },
                                    label = { Text(action.title) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "规范形态是可视化 JSON，执行时编译成现有 FlowSpec。触发器只引用已有的 18 种事件。",
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                KedgeOutlinedTextFieldWithSlots(
                    value = classification,
                    onValueChange = { classification = it },
                    label = { Text("预览分类结果") },
                    supportingText = { Text("urgent 走回复，其他值写文件") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                KedgeTextButton(
                    onClick = {
                        val graph = WorkflowLegacy.fromStored(currentDefinition(editing?.id, name, nodes.toList()))
                        val execution = WorkflowEngine.execute(
                            graph,
                            mapOf("classification" to classification, "event" to "notification"),
                        )
                        preview = execution
                        expandedStep = 0
                        scope.launch {
                            runCatching {
                                repository.save(graph)
                                repository.record(graph, execution)
                            }
                        }
                    },
                ) { Text("预览运行") }
            }
            preview?.let { execution ->
                item {
                    Text(
                        "运行 ${execution.status}",
                        style = KedgeTextStyles.title(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                itemsIndexed(execution.steps, key = { index, step -> "${step.nodeId}-$index" }) { index, step ->
                    KedgeCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            KedgeTextButton(onClick = {
                                expandedStep = if (expandedStep == index) null else index
                            }) {
                                Text("${index + 1}. ${step.nodeId} · ${step.kind} · ${step.status}")
                            }
                            if (expandedStep == index) {
                                Text("输入 ${step.input}", style = KedgeTextStyles.footnoteSmall())
                                Text("输出 ${step.output}", style = KedgeTextStyles.footnoteSmall())
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class WorkflowPreset(
    val kind: String,
    val category: String,
    val title: String,
    val params: Map<String, String>,
)

private fun currentDefinition(id: String?, name: String, nodes: List<WorkflowNode>): WorkflowDefinition {
    val draft = WorkflowDefinition(
        id = id ?: java.util.UUID.randomUUID().toString(),
        name = name.ifBlank { "未命名" },
        nodes = nodes,
    )
    val graph = WorkflowLegacy.fromStored(draft.copy(graphJson = ""))
    return draft.copy(graphJson = graph.encode())
}

@Composable
private fun WorkflowNodeCard(
    index: Int,
    node: WorkflowNode,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChange: (WorkflowNode) -> Unit,
    onDuplicate: () -> Unit,
) {
    KedgeCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = KedgeTextStyles.title(), color = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(node.title, style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
                    Text("${node.category} · ${node.detail}", style = KedgeTextStyles.footnoteSmall())
                }
                KedgeSwitch(checked = node.enabled, onCheckedChange = { onChange(node.copy(enabled = it)) })
            }
            KedgeTextButton(onClick = onExpand) { Text(if (expanded) "收起参数" else "编辑参数") }
            if (expanded) {
                node.params.forEach { (key, value) ->
                    KedgeOutlinedTextFieldWithSlots(
                        value = value,
                        onValueChange = { onChange(node.copy(params = node.params + (key to it))) },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KedgeTextButton(onClick = onUp, enabled = canMoveUp) { Text("上移") }
                KedgeTextButton(onClick = onDown, enabled = canMoveDown) { Text("下移") }
                KedgeTextButton(onClick = onDuplicate) { Text("复制") }
                KedgeTextButton(onClick = onDelete, enabled = index > 0) { Text("删除") }
            }
        }
    }
}


