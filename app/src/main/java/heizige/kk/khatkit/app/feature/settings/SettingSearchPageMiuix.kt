package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.search.SearchServiceOptions
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

/**
 * 搜索服务列表页的 Miuix 风格版。
 *
 * 服务卡片（SearchProviderCard）与拖拽排序（sh.calvin.reorderable）本身与风格无关 ——
 * 卡片底色走已双风格化的 CustomColors.listItemColors，拖拽库独立于 UI 框架 ——
 * 这里只换外壳与顶栏操作按钮。
 */
@Composable
fun SettingSearchPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    val haptic = LocalHapticFeedback.current
    var showAddDialog by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex >= 0 && toIndex >= 0 &&
            fromIndex < settings.searchServices.size &&
            toIndex < settings.searchServices.size
        ) {
            val newServices = settings.searchServices.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            vm.updateSettings(settings.copy(searchServices = newServices))
        }
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_search_title),
        navigationIcon = { BackButton() },
        actions = {
            KedgeIconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = add,
                    contentDescription = stringResource(R.string.setting_page_search_add_provider),
                )
            }
        },
        bottomInnerPadding = 16.dp,
    ) {
        items(settings.searchServices, key = { it.id }) { service ->
            ReorderableItem(
                state = reorderableState,
                key = service.id,
            ) { isDragging ->
                SearchProviderCard(
                    service = service,
                    onEdit = { nav.navigate(Screen.SettingSearchDetail(service.id.toString())) },
                    onDelete = {
                        if (settings.searchServices.size > 1) {
                            val index = settings.searchServices.indexOf(service)
                            val newServices = settings.searchServices.toMutableList()
                            newServices.removeAt(index)
                            vm.updateSettings(settings.copy(searchServices = newServices))
                        }
                    },
                    canDelete = settings.searchServices.size > 1,
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .animateItem()
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        ),
                )
            }
        }

        item {
            CommonOptions(
                settings = settings,
                onUpdate = { options ->
                    vm.updateSettings(settings.copy(searchCommonOptions = options))
                },
            )
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { options ->
                showAddDialog = false
                vm.updateSettings(
                    settings.copy(searchServices = listOf(options) + settings.searchServices)
                )
                scope.launch { lazyListState.animateScrollToItem(0) }
            },
        )
    }
}
