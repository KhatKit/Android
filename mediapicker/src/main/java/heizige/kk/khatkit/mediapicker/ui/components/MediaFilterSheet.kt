/*
 * 排序/分组面板（对应 ImageToolbox `MediaSearchAndFilter`，Apache-2.0, T8RIN）。
 *
 * 两页：分组维度（按时间/类型 + 年月日粒度 + 分组顺序）与排序维度 + 升降序。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.MediaDateGroup
import heizige.kk.khatkit.mediapicker.domain.MediaDisplaySettings
import heizige.kk.khatkit.mediapicker.domain.MediaGrouping
import heizige.kk.khatkit.mediapicker.domain.MediaOrder
import heizige.kk.khatkit.mediapicker.domain.OrderType
import kotlinx.collections.immutable.toPersistentList
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.theme.MochiDefaults

@Composable
internal fun MediaFilterSheet(
    settings: MediaDisplaySettings,
    onSettingsChange: (MediaDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    val sheet = rememberMochiSheetState()
    LaunchedEffect(sheet) { sheet.show() }
    MochiSheet(state = sheet, onDismiss = onDismiss, hideNavigationBar = false) {
        MochiSheetHeader(
            title = stringResource(R.string.media_picker_sorting),
            navigationIcon = {
                MochiBarButton(stringResource(R.string.media_picker_close), onClick = sheet::hide)
            },
        )
        MochiSheetContent {
            MochiSegmentedControl(
                selectedIndex = page,
                onSelect = { page = it },
                items = listOf(
                    stringResource(R.string.media_picker_gallery_grouping),
                    stringResource(R.string.media_picker_sorting),
                ).toPersistentList(),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (page == 0) MediaGroupingOptions(settings, onSettingsChange)
                else MediaSortingOptions(settings, onSettingsChange)
            }
        }
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
        Text(text = title, style = MaterialTheme.typography.titleMedium)
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
        MochiListRow(
            title = title,
            checked = selected,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().clip(shape).background(MochiDefaults.groupedContainer()),
        )
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
    MochiSegmentedControl(
        selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { onSelect(options[it].first) },
        items = options.map { stringResource(it.second) }.toPersistentList(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

private fun MediaGrouping.labelRes(): Int = when (this) {
    MediaGrouping.DateModified -> R.string.media_picker_sort_by_date_modified
    MediaGrouping.DateTaken -> R.string.media_picker_caption_date_taken
    MediaGrouping.MimeType -> R.string.media_picker_sort_by_mime_type
    MediaGrouping.Extension -> R.string.media_picker_sort_by_extension
}
