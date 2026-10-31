package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.app.core.ui.components.ui.KkRichInputChip
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.collections.immutable.persistentListOf
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Tag
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.close
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

@Composable
fun TagsInput(
    value: List<Uuid>,
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    onValueChange: (value: List<Uuid>, tags: List<Tag>) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // 输入态提到外层：MochiDialog 用 visible 驱动，关闭时才有退场动画
    var tagName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    // MochiDialog 点按钮后一定会调 onDismissRequest；重名校验失败时要留在原地继续改，
    // 所以这里置位，让紧接着的那次 onDismissRequest 只清标志、不关对话框
    var keepOpenAfterValidation by remember { mutableStateOf(false) }

    fun resetAndClose() {
        showAddDialog = false
        tagName = ""
        showError = false
        keepOpenAfterValidation = false
    }

    // 根据value获取对应的tags
    val selectedTags = tags.filter { tag -> value.contains(tag.id) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        // 显示已选择的tags
        selectedTags.fastForEach { tag ->
            KkRichInputChip(selected = false, onClick = {}, label = { androidx.compose.material3.Text(tag.name) }, trailingIcon = {
                Icon(
                    imageVector = close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            onValueChange(
                                value.filter { it != tag.id }, tags
                            )
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            })
        }

        // 添加按钮
        KkSurface(
            shape = CircleShape,
            tonalElevation = 2.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { showAddDialog = true }) {
            Icon(
                imageVector = add,
                contentDescription = stringResource(R.string.add),
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // 添加tag对话框（DG1：MochiDialog；输入框/校验逻辑与调用点签名不变）
    val unselectedTags = tags.filter { tag -> !value.contains(tag.id) }

    MochiDialog(
        visible = showAddDialog,
        onDismissRequest = {
            if (keepOpenAfterValidation) {
                keepOpenAfterValidation = false
            } else {
                resetAndClose()
            }
        },
        title = stringResource(R.string.tag_input_dialog_title),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.cancel),
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = stringResource(R.string.confirm),
                role = MochiDialogActionRole.Primary,
                enabled = tagName.isNotBlank(),
                onClick = {
                    val trimmedName = tagName.trim()
                    // 检查是否已存在同名标签
                    val existingTag = tags.find { it.name.equals(trimmedName, ignoreCase = true) }
                    if (existingTag != null) {
                        // 同名：显示错误并留在对话框里（吞掉紧随其后的 onDismissRequest）
                        showError = true
                        keepOpenAfterValidation = true
                    } else {
                        keepOpenAfterValidation = false
                        // 新标签的 id 既进 tags 也进 value，必须同一个
                        val newTag = Tag(id = Uuid.random(), name = trimmedName)
                        onValueChange(value + newTag.id, tags + newTag)
                    }
                },
            ),
        ),
        dismissOnOutsideTap = true,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 显示现有标签列表（如果有未选择的标签）
            if (unselectedTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tag_input_dialog_existing_tags),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    unselectedTags.forEach { tag ->
                        KkInputChip(
                            selected = false,
                            onClick = {
                                onValueChange(value + tag.id, tags)
                                resetAndClose()
                            }, label = tag.name
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.tag_input_dialog_create_new),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 输入新标签名称
            Md3OutlinedTextFieldWithSlots(
                value = tagName,
                onValueChange = {
                    tagName = it
                    showError = false
                    keepOpenAfterValidation = false
                },
                label = { Text(stringResource(R.string.tag_input_dialog_label)) },
                placeholder = { Text(stringResource(R.string.tag_input_dialog_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showError,
                shape = RoundedCornerShape(16.dp)
            )

            // 显示错误信息
            if (showError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tag_input_dialog_tag_exists),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
