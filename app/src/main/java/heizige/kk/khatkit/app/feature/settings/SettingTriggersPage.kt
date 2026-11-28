package heizige.kk.khatkit.app.feature.settings

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.app.feature.automation.eventTypeLabel

import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.KkRichFilterChip
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkCard
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.feature.automation.data.CardTriggerOverride
import heizige.kk.khatkit.app.feature.automation.KhatKitNotificationListenerService
import heizige.kk.khatkit.app.feature.automation.helper.TriggerManager
import heizige.kk.khatkit.app.feature.automation.helper.TriggerAlarmHelper
import heizige.kk.khatkit.app.feature.automation.TriggerService
import heizige.kk.khatkit.app.feature.automation.data.TriggerSettingsRepository
import heizige.kk.khatkit.app.feature.automation.data.computeTriggerLogStats
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.MochiPageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionInfo
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.icons.automation.bolt
import heizige.kk.khatkit.app.core.ui.icons.utility.cleaningServices
import heizige.kk.khatkit.app.core.ui.icons.utility.editNote
import heizige.kk.khatkit.app.core.ui.icons.settings.notifications
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerCard
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import heizige.kk.khatkit.app.feature.workflow.data.WorkflowPreferencesRepository
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.feature.automation.TriggerCardEditorSheet



private val PermissionFineLocation = PermissionInfo(
    permission = Manifest.permission.ACCESS_FINE_LOCATION,
    displayName = { Text(stringResource(R.string.setting_trigger_permission_location)) },
    usage = { Text(stringResource(R.string.setting_trigger_permission_location_desc)) },
    required = false,
)

private val PermissionBluetoothConnect = PermissionInfo(
    permission = Manifest.permission.BLUETOOTH_CONNECT,
    displayName = { Text(stringResource(R.string.setting_trigger_permission_bluetooth)) },
    usage = { Text(stringResource(R.string.setting_trigger_permission_bluetooth_desc)) },
    required = false,
)

/** 自动化触发器设置：总开关、事件卡片编辑、执行日志、通知监听/电池优化入口。 */
@Composable
fun SettingTriggersPage() {
    val controller: TriggerManager = rememberAppEntryPoint().triggerController()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val triggerState by controller.settings.state.collectAsStateWithLifecycle()
    val cards by controller.cards.collectAsStateWithLifecycle()
    val logEntries by controller.logs.entries.collectAsStateWithLifecycle()
    var workflows by remember { mutableStateOf(WorkflowPreferencesRepository.load(context)) }

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(PermissionNotification)
            add(PermissionFineLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PermissionBluetoothConnect)
        },
    )
    PermissionManager(permissionState = permissionState)
    var pendingStart by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<TriggerCard?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }
    val editingOverride = remember(editingCard) {
        editingCard?.let { controller.settings.overrideFor(it.name) } ?: CardTriggerOverride.NONE
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMddHHmmss"), locale)
    }
    val logStats = remember(logEntries) { computeTriggerLogStats(logEntries) }
    val exactAlarmAllowed = runCatching {
        TriggerAlarmHelper.canScheduleExact(context)
    }.getOrDefault(true)

    LaunchedEffect(Unit) {
        controller.refreshCardsAsync()
    }
    LaunchedEffect(permissionState.allRequiredPermissionsGranted) {
        if (pendingStart && permissionState.allRequiredPermissionsGranted) {
            pendingStart = false
            TriggerService.start(context)
        }
    }

    fun applyMaster(enabled: Boolean) {
        scope.launch {
            controller.settings.setMasterEnabled(enabled)
            controller.syncEngine()
        }
        if (enabled) {
            controller.refreshCardsAsync()
            if (permissionState.allRequiredPermissionsGranted) {
                TriggerService.start(context)
            } else {
                pendingStart = true
                permissionState.requestPermissions()
            }
        } else {
            TriggerService.stop(context)
        }
    }

    // U7b-2d：宿主接收 sheetState；保留最后一份卡片快照，退出动画期间卡片内容不消失。
    val cardEditorSheet = rememberMochiSheetState()
    var lastEditingCard by remember { mutableStateOf<TriggerCard?>(null) }
    LaunchedEffect(editingCard) {
        if (editingCard != null) {
            lastEditingCard = editingCard
            cardEditorSheet.show()
        } else {
            cardEditorSheet.hide()
        }
    }
    lastEditingCard?.let { card ->
        TriggerCardEditorSheet(
            sheetState = cardEditorSheet,
            card = card,
            override = editingOverride,
            onSave = { override ->
                controller.saveOverride(card.name, override)
                editingCard = null
            },
            onDismiss = { editingCard = null },
        )
    }

    if (showAiDialog) {
        AppDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text(stringResource(R.string.setting_trigger_ai_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.setting_trigger_ai_desc),
                        style = MochiTheme.textStyles.body,
                    )
                    listOf(
                        stringResource(R.string.setting_trigger_ai_screen),
                        stringResource(R.string.setting_trigger_ai_reply),
                        stringResource(R.string.setting_trigger_ai_retry),
                    ).forEach { suggestion ->
                        KkCard(
                            colors = CardDefaults.cardColors(
                                containerColor = MochiTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                suggestion,
                                modifier = Modifier.padding(12.dp),
                                style = MochiTheme.textStyles.body,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MochiTextButton(onClick = { showAiDialog = false }) { Text(stringResource(R.string.setting_trigger_ai_acknowledge)) }
            },
        )
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_triggers_title),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                top = PageMetrics.TopContentPadding,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("master") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_trigger_master)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_trigger_master_desc))
                        },
                        trailingContent = {
                            KkSwitch(
                                checked = triggerState.masterEnabled,
                                onCheckedChange = { applyMaster(it) },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_trigger_parallel)) },
                        supportingContent = { Text(stringResource(R.string.setting_trigger_parallel_desc)) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (TriggerSettingsRepository.MIN_MAX_PARALLEL..TriggerSettingsRepository.MAX_MAX_PARALLEL).forEach { value ->
                                    KkFilterChip(
                                        selected = triggerState.maxParallel == value,
                                        onClick = { controller.setMaxParallel(value) },
                                        label = "$value",
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item("overview") {
                FlowOverviewCard(
                    cards = cards,
                    enabled = triggerState.masterEnabled,
                    onAddAi = { showAiDialog = true },
                    onCreate = { navController.navigate(Screen.CreateWorkflow()) },
                )
            }

            item("saved-workflows") {
                if (workflows.isNotEmpty()) {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text(stringResource(R.string.setting_trigger_my_workflows)) },
                    ) {
                        workflows.forEach { workflow ->
                            item(
                                onClick = {
                                    navController.navigate(Screen.CreateWorkflow(workflow.id))
                                },
                                chevron = true,
                                headlineContent = { Text(workflow.name) },
                                supportingContent = {
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.setting_trigger_workflow_nodes, workflow.nodes.size, workflow.nodes.size, stringResource(if (workflow.enabled) R.string.setting_trigger_enabled else R.string.setting_trigger_disabled)))
                                },
                                trailingContent = {
                                    MochiTextButton(
                                        onClick = {
                                            WorkflowPreferencesRepository.delete(context, workflow.id)
                                            workflows = WorkflowPreferencesRepository.load(context)
                                        },
                                    ) { Text(stringResource(R.string.common_delete)) }
                                },
                            )
                        }
                    }
                }
            }

            item("cards") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_trigger_workflow_nodes)) },
                ) {
                    if (cards.isEmpty()) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_trigger_no_cards)) },
                            supportingContent = {
                                Text(stringResource(R.string.setting_trigger_cards_desc))
                            },
                        )
                    }
                    cards.forEach { card ->
                        val cardEnabled = triggerState.masterEnabled &&
                            triggerState.isCardEnabled(card.name)
                        item(
                            onClick = { editingCard = card },
                            leadingContent = { Icon(editNote, contentDescription = null) },
                            headlineContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(card.name, fontWeight = FontWeight.SemiBold)
                                    FlowNodeStrip(
                                        events = card.events,
                                        disabledIndexes = card.disabledIndexes,
                                    )
                                }
                            },
                            supportingContent = {
                                Text(
                                    if (card.events.isEmpty()) {
                                        stringResource(R.string.setting_trigger_no_events)
                                    } else {
                                        card.events.mapIndexed { index, event ->
                                            resources.getString(R.string.setting_trigger_list_summary,
                                                eventSummary(context, event).let { summary ->
                                                    if (index in card.disabledIndexes) resources.getString(R.string.setting_trigger_disabled_label, summary) else summary
                                                })
                                        }.joinToString("\n")
                                    }
                                )
                            },
                            trailingContent = {
                                KkSwitch(
                                    checked = cardEnabled,
                                    enabled = triggerState.masterEnabled,
                                    onCheckedChange = { checked ->
                                        controller.settings.setCardEnabled(card.name, checked)
                                        controller.syncEngine()
                                        controller.syncExternalBindingsAsync()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item("stats") {
                if (logEntries.isNotEmpty()) {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.setting_trigger_stats_records, logEntries.size, logEntries.size)) },
                    ) {
                        item(
                            headlineContent = {
                                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.setting_trigger_today_runs, logStats.todayRuns, logStats.todayRuns, logStats.successRate))
                            },
                            supportingContent = {
                                Text(
                                    buildString {
                                        if (logStats.topCards.isNotEmpty()) {
                                            append(resources.getString(R.string.setting_trigger_top_cards,
                                                logStats.topCards.joinToString(resources.getString(R.string.setting_trigger_summary_separator)) {
                                                    resources.getString(R.string.setting_trigger_card_count, it.first, it.second)
                                                }
                                            ))
                                        }
                                        if (logStats.typeCounts.isNotEmpty()) {
                                            if (isNotEmpty()) append("\n")
                                            append(resources.getString(R.string.setting_trigger_type_distribution,
                                                logStats.typeCounts.joinToString(resources.getString(R.string.setting_trigger_summary_separator)) {
                                                    resources.getString(R.string.setting_trigger_type_count, eventTypeLabel(context, it.first), it.second)
                                                }
                                            ))
                                        }
                                    }
                                )
                            },
                        )
                    }
                }
            }

            item("logs") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.setting_trigger_log_records, logEntries.size, minOf(logEntries.size, LOG_UI_LIMIT), logEntries.size)) },
                ) {
                    if (logEntries.isEmpty()) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_trigger_no_logs)) },
                            supportingContent = { Text(stringResource(R.string.setting_trigger_logs_desc)) },
                        )
                    } else {
                        item(
                            onClick = { controller.logs.clear() },
                            leadingContent = { Icon(cleaningServices, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.setting_trigger_logs_clear)) },
                            supportingContent = { Text(stringResource(R.string.setting_trigger_logs_clear_desc)) },
                        )
                        logEntries.asReversed().take(LOG_UI_LIMIT).forEach { entry ->
                            item(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.setting_trigger_log_result, stringResource(if (entry.ok) R.string.setting_trigger_success else R.string.setting_trigger_failure), entry.card),
                                        color = if (entry.ok) {
                                            MochiTheme.colorScheme.onSurface
                                        } else {
                                            MochiTheme.colorScheme.error
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(timeFormat.format(Date(entry.at)))
                                            append(" · ")
                                            append(eventTypeLabel(context, entry.type))
                                            if (entry.payload.isNotBlank()) {
                                                append(" · ")
                                                append(entry.payload)
                                            }
                                            if (entry.message.isNotBlank()) {
                                                append("\n")
                                                append(entry.message)
                                            }
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item("permissions") {
                val listenerEnabled by produceState(
                    initialValue = KhatKitNotificationListenerService.isEnabled(context),
                ) {
                    value = KhatKitNotificationListenerService.isEnabled(context)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_trigger_permissions)) },
                ) {
                    item(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        leadingContent = { Icon(notifications, null) },
                        headlineContent = { Text(stringResource(R.string.setting_trigger_notification_access)) },
                        supportingContent = {
                            Text(
                                if (listenerEnabled) stringResource(R.string.setting_trigger_permission_granted)
                                else stringResource(R.string.setting_trigger_permission_not_granted)
                            )
                        },
                    )
                    item(
                        onClick = {
                            val packageUri = Uri.parse("package:${context.packageName}")
                            val request = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                packageUri,
                            )
                            runCatching { context.startActivity(request) }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    )
                                }
                            }
                        },
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text(stringResource(R.string.setting_trigger_ignore_battery)) },
                        supportingContent = { Text(stringResource(R.string.setting_trigger_ignore_battery_desc)) },
                    )
                    item(
                        onClick = { permissionState.requestPermissions() },
                        headlineContent = { Text(stringResource(R.string.setting_trigger_runtime_permissions)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_trigger_runtime_permissions_desc))
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmAllowed) {
                        item(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            leadingContent = { Icon(bolt, null) },
                            headlineContent = { Text(stringResource(R.string.setting_trigger_exact_alarm)) },
                            supportingContent = {
                                Text(stringResource(R.string.setting_trigger_exact_alarm_desc))
                            },
                        )
                    }
                    item(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_trigger_service_hint),
                                style = MochiTheme.textStyles.body,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowOverviewCard(
    cards: List<TriggerCard>,
    enabled: Boolean,
    onAddAi: () -> Unit,
    onCreate: () -> Unit,
) {
    KkCard(
        modifier = Modifier.padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MochiTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_workflow_title), style = MochiTheme.textStyles.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) stringResource(R.string.setting_workflow_listening) else stringResource(R.string.setting_workflow_paused),
                        style = MochiTheme.textStyles.body,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.setting_workflow_count, cards.count { it.events.isNotEmpty() }, cards.count { it.events.isNotEmpty() }),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.primary,
                )
            }
            if (cards.isEmpty()) {
                Text(
                    stringResource(R.string.setting_workflow_install_hint),
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowNodeStrip(
                    events = cards.firstOrNull { it.events.isNotEmpty() }?.events.orEmpty(),
                    disabledIndexes = cards.firstOrNull { it.events.isNotEmpty() }?.disabledIndexes.orEmpty(),
                    showLabels = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KkRichFilterChip(
                    selected = false,
                    onClick = onCreate,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.setting_workflow_create))
                        }
                    },
                )
                KkFilterChip(
                    selected = false,
                    onClick = onAddAi,
                    label = stringResource(R.string.setting_workflow_ai),
                )
                Text(
                    stringResource(R.string.setting_workflow_nodes_hint),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MochiTheme.textStyles.labelSmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlowNodeStrip(
    events: List<CardManifest.Event>,
    disabledIndexes: Set<Int> = emptySet(),
    showLabels: Boolean = false,
) {
    if (events.isEmpty()) {
        Text(
            stringResource(R.string.setting_workflow_no_trigger),
            style = MochiTheme.textStyles.labelSmall,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        events.take(5).forEachIndexed { index, event ->
            if (index > 0) {
                // Canvas 的 lambda 是 DrawScope，不是 composable：颜色必须在
                // 组合阶段读出来再传进去。
                val connectorColor = MochiTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 1.dp)
                        .padding(horizontal = 2.dp),
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawLine(
                            color = connectorColor,
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 2f,
                        )
                    }
                }
            }
            val active = index !in disabledIndexes
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (active) Modifier else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = editNote,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (active) {
                            MochiTheme.colorScheme.primary
                        } else {
                            MochiTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (showLabels) {
                    Text(
                        eventTypeLabel(LocalContext.current, event.type),
                        style = MochiTheme.textStyles.labelSmall,
                        color = if (active) {
                            MochiTheme.colorScheme.onSurface
                        } else {
                            MochiTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        if (events.size > 5) {
            Text(
                stringResource(R.string.setting_trigger_more_count, events.size - 5),
                style = MochiTheme.textStyles.labelSmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private const val LOG_UI_LIMIT = 50

internal fun eventSummary(context: android.content.Context, event: CardManifest.Event): String {
    fun label(id: Int) = context.getString(id)
    fun join(parts: List<String>) = parts.filter { it.isNotBlank() }
        .joinToString(label(R.string.setting_trigger_summary_separator))
    fun detail(value: String) = context.getString(
        R.string.setting_trigger_summary_detail, eventTypeLabel(context, event.type), value,
    )
    val anyApp = label(R.string.setting_trigger_any_app)
    fun connection() = label(when (event.state) {
        CardManifest.STATE_CONNECTED -> R.string.setting_trigger_connected
        CardManifest.STATE_DISCONNECTED -> R.string.setting_trigger_disconnected
        else -> R.string.setting_trigger_any
    })
    return when (event.type) {
        CardManifest.EVENT_SCHEDULE -> {
            val timing = if (event.times.isNotEmpty()) join(event.times) else if (event.intervalMinutes > 0)
                context.resources.getQuantityString(R.plurals.setting_trigger_interval_minutes, event.intervalMinutes, event.intervalMinutes)
            else ""
            val days = if (event.days.isEmpty()) label(R.string.setting_trigger_every_day) else join(event.days.map { day ->
                val weekdays = listOf(R.string.setting_trigger_monday, R.string.setting_trigger_tuesday,
                    R.string.setting_trigger_wednesday, R.string.setting_trigger_thursday,
                    R.string.setting_trigger_friday, R.string.setting_trigger_saturday, R.string.setting_trigger_sunday)
                weekdays.getOrNull(day - 1)?.let { label(it) } ?: label(R.string.setting_trigger_unknown_value)
            })
            val calendar = when (event.calendar) {
                CardManifest.CALENDAR_WORKDAY -> label(R.string.setting_trigger_workday)
                CardManifest.CALENDAR_WEEKEND -> label(R.string.setting_trigger_day_off)
                CardManifest.CALENDAR_HOLIDAY -> label(R.string.setting_trigger_holiday)
                else -> ""
            }
            detail(join(listOf(timing, days, calendar)))
        }
        CardManifest.EVENT_NOTIFICATION, CardManifest.EVENT_NOTIFICATION_CLICK,
        CardManifest.EVENT_NOTIFICATION_REPLY -> detail(join(buildList {
            add(event.packageName.ifBlank { anyApp })
            if (event.titleContains.isNotBlank()) add(context.getString(R.string.setting_trigger_title_filter, event.titleContains))
            if (event.textContains.isNotBlank()) add(context.getString(R.string.setting_trigger_body_filter, event.textContains))
        }))
        CardManifest.EVENT_APP_LAUNCH, CardManifest.EVENT_APP_INSTALL, CardManifest.EVENT_APP_UNINSTALL ->
            detail(event.packageName.ifBlank { anyApp })
        CardManifest.EVENT_APP_EXIT -> detail(event.packageName.ifBlank { label(R.string.setting_trigger_package_missing) })
        CardManifest.EVENT_SHORTCUT, CardManifest.EVENT_TILE -> detail(event.name.ifBlank { label(R.string.setting_trigger_unnamed) })
        CardManifest.EVENT_CHARGING -> detail(label(if (event.state == CardManifest.CHARGING_DISCONNECTED)
            R.string.setting_trigger_power_disconnected else R.string.setting_trigger_power_connected))
        CardManifest.EVENT_WIFI -> detail(join(listOf(event.ssid.ifBlank { label(R.string.setting_trigger_any_hotspot) }, connection())))
        CardManifest.EVENT_NETWORK -> detail(label(if (event.state == CardManifest.NETWORK_OFFLINE)
            R.string.setting_trigger_offline else R.string.setting_trigger_online))
        CardManifest.EVENT_BATTERY -> detail(join(buildList {
            if (event.levelBelow >= 0) add(context.getString(R.string.setting_trigger_summary_battery_below, event.levelBelow))
            if (event.levelAbove >= 0) add(context.getString(R.string.setting_trigger_summary_battery_above, event.levelAbove))
            when (event.state) {
                CardManifest.BATTERY_CHARGING -> add(label(R.string.setting_trigger_charging))
                CardManifest.BATTERY_DISCHARGING -> add(label(R.string.setting_trigger_discharging))
            }
        }).ifBlank { label(R.string.setting_trigger_any) })
        CardManifest.EVENT_SCREEN -> {
            val state = when (event.state) {
                CardManifest.SCREEN_ON -> R.string.setting_trigger_screen_on
                CardManifest.SCREEN_OFF -> R.string.setting_trigger_screen_off
                CardManifest.SCREEN_UNLOCKED -> R.string.setting_trigger_screen_unlocked
                CardManifest.SCREEN_LOCKED -> R.string.setting_trigger_screen_locked
                else -> null
            }
            state?.let { detail(label(it)) } ?: eventTypeLabel(context, event.type)
        }
        CardManifest.EVENT_CLIPBOARD -> context.getString(R.string.setting_trigger_clipboard_filter, event.textContains)
        CardManifest.EVENT_BLUETOOTH -> detail(join(listOf(event.device,
            label(if (event.state == CardManifest.STATE_DISCONNECTED) R.string.setting_trigger_disconnected else R.string.setting_trigger_connected))))
        CardManifest.EVENT_LOCATION -> context.getString(R.string.setting_trigger_location_summary,
            label(if (event.state == CardManifest.LOCATION_EXIT) R.string.setting_trigger_exit else R.string.setting_trigger_enter),
            event.lat?.toString() ?: label(R.string.setting_trigger_unknown_value),
            event.lon?.toString() ?: label(R.string.setting_trigger_unknown_value), event.radiusM)
        else -> event.type
    }
}
