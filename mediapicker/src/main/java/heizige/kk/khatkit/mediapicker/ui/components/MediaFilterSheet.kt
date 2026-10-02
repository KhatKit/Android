/*
 * 排序/分组面板（对应 ImageToolbox `MediaSearchAndFilter`，Apache-2.0, T8RIN）。
 *
 * 两页：分组维度（按时间/类型 + 年月日粒度 + 分组顺序）与排序维度 + 升降序。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.MediaDateGroup
import heizige.kk.khatkit.mediapicker.domain.MediaDisplaySettings
import heizige.kk.khatkit.mediapicker.domain.MediaGrouping
import heizige.kk.khatkit.mediapicker.domain.MediaOrder
import heizige.kk.khatkit.mediapicker.domain.OrderType
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
internal fun MediaFilterSheet(
    settings: MediaDisplaySettings,
    onSettingsChange: (MediaDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterTab(
                    text = stringResource(R.string.media_picker_gallery_grouping),
                    icon = Icons.Rounded.ViewComfy,
                    selected = page == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { page = 0 },
                )
                FilterTab(
                    text = stringResource(R.string.media_picker_sorting),
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    selected = page == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { page = 1 },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (page == 0) {
                    MediaGroupingOptions(settings, onSettingsChange)
                } else {
                    MediaSortingOptions(settings, onSettingsChange)
                }
            }

            KedgeButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.media_picker_close))
            }
        }
    }
}

@Composable
private fun FilterTab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = content, style = KedgeTextStyles.body())
    }
}

@Composable
private fun MediaGroupingOptions(
    settings: MediaDisplaySettings,
    onChange: (MediaDisplaySettings) -> Unit,
) {
    val allLabel = stringResource(R.string.media_picker_all)
    val allSelected = settings.dateGroup == MediaDateGroup.None
    OptionRow(
        title = allLabel,
        selected = allSelected,
        shape = groupedShape(index = 0, size = MediaGrouping.entries.size + 1),
        onClick = { onChange(settings.copy(dateGroup = MediaDateGroup.None)) },
    )
    MediaGrouping.entries.forEachIndexed { index, grouping ->
        val selected = settings.dateGroup != MediaDateGroup.None && settings.grouping == grouping
        OptionRow(
            title = stringResource(grouping.labelRes()),
            selected = selected,
            shape = groupedShape(index = index + 1, size = MediaGrouping.entries.size + 1),
            onClick = {
                onChange(
                    settings.copy(
                        grouping = grouping,
                        dateGroup = settings.dateGroup.takeUnless { it == MediaDateGroup.None }
                            ?: MediaDateGroup.Day,
                    )
                )
            },
            bottomContent = if (grouping == MediaGrouping.DateModified ||
                grouping == MediaGrouping.DateTaken
            ) {
                {
                    AnimatedVisibility(visible = selected, modifier = Modifier.fillMaxWidth()) {
                        DateGroupSegmented(
                            selected = settings.dateGroup,
                            onSelect = { onChange(settings.copy(dateGroup = it)) },
                        )
                    }
                }
            } else {
                null
            },
        )
    }
    OptionOrderRow(
        title = stringResource(R.string.media_picker_gallery_order),
        order = settings.groupOrder,
        onChange = { onChange(settings.copy(groupOrder = it)) },
    )
}

@Composable
private fun MediaSortingOptions(
    settings: MediaDisplaySettings,
    onChange: (MediaDisplaySettings) -> Unit,
) {
    val order = settings.mediaOrder.orderType
    val items = remember(order) {
        listOf(
            R.string.media_picker_filename to MediaOrder.Label(order),
            R.string.media_picker_path to MediaOrder.Path(order),
            R.string.media_picker_sort_by_size to MediaOrder.Size(order),
            R.string.media_picker_sort_by_date_modified to MediaOrder.Date(order),
            R.string.media_picker_caption_date_taken to MediaOrder.DateTaken(order),
            R.string.media_picker_shuffle to MediaOrder.Random(order),
        )
    }
    items.forEachIndexed { index, (titleRes, item) ->
        val selected = settings.mediaOrder::class == item::class
        OptionRow(
            title = stringResource(titleRes),
            selected = selected,
            shape = groupedShape(index = index, size = items.size),
            onClick = {
                onChange(
                    settings.copy(
                        mediaOrder = if (item is MediaOrder.Random) item.copy(order) else item,
                    )
                )
            },
        )
    }
    if (settings.mediaOrder !is MediaOrder.Random) {
        OptionOrderRow(
            title = stringResource(R.string.media_picker_gallery_order),
            order = settings.mediaOrder.orderType,
            onChange = { onChange(settings.copy(mediaOrder = settings.mediaOrder.copy(it))) },
        )
    }
}

@Composable
private fun OptionOrderRow(
    title: String,
    order: OrderType,
    onChange: (OrderType) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(text = title, style = KedgeTextStyles.title())
        Spacer(Modifier.height(4.dp))
        listOf(OrderType.Ascending, OrderType.Descending).forEachIndexed { index, type ->
            OptionRow(
                title = stringResource(
                    if (type == OrderType.Ascending) R.string.media_picker_ascending
                    else R.string.media_picker_descending
                ),
                selected = type == order,
                shape = groupedShape(index = index, size = 2),
                onClick = { onChange(type) },
            )
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = KedgeTextStyles.body())
            Icon(
                imageVector = if (selected) Icons.Rounded.RadioButtonChecked
                else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bottomContent?.invoke()
    }
}

@Composable
private fun DateGroupSegmented(
    selected: MediaDateGroup,
    onSelect: (MediaDateGroup) -> Unit,
) {
    val options = listOf(
        MediaDateGroup.Year to R.string.media_picker_years,
        MediaDateGroup.Month to R.string.media_picker_months,
        MediaDateGroup.Day to R.string.media_picker_days,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, (group, labelRes) ->
            val isSelected = group == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(groupedShape(index = index, size = options.size, vertical = false, roundedCorner = 10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.tertiaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(group) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = KedgeTextStyles.footnote(),
                    color = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun MediaGrouping.labelRes(): Int = when (this) {
    MediaGrouping.DateModified -> R.string.media_picker_sort_by_date_modified
    MediaGrouping.DateTaken -> R.string.media_picker_caption_date_taken
    MediaGrouping.MimeType -> R.string.media_picker_sort_by_mime_type
    MediaGrouping.Extension -> R.string.media_picker_sort_by_extension
}
