package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import heizige.kk.khromia.components.OptionSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import heizige.kk.khatkit.highlight.LocalCodeHighlighter
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.richtext.HighlightCodeVisualTransformation
import heizige.kk.khatkit.app.core.ui.components.ui.FormItem
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.search.DoubaoSearchMode
import heizige.kk.khatkit.search.SearchCommonOptions
import heizige.kk.khatkit.search.SearchResult
import heizige.kk.khatkit.search.SearchRepository
import heizige.kk.khatkit.search.SearchOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.feature.settings.search.TavilyOptions
import heizige.kk.khatkit.app.feature.settings.search.ExaOptions
import heizige.kk.khatkit.app.feature.settings.search.ZhipuOptions
import heizige.kk.khatkit.app.feature.settings.search.DeepSeekOptions
import heizige.kk.khatkit.app.feature.settings.search.DoubaoOptions
import heizige.kk.khatkit.app.feature.settings.search.SearXNGOptions
import heizige.kk.khatkit.app.feature.settings.search.SearchLinkUpOptions
import heizige.kk.khatkit.app.feature.settings.search.BraveOptions
import heizige.kk.khatkit.app.feature.settings.search.SerperOptions
import heizige.kk.khatkit.app.feature.settings.search.MetasoOptions
import heizige.kk.khatkit.app.feature.settings.search.OllamaOptions
import heizige.kk.khatkit.app.feature.settings.search.PerplexityOptions
import heizige.kk.khatkit.app.feature.settings.search.FirecrawlOptions
import heizige.kk.khatkit.app.feature.settings.search.JinaOptions
import heizige.kk.khatkit.app.feature.settings.search.BochaOptions
import heizige.kk.khatkit.app.feature.settings.search.KhatKitOptions
import heizige.kk.khatkit.app.feature.settings.search.TinyfishOptions
import heizige.kk.khatkit.app.feature.settings.search.GrokOptions
import heizige.kk.khatkit.app.feature.settings.search.CustomJsOptions
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.feature.settings.data.removeSearchRepository

@Composable
fun SettingSearchDetailPage(
    serviceId: Uuid,
    vm: SettingViewModel = hiltViewModel()
) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingSearchDetailPageMiuix(serviceId, vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val nav = LocalNavController.current

    val service = settings.searchServices.find { it.id == serviceId } ?: return
    val serviceIndex = settings.searchServices.indexOf(service)
    var options by remember(service) { mutableStateOf(service) }

    fun save(updated: SearchOptions) {
        options = updated
        val newServices = settings.searchServices.toMutableList()
        newServices[serviceIndex] = updated
        vm.updateSettings(settings.copy(searchServices = newServices))
    }

    KedgeSettingsPageScaffold(
        title = options.displayName,
        scrollBehavior = scrollBehavior,
        actions = {
            if (settings.searchServices.size > 1) {
                KedgeIconButton(
                    onClick = {
                        // 走统一入口：删掉的服务若正好是「当前使用的搜索服务」，选中下标要跟着重绑。
                        removeSearchRepository(vm = vm, settings = settings, service = service)
                        nav.popBackStack()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = delete,
                        contentDescription = stringResource(R.string.delete)
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("config") {
                SearchConfigCard(
                    options = options,
                    onUpdate = { save(it) }
                )
            }

            item("test") {
                SearchTestSection(
                    options = options,
                    commonOptions = settings.searchCommonOptions
                )
            }
        }
    }
}

/**
 * 「服务参数」分组卡片：标题 + 参数表单 + 能力说明，一整组分段卡片。
 *
 * 改造前是 `KedgeCard { Column(padding 16) { 标题; SearchOptionsEditor; 说明 } }`：
 * 外壳手搓一张卡，里面的参数行（`KedgeFormRow` → `FormItem` → `KedgeOptionItem`）在组外
 * 各画一张四角全圆的卡 —— 卡里套卡，Miuix 下尤其明显（外壳是 MD3 的 `KedgeCard`，
 * 行是 Miuix 的 `KedgeCard` 行）。现在外壳交给 `KedgeFormCard`（`InlineCardGroup`）：
 * 底色与圆角由组统一出，组内行只留内侧小圆角，首项上边、末项下边由整组裁切给出，
 * 两种风格一致，`SearchOptionsEditor` 一行不用改。
 */
@Composable
internal fun SearchConfigCard(
    options: SearchOptions,
    modifier: Modifier = Modifier,
    onUpdate: (SearchOptions) -> Unit
) {
    KedgeFormCard(modifier = modifier.animateContentSize()) {
        // 标题块是组内「不带底色的子项」，卡片底由 InlineCardGroup 那层透出来；
        // 内边距由调用点补（组容器不加内边距，行组件自带的 16dp 是行的）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_search_config),
                style = KedgeTextStyles.title()
            )
        }

        SearchOptionsEditor(
            options = options,
            onUpdateOptions = onUpdate
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ProvideTextStyle(KedgeTextStyles.footnote()) {
                SearchRepository.get(options).Description()
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun SearchOptionsEditor(
    options: SearchOptions,
    onUpdateOptions: (SearchOptions) -> Unit
) {
    when (options) {
        is SearchOptions.TavilyOptions -> {
            TavilyOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.ExaOptions -> {
            ExaOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.ZhipuOptions -> {
            ZhipuOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.DeepSeekOptions -> {
            DeepSeekOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.DoubaoOptions -> {
            DoubaoOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.SearXNGOptions -> {
            SearXNGOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.LinkUpOptions -> {
            SearchLinkUpOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.BraveOptions -> {
            BraveOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.MetasoOptions -> {
            MetasoOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.OllamaOptions -> {
            OllamaOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.PerplexityOptions -> {
            PerplexityOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.BingLocalOptions -> {}
        is SearchOptions.FirecrawlOptions -> {
            FirecrawlOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.JinaOptions -> {
            JinaOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.BochaOptions -> {
            BochaOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.KhatKitOptions -> {
            KhatKitOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.GrokOptions -> {
            GrokOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.TinyfishOptions -> {
            TinyfishOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.SerperOptions -> {
            SerperOptions(options) { onUpdateOptions(it) }
        }
        is SearchOptions.CustomJsOptions -> {
            CustomJsOptions(options) { onUpdateOptions(it) }
        }
    }
}

@Composable
internal fun SearchTestSection(
    options: SearchOptions,
    commonOptions: SearchCommonOptions
) {
    var query by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<SearchResult>?>(null) }
    val scope = rememberCoroutineScope()

    KedgeCard(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.setting_page_search_test),
                style = KedgeTextStyles.title()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KedgeOutlinedTextFieldWithSlots(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.setting_page_search_test_query_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                KedgeIconButton(
                    onClick = {
                        if (query.isNotBlank() && !testing) {
                            testing = true
                            result = null
                            scope.launch {
                                val service = SearchRepository.get(options)
                                val params = JsonObject(
                                    mapOf("query" to JsonPrimitive(query))
                                )
                                result = service.search(params, commonOptions, options)
                                testing = false
                            }
                        }
                    },
                    enabled = query.isNotBlank() && !testing,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (testing) {
                        KedgeProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = playArrow,
                            contentDescription = stringResource(R.string.setting_page_search_test_run)
                        )
                    }
                }
            }

            result?.let { res ->
                res.onSuccess { searchResult ->
                    searchResult.answer?.let { answer ->
                        Text(
                            text = answer,
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    searchResult.items.forEachIndexed { index, item ->
                        KedgeCard(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.title}",
                                    style = KedgeTextStyles.title()
                                )
                                Text(
                                    text = item.url,
                                    style = KedgeTextStyles.body(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.text.take(200),
                                    style = KedgeTextStyles.body(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                res.onFailure { error ->
                    Text(
                        text = error.message ?: stringResource(R.string.search_detail_unknown_error),
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
