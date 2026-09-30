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
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import heizige.kk.khatkit.highlight.LocalCodeHighlighter
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.richtext.HighlightCodeVisualTransformation
import heizige.kk.khatkit.app.core.ui.components.ui.FormItem
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
import heizige.kk.khatkit.search.SearchService
import heizige.kk.khatkit.search.SearchServiceOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.feature.settings.search.TavilyOptions
import heizige.kk.khatkit.app.feature.settings.search.ExaOptions
import heizige.kk.khatkit.app.feature.settings.search.ZhipuOptions
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

    fun save(updated: SearchServiceOptions) {
        options = updated
        val newServices = settings.searchServices.toMutableList()
        newServices[serviceIndex] = updated
        vm.updateSettings(settings.copy(searchServices = newServices))
    }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = options.displayName,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (settings.searchServices.size > 1) {
                        KedgeIconButton(
                            onClick = {
                                val newServices = settings.searchServices.toMutableList()
                                newServices.removeAt(serviceIndex)
                                vm.updateSettings(settings.copy(searchServices = newServices))
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
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("config") {
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
                            text = stringResource(R.string.setting_page_search_config),
                            style = MaterialTheme.typography.titleMedium
                        )

                        SearchServiceOptionsEditor(
                            options = options,
                            onUpdateOptions = { save(it) }
                        )

                        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                            SearchService.getService(options).Description()
                        }
                    }
                }
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

@Suppress("UNCHECKED_CAST")
@Composable
internal fun SearchServiceOptionsEditor(
    options: SearchServiceOptions,
    onUpdateOptions: (SearchServiceOptions) -> Unit
) {
    when (options) {
        is SearchServiceOptions.TavilyOptions -> {
            TavilyOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.ExaOptions -> {
            ExaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.ZhipuOptions -> {
            ZhipuOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.DoubaoOptions -> {
            DoubaoOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.SearXNGOptions -> {
            SearXNGOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.LinkUpOptions -> {
            SearchLinkUpOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BraveOptions -> {
            BraveOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.MetasoOptions -> {
            MetasoOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.OllamaOptions -> {
            OllamaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.PerplexityOptions -> {
            PerplexityOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BingLocalOptions -> {}
        is SearchServiceOptions.FirecrawlOptions -> {
            FirecrawlOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.JinaOptions -> {
            JinaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.BochaOptions -> {
            BochaOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.KhatKitOptions -> {
            KhatKitOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.GrokOptions -> {
            GrokOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.TinyfishOptions -> {
            TinyfishOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.SerperOptions -> {
            SerperOptions(options) { onUpdateOptions(it) }
        }
        is SearchServiceOptions.CustomJsOptions -> {
            CustomJsOptions(options) { onUpdateOptions(it) }
        }
    }
}

@Composable
internal fun SearchTestSection(
    options: SearchServiceOptions,
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
                style = MaterialTheme.typography.titleMedium
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
                                val service = SearchService.getService(options)
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
                            style = MaterialTheme.typography.bodySmall,
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
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.text.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                res.onFailure { error ->
                    Text(
                        text = error.message ?: stringResource(R.string.search_detail_unknown_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
