package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khatkit.app.feature.automation.CardTriggerOverride
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.feature.automation.TriggerTilePublisher
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerCard

/**
 * 单卡片事件编辑面板（Khromia PrimaryBottomSheet）：
 * 增删事件、按类型填参数、配置失败重试；保存后由 [TriggerController] 合并进引擎。
 */
@Composable
internal fun TriggerCardEditorSheet(
    card: TriggerCard,
    override: CardTriggerOverride,
    onSave: (CardTriggerOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val events = remember(card.name) {
        (override.events ?: card.events).toMutableStateList()
    }
    val disabledIndexes = remember(card.name) {
        override.disabledEvents.filter { it >= 0 }.toMutableStateList()
    }
    var maxRetries by remember(card.name) { mutableStateOf(override.maxRetries.coerceIn(0, 3)) }
    var retryDelay by remember(card.name) {
        mutableStateOf(override.retryDelaySeconds.coerceAtLeast(0).toString())
    }
    var expandedIndex by remember(card.name) { mutableStateOf<Int?>(null) }
    var showTypePicker by remember(card.name) { mutableStateOf(false) }
    var errorText by remember(card.name) { mutableStateOf<String?>(null) }
    val dismissHolder = remember { mutableStateOf<(() -> Unit)?>(null) }

    fun toggleEventEnabled(index: Int, enabled: Boolean) {
        if (enabled) {
            disabledIndexes.remove(index)
        } else if (index !in disabledIndexes) {
            disabledIndexes.add(index)
        }
    }

    fun removeEvent(index: Int) {
        events.removeAt(index)
        val shifted = disabledIndexes
            .filter { it != index }
            .map { if (it > index) it - 1 else it }
        disabledIndexes.clear()
        disabledIndexes.addAll(shifted)
        expandedIndex = null
    }

    fun save() {
        val invalid = events.indexOfFirst { validateEvent(it) != null }
        if (invalid >= 0) {
            expandedIndex = invalid
            errorText = "第 ${invalid + 1} 个事件：${validateEvent(events[invalid])}"
            return
        }
        val delay = retryDelay.toIntOrNull()?.coerceIn(0, 3600) ?: 0
        val disabled = disabledIndexes.distinct().sorted()
        val merged = CardTriggerOverride(
            events = events.toList(),
            maxRetries = maxRetries,
            retryDelaySeconds = delay,
            disabledEvents = disabled,
        )
        val unchanged = events.toList() == card.baseEvents && maxRetries == 0 && delay == 0 &&
            disabled.isEmpty()
        onSave(if (unchanged) CardTriggerOverride.NONE else merged)
        dismissHolder.value?.invoke()
    }

    PrimaryBottomSheet(
        visible = true,
        title = "编辑 ${card.name}",
        imageVector = editNote,
        confirmText = "保存",
        onConfirm = { save() },
        onDismiss = onDismiss,
    ) { dismiss ->
        SideEffect { dismissHolder.value = dismiss }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------------------ 重试配置
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("失败重试", style = MaterialTheme.typography.titleSmall)
                    ChipSelector(
                        label = "最多重试",
                        options = listOf("0" to "0", "1" to "1", "2" to "2", "3" to "3"),
                        selected = maxRetries.toString(),
                        onSelect = { maxRetries = it.toIntOrNull() ?: 0 },
                    )
                    if (maxRetries > 0) {
                        OutlinedTextField(
                            value = retryDelay,
                            onValueChange = { retryDelay = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("重试间隔（秒）") },
                            supportingText = { Text("默认 0 秒，即下一个轮询周期立即重试") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ------------------------------------------------------------------ 事件列表
            Text(
                text = "事件（${events.size}）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (events.isEmpty()) {
                Text(
                    text = "暂无事件，点击下方「添加事件」；清空后该卡片不会自动触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            events.forEachIndexed { index, event ->
                key(index, event.type) {
                    EventCard(
                        cardName = card.name,
                        event = event,
                        enabled = index !in disabledIndexes,
                        expanded = expandedIndex == index,
                        onToggleEnabled = { toggleEventEnabled(index, it) },
                        onToggle = { expandedIndex = if (expandedIndex == index) null else index },
                        onChange = { events[index] = it },
                        onRemove = { removeEvent(index) },
                    )
                }
            }

            TextButton(onClick = { showTypePicker = !showTypePicker }, shapes = ButtonDefaults.shapes()) {
                Icon(add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showTypePicker) "收起类型" else "添加事件")
            }
            AnimatedVisibility(visible = showTypePicker) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ALL_EVENT_TYPES.forEach { type ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                events.add(defaultEventFor(type))
                                expandedIndex = events.lastIndex
                                showTypePicker = false
                            },
                            label = { Text(eventTypeLabel(type)) },
                        )
                    }
                }
            }

            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = {
                    events.clear()
                    events.addAll(card.baseEvents)
                    disabledIndexes.clear()
                    maxRetries = 0
                    retryDelay = "0"
                    errorText = null
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("恢复卡片默认事件")
            }
        }
    }
}

@Composable
private fun EventCard(
    cardName: String,
    event: CardManifest.Event,
    enabled: Boolean,
    expanded: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggle: () -> Unit,
    onChange: (CardManifest.Event) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eventTypeLabel(event.type) + if (enabled) "" else "（已停用）",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = eventSummary(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
            )
            IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                Icon(delete, contentDescription = "删除事件", modifier = Modifier.size(18.dp))
            }
            Icon(
                imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            EventFields(cardName = cardName, event = event, onChange = onChange)
        }
    }
}

@Composable
private fun EventFields(
    cardName: String,
    event: CardManifest.Event,
    onChange: (CardManifest.Event) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (event.type) {
            CardManifest.EVENT_SCHEDULE -> {
                TextStateField(
                    label = "时间列表（HH:mm，逗号分隔）",
                    value = event.times.joinToString(", "),
                    onValueChange = { raw ->
                        val times = raw.split(',', '，', ' ', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onChange(event.copy(times = times))
                    },
                    placeholder = "08:00, 21:30",
                )
                IntStateField(
                    label = "间隔分钟（与时间列表二选一）",
                    value = event.intervalMinutes,
                    onValueChange = { onChange(event.copy(intervalMinutes = it)) },
                )
                ChipSelector(
                    label = "星期（不选 = 每天）",
                    options = listOf(
                        "" to "每天",
                        "1" to "周一",
                        "2" to "周二",
                        "3" to "周三",
                        "4" to "周四",
                        "5" to "周五",
                        "6" to "周六",
                        "7" to "周日",
                    ),
                    selected = when {
                        event.days.isEmpty() -> ""
                        event.days.size == 1 -> event.days.first().toString()
                        else -> ""
                    },
                    onSelect = { value ->
                        onChange(
                            event.copy(
                                days = if (value.isBlank()) emptyList() else listOf(value.toInt())
                            )
                        )
                    },
                )
                Text(
                    text = "提示：多选星期请在卡片市场用 JSON 编辑；此处支持单选或每天。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipSelector(
                    label = "日历（按中国节假日过滤）",
                    options = listOf(
                        CardManifest.CALENDAR_ANY to "不限",
                        CardManifest.CALENDAR_WORKDAY to "工作日",
                        CardManifest.CALENDAR_WEEKEND to "休息日",
                        CardManifest.CALENDAR_HOLIDAY to "节假日",
                    ),
                    selected = event.calendar.ifBlank { CardManifest.CALENDAR_ANY },
                    onSelect = { onChange(event.copy(calendar = it)) },
                )
                Text(
                    text = "「休息日」= 周末与法定假日，「节假日」仅法定放假日；" +
                        "数据来自内置 holidays_cn.json，可按格式自行覆盖编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_NOTIFICATION -> {
                TextStateField(
                    label = "应用包名（留空 = 任意应用）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = "com.tencent.mm",
                )
                TextStateField(
                    label = "标题包含",
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = "正文包含",
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
            }

            CardManifest.EVENT_NOTIFICATION_CLICK -> {
                TextStateField(
                    label = "应用包名（留空 = 任意应用）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = "com.tencent.mm",
                )
                TextStateField(
                    label = "标题包含",
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = "正文包含",
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
                Text(
                    text = "用户在状态栏点击该通知时触发，需通知监听权限；3 秒内多次点击只触发一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_NOTIFICATION_REPLY -> {
                TextStateField(
                    label = "应用包名（必填）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = "com.tencent.mm",
                )
                TextStateField(
                    label = "标题包含（可选）",
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = "回复文本包含（可选，启发式）",
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
                Text(
                    text = "启发式：点击带「直接回复」输入框的通知后，监听同应用 10 秒内的下一条通知；" +
                        "无法读取你实际发送的内容，文本可能为空，部分应用不会触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_LAUNCH -> {
                TextStateField(
                    label = "应用包名（留空 = 任意应用）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
            }

            CardManifest.EVENT_APP_EXIT -> {
                TextStateField(
                    label = "应用包名（必填）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = "com.tencent.mm",
                )
                Text(
                    text = "该应用离开前台约 2 秒后触发（回到前台则取消）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_INSTALL -> {
                TextStateField(
                    label = "应用包名（留空 = 任意应用）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
                Text(
                    text = "仅在触发服务运行期间监听系统安装广播；应用更新（覆盖安装）不算安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_UNINSTALL -> {
                TextStateField(
                    label = "应用包名（留空 = 任意应用）",
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
                Text(
                    text = "仅在触发服务运行期间监听系统卸载广播；应用更新（覆盖安装）不算卸载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_SHORTCUT -> {
                TextStateField(
                    label = "快捷方式名称（必填）",
                    value = event.name,
                    onValueChange = { onChange(event.copy(name = it)) },
                    placeholder = "一键签到",
                )
                Text(
                    text = "保存后出现在启动器长按图标的快捷方式列表里，点击即运行卡片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_TILE -> {
                TextStateField(
                    label = "磁贴名称（必填）",
                    value = event.name,
                    onValueChange = { onChange(event.copy(name = it)) },
                    placeholder = "快速记账",
                )
                Text(
                    text = "每个磁贴占用一个槽位（最多 3 个）。保存后点击下方按钮，在系统快捷设置里添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        TriggerTilePublisher.requestAdd(
                            context = context,
                            card = cardName,
                            label = event.name.ifBlank { cardName },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("添加到快捷设置")
                }
            }

            CardManifest.EVENT_CHARGING -> {
                ChipSelector(
                    label = "状态",
                    options = listOf("connected" to "接入电源", "disconnected" to "断开电源"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_WIFI -> {
                TextStateField(
                    label = "热点 SSID（留空 = 任意）",
                    value = event.ssid,
                    onValueChange = { onChange(event.copy(ssid = it.trim())) },
                )
                ChipSelector(
                    label = "状态",
                    options = listOf("" to "任意", "connected" to "已连接", "disconnected" to "已断开"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_NETWORK -> {
                ChipSelector(
                    label = "网络状态",
                    options = listOf("online" to "在线", "offline" to "离线"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_BATTERY -> {
                IntStateField(
                    label = "低于电量（-1 = 不启用）",
                    value = event.levelBelow,
                    allowNegative = true,
                    onValueChange = { onChange(event.copy(levelBelow = it)) },
                )
                IntStateField(
                    label = "高于电量（-1 = 不启用）",
                    value = event.levelAbove,
                    allowNegative = true,
                    onValueChange = { onChange(event.copy(levelAbove = it)) },
                )
                ChipSelector(
                    label = "充放电状态",
                    options = listOf("" to "任意", "charging" to "充电中", "discharging" to "放电中"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_SCREEN -> {
                ChipSelector(
                    label = "屏幕状态",
                    options = listOf(
                        "on" to "点亮",
                        "off" to "熄灭",
                        "unlocked" to "解锁",
                        "locked" to "锁屏",
                    ),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_CLIPBOARD -> {
                TextStateField(
                    label = "剪贴板文本包含",
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                    placeholder = "https://",
                )
            }

            CardManifest.EVENT_BLUETOOTH -> {
                ChipSelector(
                    label = "状态",
                    options = listOf("connected" to "已连接", "disconnected" to "已断开"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
                TextStateField(
                    label = "设备名包含（留空 = 任意设备）",
                    value = event.device,
                    onValueChange = { onChange(event.copy(device = it)) },
                )
            }

            CardManifest.EVENT_LOCATION -> {
                DoubleStateField(
                    label = "纬度 lat",
                    value = event.lat,
                    onValueChange = { onChange(event.copy(lat = it)) },
                )
                DoubleStateField(
                    label = "经度 lon",
                    value = event.lon,
                    onValueChange = { onChange(event.copy(lon = it)) },
                )
                IntStateField(
                    label = "半径（米）",
                    value = event.radiusM,
                    onValueChange = { onChange(event.copy(radiusM = it)) },
                )
                ChipSelector(
                    label = "转换方向",
                    options = listOf("enter" to "进入区域", "exit" to "离开区域"),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
                Text(
                    text = "位置轮询约 60s 一次，需定位权限；首次定位只记录基线不触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChipSelector(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
private fun TextStateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntStateField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    allowNegative: Boolean = false,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = buildString {
                raw.forEachIndexed { index, c ->
                    if (c.isDigit() || (allowNegative && c == '-' && index == 0)) append(c)
                }
            }.take(4)
            text = filtered
            filtered.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowNegative) KeyboardType.Number else KeyboardType.Number
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DoubleStateField(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' || it == '-' }
            text = filtered
            onValueChange(filtered.toDoubleOrNull())
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun eventTypeLabel(type: String): String = when (type) {
    CardManifest.EVENT_SCHEDULE -> "定时"
    CardManifest.EVENT_NOTIFICATION -> "通知"
    CardManifest.EVENT_NOTIFICATION_CLICK -> "通知点击"
    CardManifest.EVENT_NOTIFICATION_REPLY -> "通知直接回复"
    CardManifest.EVENT_APP_LAUNCH -> "应用启动"
    CardManifest.EVENT_APP_EXIT -> "应用退出"
    CardManifest.EVENT_APP_INSTALL -> "应用安装"
    CardManifest.EVENT_APP_UNINSTALL -> "应用卸载"
    CardManifest.EVENT_CHARGING -> "充电"
    CardManifest.EVENT_WIFI -> "Wi-Fi"
    CardManifest.EVENT_NETWORK -> "网络"
    CardManifest.EVENT_BATTERY -> "电量"
    CardManifest.EVENT_SCREEN -> "屏幕"
    CardManifest.EVENT_CLIPBOARD -> "剪贴板"
    CardManifest.EVENT_BLUETOOTH -> "蓝牙"
    CardManifest.EVENT_LOCATION -> "位置"
    CardManifest.EVENT_SHORTCUT -> "桌面快捷方式"
    CardManifest.EVENT_TILE -> "快捷设置磁贴"
    else -> type
}

internal fun defaultEventFor(type: String): CardManifest.Event = when (type) {
    CardManifest.EVENT_SCHEDULE -> CardManifest.Event(type = type, times = listOf("08:00"))
    CardManifest.EVENT_NOTIFICATION -> CardManifest.Event(type = type)
    CardManifest.EVENT_NOTIFICATION_CLICK -> CardManifest.Event(type = type)
    CardManifest.EVENT_NOTIFICATION_REPLY -> CardManifest.Event(type = type)
    CardManifest.EVENT_APP_LAUNCH -> CardManifest.Event(type = type)
    CardManifest.EVENT_APP_EXIT -> CardManifest.Event(type = type)
    CardManifest.EVENT_APP_INSTALL -> CardManifest.Event(type = type)
    CardManifest.EVENT_APP_UNINSTALL -> CardManifest.Event(type = type)
    CardManifest.EVENT_CHARGING -> CardManifest.Event(type = type, state = CardManifest.STATE_CONNECTED)
    CardManifest.EVENT_WIFI -> CardManifest.Event(type = type)
    CardManifest.EVENT_NETWORK -> CardManifest.Event(type = type, state = CardManifest.NETWORK_OFFLINE)
    CardManifest.EVENT_BATTERY -> CardManifest.Event(type = type, levelBelow = 20)
    CardManifest.EVENT_SCREEN -> CardManifest.Event(type = type, state = CardManifest.SCREEN_UNLOCKED)
    CardManifest.EVENT_CLIPBOARD -> CardManifest.Event(type = type)
    CardManifest.EVENT_BLUETOOTH -> CardManifest.Event(type = type, state = CardManifest.STATE_CONNECTED)
    CardManifest.EVENT_LOCATION -> CardManifest.Event(
        type = type,
        state = CardManifest.LOCATION_ENTER,
        lat = 0.0,
        lon = 0.0,
        radiusM = 100,
    )

    else -> CardManifest.Event(type = type)
}

internal val ALL_EVENT_TYPES = listOf(
    CardManifest.EVENT_SCHEDULE,
    CardManifest.EVENT_NOTIFICATION,
    CardManifest.EVENT_NOTIFICATION_CLICK,
    CardManifest.EVENT_NOTIFICATION_REPLY,
    CardManifest.EVENT_APP_LAUNCH,
    CardManifest.EVENT_APP_EXIT,
    CardManifest.EVENT_APP_INSTALL,
    CardManifest.EVENT_APP_UNINSTALL,
    CardManifest.EVENT_CHARGING,
    CardManifest.EVENT_WIFI,
    CardManifest.EVENT_NETWORK,
    CardManifest.EVENT_BATTERY,
    CardManifest.EVENT_SCREEN,
    CardManifest.EVENT_CLIPBOARD,
    CardManifest.EVENT_BLUETOOTH,
    CardManifest.EVENT_LOCATION,
    CardManifest.EVENT_SHORTCUT,
    CardManifest.EVENT_TILE,
)

private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

/** 与 CardValidator 保持一致的轻量校验，返回 null 表示可保存。 */
internal fun validateEvent(event: CardManifest.Event): String? = when (event.type) {
    CardManifest.EVENT_SCHEDULE -> when {
        event.times.isEmpty() && event.intervalMinutes <= 0 -> "需要时间列表或间隔分钟（>=1）"
        event.times.any { !TIME_REGEX.matches(it) } -> "时间格式必须是 HH:mm"
        else -> null
    }

    CardManifest.EVENT_NOTIFICATION ->
        if (event.packageName.isBlank() && event.titleContains.isBlank() && event.textContains.isBlank()) {
            "至少填写包名 / 标题 / 正文之一"
        } else null

    CardManifest.EVENT_NOTIFICATION_CLICK ->
        if (event.packageName.isBlank() && event.titleContains.isBlank() && event.textContains.isBlank()) {
            "至少填写包名 / 标题 / 正文之一"
        } else null

    CardManifest.EVENT_NOTIFICATION_REPLY ->
        if (event.packageName.isBlank()) "需要填写应用包名（要监听回复的应用）" else null

    CardManifest.EVENT_APP_EXIT ->
        if (event.packageName.isBlank()) "需要填写应用包名" else null

    CardManifest.EVENT_SHORTCUT ->
        if (event.name.isBlank()) "需要填写快捷方式名称" else null

    CardManifest.EVENT_TILE ->
        if (event.name.isBlank()) "需要填写磁贴名称" else null

    CardManifest.EVENT_CLIPBOARD ->
        if (event.textContains.isBlank()) "需要填写匹配文本" else null

    CardManifest.EVENT_BATTERY -> when {
        event.levelBelow < -1 || event.levelBelow > 100 -> "低于电量需在 0..100"
        event.levelAbove < -1 || event.levelAbove > 100 -> "高于电量需在 0..100"
        event.levelBelow < 0 && event.levelAbove < 0 && event.state.isBlank() ->
            "至少填写电量阈值或充放电状态"
        else -> null
    }

    CardManifest.EVENT_LOCATION -> {
        val lat = event.lat
        val lon = event.lon
        when {
            lat == null || lat !in -90.0..90.0 -> "纬度需在 -90..90"
            lon == null || lon !in -180.0..180.0 -> "经度需在 -180..180"
            event.radiusM < 1 -> "半径需 >= 1 米"
            else -> null
        }
    }

    else -> null
}
