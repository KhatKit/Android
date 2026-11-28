package heizige.kk.khatkit.app.feature.automation

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.app.feature.settings.eventSummary

import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import heizige.kk.khatkit.uikit.mochi.MochiExpandableOptionItem
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import ui.feice.mochi.basic.MochiSheetState
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
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.app.feature.automation.data.CardTriggerOverride
import heizige.kk.khatkit.app.feature.automation.helper.TriggerManager
import heizige.kk.khatkit.app.feature.automation.helper.TriggerTileHelper
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import heizige.kk.khatkit.app.core.ui.icons.utility.delete
import heizige.kk.khatkit.app.core.ui.icons.utility.editNote
import heizige.kk.khatkit.app.core.ui.icons.navigation.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.navigation.keyboardArrowUp
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerCard
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet




/**
 * 单卡片事件编辑面板（Khromia PrimaryBottomSheet）：
 * 增删事件、按类型填参数、配置失败重试；保存后由 [TriggerManager] 合并进引擎。
 */
@Composable
internal fun TriggerCardEditorSheet(
    /** U7b-2d：宿主接收 sheetState；card 用调用方保留的最后一份快照，退出动画期间内容不空。 */
    sheetState: MochiSheetState,
    card: TriggerCard,
    override: CardTriggerOverride,
    onSave: (CardTriggerOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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
            errorText = resources.getString(R.string.setting_trigger_event_invalid, invalid + 1, resources.getString(validateEvent(events[invalid])!!))
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
        state = sheetState,
        title = stringResource(R.string.setting_trigger_edit, card.name),
        imageVector = editNote,
        confirmText = stringResource(R.string.common_save),
        onConfirm = { save() },
        onDismiss = onDismiss,
    ) { dismiss ->
        SideEffect { dismissHolder.value = dismiss }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------------------ 重试配置
            KkCard(
                colors = CardDefaults.cardColors(
                    containerColor = MochiTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.setting_trigger_retry_group), style = MochiTheme.textStyles.titleMedium)
                    ChipSelector(
                        label = stringResource(R.string.setting_trigger_retry_max),
                        options = listOf("0" to "0", "1" to "1", "2" to "2", "3" to "3"),
                        selected = maxRetries.toString(),
                        onSelect = { maxRetries = it.toIntOrNull() ?: 0 },
                    )
                    if (maxRetries > 0) {
                        Md3OutlinedTextFieldWithSlots(
                            value = retryDelay,
                            onValueChange = { retryDelay = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text(stringResource(R.string.setting_trigger_retry_delay)) },
                            supportingText = { Text(stringResource(R.string.setting_trigger_retry_delay_desc)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // ------------------------------------------------------------------ 事件列表
            Text(
                text = androidx.compose.ui.res.pluralStringResource(R.plurals.setting_trigger_events, events.size, events.size),
                style = MochiTheme.textStyles.titleMedium,
                color = MochiTheme.colorScheme.primary,
            )
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.setting_trigger_events_empty),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
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

            MochiTextButton(onClick = { showTypePicker = !showTypePicker }, shapes = ButtonDefaults.shapes()) {
                Icon(add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showTypePicker) stringResource(R.string.setting_trigger_types_hide) else stringResource(R.string.setting_trigger_event_add))
            }
            AnimatedVisibility(visible = showTypePicker) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ALL_EVENT_TYPES.forEach { type ->
                        KkFilterChip(
                            selected = false,
                            onClick = {
                                events.add(defaultEventFor(type))
                                expandedIndex = events.lastIndex
                                showTypePicker = false
                            },
                            label = eventTypeLabel(context, type),
                        )
                    }
                }
            }

            errorText?.let {
                Text(
                    text = it,
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.error,
                )
            }

            MochiTextButton(
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
                Text(stringResource(R.string.setting_trigger_defaults))
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
    // 事件卡的表头本来就是一个选项行：整行可点 = 展开/收起，右侧挂开关 + 删除 + 箭头。
    // 之前是 KkCard 里手写的 Row（裸 KkSwitch），行皮/圆角/内边距只有这里跟
    // 其它选项行不一样。改走 MochiExpandableOptionItem（= MochiOptionItem + 同卡展开区）
    // 后：折叠区仍在同一张卡内（wrapContent），点击语义 / 开关 / 删除 / 文案全部原样。
    val cards = listCardStyle()
    MochiExpandableOptionItem(
        expanded = expanded,
        onExpandedChange = { onToggle() },
        modifier = Modifier.fillMaxWidth(),
        shape = cards.indexedShape(0, 1),
        wrapContent = true,
        // 箭头自己画在尾槽里（与原静态上下箭头逐字相同），不让组件再补一个。
        showChevron = false,
        contentPadding = PaddingValues(bottom = 12.dp),
        titleContent = {
            Text(
                text = if (enabled) eventTypeLabel(LocalContext.current, event.type) else
                    stringResource(R.string.setting_trigger_disabled_label, eventTypeLabel(LocalContext.current, event.type)),
                style = MochiTheme.textStyles.titleMedium,
                color = if (enabled) {
                    MochiTheme.colorScheme.onSurface
                } else {
                    MochiTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = {
            Text(
                text = eventSummary(LocalContext.current, event),
                style = MochiTheme.textStyles.body,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KkSwitch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                )
                MochiIconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    Icon(delete, contentDescription = stringResource(R.string.setting_trigger_event_delete), modifier = Modifier.size(18.dp))
                }
                Icon(
                    imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        EventFields(cardName = cardName, event = event, onChange = onChange)
    }
}

@Composable
private fun EventFields(
    cardName: String,
    event: CardManifest.Event,
    onChange: (CardManifest.Event) -> Unit,
) {
    Column(
        // 水平内边距由 ExpandableOptionItem 的 contentPadding（InsideMargin 16dp）给，
        // 这里再加一层会变成 28dp。
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (event.type) {
            CardManifest.EVENT_SCHEDULE -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_times),
                    value = event.times.joinToString(", "),
                    onValueChange = { raw ->
                        val times = raw.split(',', '，', ' ', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onChange(event.copy(times = times))
                    },
                    placeholder = stringResource(R.string.setting_trigger_times_example),
                )
                IntStateField(
                    label = stringResource(R.string.setting_trigger_interval),
                    value = event.intervalMinutes,
                    onValueChange = { onChange(event.copy(intervalMinutes = it)) },
                )
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_weekdays),
                    options = listOf(
                        "" to stringResource(R.string.setting_trigger_every_day),
                        "1" to stringResource(R.string.setting_trigger_monday),
                        "2" to stringResource(R.string.setting_trigger_tuesday),
                        "3" to stringResource(R.string.setting_trigger_wednesday),
                        "4" to stringResource(R.string.setting_trigger_thursday),
                        "5" to stringResource(R.string.setting_trigger_friday),
                        "6" to stringResource(R.string.setting_trigger_saturday),
                        "7" to stringResource(R.string.setting_trigger_sunday),
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
                    text = stringResource(R.string.setting_trigger_weekdays_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_calendar),
                    options = listOf(
                        CardManifest.CALENDAR_ANY to stringResource(R.string.setting_trigger_calendar_any),
                        CardManifest.CALENDAR_WORKDAY to stringResource(R.string.setting_trigger_workday),
                        CardManifest.CALENDAR_WEEKEND to stringResource(R.string.setting_trigger_day_off),
                        CardManifest.CALENDAR_HOLIDAY to stringResource(R.string.setting_trigger_holiday),
                    ),
                    selected = event.calendar.ifBlank { CardManifest.CALENDAR_ANY },
                    onSelect = { onChange(event.copy(calendar = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_calendar_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_NOTIFICATION -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_optional),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = stringResource(R.string.setting_trigger_package_example),
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_title_contains),
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_body_contains),
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
            }

            CardManifest.EVENT_NOTIFICATION_CLICK -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_optional),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = stringResource(R.string.setting_trigger_package_example),
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_title_contains),
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_body_contains),
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_notification_click_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_NOTIFICATION_REPLY -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_required),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = stringResource(R.string.setting_trigger_package_example),
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_title_optional),
                    value = event.titleContains,
                    onValueChange = { onChange(event.copy(titleContains = it)) },
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_reply_contains),
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_reply_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_LAUNCH -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_optional),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
            }

            CardManifest.EVENT_APP_EXIT -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_required),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                    placeholder = stringResource(R.string.setting_trigger_package_example),
                )
                Text(
                    text = stringResource(R.string.setting_trigger_app_exit_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_INSTALL -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_optional),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_install_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_APP_UNINSTALL -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_package_optional),
                    value = event.packageName,
                    onValueChange = { onChange(event.copy(packageName = it.trim())) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_uninstall_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_SHORTCUT -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_shortcut_name),
                    value = event.name,
                    onValueChange = { onChange(event.copy(name = it)) },
                    placeholder = stringResource(R.string.setting_trigger_shortcut_example),
                )
                Text(
                    text = stringResource(R.string.setting_trigger_shortcut_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardManifest.EVENT_TILE -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_tile_name),
                    value = event.name,
                    onValueChange = { onChange(event.copy(name = it)) },
                    placeholder = stringResource(R.string.setting_trigger_tile_example),
                )
                Text(
                    text = stringResource(R.string.setting_trigger_tile_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
                val context = LocalContext.current
                val resources = LocalResources.current
                MochiTextButton(
                    onClick = {
                        TriggerTileHelper.requestAdd(
                            context = context,
                            card = cardName,
                            label = event.name.ifBlank { cardName },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.setting_trigger_tile_add))
                }
            }

            CardManifest.EVENT_CHARGING -> {
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_state),
                    options = listOf("connected" to stringResource(R.string.setting_trigger_power_connected), "disconnected" to stringResource(R.string.setting_trigger_power_disconnected)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_WIFI -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_ssid),
                    value = event.ssid,
                    onValueChange = { onChange(event.copy(ssid = it.trim())) },
                )
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_state),
                    options = listOf("" to stringResource(R.string.setting_trigger_any), "connected" to stringResource(R.string.setting_trigger_connected), "disconnected" to stringResource(R.string.setting_trigger_disconnected)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_NETWORK -> {
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_network_state),
                    options = listOf("online" to stringResource(R.string.setting_trigger_online), "offline" to stringResource(R.string.setting_trigger_offline)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_BATTERY -> {
                IntStateField(
                    label = stringResource(R.string.setting_trigger_battery_below),
                    value = event.levelBelow,
                    allowNegative = true,
                    onValueChange = { onChange(event.copy(levelBelow = it)) },
                )
                IntStateField(
                    label = stringResource(R.string.setting_trigger_battery_above),
                    value = event.levelAbove,
                    allowNegative = true,
                    onValueChange = { onChange(event.copy(levelAbove = it)) },
                )
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_charging_state),
                    options = listOf("" to stringResource(R.string.setting_trigger_any), "charging" to stringResource(R.string.setting_trigger_charging), "discharging" to stringResource(R.string.setting_trigger_discharging)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_SCREEN -> {
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_screen_state),
                    options = listOf(
                        "on" to stringResource(R.string.setting_trigger_screen_on),
                        "off" to stringResource(R.string.setting_trigger_screen_off),
                        "unlocked" to stringResource(R.string.setting_trigger_screen_unlocked),
                        "locked" to stringResource(R.string.setting_trigger_screen_locked),
                    ),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
            }

            CardManifest.EVENT_CLIPBOARD -> {
                TextStateField(
                    label = stringResource(R.string.setting_trigger_clipboard_contains),
                    value = event.textContains,
                    onValueChange = { onChange(event.copy(textContains = it)) },
                    placeholder = stringResource(R.string.setting_trigger_url_example),
                )
            }

            CardManifest.EVENT_BLUETOOTH -> {
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_state),
                    options = listOf("connected" to stringResource(R.string.setting_trigger_connected), "disconnected" to stringResource(R.string.setting_trigger_disconnected)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
                TextStateField(
                    label = stringResource(R.string.setting_trigger_device_contains),
                    value = event.device,
                    onValueChange = { onChange(event.copy(device = it)) },
                )
            }

            CardManifest.EVENT_LOCATION -> {
                DoubleStateField(
                    label = stringResource(R.string.setting_trigger_latitude),
                    value = event.lat,
                    onValueChange = { onChange(event.copy(lat = it)) },
                )
                DoubleStateField(
                    label = stringResource(R.string.setting_trigger_longitude),
                    value = event.lon,
                    onValueChange = { onChange(event.copy(lon = it)) },
                )
                IntStateField(
                    label = stringResource(R.string.setting_trigger_radius),
                    value = event.radiusM,
                    onValueChange = { onChange(event.copy(radiusM = it)) },
                )
                ChipSelector(
                    label = stringResource(R.string.setting_trigger_direction),
                    options = listOf("enter" to stringResource(R.string.setting_trigger_enter), "exit" to stringResource(R.string.setting_trigger_exit)),
                    selected = event.state,
                    onSelect = { onChange(event.copy(state = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_trigger_location_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
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
        Text(label, style = MochiTheme.textStyles.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, text) ->
                KkFilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = text,
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
    Md3OutlinedTextFieldWithSlots(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { placeholder?.let { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
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
    Md3OutlinedTextFieldWithSlots(
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
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DoubleStateField(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    Md3OutlinedTextFieldWithSlots(
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
        shape = RoundedCornerShape(16.dp)
    )
}

internal fun eventTypeLabel(context: android.content.Context, type: String): String = when (type) {
    CardManifest.EVENT_SCHEDULE -> context.getString(R.string.setting_trigger_type_schedule)
    CardManifest.EVENT_NOTIFICATION -> context.getString(R.string.setting_trigger_type_notification)
    CardManifest.EVENT_NOTIFICATION_CLICK -> context.getString(R.string.setting_trigger_type_notification_click)
    CardManifest.EVENT_NOTIFICATION_REPLY -> context.getString(R.string.setting_trigger_type_notification_reply)
    CardManifest.EVENT_APP_LAUNCH -> context.getString(R.string.setting_trigger_type_app_launch)
    CardManifest.EVENT_APP_EXIT -> context.getString(R.string.setting_trigger_type_app_exit)
    CardManifest.EVENT_APP_INSTALL -> context.getString(R.string.setting_trigger_type_app_install)
    CardManifest.EVENT_APP_UNINSTALL -> context.getString(R.string.setting_trigger_type_app_uninstall)
    CardManifest.EVENT_CHARGING -> context.getString(R.string.setting_trigger_type_charging)
    CardManifest.EVENT_WIFI -> context.getString(R.string.setting_trigger_type_wifi)
    CardManifest.EVENT_NETWORK -> context.getString(R.string.setting_trigger_type_network)
    CardManifest.EVENT_BATTERY -> context.getString(R.string.setting_trigger_type_battery)
    CardManifest.EVENT_SCREEN -> context.getString(R.string.setting_trigger_type_screen)
    CardManifest.EVENT_CLIPBOARD -> context.getString(R.string.setting_trigger_type_clipboard)
    CardManifest.EVENT_BLUETOOTH -> context.getString(R.string.setting_trigger_type_bluetooth)
    CardManifest.EVENT_LOCATION -> context.getString(R.string.setting_trigger_type_location)
    CardManifest.EVENT_SHORTCUT -> context.getString(R.string.setting_trigger_type_shortcut)
    CardManifest.EVENT_TILE -> context.getString(R.string.setting_trigger_type_tile)
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
internal fun validateEvent(event: CardManifest.Event): Int? = when (event.type) {
    CardManifest.EVENT_SCHEDULE -> when {
        event.times.isEmpty() && event.intervalMinutes <= 0 -> R.string.setting_trigger_error_schedule_required
        event.times.any { !TIME_REGEX.matches(it) } -> R.string.setting_trigger_error_time_format
        else -> null
    }

    CardManifest.EVENT_NOTIFICATION ->
        if (event.packageName.isBlank() && event.titleContains.isBlank() && event.textContains.isBlank()) {
            R.string.setting_trigger_error_notification_required
        } else null

    CardManifest.EVENT_NOTIFICATION_CLICK ->
        if (event.packageName.isBlank() && event.titleContains.isBlank() && event.textContains.isBlank()) {
            R.string.setting_trigger_error_notification_required
        } else null

    CardManifest.EVENT_NOTIFICATION_REPLY ->
        if (event.packageName.isBlank()) R.string.setting_trigger_error_reply_package else null

    CardManifest.EVENT_APP_EXIT ->
        if (event.packageName.isBlank()) R.string.setting_trigger_error_package else null

    CardManifest.EVENT_SHORTCUT ->
        if (event.name.isBlank()) R.string.setting_trigger_error_shortcut else null

    CardManifest.EVENT_TILE ->
        if (event.name.isBlank()) R.string.setting_trigger_error_tile else null

    CardManifest.EVENT_CLIPBOARD ->
        if (event.textContains.isBlank()) R.string.setting_trigger_error_text else null

    CardManifest.EVENT_BATTERY -> when {
        event.levelBelow < -1 || event.levelBelow > 100 -> R.string.setting_trigger_error_battery_below
        event.levelAbove < -1 || event.levelAbove > 100 -> R.string.setting_trigger_error_battery_above
        event.levelBelow < 0 && event.levelAbove < 0 && event.state.isBlank() ->
            R.string.setting_trigger_error_battery_required
        else -> null
    }

    CardManifest.EVENT_LOCATION -> {
        val lat = event.lat
        val lon = event.lon
        when {
            lat == null || lat !in -90.0..90.0 -> R.string.setting_trigger_error_latitude
            lon == null || lon !in -180.0..180.0 -> R.string.setting_trigger_error_longitude
            event.radiusM < 1 -> R.string.setting_trigger_error_radius
            else -> null
        }
    }

    else -> null
}
