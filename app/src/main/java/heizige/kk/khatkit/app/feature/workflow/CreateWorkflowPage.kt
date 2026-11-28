package heizige.kk.khatkit.app.feature.workflow

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.workflow.helper.workflowLabel
import heizige.kk.khatkit.app.feature.workflow.helper.workflowStatusLabel
import heizige.kk.khatkit.app.feature.workflow.helper.workflowParameterLabel
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkCard
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
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowExecution
import heizige.kk.khatkit.app.feature.workflow.helper.executeWorkflow
import heizige.kk.khatkit.app.feature.workflow.helper.notificationClassifySample
import heizige.kk.khatkit.app.feature.workflow.helper.toVisualWorkflow
import heizige.kk.khatkit.app.feature.workflow.data.encode
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowDefinition
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowNode
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowPreferencesRepository
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.feature.workflow.helper.toStoredNodes





@Composable
fun CreateWorkflowPage(workflowId: String? = null) {
    val nav = LocalNavController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = LocalResources.current
    val loaded = remember(workflowId) {
        workflowId?.let { id -> WorkflowPreferencesRepository.load(context).firstOrNull { it.id == id } }
    }
    var editing by remember(workflowId) { mutableStateOf(loaded) }
    var name by remember(workflowId) { mutableStateOf(loaded?.name ?: resources.getString(R.string.setting_workflow_default_name)) }
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

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_workflow_create),
        navigationIcon = { BackButton() },
        actions = {
            MochiTextButton(
                onClick = {
                    val saved = currentDefinition(editing?.id, name, nodes.toList(), resources.getString(R.string.setting_trigger_unnamed))
                    WorkflowPreferencesRepository.save(context, saved)
                    scope.launch {
                        runCatching {
                            repository.save(saved.toVisualWorkflow())
                        }
                    }
                    Toast.show(resources.getString(R.string.setting_workflow_saved, name.ifBlank { resources.getString(R.string.setting_trigger_unnamed) }))
                    nav.navigate(Screen.SettingTriggers) {
                        popUpTo(Screen.CreateWorkflow()) { inclusive = true }
                    }
                },
            ) {
                Text(stringResource(R.string.common_save))
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
                Md3OutlinedTextFieldWithSlots(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setting_workflow_name)) },
                    supportingText = { Text(stringResource(R.string.setting_workflow_name_desc)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                Text(stringResource(R.string.setting_workflow_nodes), style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.setting_workflow_add_module), style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                MochiTextButton(
                    onClick = {
                        val sample = notificationClassifySample()
                        name = sample.name
                        nodes.clear()
                        nodes.addAll(sample.toStoredNodes())
                    },
                ) { Text(stringResource(R.string.setting_workflow_load_sample)) }
            }
            presets.groupBy { it.category }.forEach { (category, actions) ->
                item(key = category) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(workflowLabel(context, category), style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.primary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            actions.forEach { action ->
                                KkFilterChip(
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
                                    label = workflowLabel(context, action.title),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.setting_workflow_format_hint),
                    style = MochiTheme.textStyles.labelSmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Md3OutlinedTextFieldWithSlots(
                    value = classification,
                    onValueChange = { classification = it },
                    label = { Text(stringResource(R.string.setting_workflow_preview_classification)) },
                    supportingText = { Text(stringResource(R.string.setting_workflow_classification_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                MochiTextButton(
                    onClick = {
                        val graph = currentDefinition(editing?.id, name, nodes.toList(), resources.getString(R.string.setting_trigger_unnamed)).toVisualWorkflow()
                        val execution = executeWorkflow(
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
                ) { Text(stringResource(R.string.setting_workflow_preview_run)) }
            }
            preview?.let { execution ->
                item {
                    Text(
                        stringResource(R.string.setting_workflow_run_status, workflowStatusLabel(context, execution.status)),
                        style = MochiTheme.textStyles.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                itemsIndexed(execution.steps, key = { index, step -> "${step.nodeId}-$index" }) { index, step ->
                    KkCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MochiTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MochiTextButton(onClick = {
                                expandedStep = if (expandedStep == index) null else index
                            }) {
                                Text(stringResource(R.string.setting_workflow_step, index + 1, step.nodeId, workflowLabel(context, step.kind), workflowStatusLabel(context, step.status)))
                            }
                            if (expandedStep == index) {
                                Text(stringResource(R.string.setting_workflow_step_input, step.input), style = MochiTheme.textStyles.labelSmall)
                                Text(stringResource(R.string.setting_workflow_step_output, step.output), style = MochiTheme.textStyles.labelSmall)
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

private fun currentDefinition(id: String?, name: String, nodes: List<WorkflowNode>, unnamedFallback: String): WorkflowDefinition {
    val draft = WorkflowDefinition(
        id = id ?: java.util.UUID.randomUUID().toString(),
        name = name.ifBlank { unnamedFallback },
        nodes = nodes,
    )
    val graph = draft.copy(graphJson = "").toVisualWorkflow()
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
    KkCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MochiTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = MochiTheme.textStyles.titleMedium, color = MochiTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(workflowLabel(androidx.compose.ui.platform.LocalContext.current, node.title), style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.setting_workflow_node_detail, workflowLabel(androidx.compose.ui.platform.LocalContext.current, node.category), workflowLabel(androidx.compose.ui.platform.LocalContext.current, node.detail)), style = MochiTheme.textStyles.labelSmall)
                }
                KkSwitch(checked = node.enabled, onCheckedChange = { onChange(node.copy(enabled = it)) })
            }
            MochiTextButton(onClick = onExpand) { Text(if (expanded) stringResource(R.string.setting_workflow_parameters_hide) else stringResource(R.string.setting_workflow_parameters_edit)) }
            if (expanded) {
                node.params.forEach { (key, value) ->
                    Md3OutlinedTextFieldWithSlots(
                        value = value,
                        onValueChange = { onChange(node.copy(params = node.params + (key to it))) },
                        label = { Text(workflowParameterLabel(androidx.compose.ui.platform.LocalContext.current, key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MochiTextButton(onClick = onUp, enabled = canMoveUp) { Text(stringResource(R.string.setting_workflow_move_up)) }
                MochiTextButton(onClick = onDown, enabled = canMoveDown) { Text(stringResource(R.string.setting_workflow_move_down)) }
                MochiTextButton(onClick = onDuplicate) { Text(stringResource(R.string.copy)) }
                MochiTextButton(onClick = onDelete, enabled = index > 0) { Text(stringResource(R.string.common_delete)) }
            }
        }
    }
}
