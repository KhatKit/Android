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
import heizige.kk.kedge.components.KedgeSwitch
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
    var expandedIndex by remember { mutableStateOf<Int?>(0) }
    val categories = listOf(
        "触发器" to listOf("手动启动", "定时触发", "通知触发", "应用启动", "剪贴板变化", "充电状态", "Wi-Fi 状态", "位置进入"),
        "交互" to listOf("点击控件", "长按控件", "输入文本", "滑动页面", "返回上一页", "打开应用", "截图"),
        "识别" to listOf("查找文本", "查找控件", "OCR 识别", "等待控件出现", "等待页面稳定"),
        "逻辑" to listOf("条件判断", "否则分支", "重复执行", "遍历列表", "等待", "停止流程", "调用子流程"),
        "数据" to listOf("提取文本", "保存变量", "使用变量", "格式化文本", "JSON 解析", "数组操作"),
        "网络" to listOf("HTTP 请求", "下载文件", "上传文件", "解析响应"),
        "文件" to listOf("读取文件", "写入文件", "复制文件", "删除文件"),
        "系统" to listOf("发送通知", "震动", "设置剪贴板", "调节音量", "打开系统设置"),
        "AI" to listOf("AI 识别页面", "AI 判断下一步", "AI 生成文本", "AI 提取字段", "AI 总结结果"),
        "脚本" to listOf("运行 Lua", "运行 JavaScript", "执行 Shell"),
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
                    expanded = expandedIndex == index,
                    onExpand = { expandedIndex = if (expandedIndex == index) null else index },
                    onChange = { nodes[index] = it },
                    onDuplicate = { nodes.add(index + 1, node.copy(params = node.params.toMap())) },
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
                                                params = defaultParams(action),
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

private fun moduleHint(action: String): String = when {
    action.startsWith("AI") -> "需要模型参与判断或生成"
    action.contains("条件") || action.contains("重复") -> "控制后续节点的执行路径"
    action.contains("变量") || action.contains("文本") -> "读写流程中的动态数据"
    else -> "在当前设备上执行自动化操作"
}

private fun defaultParams(action: String): Map<String, String> = when {
    action.contains("定时") -> mapOf("时间" to "08:00", "星期" to "每天")
    action.contains("通知") -> mapOf("应用包名" to "", "标题包含" to "", "正文包含" to "")
    action.contains("应用") -> mapOf("包名" to "")
    action.contains("点击") || action.contains("长按") -> mapOf("文本或 resourceId" to "")
    action.contains("输入") -> mapOf("文本" to "")
    action.contains("滑动") -> mapOf("方向" to "向上", "时长(ms)" to "300")
    action.contains("等待") -> mapOf("超时(ms)" to "5000")
    action.contains("重复") -> mapOf("次数" to "3")
    action.contains("条件") -> mapOf("条件表达式" to "")
    action.contains("HTTP") -> mapOf("URL" to "", "方法" to "GET", "请求体" to "")
    action.contains("文件") || action.contains("读取") || action.contains("写入") ||
        action.contains("复制") || action.contains("删除") -> mapOf("路径" to "")
    action.startsWith("AI") -> mapOf("提示词" to "", "输出变量" to "ai_result")
    action.contains("Lua") || action.contains("JavaScript") || action.contains("Shell") ->
        mapOf("脚本" to "")
    else -> mapOf("参数" to "")
}
