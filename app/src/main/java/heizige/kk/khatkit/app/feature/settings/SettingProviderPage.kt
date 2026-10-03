package heizige.kk.khatkit.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeSearchBar
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.RECOMMENDED_PROVIDERS
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.decodeProviderSetting
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.feature.settings.components.ProviderConfigure
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.ImageUtils
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.dragIndicator
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics

@Composable
fun SettingProviderPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.providers.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.updateSettings(settings.copy(providers = newProviders))
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

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_provider_page_title),
        scrollBehavior = scrollBehavior,
        actions = {
            RecommendProviderButton { provider ->
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(provider.copyProvider(Uuid.random())) + settings.providers
                    )
                )
            }
            ImportProviderButton {
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(it.copyProvider(Uuid.random())) + settings.providers
                    )
                )
            }
            AddButton {
                vm.updateSettings(
                    settings.copy(
                        providers = listOf(it) + settings.providers
                    )
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // KSU 式药丸搜索框（KernelSU SuperSearchBar 同一套逻辑）：点一下折叠药丸，
            // 展开成铺满全屏的搜索页；取消键/返回键收起，关键词同时清空。
            // 展开期间页面正文整个抽掉（KSU 的 SearchBox 同款），列表只在全屏槽里出现一次。
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.setting_provider_page_search_providers),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
                expandedContent = {
                    // 独立窗口里不能有 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩
                    NoHeroTransition {
                        ProviderList(
                            providers = filteredProviders,
                            modifier = Modifier.fillMaxSize(),
                            listState = lazyListState,
                            reorderableState = reorderableState,
                            bottomPadding = innerPadding.calculateBottomPadding() +
                                PageMetrics.BottomContentPadding,
                            onClick = { provider ->
                                navController.navigate(Screen.SettingProviderDetail(provider.id.toString()))
                            },
                        )
                    }
                },
            )

            if (!searchExpanded) {
                ProviderList(
                    providers = filteredProviders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding(),
                    listState = lazyListState,
                    reorderableState = reorderableState,
                    bottomPadding = innerPadding.calculateBottomPadding() +
                        PageMetrics.BottomContentPadding,
                    onClick = { provider ->
                        navController.navigate(Screen.SettingProviderDetail(provider.id.toString()))
                    },
                )
            }
        }
    }
}

/**
 * 供应商列表。折叠态挂在页面正文里，展开态搬进搜索框的全屏槽，所以两处共用一份实现，
 * 只有 [modifier] 不同（页面里用 `weight(1f)` 跟着 Column 走，全屏槽里 `fillMaxSize`）。
 */
@Composable
private fun ProviderList(
    providers: List<ProviderSetting>,
    onClick: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) +
            PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = listState,
    ) {
        items(providers, key = { it.id }) { provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                ProviderItem(
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .fillMaxWidth(),
                    provider = provider,
                    dragHandle = {
                        val haptic = LocalHapticFeedback.current
                        KedgeIconButton(
                            onClick = {},
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
                                ),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = dragIndicator,
                                contentDescription = null
                            )
                        }
                    },
                    onClick = { onClick(provider) },
                )
            }
        }
    }
}

@Composable
private fun RecommendProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    var showSheet by remember { mutableStateOf(false) }
    val importSuccessMessage = stringResource(R.string.setting_provider_page_import_success)

    KedgeIconButton(
        onClick = { showSheet = true },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(autoAwesome, contentDescription = stringResource(R.string.setting_provider_page_recommend))
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RECOMMENDED_PROVIDERS.forEach { provider ->
                    RecommendProviderItem(
                        provider = provider,
                        onAdd = {
                            onAdd(provider)
                            Toast.show(
                                importSuccessMessage,
                                isError = false
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendProviderItem(
    provider: ProviderSetting,
    onAdd: () -> Unit
) {
    KedgeCard(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProvideTextStyle(KedgeTextStyles.footnoteSmall()) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.description()
                    }
                }
            }
            KedgeIconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes()) {
                Icon(add, contentDescription = stringResource(R.string.setting_provider_page_add))
            }
        }
    }
}

@Composable
private fun ImportProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            handleImageQRCode(it, onAdd, toaster, context)
        }
    }

    KedgeIconButton(
        onClick = {
            showImportDialog = true
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(uploadFile, null)
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_import_dialog_message),
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 从相册选择（扫码导入已随 libbarhopper 一并移除，
                        // 供应商配置仍可从图片或手动填写导入）
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
                            shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.large)
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
                }
            },
            confirmButton = {},
            dismissButton = {
                KedgeTextButton(
                    onClick = { showImportDialog = false },
                    shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.large)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = KedgeTextStyles.footnote()
                    )
                }
            }
        )
    }
}

private fun handleImageQRCode(
    uri: Uri,
    onAdd: (ProviderSetting) -> Unit,
    toaster: com.dokar.sonner.ToasterState,
    context: android.content.Context
) {
    runCatching {
        // 使用ImageUtils解析二维码
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


@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it.copyProvider(name = it.name.trim()))
    }

    KedgeIconButton(
        onClick = {
            dialogState.open(ProviderSetting.OpenAI())
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(add, "Add")
    }

    if (dialogState.isEditing) {
        AppAlertDialog(
            onDismissRequest = {
                dialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                dialogState.currentState?.let {
                    ProviderConfigure(it) { newState ->
                        dialogState.currentState = newState
                    }
                }
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        dialogState.confirm()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                KedgeTextButton(
                    onClick = {
                        dialogState.dismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit
) {
    KedgeCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (provider.enabled) {
                CustomColors.listItemColors.containerColor
            } else MaterialTheme.colorScheme.errorContainer,
        ),
        onClick = {
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProvideTextStyle(KedgeTextStyles.footnote()) {
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
                    if (provider.name == "AiHubMix") {
                        Tag(type = TagType.INFO) {
                            Text("10% 优惠")
                        }
                    }
                }
            }
            dragHandle()
        }
    }
}
