package heizige.kk.khatkit.app.core.ui.activity

import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.KkCard
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import heizige.kk.khatkit.app.core.ui.components.ui.KkFilterChip
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.ui.hooks.writeStringPreference
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.util.CrashHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.uuid.Uuid
import heizige.kk.khatkit.uikit.mochi.MochiTopAppBar

@AndroidEntryPoint
class SafeModeActivity : ComponentActivity() {
    @Inject
    lateinit var settingsStore: SettingsRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = CrashHandler.getStackTrace(this)
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            KhatKitTheme {
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                var showAssistantPicker by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // 安全模式也要跟主题走：Miuix 下用 Kedge 的顶栏
                        MochiTopAppBar(title = stringResource(R.string.safe_mode_title))
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.safe_mode_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(
                                R.string.safe_mode_current_assistant,
                                settings.getCurrentAssistant().name.ifEmpty { stringResource(R.string.safe_mode_default_assistant) }),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        MochiButton(
                            onClick = { showAssistantPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.safe_mode_switch_assistant))
                        }

                        MochiButton(
                            onClick = {
                                startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.safe_mode_enter_app))
                        }

                        if (stackTrace != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.safe_mode_crash_report),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                MochiButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("crash", stackTrace))
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.safe_mode_copy))
                                }
                            }
                            KkCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                val vScroll = rememberScrollState()
                                val hScroll = rememberScrollState()
                                Text(
                                    text = stackTrace,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(vScroll)
                                        .horizontalScroll(hScroll),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                // U7b-6：state 由这里持有，去 if 门控。
                val pickerSheet = rememberMochiSheetState()
                LaunchedEffect(showAssistantPicker) { if (showAssistantPicker) pickerSheet.show() else pickerSheet.hide() }
                AssistantPickerSheet(
                        sheetState = pickerSheet,
                        settings = settings,
                        onAssistantSelected = { assistantId ->
                            scope.launch { settingsStore.updateAssistant(assistantId) }
                            context.writeStringPreference("lastConversationId", null)
                            showAssistantPicker = false
                        },
                        onDismiss = { showAssistantPicker = false }
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantPickerSheet(
    sheetState: ui.feice.mochi.basic.MochiSheetState,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settings.assistantTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
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
            // 圆角与同页其他行同源：listCardStyle().indexedShape(index, count)，不再直写
            // MaterialTheme.shapes.large。选中态沿用改动前的主色容器 + onPrimaryContainer
            // 字色（MochiOptionItem 只有 backgroundColor 槽，字色在 titleContent 里显式给），
            // 行尾再补一个勾作为选中标记。
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
