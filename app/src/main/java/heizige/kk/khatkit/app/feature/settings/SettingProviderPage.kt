package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup
import heizige.kk.khatkit.uikit.mochi.Md3SearchBar
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.MochiPageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.RECOMMENDED_PROVIDERS
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheet
import heizige.kk.khatkit.app.core.ui.components.ui.decodeProviderSetting
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.hooks.EditStateContent
import heizige.kk.khatkit.app.feature.settings.components.ProviderConfigure
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.ImageHelper
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults

/** 列表首项是搜索框，重排位号要跳过它。 */
private const val SearchHeaderItems = 1

@Composable
fun SettingProviderPage(vm: SettingViewModel = hiltViewModel()) {
    

    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 只有在没按搜索词过滤时才允许改顺序：过滤态下的 index 是过滤后列表的，
        // 拿它去索引 settings.providers 会把顺序搅乱。
        // 列表首项是搜索框（不参与拖拽），所以位号要先减掉这一项。
        if (searchQuery.isBlank() && settings.providers.isNotEmpty()) {
            val fromIndex = (from.index - SearchHeaderItems).coerceIn(0, settings.providers.lastIndex)
            val toIndex = (to.index - SearchHeaderItems).coerceIn(0, settings.providers.lastIndex)
            val newProviders = settings.providers.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            vm.updateSettings(settings.copy(providers = newProviders))
        }
    }

    val filteredProviders = remember(settings.providers, searchQuery) {
        if (searchQuery.isBlank()) {
            settings.providers
        } else {
            settings.providers.filter { provider ->
                provider.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_provider_page_title),
        scrollBehavior = scrollBehavior,
        // 大标题常显：往上滚收起后顶栏才出现小标题、栏底色同时淡入（Mochi 示例子页那套）
        actions = {
            AddButton {
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(it) + settings.providers
                    )
                )
            }
        },
    ) { innerPadding ->
        // 搜索框是滚动正文的首项：静止时停在栏下，往上滚时从栏后穿过去（栏下才有可压的渐隐）
        ProviderList(
            providers = filteredProviders,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            listState = lazyListState,
            reorderableState = reorderableState,
            dragEnabled = !searchExpanded && searchQuery.isBlank(),
            topPadding = innerPadding.calculateTopPadding(),
            bottomPadding = innerPadding.calculateBottomPadding() + PageMetrics.BottomContentPadding,
            header = {
                // KSU 式药丸搜索框（KernelSU SuperSearchBar 同一套逻辑）：点一下折叠药丸，
                // 展开后结果就在下面这份列表里，不再另外搬一份全屏列表。
                Md3SearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.setting_provider_page_search_providers),
                    active = searchExpanded,
                    onActiveChange = { searchExpanded = it },
                    cancelLabel = stringResource(R.string.cancel),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            onClick = { provider ->
                navController.navigate(Screen.SettingProviderDetail(provider.id.toString()))
            },
        )
    }
}

/**
 * 供应商列表。搜索框是列表首项（[header]），整页只有一个滚动容器：
 * 搜索框和卡片一起从顶栏后面滚过去，顶栏据此淡入底色和栏下渐隐。
 */
@Composable
private fun ProviderList(
    providers: List<ProviderSetting>,
    onClick: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    dragEnabled: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    header: @Composable () -> Unit,
) {
    // 与助手列表同款分组：整列看着是一张卡片，圆角按 index/count 逐项算
    // （首项只圆上边、末项只圆下边、中间项直角），项间距取全局列表卡片样式。
    // 不能换成 CardGroup —— 那会把所有项收进一个 Column，ReorderableItem
    // 需要的「每项一个 lazy item」就没了，长按拖拽排序会失效。
    val cards = listCardStyle()
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier,
        // 顶栏高度当 contentPadding：列表内容从栏后穿过去，栏下才有可渐隐 / 模糊的内容
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 12.dp,
            bottom = 8.dp,
        ) + PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
        state = listState,
    ) {
        // 搜索框和下面那张卡片之间留出其它设置子页的段距（28dp），
        // 原来这里是 0 段距，搜索框直接贴着第一行卡片
        item(key = "provider-search") {
            Box(
                Modifier.padding(
                    bottom = (MochiListDefaults.SectionSpacing - cards.gap).coerceAtLeast(0.dp)
                )
            ) { header() }
        }
        lazyItemsIndexed(providers, key = { _, provider -> provider.id }) { index, provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                KkGroupedColumn(
                    modifier = Modifier.scale(if (isDragging) 0.95f else 1f),
                    shape = cards.indexedShape(index, providers.size),
                    containerColor = MochiListDefaults.colors().container,
                    topDivider = index > 0 && cards.gap == 0.dp,
                ) {
                    ProviderItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 和助手列表一致：长按**整行**就能拖，不需要先精准按住右侧拖柄。
                            // 过滤态（搜索中）不开拖拽 —— 那时列表是子集，重排会写坏顺序。
                            .then(
                                if (dragEnabled) {
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        }
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        provider = provider,
                        shape = cards.indexedShape(index, providers.size),
                        onClick = { onClick(provider) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it.copyProvider(name = it.name.trim()))
    }

    MochiIconButton(
        onClick = {
            dialogState.open(ProviderSetting.OpenAI())
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(add, "Add")
    }

    PrimaryBottomSheet(
        visible = dialogState.isEditing,
        title = stringResource(R.string.setting_provider_page_add_provider),
        imageVector = add,
        confirmText = stringResource(R.string.setting_provider_page_add),
        onConfirm = dialogState::confirm,
        dismissText = stringResource(R.string.cancel),
        onDismiss = dialogState::dismiss,
        scrollable = false,
    ) {
        dialogState.EditStateContent { current, update ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ProviderConfigure(current, onEdit = update)
            }
        }
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderSetting,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    MochiOptionItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        // 停用的供应商保留红底 —— 这是整行唯一需要一眼看出来的状态；启用的走组件
        // 默认底色，不再自己上 listItemColors（与助手列表同一套口径）。
        backgroundColor = if (provider.enabled) {
            Color.Unspecified
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        leadingContent = {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
        },
        titleContent = {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.shortDescription()
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = if (provider.enabled) TagType.SUCCESS else TagType.WARNING) {
                        Text(stringResource(if (provider.enabled) R.string.setting_provider_page_enabled else R.string.setting_provider_page_disabled))
                    }
                    Tag(type = TagType.INFO) {
                        Text(
                            stringResource(
                                R.string.setting_provider_page_model_count,
                                provider.models.size
                            )
                        )
                    }
                }
            }
        },
        // 没有 trailingContent：拖拽手势挂在整行上（见 ProviderList），
        // 再摆一个拖柄图标只会让人以为「必须按住那里才能拖」。
        // 点行进供应商详情页（二级页），行尾只放统一的 ›
        chevron = true,
    )
}
