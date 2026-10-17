package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.hooks.rememberAssistantState
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.kedge.components.KedgeSurface

@Composable
fun AssistantPicker(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    modifier: Modifier = Modifier,
    onClickSetting: () -> Unit,
) {
    val state = rememberAssistantState(settings, onUpdateSettings)
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    var showPicker by remember { mutableStateOf(false) }

    KedgeSurface(
        onClick = {
            showPicker = true
        },
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Spacer(Modifier.width(4.dp))
            Icon(search, contentDescription = null)

            Spacer(Modifier.width(12.dp))

            Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.currentAssistant.name.ifEmpty { defaultAssistantName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.weight(1f))

                    UIAvatar(
                        name = state.currentAssistant.name.ifEmpty { defaultAssistantName },
                        value = state.currentAssistant.avatar,
                        avatarShape = state.currentAssistant.avatarShape,
                        avatarColor = state.currentAssistant.avatarColor,
                        defaultIconName = settings.findModelProviderName(
                            state.currentAssistant.chatModelId ?: settings.chatModelId
                        ),
                        onClick = onClickSetting
                    )
                }
            }
        }
    }

    if (showPicker) {
        AssistantPickerSheet(
            settings = settings,
            currentAssistant = state.currentAssistant,
            onAssistantSelected = { assistant ->
                showPicker = false
                state.setSelectAssistant(assistant)
            },
            onDismiss = {
                showPicker = false
            }
        )
    }
}

@Composable
fun AssistantPickerSheet(
    settings: Settings,
    currentAssistant: Assistant,
    onAssistantSelected: (Assistant) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)

    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }

    // 根据选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) {
            settings.assistants
        } else {
            settings.assistants.filter { assistant ->
                assistant.tags.any { tagId -> tagId in selectedTagIds }
            }
        }
    }

    val navController = LocalNavController.current

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.assistant_page_title),
        imageVector = groups,
        confirmText = stringResource(R.string.assistant_page_manage),
        onConfirm = {
            onDismiss()
            navController.navigate(Screen.Assistant)
        },
        onDismiss = onDismiss,
        scrollable = false,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标签过滤器
            if (settings.assistantTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(settings.assistantTags, key = { tag -> tag.id }) { tag ->
                        KedgeFilterChip(
                            onClick = {
                                selectedTagIds = if (tag.id in selectedTagIds) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = { Text(tag.name) },
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 助手列表：成组 option item（与设置页 / 抽屉角色列表 / 角色面板同一套
            // listCardStyle 圆角与行间空隙），不再每行单独一张 surface 卡片。
            val cards = listCardStyle()
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(filteredAssistants, key = { _, assistant -> assistant.id }) { index, assistant ->
                    val checked = assistant.id == currentAssistant.id
                    // 行本身与助手页共用 AssistantItem（core 里的唯一实现）；
                    // 选中态只用行尾一个勾，底色/字色保持 option item 默认值。
                    AssistantItem(
                        assistant = assistant,
                        defaultAssistantName = defaultAssistantName,
                        modifier = Modifier
                            .animateItem()
                            .padding(bottom = if (index == filteredAssistants.lastIndex) 0.dp else cards.gap),
                        shape = cards.indexedShape(index, filteredAssistants.size),
                        avatarSize = 32.dp,
                        onClick = {
                            onAssistantSelected(assistant)
                            dismiss()
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    listOfNotNull(
                                        if (assistant.canManageRoles) "主管" else null,
                                        settings.findModelById(assistant.chatModelId ?: settings.chatModelId)?.displayName,
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        providerName = settings.findModelProviderName(
                            assistant.chatModelId ?: settings.chatModelId
                        ),
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (checked) {
                                    Icon(
                                        imageVector = check,
                                        contentDescription = "当前角色",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 4.dp).size(18.dp),
                                    )
                                }
                                KedgeIconButton(
                                    onClick = {
                                        onDismiss()
                                        navController.navigate(Screen.AssistantDetail(assistant.id.toString()))
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        imageVector = editNote,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
