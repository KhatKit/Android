package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.app.core.data.model.Lorebook
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import heizige.kk.khatkit.app.core.data.model.QuickMessage
import heizige.kk.khatkit.app.core.ui.icons.link
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows
import ui.feice.mochi.theme.LocalContentColor

/*
 * 扩展选择列表（快捷消息 / 模式注入 / 世界书 / Skills）。
 *
 * 每一类是一张 Mochi 分组卡片（[mochiListSection]：每行一个懒加载项、仍是同一张卡片），
 * 行尾是 [MochiSwitch]，点整行也切换。下面的 `xxxSection` 是 [LazyListScope] 扩展，
 * 页面（角色的扩展管理页）直接放进自己的 LazyColumn；`XxxContent` 是自带 LazyColumn 的
 * 包装，聊天输入栏的扩展面板（ExtensionSelector）在用。
 */

/** 分组之间的间距；mochiListSection 的行要跨过这段间距连成一张卡片，需同值提供给 [LocalMochiListItemSpacing] */
private val ExtensionSectionSpacing = 16.dp

@Composable
private fun ExtensionToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { MochiSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

fun LazyListScope.modeInjectionsSection(
    modeInjections: List<PromptInjection.ModeInjection>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    key: Any = "mode_injections",
) {
    mochiListSection(key) {
        rows(modeInjections) { injection ->
            ExtensionToggleRow(
                title = injection.name.ifBlank { stringResource(R.string.extension_content_unnamed) },
                subtitle = null,
                checked = selectedIds.contains(injection.id),
                onCheckedChange = { checked -> onToggle(injection.id, checked) },
            )
        }
    }
}

fun LazyListScope.lorebooksSection(
    lorebooks: List<Lorebook>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    key: Any = "lorebooks",
) {
    mochiListSection(key) {
        rows(lorebooks) { lorebook ->
            ExtensionToggleRow(
                title = lorebook.name.ifBlank { stringResource(R.string.extension_content_unnamed_lorebook) },
                subtitle = lorebook.description.takeIf { it.isNotBlank() },
                checked = selectedIds.contains(lorebook.id),
                onCheckedChange = { checked -> onToggle(lorebook.id, checked) },
            )
        }
    }
}

fun LazyListScope.skillsSection(
    skills: List<SkillMetadata>,
    enabledSkills: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    key: Any = "skills",
) {
    mochiListSection(key) {
        rows(skills, key = { skill -> skill.skillDir.absolutePath }) { skill ->
            ExtensionToggleRow(
                title = skill.name,
                subtitle = skill.description.takeIf { it.isNotBlank() },
                checked = enabledSkills.contains(skill.name),
                onCheckedChange = { checked -> onToggle(skill.name, checked) },
            )
        }
    }
}

fun LazyListScope.quickMessagesSection(
    quickMessages: List<QuickMessage>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    key: Any = "quick_messages",
) {
    mochiListSection(key) {
        rows(quickMessages, key = { item -> item.id }) { quickMessage ->
            ExtensionToggleRow(
                title = quickMessage.title.ifBlank { stringResource(R.string.extension_content_unnamed) },
                // MochiListRow 的副标题最多两行（原先 maxLines = 2）
                subtitle = quickMessage.content.takeIf { it.isNotBlank() },
                checked = selectedIds.contains(quickMessage.id),
                onCheckedChange = { checked -> onToggle(quickMessage.id, checked) },
            )
        }
    }
}

/** 列表下方单独一组的跳转操作（「管理」「前往扩展管理」）：一行强调色文字按钮 */
fun LazyListScope.extensionActionSection(
    key: Any,
    text: String,
    onClick: () -> Unit,
) {
    item(key = key) {
        MochiListSection {
            MochiListButton(text = text, onClick = onClick)
        }
    }
}

@Composable
private fun ExtensionLazyColumn(
    modifier: Modifier,
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMochiListItemSpacing provides ExtensionSectionSpacing) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ExtensionSectionSpacing),
            content = content,
        )
    }
}

@Composable
fun ModeInjectionsContent(
    modeInjections: List<PromptInjection.ModeInjection>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    val manageText = stringResource(R.string.extension_content_manage)
    ExtensionLazyColumn(modifier) {
        modeInjectionsSection(modeInjections, selectedIds, onToggle)
        if (onManage != null) extensionActionSection("manage", manageText, onManage)
    }
}

@Composable
fun LorebooksContent(
    lorebooks: List<Lorebook>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    val manageText = stringResource(R.string.extension_content_manage)
    ExtensionLazyColumn(modifier) {
        lorebooksSection(lorebooks, selectedIds, onToggle)
        if (onManage != null) extensionActionSection("manage", manageText, onManage)
    }
}

@Composable
fun SkillsContent(
    skills: List<SkillMetadata>,
    enabledSkills: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    val manageText = stringResource(R.string.extension_content_manage)
    ExtensionLazyColumn(modifier) {
        skillsSection(skills, enabledSkills, onToggle)
        if (onManage != null) extensionActionSection("manage", manageText, onManage)
    }
}

@Composable
fun QuickMessagesContent(
    quickMessages: List<QuickMessage>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggle: (kotlin.uuid.Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // 原先就没有接这个回调（快捷消息列表下不显示「管理」），保留参数以兼容调用方
    @Suppress("UNUSED_PARAMETER") onManage: (() -> Unit)? = null,
) {
    ExtensionLazyColumn(modifier) {
        quickMessagesSection(quickMessages, selectedIds, onToggle)
    }
}

@Composable
fun ExtensionEmptyState(
    message: String,
    buttonText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MochiEmptyState(
        title = message,
        action = if (buttonText != null && onAction != null) {
            {
                MochiButton(onClick = onAction, style = MochiButtonStyle.Text) {
                    Icon(link, contentDescription = null, modifier = Modifier.size(18.dp), tint = LocalContentColor.current)
                    Text(buttonText)
                }
            }
        } else null,
    )
}
