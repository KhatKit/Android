package heizige.kk.khatkit.app.core.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import heizige.kk.khatkit.app.core.ui.hooks.writeStringPreference
import heizige.kk.khatkit.app.core.ui.icons.bugReport
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.CrashHandler
import heizige.kk.khatkit.app.core.util.CrashReport
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import heizige.kk.khatkit.uikit.mochi.KkCard
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiButtonVariant
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButtonVariant
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.MochiStatusLevel
import heizige.kk.khatkit.uikit.mochi.MochiStatusTag
import heizige.kk.khatkit.uikit.mochi.MochiTopAppBar
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListDefaults
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * 安全模式 / 崩溃页。
 *
 * 上次进程没正常退出时由 [RouteActivity] 拉起：Java 未捕获异常在崩溃当时就被 [CrashHandler] 记下，
 * 原生崩溃（渲染线程栈溢出、SIGSEGV 等）由下次启动时从 ApplicationExitInfo 取回的 tombstone 补上。
 *
 * 版面从上到下只有三层，眼睛扫一遍就知道「出了什么事 / 现在能做什么 / 报告在哪」：
 *  1. 结论卡：一颗状态图标 + 一句发生了什么 + 一行元信息（原生崩溃 / 未捕获异常 · 线程 · 时间）。
 *  2. 动作：进入应用（主按钮）、切换助手（次按钮，皮肤/助手配置有问题时换一个再试）。
 *  3. 报告：等宽字、可选中、可整段复制的原始栈；没有报告时给一句解释，不留一个空白的框。
 */
@AndroidEntryPoint
class SafeModeActivity : ComponentActivity() {
    @Inject
    lateinit var settingsStore: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读一次就清：这一页自己再崩，下次仍会重新拿系统记录（不在这里留下「上次崩溃」的死循环）。
        val report = CrashHandler.getReport(this)
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            KhatKitTheme {
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                var showAssistantPicker by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val pickerSheet = rememberMochiSheetState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { MochiTopAppBar(title = stringResource(R.string.safe_mode_title)) },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CrashConclusion(report)
                        CrashActions(
                            assistantName = settings.getCurrentAssistant().name
                                .ifEmpty { stringResource(R.string.safe_mode_default_assistant) },
                            onEnterApp = {
                                startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                                finish()
                            },
                            onSwitchAssistant = { showAssistantPicker = true },
                        )
                        if (report != null) {
                            CrashReportCard(report, Modifier.weight(1f))
                        } else {
                            NoReportCard()
                        }
                    }
                }

                // U7b-6：state 由这里持有，去 if 门控。
                LaunchedEffect(showAssistantPicker) {
                    if (showAssistantPicker) pickerSheet.show() else pickerSheet.hide()
                }
                AssistantPickerSheet(
                    sheetState = pickerSheet,
                    settings = settings,
                    onAssistantSelected = { assistantId ->
                        scope.launch { settingsStore.updateAssistant(assistantId) }
                        context.writeStringPreference("lastConversationId", null)
                        showAssistantPicker = false
                    },
                    onDismiss = { showAssistantPicker = false },
                )
            }
        }
    }
}

/** 结论卡：发生了什么 + 哪一类崩溃 + 线程/时间。 */
@Composable
private fun CrashConclusion(report: CrashReport?) {
    val scheme = MaterialTheme.colorScheme
    KkCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (report?.native == true) bugReport else warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = scheme.onErrorContainer,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.safe_mode_crashed_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.safe_mode_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (report != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        MochiStatusTag(
                            label = stringResource(
                                if (report.native) R.string.safe_mode_kind_native else R.string.safe_mode_kind_java,
                            ),
                            level = if (report.native) MochiStatusLevel.Error else MochiStatusLevel.Warning,
                        )
                        Text(
                            text = reportMeta(report),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 元信息：线程名 · 崩溃时间；缺哪个少哪个。 */
@Composable
private fun reportMeta(report: CrashReport): String {
    val time = report.time.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toLocalDateTime() }
    return when {
        report.thread.isNotBlank() && time != null -> stringResource(R.string.safe_mode_report_meta, report.thread, time)
        report.thread.isNotBlank() -> report.thread
        else -> time.orEmpty()
    }
}

/** 动作区：进入应用 / 切换助手，下面一行小字写当前助手。 */
@Composable
private fun CrashActions(
    assistantName: String,
    onEnterApp: () -> Unit,
    onSwitchAssistant: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MochiButton(
            onClick = onEnterApp,
            modifier = Modifier.fillMaxWidth(),
            variant = MochiButtonVariant.Primary,
        ) {
            Text(stringResource(R.string.safe_mode_enter_app))
        }
        MochiButton(
            onClick = onSwitchAssistant,
            modifier = Modifier.fillMaxWidth(),
            variant = MochiButtonVariant.Secondary,
        ) {
            Text(stringResource(R.string.safe_mode_switch_assistant))
        }
        Text(
            text = stringResource(R.string.safe_mode_current_assistant, assistantName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * 报告卡：标题行（标题 + 一句话摘要 + 复制按钮） / 分隔线 / 等宽原文。
 * 原文可长按选中，也可以一键整段复制；滚动跟着手指，卡内独立滚动，不吃外层。
 */
@Composable
private fun CrashReportCard(report: CrashReport, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }
    KkCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.safe_mode_crash_report),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = report.summary.ifBlank {
                        stringResource(if (report.native) R.string.safe_mode_kind_native else R.string.safe_mode_kind_java)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MochiIconButton(
                onClick = {
                    copyToClipboard(context, report)
                    copied = true
                },
                variant = MochiIconButtonVariant.Tonal,
            ) {
                Icon(
                    imageVector = if (copied) check else contentCopy,
                    contentDescription = stringResource(
                        if (copied) R.string.safe_mode_copied else R.string.safe_mode_copy,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        MochiHorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            SelectionContainer {
                Text(
                    text = report.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 没有报告时：说清楚为什么没有，而不是留一个空框。 */
@Composable
private fun NoReportCard() {
    KkCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(
            text = stringResource(R.string.safe_mode_report_empty),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.safe_mode_report_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyToClipboard(context: Context, report: CrashReport) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val header = buildString {
        appendLine(if (report.native) "Native crash" else "Uncaught exception")
        appendLine(report.summary)
        if (report.thread.isNotBlank()) appendLine("Thread: ${report.thread}")
        if (report.time > 0L) appendLine("Time: ${Instant.ofEpochMilli(report.time).toLocalDateTime()}")
        appendLine()
    }
    cm.setPrimaryClip(ClipData.newPlainText("crash", header + report.text))
}

@Composable
private fun AssistantPickerSheet(
    sheetState: MochiSheetState,
    settings: Settings,
    onAssistantSelected: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) settings.assistants
        else settings.assistants.filter { it.tags.any { id -> id in selectedTagIds } }
    }

    // U7b-6：直连 MochiSheet（state 由调用方持有）。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(R.string.safe_mode_assistants))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.assistantTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(settings.assistantTags, key = { it.id }) { tag ->
                        KkFilterChip(
                            onClick = {
                                selectedTagIds = if (tag.id in selectedTagIds) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = tag.name,
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }

            // 助手单选行 = 一张 option item（底色的内边距/按压反馈都交给 MochiOptionItem），
            // 圆角与同页其他行同源：listCardStyle().indexedShape(index, count)。
            val cards = listCardStyle()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(cards.gap),
            ) {
                itemsIndexed(filteredAssistants, key = { _, assistant -> assistant.id }) { index, assistant ->
                    val checked = assistant.id == settings.assistantId
                    KkGroupedColumn(
                        modifier = Modifier.animateItem(),
                        shape = cards.indexedShape(index, filteredAssistants.size),
                        containerColor = MochiListDefaults.colors().container,
                        topDivider = index > 0 && cards.gap == 0.dp,
                    ) {
                        MochiOptionItem(
                            onClick = {
                                onAssistantSelected(assistant.id)
                                onDismiss()
                            },
                            shape = cards.indexedShape(index, filteredAssistants.size),
                            backgroundColor = if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            titleContent = {
                                Text(
                                    text = assistant.name.ifEmpty { stringResource(R.string.safe_mode_default_assistant) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            trailingContent = if (checked) {
                                {
                                    Icon(
                                        imageVector = check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
