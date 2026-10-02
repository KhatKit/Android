package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.hooks.EditStateContent
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeSettingsPageScaffold(
            title = stringResource(R.string.assistant_page_tab_memory),
            navigationIcon = { BackButton() },
        ) { innerPadding ->
            AssistantMemoryContent(
                // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
                // 旧写法硬编码 PaddingValues(horizontal = 16.dp)（因为 MiuixSettingsPage
                // 自己用 LazyColumn 的 contentPadding 处理了顶栏），改成内容直接透传后
                // 顶部就是 0，content 直接压在 TopAppBar 下面。
                innerPadding = innerPadding,
                assistant = assistant,
                memories = memories,
                onUpdateAssistant = { vm.update(it) },
                onDeleteMemory = { vm.deleteMemory(it) },
                onAddMemory = { vm.addMemory(it) },
                onUpdateMemory = { vm.updateMemory(it) },
            )
        }
        return
    }

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.assistant_page_tab_memory),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantMemoryContent(
            innerPadding = innerPadding,
            assistant = assistant,
            memories = memories,
            onUpdateAssistant = { vm.update(it) },
            onDeleteMemory = { vm.deleteMemory(it) },
            onAddMemory = { vm.addMemory(it) },
            onUpdateMemory = { vm.updateMemory(it) }
        )
    }
}

@Composable
private fun AssistantMemoryContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    memories: List<AssistantMemory>,
    onUpdateAssistant: (Assistant) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }

    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AppAlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                KedgeTextFieldWithSlots(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                KedgeTextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                KedgeTextButton(onClick = { showTimeReminderIntervalDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    // 记忆对话框
    memoryDialogState.EditStateContent { memory, update ->
        AppAlertDialog(
            onDismissRequest = {
                memoryDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.assistant_page_manage_memory_title))
            },
            text = {
                KedgeTextFieldWithSlots(
                    value = memory.content,
                    onValueChange = {
                        update(memory.copy(content = it))
                    },
                    label = {
                        Text(stringResource(R.string.assistant_page_manage_memory_title))
                    },
                    minLines = 2,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        memoryDialogState.confirm()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                KedgeTextButton(
                    onClick = {
                        memoryDialogState.dismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KedgeFormCard {
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_memory),
                summary = stringResource(R.string.assistant_page_memory_desc),
                checked = assistant.enableMemory,
                onCheckedChange = {
                    onUpdateAssistant(assistant.copy(enableMemory = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_global_memory),
                summary = stringResource(R.string.assistant_page_global_memory_desc),
                checked = assistant.useGlobalMemory,
                onCheckedChange = {
                    onUpdateAssistant(assistant.copy(useGlobalMemory = it))
                },
                enabled = assistant.enableMemory,
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_recent_chats),
                summary = stringResource(R.string.assistant_page_recent_chats_desc),
                checked = assistant.enableRecentChatsReference,
                onCheckedChange = {
                    onUpdateAssistant(assistant.copy(enableRecentChatsReference = it))
                },
            )
        }

        KedgeFormCard {
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_time_reminder),
                summary = stringResource(R.string.assistant_page_time_reminder_desc),
                checked = assistant.enableTimeReminder,
                onCheckedChange = {
                    onUpdateAssistant(assistant.copy(enableTimeReminder = it))
                },
            )
            if (assistant.enableTimeReminder) {
                PreferenceArrow(
                    title = stringResource(R.string.assistant_page_time_reminder_interval),
                    summary = stringResource(R.string.assistant_page_time_reminder_interval_desc),
                    onClick = {
                        timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showTimeReminderIntervalDialog = true
                    },
                    endAction = {
                        Text(stringResource(
                            R.string.assistant_page_time_reminder_interval_value,
                            assistant.timeReminderIntervalMinutes,
                        ))
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = KedgeTextStyles.title(),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterStart)
            )

            KedgeIconButton(
                onClick = {
                    memoryDialogState.open(AssistantMemory(0, ""))
                },
                modifier = Modifier.align(Alignment.CenterEnd),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = add,
                    contentDescription = null
                )
            }
        }

        memories.fastForEach { memory ->
            key(memory.id) {
                MemoryItem(
                    memory = memory,
                    onEditMemory = {
                        memoryDialogState.open(it)
                    },
                    onDeleteMemory = {
                        pendingDeleteMemory = it
                    }
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.content.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit
) {
    KedgeCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = memory.content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = KedgeTextStyles.body(),
                )
            }
            KedgeIconButton(
                onClick = { onEditMemory(memory) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(edit, null)
            }
            KedgeIconButton(
                onClick = { onDeleteMemory(memory) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    delete,
                    stringResource(R.string.assistant_page_delete)
                )
            }
        }
    }
}
