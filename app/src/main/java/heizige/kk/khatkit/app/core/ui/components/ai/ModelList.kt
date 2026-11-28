package heizige.kk.khatkit.app.core.ui.components.ai

import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.mask.mochiScrollFadeMask
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import heizige.kk.khatkit.uikit.mochi.fadingEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.icons.social.HeartIcon
import heizige.kk.khatkit.app.core.ui.hooks.rememberSharedPreferenceString
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.extendColors
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.toDp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.navigation.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.utility.build
import heizige.kk.khatkit.app.core.ui.icons.navigation.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.navigation.close
import heizige.kk.khatkit.app.core.ui.icons.utility.dragIndicator
import heizige.kk.khatkit.app.core.ui.icons.navigation.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.settings.favorite as favoriteIcon
import heizige.kk.khatkit.app.core.ui.icons.settings.memory
import heizige.kk.khatkit.app.core.ui.icons.media.photo
import heizige.kk.khatkit.app.core.ui.icons.ai.psychology
import heizige.kk.khatkit.app.core.ui.icons.utility.search
import heizige.kk.khatkit.app.core.ui.icons.settings.title
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import heizige.kk.khatkit.uikit.mochi.KkAssistChip



class ModelListState internal constructor(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
) {
    var modelId by mutableStateOf(modelId)
        private set

    var providers by mutableStateOf(providers)
        private set

    var type by mutableStateOf(type)
        private set

    var visible by mutableStateOf(false)
        private set

    val currentModel: Model?
        get() = modelId?.let { providers.findModelById(it) }

    val filteredProviders: List<ProviderSetting>
        get() = providers.fastFilter { provider ->
            provider.enabled && provider.models.fastAny { model -> model.type == type }
        }

    fun open() {
        visible = true
    }

    fun close() {
        visible = false
    }

    internal fun update(
        modelId: Uuid?,
        providers: List<ProviderSetting>,
        type: ModelType,
    ) {
        this.modelId = modelId
        this.providers = providers
        this.type = type
    }
}

@Composable
fun rememberModelListState(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
): ModelListState {
    return remember {
        ModelListState(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }.also {
        it.update(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }
}

@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onSelect: (Model) -> Unit
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = type,
    )

    ModelSelectorButton(
        state = state,
        modifier = modifier,
        onlyIcon = onlyIcon,
        allowClear = allowClear,
        onClear = { onSelect(Model()) },
    )

    ModelListSheet(
        state = state,
        onSelect = onSelect,
    )
}

@Composable
internal fun ModelSelectorButton(
    state: ModelListState,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
) {
    val model = state.currentModel

    if (!onlyIcon) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MochiTextButton(
                onClick = {
                    state.open()
                },
                modifier = modifier,
                shapes = ButtonDefaults.shapes(),
            ) {
                model?.modelId?.let {
                    AutoAIIcon(
                        it, Modifier
                            .padding(end = 4.dp)
                            .size(36.dp),
                        color = Color.Transparent
                    )
                }
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (allowClear && model != null) {
                MochiIconButton(
                    onClick = onClear,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = close,
                        contentDescription = stringResource(R.string.model_list_clear_model)
                    )
                }
            }
        }
    } else {
        MochiIconButton(
            onClick = {
                state.open()
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                if (model != null) {
                    AutoAIIcon(
                        modifier = Modifier.size(36.dp),
                        name = model.modelId,
                        color = Color.Transparent
                    )
                } else {
                    Icon(
                        imageVector = psychology,
                        contentDescription = stringResource(R.string.setting_model_page_chat_model),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelListSheet(
    state: ModelListState,
    onSelect: (Model) -> Unit,
) {
    // U7b-2c：不再用 "if (!visible) return" 提前返回（那会杀掉收起动画），显隐交给 MochiSheet 状态。
    PrimaryBottomSheet(
        visible = state.visible,
        title = stringResource(R.string.model_list_select_model),
        imageVector = memory,
        onDismiss = {
            state.close()
        },
        scrollable = false,
    ) { dismiss ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModelList(
                currentModel = state.modelId,
                providers = state.filteredProviders,
                modelType = state.type,
                onSelect = {
                    onSelect(it)
                    dismiss()
                },
                onDismiss = {
                    dismiss()
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.ModelList(
    currentModel: Uuid? = null,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = rememberAppEntryPoint().settingsStore()
    val settings = settingsStore.settingsFlow
        .collectAsStateWithLifecycle()

    // 拖动排序时先改这份本地顺序，松手再写回设置。设置是异步回到界面的，拖到边缘自动滚动时拖拽库每帧都会按
    // 旧布局再算一次移动，直接改设置会让顺序被反复改回去，看起来就是来回抽搐
    var draggingFavoriteOrder by remember { mutableStateOf<List<Uuid>?>(null) }
    val favoriteModels = (draggingFavoriteOrder ?: settings.value.favoriteModels).mapNotNull { modelId ->
        val model = settings.value.providers.findModelById(modelId) ?: return@mapNotNull null
        if (model.type != modelType) return@mapNotNull null
        val provider = model.findProvider(providers = settings.value.providers, checkOverwrite = false) ?: return@mapNotNull null
        model to provider
    }

    var searchKeywords by remember { mutableStateOf("") }

    // 折叠的供应商（持久化），搜索时全部展开
    var collapsedProvidersPref by rememberSharedPreferenceString("model_list_collapsed_providers", "")
    val collapsedProviders = remember(collapsedProvidersPref) {
        collapsedProvidersPref.orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    }
    fun isCollapsed(provider: ProviderSetting): Boolean =
        searchKeywords.isBlank() && provider.id.toString() in collapsedProviders

    fun toggleCollapsed(provider: ProviderSetting) {
        val id = provider.id.toString()
        collapsedProvidersPref = (if (id in collapsedProviders) collapsedProviders - id else collapsedProviders + id)
            .joinToString(",")
    }

    val typeFilteredModelsByProvider = remember(providers, modelType) {
        providers.associate { provider ->
            provider.id to provider.models.fastFilter { it.type == modelType }
        }
    }

    val searchFilteredModelsByProvider = remember(providers, modelType, searchKeywords) {
        providers.associate { provider ->
            provider.id to provider.models.fastFilter {
                it.type == modelType && it.displayName.contains(searchKeywords, true)
            }
        }
    }

    val visibleModelsByProvider = remember(providers, searchFilteredModelsByProvider, collapsedProviders, searchKeywords) {
        providers.associate { provider ->
            provider.id to if (isCollapsed(provider)) emptyList() else searchFilteredModelsByProvider[provider.id].orEmpty()
        }
    }

    // 计算当前选中模型的位置
    val selectedModelPosition = remember(currentModel, favoriteModels, providers, typeFilteredModelsByProvider) {
        if (currentModel == null) return@remember 0

        var position = 0

        // 跳过无providers提示
        if (providers.isEmpty()) {
            position += 1
        }

        // 检查是否在收藏列表中
        val favoriteIndex = favoriteModels.indexOfFirst { it.first.id == currentModel }
        if (favoriteIndex >= 0) {
            if (favoriteModels.isNotEmpty()) {
                position += 1 // favorite header
            }
            position += favoriteIndex
            return@remember position
        }

        // 跳过收藏列表
        if (favoriteModels.isNotEmpty()) {
            position += 1 // favorite header
            position += favoriteModels.size
        }

        // 在providers中查找
        for (provider in providers) {
            position += 1 // provider header
            val models = typeFilteredModelsByProvider[provider.id].orEmpty()
            val modelIndex = models.indexOfFirst { it.id == currentModel }
            val collapsed = isCollapsed(provider)
            if (modelIndex >= 0) {
                // 折叠时定位到供应商标题
                if (collapsed) return@remember position - 1
                position += modelIndex
                return@remember position
            }
            if (!collapsed) position += models.size
        }

        0
    }

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedModelPosition
    )
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 列表里只显示当前类型的收藏，下标和完整的收藏列表对不上，按 key 找到对应的模型再移动
        val ids = (draggingFavoriteOrder ?: settings.value.favoriteModels).toMutableList()
        val fromPos = ids.indexOfFirst { "favorite:$it" == from.key }
        val toPos = ids.indexOfFirst { "favorite:$it" == to.key }
        if (fromPos >= 0 && toPos >= 0) {
            ids.add(toPos, ids.removeAt(fromPos))
            draggingFavoriteOrder = ids
        }
    }
    val haptics = rememberKkHaptics()

    val providerPositions = remember(providers, favoriteModels, visibleModelsByProvider) {
        var currentIndex = 0
        if (providers.isEmpty()) {
            currentIndex = 1 // no providers item
        }
        if (favoriteModels.isNotEmpty()) {
            currentIndex += 1 // favorite header
            currentIndex += favoriteModels.size // favorite models
        }

        providers.map { provider ->
            val position = currentIndex
            currentIndex += 1 // provider header
            currentIndex += visibleModelsByProvider[provider.id].orEmpty().size
            provider.id to position
        }.toMap()
    }

    // 列表独立占用搜索框和供应商筛选条之间的剩余高度。
    val modelList: @Composable (Modifier) -> Unit = { listModifier ->
        // 行的圆角与行间空隙全部取全局列表卡片样式：收藏组、每个供应商组各自成组，
        // 组内首项只圆上边、末项只圆下边（MD3 20dp/gap 2dp，Miuix 同组同色/gap 0）。
        val cards = listCardStyle()
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(cards.gap),
            contentPadding = PaddingValues(vertical = 8.dp),
            flingBehavior = MochiScrollDefaults.flingBehavior(),
            // U8：长列表底部淡出，和 Mochi 的滚动边缘观感一致（顶部有 sticky 分组标题，不加顶边淡出）。
            modifier = listModifier.fadingEdge(bottom = 8.dp),
        ) {
            if (providers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.model_list_no_providers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendColors.gray6,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (favoriteModels.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.model_list_favorite),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp, top = 8.dp)
                    )
                }

                itemsIndexed(
                    items = favoriteModels,
                    key = { _, favorite -> "favorite:" + favorite.first.id.toString() }
                ) { index, (model, provider) ->
                    ReorderableItem(
                        state = reorderableState,
                        key = "favorite:" + model.id.toString()
                    ) { isDragging ->
                        // Mochi 分组：每项各包一层分组卡片，非首项在顶边补分隔线（gap > 0 时每项本来就是独立卡片，不画）
                        KkGroupedColumn(
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .animateItem(),
                            shape = cards.indexedShape(index, favoriteModels.size),
                            containerColor = MochiListDefaults.colors().container,
                            topDivider = index > 0 && cards.gap == 0.dp,
                        ) {
                            ModelItem(
                                model = model,
                                onSelect = onSelect,
                                providerSetting = provider,
                                shape = cards.indexedShape(index, favoriteModels.size),
                                select = model.id == currentModel,
                                onDismiss = {
                                    onDismiss()
                                },
                                tail = {
                                    MochiIconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsStore.update { settings ->
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels.filter { it != model.id }
                                                    )
                                                }
                                            }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            HeartIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                dragHandle = {
                                    Icon(
                                        imageVector = dragIndicator,
                                        contentDescription = null,
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptics.longPress()
                                            },
                                            onDragStopped = {
                                                haptics.confirm()
                                                // 松手才写回设置：写盘时界面已经收到新设置，这时再撤掉本地顺序不会闪回旧顺序；
                                                // 写盘期间可能已经开始下一次拖动，那时的本地顺序要留给下一次松手保存
                                                val order = draggingFavoriteOrder
                                                if (order != null) {
                                                    coroutineScope.launch {
                                                        settingsStore.update { it.copy(favoriteModels = order) }
                                                        if (draggingFavoriteOrder === order) draggingFavoriteOrder = null
                                                    }
                                                }
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            providers.fastForEach { providerSetting ->
                val collapsed = isCollapsed(providerSetting)
                // 分组标题本身也是「可点的一行」（点一下折叠/展开该供应商），所以同样走
                // option item：leading 折叠箭头、title 供应商名、trailing 余额。
                // 底色给 Transparent —— 它是组标题不是一个选项，保持原来「不带卡面」的观感；
                // 圆角仍按组首项算（标题就是该供应商组的第一行）。
                stickyHeader(key = "header:${providerSetting.id}") {
                    MochiOptionItem(
                        onClick = { toggleCollapsed(providerSetting) },
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = cards.indexedShape(
                            index = 0,
                            count = 1 + visibleModelsByProvider[providerSetting.id].orEmpty().size,
                        ),
                        backgroundColor = Color.Transparent,
                        leadingContent = {
                            Icon(
                                imageVector = if (collapsed) chevronRight else keyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        titleContent = {
                            Text(
                                text = providerSetting.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            ProviderBalanceText(
                                providerSetting = providerSetting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }

                itemsIndexed(
                    items = visibleModelsByProvider[providerSetting.id].orEmpty(),
                    key = { _, model -> model.id }
                ) { index, model ->
                    val favorite = settings.value.favoriteModels.contains(model.id)
                    val groupCount = visibleModelsByProvider[providerSetting.id].orEmpty().size
                    KkGroupedColumn(
                        modifier = Modifier.animateItem(),
                        shape = cards.indexedShape(index = index, count = groupCount),
                        containerColor = MochiListDefaults.colors().container,
                        topDivider = index > 0 && cards.gap == 0.dp,
                    ) {
                        ModelItem(
                            model = model,
                            onSelect = onSelect,
                            providerSetting = providerSetting,
                            shape = cards.indexedShape(
                                index = index,
                                count = visibleModelsByProvider[providerSetting.id].orEmpty().size,
                            ),
                            select = currentModel == model.id,
                            onDismiss = {
                                onDismiss()
                            },
                            tail = {
                                MochiIconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            settingsStore.update { settings ->
                                                if (favorite) {
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels.filter { it != model.id }
                                                    )

                                                } else {
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels + model.id
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    if (favorite) {
                                        Icon(
                                            HeartIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = favoriteIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 搜索框、模型表和供应商筛选条共用弹层正文的有限高度，避免搜索结果槽按整屏扩展，
    // 与弹层正文中的模型列表重叠。
    val providerBadgeListState = rememberLazyListState()
    LaunchedEffect(lazyListState) {
        // 当 LazyColumn 滚动时，筛选条也跟随滚动到当前供应商。
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(100)
            .collect { index ->
                if (index > 0) {
                    val currentProvider = providerPositions.entries.findLast {
                        index > it.value
                    }
                    val providerIndex = providers.indexOfFirst { it.id == currentProvider?.key }
                    if (providerIndex >= 0) {
                        providerBadgeListState.animateScrollToItem(providerIndex)
                    } else {
                        providerBadgeListState.requestScrollToItem(0)
                    }
                } else {
                    providerBadgeListState.requestScrollToItem(0)
                }
            }
    }

    val focusManager = LocalFocusManager.current
    MochiTextField(
        value = searchKeywords,
        onValueChange = { searchKeywords = it },
        placeholder = stringResource(R.string.model_list_search_placeholder),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
    modelList(Modifier.weight(1f).fillMaxWidth())
    if (providers.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .mochiScrollFadeMask(providerBadgeListState, vertical = false),
            state = providerBadgeListState,
            flingBehavior = MochiScrollDefaults.flingBehavior(),
        ) {
            items(providers, key = { it.id }) { provider ->
                KkAssistChip(
                    onClick = {
                        val position = providerPositions[provider.id] ?: 0
                        coroutineScope.launch { lazyListState.animateScrollToItem(position) }
                    },
                    label = provider.name,
                    leadingIcon = {
                        AutoAIIcon(name = provider.name, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: Model,
    providerSetting: ProviderSetting,
    select: Boolean,
    shape: Shape,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tail: @Composable RowScope.() -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null
) {
    val navController = LocalNavController.current
    // 与全站其它列表同一套选项行：头像 → leading、模型名 → title、类型/模态/能力标签 →
    // supporting、收藏按钮与拖拽把手 → trailing。行圆角由调用点按 index/count 传下来，
    // 组件内不再带圆角默认值。
    //
    // 手势保持原样（整行单击 = 选中，长按 = 进该供应商详情），两件事仍由**同一个**
    // combinedClickable 承担，所以挂在行外层 modifier 上，行组件自身传 onClick = null：
    // 否则会挂两份点击回调，一次点击触发两次。
    MochiOptionItem(
        onClick = null,
        modifier = modifier.combinedClickable(
            enabled = true,
            onLongClick = {
                onDismiss()
                navController.navigate(
                    Screen.SettingProviderDetail(
                        providerSetting.id.toString()
                    )
                )
            },
            onClick = { onSelect(model) },
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
        ),
        shape = shape,
        // 选中态沿用改动前的 primaryContainer 底色 + onPrimaryContainer 字色。
        backgroundColor = if (select) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
        leadingContent = {
            KkSurface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp)
                )
            }
        },
        titleContent = {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (select) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ModelTypeTag(model = model)

                ModelModalityTag(model = model)

                ModelAbilityTag(model = model)
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tail()
                dragHandle?.invoke(this)
            }
        },
    )
}

@Composable
fun ModelTypeTag(model: Model) {
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> title
                    Modality.IMAGE -> photo
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
        Icon(
            imageVector = arrowForward,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> title
                    Modality.IMAGE -> photo
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> {
                Tag(
                    type = TagType.WARNING
                ) {
                    Icon(
                        imageVector = build,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
                    )
                }
            }

            ModelAbility.REASONING -> {
                Tag(
                    type = TagType.INFO
                ) {
                    Icon(
                        painter = rememberVectorPainter(psychology),
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                    )
                }
            }
        }
    }
}
