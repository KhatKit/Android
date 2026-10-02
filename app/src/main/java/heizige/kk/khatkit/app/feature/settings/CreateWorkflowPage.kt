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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
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
    val categories = listOf(
        "动作" to listOf("点击控件", "输入文本", "滑动页面", "打开应用"),
        "逻辑" to listOf("条件判断", "重复执行", "等待"),
        "数据" to listOf("提取文本", "保存变量", "使用变量"),
        "AI" to listOf("AI 识别页面", "AI 判断下一步", "AI 生成文本"),
    )

    KedgeSettingsPageScaffold(
        title = "创建工作流",
        navigationIcon = { BackButton() },
        actions = {
            KedgeTextButton(
                onClick = {
                    WorkflowStore.save(
                        context,
                        WorkflowDefinition(
                            id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { "未命名" },
                            nodes = nodes.toList(),
                        ),
                    )
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
                )
            }
            item {
                Text("添加模块", style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
            }
            categories.forEach { (category, actions) ->
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
                                        nodes.add(
                                            WorkflowNode(
                                                category = category,
                                                title = action,
                                                detail = moduleHint(action),
                                            )
                                        )
                                    },
                                    label = { Text(action) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "节点会按顺序执行。AI 节点只在你明确添加后介入，并遵循自动化授权策略。",
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
) {
    KedgeCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = KedgeTextStyles.title(), color = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(node.title, style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
                    Text("${node.category} · ${node.detail}", style = KedgeTextStyles.footnoteSmall())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KedgeTextButton(onClick = onUp, enabled = canMoveUp) { Text("上移") }
                KedgeTextButton(onClick = onDown, enabled = canMoveDown) { Text("下移") }
                KedgeTextButton(onClick = onDelete, enabled = index > 0) { Text("删除") }
            }
        }
    }
}

private fun moduleHint(action: String): String = when {
    action.startsWith("AI") -> "需要模型参与判断或生成"
    action.contains("条件") || action.contains("重复") -> "控制后续节点的执行路径"
    action.contains("变量") || action.contains("文本") -> "读写流程中的动态数据"
    else -> "在当前设备上执行自动化操作"
}
