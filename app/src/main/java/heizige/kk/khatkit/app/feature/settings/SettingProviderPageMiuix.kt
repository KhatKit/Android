package heizige.kk.khatkit.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.RECOMMENDED_PROVIDERS
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.decodeProviderSetting
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixPageMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixItemSpacing
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.dragIndicator
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.ImageUtils
import heizige.kk.khatkit.app.feature.settings.components.ProviderConfigure
import heizige.kk.khromia.helper.Toast
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeSearchBar
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import kotlin.uuid.Uuid
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * AI 提供商列表页的 Miuix 风格版。
 *
 * ## 复用边界（为什么不直接调 MD3 文件里的东西）
 *
 * `SettingProviderPage.kt` 里的 `ProviderList` / `RecommendProviderButton` / `ImportProviderButton` /
 * `AddButton` / `ProviderItem` 全是 `private`，而本任务的 diff 预算只允许给那个文件加
 * 「import + 函数开头早退块」，不能顺手把可见性放宽 —— 所以**行与三个弹窗的动作层**在这里重建，
 * 但下面这些全部按原样复用，没有第二份实现：
 *
 * - `KedgeSearchBar`（自带 MD3/Miuix 分流）、`KedgeCard`、`KedgeIconButton`、`KedgeButton`、
 *   `KedgeTextButton`、`AppAlertDialog`（Miuix 下走 `KedgeAlertDialogSlots`）、
 *   `PrimaryBottomSheet`（Miuix 下走 `KedgePrimaryBottomSheet`）；
 * - `ProviderConfigure`（表单本身已双风格化：`KedgeSingleChoiceSegmentedRow` +
 *   `KedgeOutlinedTextField` + `KedgeSwitchFormRow`），新增/编辑提供商**不重写表单**；
 * - `AutoAIIcon`、`Screen.SettingProviderDetail`、`Toast`、`ImageUtils.decodeQRCodeFromUri`、
 *   `decodeProviderSetting`、`RECOMMENDED_PROVIDERS`、`useEditState`。
 *
 * ## 外壳与列表
 *
 * 外壳用 `MiuixSettingsPage`；每一行是 `KedgeCard(contentPadding = 0)` + miuix 的
 * `ArrowPreference`（`PreferenceArrow` 的底层），因为只有它有 `bottomAction` 槽可以放
 * `provider.shortDescription()`（MD3 行里也有这段描述），而 KhatKit 的 `PreferenceArrow`
 * 包装没有暴露该槽，且 `PreferenceItems.kt` 在 settings-parity-gaps.md §21 的禁改清单里。
 */
@Composable
fun SettingProviderPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    // 两个列表（页面里的折叠态列表 / 搜索框的全屏结果槽）各自持有一份 LazyListState：
    // 复用同一个 state 会让两个 LazyColumn 共享滚动下标，而页面列表里第 0 项是搜索框、
    // 结果槽里第 0 项是第一个提供商，下标含义不同。MD3 侧靠「展开时把页面列表整个抽掉」
    // 规避了这点，这里直接给两份 state，互不干扰。
    val pageListState = rememberLazyListState()
    val pageReorderableState = rememberReorderableLazyListState(pageListState) { from, to ->
        moveProvider(settings, vm, fromProviderId = from.key, toProviderId = to.key)
    }
    val resultListState = rememberLazyListState()
    val resultReorderableState = rememberReorderableLazyListState(resultListState) { from, to ->
        moveProvider(settings, vm, fromProviderId = from.key, toProviderId = to.key)
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

    val openDetail: (ProviderSetting) -> Unit = { provider ->
        navController.navigate(Screen.SettingProviderDetail(provider.id.toString()))
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_provider_page_title),
        navigationIcon = { BackButton() },
        actions = {
            RecommendProviderButtonMiuix { provider ->
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(provider.copyProvider(Uuid.random())) + settings.providers
                    )
                )
            }
            ImportProviderButtonMiuix { provider ->
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(provider.copyProvider(Uuid.random())) + settings.providers
                    )
                )
            }
            AddProviderButtonMiuix { provider ->
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(provider) + settings.providers
                    )
                )
            }
        },
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        lazyListState = pageListState,
    ) {
        item(key = "search") {
            // KSU 式药丸搜索框：点一下折叠药丸展开成整屏结果层，取消键/返回键收起并清空关键词。
            // Miuix 下折叠药丸自带 0 留白，间距在这里补（对齐 KSU 每个分组 Card 的 12dp）。
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.setting_provider_page_search_providers),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixPageMetrics.GroupSpacing),
                // 不传 showResultsWhenEmpty（默认 false）：点开搜索先空白，输入才出列表。
                expandedContent = {
                    // 结果层挂在窗口级浮层里（KedgeSearchOverlayLayer），不是本页子树，
                    // 所以不能带 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩。
                    NoHeroTransition {
                        ProviderListMiuix(
                            providers = filteredProviders,
                            onClick = openDetail,
                            modifier = Modifier.fillMaxSize(),
                            listState = resultListState,
                            reorderableState = resultReorderableState,
                            // 结果槽不在 MiuixSettingsPage 的 LazyColumn 里，12dp 水平边距要自己补；
                            // PaddingValues 没有 (horizontal, bottom) 这个重载，只能 start/end/bottom。
                            contentPadding = PaddingValues(
                                start = MiuixPageMetrics.HorizontalPadding,
                                end = MiuixPageMetrics.HorizontalPadding,
                                bottom = PageMetrics.BottomContentPadding,
                            ),
                        )
                    }
                },
            )
        }

        // 展开期间页面正文整个抽掉（KSU 的 SearchBox 同款）：列表只在结果槽里出现一次。
        if (!searchExpanded) {
            items(filteredProviders, key = { it.id }) { provider ->
                ReorderableItem(
                    state = pageReorderableState,
                    key = provider.id,
                ) { isDragging ->
                    ProviderRowMiuix(
                        provider = provider,
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .animateItem(),
                        dragHandle = { ProviderDragHandle() },
                        onClick = { openDetail(provider) },
                    )
                }
            }
        }
    }
}

/**
 * 按**条目 key**（提供商 id）换算下标后移动，而不是 `from.index - 1` 这类下标算术：
 * 页面列表第 0 项是搜索框，结果槽里又只有提供商，两处下标空间不同；key 换算对两种情况都成立。
 * 拖到搜索框位置上时找不到对应提供商，直接不动（该位置本来就不是条目）。
 */
private fun moveProvider(
    settings: heizige.kk.khatkit.app.core.data.datastore.Settings,
    vm: SettingViewModel,
    fromProviderId: Any,
    toProviderId: Any,
) {
    val fromIndex = settings.providers.indexOfFirst { it.id == fromProviderId }
    val toIndex = settings.providers.indexOfFirst { it.id == toProviderId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
    val newProviders = settings.providers.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
    vm.updateSettings(settings.copy(providers = newProviders))
}

/**
 * 提供商列表。折叠态挂在页面正文里，展开态搬进搜索框的全屏结果槽，两处共用一份实现，
 * 只有 [modifier] 与 [contentPadding] 不同（页面里跟着外层 `MiuixSettingsPage` 的 LazyColumn
 * 走，结果槽里是 `fillMaxSize` 且自己带 12dp 水平边距）。
 */
@Composable
private fun ProviderListMiuix(
    providers: List<ProviderSetting>,
    onClick: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        state = listState,
    ) {
        items(providers, key = { it.id }) { provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id,
            ) { isDragging ->
                ProviderRowMiuix(
                    provider = provider,
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .animateItem(),
                    dragHandle = { ProviderDragHandle() },
                    onClick = { onClick(provider) },
                )
            }
        }
    }
}

/**
 * 单个提供商行。
 *
 * 用 miuix 的 `ArrowPreference` 而不是 KhatKit 的 `PreferenceArrow`：后者没有 `bottomAction`
 * 槽，放不下 `provider.shortDescription()`（MD3 行里保留了这段描述），而 `PreferenceItems.kt`
 * 属于 §21 的禁改共享组件，不能为它加参数。外层 `KedgeCard(contentPadding = 0)` 提供分组卡片，
 * 与 `miuixGroup` 的构造完全一致。
 */
@Composable
private fun ProviderRowMiuix(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val statusText = stringResource(
        if (provider.enabled) R.string.setting_provider_page_enabled
        else R.string.setting_provider_page_disabled
    )
    val modelCountText = stringResource(
        R.string.setting_provider_page_model_count,
        provider.models.size
    )
    // MD3 行里这两条是两个 Tag（启用状态 / 模型数量），Miuix 侧收进 summary 一行。
    val summaryText = "$statusText · $modelCountText"

    KedgeCard(
        modifier = modifier
            .fillMaxWidth()
            .miuixItemSpacing(),
        contentPadding = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = provider.name,
            summary = summaryText,
            onClick = onClick,
            endActions = { dragHandle() },
            startAction = {
                AutoAIIcon(
                    name = provider.name,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 6.dp),
                )
            },
            bottomAction = { provider.shortDescription() },
        )
    }
}

/**
 * 拖拽把手：长按进入拖拽，与 MD3 行的把手行为一致（轻点整行是进入详情）。
 *
 * `longPressDraggableHandle` 是 `ReorderableCollectionItemScope` 的成员（不是顶层扩展），
 * 所以这里必须写成该 scope 的扩展；调用点在 `ReorderableItem { }` 里，隐式接收者正好是它。
 */
@Composable
private fun ReorderableCollectionItemScope.ProviderDragHandle() {
    val haptic = LocalHapticFeedback.current
    KedgeIconButton(
        onClick = {},
        modifier = Modifier.longPressDraggableHandle(
            onDragStarted = {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            },
            onDragStopped = {
                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
            },
        ),
    ) {
        Icon(imageVector = dragIndicator, contentDescription = null)
    }
}

/** 顶栏「推荐提供商」：Miuix 底部弹窗 + 每个提供商一行（点行或点加号都能加）。 */
@Composable
private fun RecommendProviderButtonMiuix(
    onAdd: (ProviderSetting) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val importSuccessMessage = stringResource(R.string.setting_provider_page_import_success)

    KedgeIconButton(onClick = { showSheet = true }) {
        Icon(imageVector = autoAwesome, contentDescription = stringResource(R.string.setting_provider_page_recommend))
    }

    if (showSheet) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.setting_provider_page_recommend),
            imageVector = dns,
            onDismiss = { showSheet = false },
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MiuixPageMetrics.HorizontalPadding)
                    .padding(bottom = 16.dp),
            ) {
                RECOMMENDED_PROVIDERS.forEach { provider ->
                    KedgeCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .miuixItemSpacing(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        ArrowPreference(
                            title = provider.name,
                            onClick = {
                                onAdd(provider)
                                Toast.show(importSuccessMessage, isError = false)
                            },
                            startAction = {
                                AutoAIIcon(
                                    name = provider.name,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .padding(end = 6.dp),
                                )
                            },
                            endActions = {
                                KedgeIconButton(onClick = {
                                    onAdd(provider)
                                    Toast.show(importSuccessMessage, isError = false)
                                }) {
                                    Icon(
                                        imageVector = add,
                                        contentDescription = stringResource(R.string.setting_provider_page_add),
                                    )
                                }
                            },
                            bottomAction = { provider.description() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶栏「导入提供商」：从相册选一张带二维码的图，解码后加入列表。
 *
 * 解码逻辑与 MD3 侧同源（`ImageUtils.decodeQRCodeFromUri` + `decodeProviderSetting` + `Toast`），
 * 只是 MD3 侧那个 `handleImageQRCode` 是 private 且不能放宽可见性，所以这里再写一遍薄壳。
 */
@Composable
private fun ImportProviderButtonMiuix(
    onAdd: (ProviderSetting) -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { importProviderFromImageMiuix(it, context, onAdd) }
    }

    KedgeIconButton(onClick = { showImportDialog = true }) {
        Icon(imageVector = uploadFile, contentDescription = null)
    }

    if (showImportDialog) {
        AppAlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_import_dialog_title),
                    style = KedgeTextStyles.title()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_import_dialog_message),
                        style = KedgeTextStyles.body(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    KedgeButton(
                        onClick = {
                            showImportDialog = false
                            pickImageLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.setting_provider_page_select_from_gallery),
                                style = KedgeTextStyles.footnoteSmall()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                KedgeTextButton(onClick = { showImportDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = KedgeTextStyles.footnote()
                    )
                }
            }
        )
    }
}

private fun importProviderFromImageMiuix(
    uri: Uri,
    context: android.content.Context,
    onAdd: (ProviderSetting) -> Unit,
) {
    runCatching {
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

        if (qrContent.isNullOrEmpty()) {
            Toast.show(
                context.getString(R.string.setting_provider_page_no_qr_found),
                isError = true
            )
            return
        }

        val setting = decodeProviderSetting(qrContent)
        onAdd(setting)
        Toast.show(
            context.getString(R.string.setting_provider_page_import_success),
            isError = false
        )
    }.onFailure { error ->
        Toast.show(
            context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            isError = true
        )
    }
}

/** 顶栏「新增提供商」：弹窗里直接放双风格化的 [ProviderConfigure] 表单。 */
@Composable
private fun AddProviderButtonMiuix(
    onAdd: (ProviderSetting) -> Unit
) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it.copyProvider(name = it.name.trim()))
    }

    KedgeIconButton(onClick = { dialogState.open(ProviderSetting.OpenAI()) }) {
        Icon(imageVector = add, contentDescription = stringResource(R.string.setting_provider_page_add))
    }

    if (dialogState.isEditing) {
        AppAlertDialog(
            onDismissRequest = { dialogState.dismiss() },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                dialogState.currentState?.let { current ->
                    ProviderConfigure(current) { newState ->
                        dialogState.currentState = newState
                    }
                }
            },
            confirmButton = {
                KedgeTextButton(onClick = { dialogState.confirm() }) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                KedgeTextButton(onClick = { dialogState.dismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
