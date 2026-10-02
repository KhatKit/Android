package heizige.kk.khatkit.app.feature.settings.search
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
import heizige.kk.khromia.components.OptionSwitch
import heizige.kk.khromia.components.SegmentedItem
import heizige.kk.khromia.components.SingleChoiceSegmentedRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import heizige.kk.kedge.components.KedgeTextField
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
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
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
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormRow
import heizige.kk.kedge.components.KedgeSingleChoiceSegmentedRow
import heizige.kk.kedge.components.KedgeMultiChoiceSegmentedRow
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
internal fun TavilyOptions(
    options: SearchServiceOptions.TavilyOptions,
    onUpdateOptions: (SearchServiceOptions.TavilyOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_depth))
        }
    ) {
        val depthOptions = listOf("basic", "advanced")
        KedgeSingleChoiceSegmentedRow(
            items = depthOptions.map { depth ->
                SegmentedItem(
                    label = depth.replaceFirstChar { it.uppercase() },
                    selected = options.depth == depth,
                    onClick = { onUpdateOptions(options.copy(depth = depth)) },
                )
            },
        )
    }
}

@Composable
internal fun ExaOptions(
    options: SearchServiceOptions.ExaOptions,
    onUpdateOptions: (SearchServiceOptions.ExaOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun ZhipuOptions(
    options: SearchServiceOptions.ZhipuOptions,
    onUpdateOptions: (SearchServiceOptions.ZhipuOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun DoubaoOptions(
    options: SearchServiceOptions.DoubaoOptions,
    onUpdateOptions: (SearchServiceOptions.DoubaoOptions) -> Unit
) {
    KedgeFormRow(label = { Text(stringResource(R.string.search_detail_api_key)) }) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = { onUpdateOptions(options.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(label = { Text("Mode") }) {
        val modes = DoubaoSearchMode.entries
        KedgeSingleChoiceSegmentedRow(
            items = modes.map { mode ->
                SegmentedItem(
                    label = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                    selected = options.mode == mode,
                    onClick = { onUpdateOptions(options.copy(mode = mode)) },
                )
            },
        )
    }
}

@Composable
internal fun SearXNGOptions(
    options: SearchServiceOptions.SearXNGOptions,
    onUpdateOptions: (SearchServiceOptions.SearXNGOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_url))
        }
    ) {
        KedgeTextField(
            value = options.url,
            onValueChange = {
                onUpdateOptions(options.copy(url = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_engines))
        }
    ) {
        KedgeTextField(
            value = options.engines,
            onValueChange = {
                onUpdateOptions(options.copy(engines = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_language))
        }
    ) {
        KedgeTextField(
            value = options.language,
            onValueChange = {
                onUpdateOptions(options.copy(language = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_username))
        }
    ) {
        KedgeTextField(
            value = options.username,
            onValueChange = {
                onUpdateOptions(options.copy(username = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_password))
        }
    ) {
        KedgeTextField(
            value = options.password,
            onValueChange = {
                onUpdateOptions(options.copy(password = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun SearchLinkUpOptions(
    options: SearchServiceOptions.LinkUpOptions,
    onUpdateOptions: (SearchServiceOptions.LinkUpOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_depth))
        }
    ) {
        val depthOptions = listOf("standard", "deep")
        KedgeSingleChoiceSegmentedRow(
            items = depthOptions.map { depth ->
                SegmentedItem(
                    label = depth.replaceFirstChar { it.uppercase() },
                    selected = options.depth == depth,
                    onClick = { onUpdateOptions(options.copy(depth = depth)) },
                )
            },
        )
    }
}

@Composable
internal fun BraveOptions(
    options: SearchServiceOptions.BraveOptions,
    onUpdateOptions: (SearchServiceOptions.BraveOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun SerperOptions(
    options: SearchServiceOptions.SerperOptions,
    onUpdateOptions: (SearchServiceOptions.SerperOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun MetasoOptions(
    options: SearchServiceOptions.MetasoOptions,
    onUpdateOptions: (SearchServiceOptions.MetasoOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun OllamaOptions(
    options: SearchServiceOptions.OllamaOptions,
    onUpdateOptions: (SearchServiceOptions.OllamaOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun PerplexityOptions(
    options: SearchServiceOptions.PerplexityOptions,
    onUpdateOptions: (SearchServiceOptions.PerplexityOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_max_tokens))
        }
    ) {
        KedgeTextField(
            value = options.maxTokens?.takeIf { it > 0 }?.toString() ?: "",
            onValueChange = { value ->
                onUpdateOptions(options.copy(maxTokens = value.toIntOrNull()))
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_max_tokens_per_page))
        }
    ) {
        KedgeTextField(
            value = options.maxTokensPerPage?.takeIf { it > 0 }?.toString() ?: "",
            onValueChange = { value ->
                onUpdateOptions(options.copy(maxTokensPerPage = value.toIntOrNull()))
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun FirecrawlOptions(
    options: SearchServiceOptions.FirecrawlOptions,
    onUpdateOptions: (SearchServiceOptions.FirecrawlOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun JinaOptions(
    options: SearchServiceOptions.JinaOptions,
    onUpdateOptions: (SearchServiceOptions.JinaOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_search_url))
        }
    ) {
        KedgeTextField(
            value = options.searchUrl,
            onValueChange = {
                onUpdateOptions(options.copy(searchUrl = it.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "https://s.jina.ai/",
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_scrape_url))
        }
    ) {
        KedgeTextField(
            value = options.scrapeUrl,
            onValueChange = {
                onUpdateOptions(options.copy(scrapeUrl = it.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "https://r.jina.ai/",
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun BochaOptions(
    options: SearchServiceOptions.BochaOptions,
    onUpdateOptions: (SearchServiceOptions.BochaOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_summary))
        },
        description = {
            Text(stringResource(R.string.search_detail_summary_desc))
        },
        tail = {
            OptionSwitch(
                checked = options.summary,
                onCheckedChange = { checked ->
                    onUpdateOptions(options.copy(summary = checked))
                }
            )
        }
    )
}

@Composable
internal fun KhatKitOptions(
    options: SearchServiceOptions.KhatKitOptions,
    onUpdateOptions: (SearchServiceOptions.KhatKitOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_depth))
        }
    ) {
        val depthOptions = listOf("standard", "deep")
        KedgeSingleChoiceSegmentedRow(
            items = depthOptions.map { depth ->
                SegmentedItem(
                    label = depth.replaceFirstChar { it.uppercase() },
                    selected = options.depth == depth,
                    onClick = { onUpdateOptions(options.copy(depth = depth)) },
                )
            },
        )
    }
}

@Composable
internal fun TinyfishOptions(
    options: SearchServiceOptions.TinyfishOptions,
    onUpdateOptions: (SearchServiceOptions.TinyfishOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun GrokOptions(
    options: SearchServiceOptions.GrokOptions,
    onUpdateOptions: (SearchServiceOptions.GrokOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_api_key))
        }
    ) {
        KedgeTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(options.copy(apiKey = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_model))
        }
    ) {
        KedgeTextField(
            value = options.model,
            onValueChange = {
                onUpdateOptions(options.copy(model = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_custom_url))
        }
    ) {
        KedgeTextField(
            value = options.customUrl,
            onValueChange = {
                onUpdateOptions(options.copy(customUrl = it))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_system_prompt))
        }
    ) {
        KedgeTextField(
            value = options.systemPrompt,
            onValueChange = {
                onUpdateOptions(options.copy(systemPrompt = it))
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
internal fun CustomJsOptions(
    options: SearchServiceOptions.CustomJsOptions,
    onUpdateOptions: (SearchServiceOptions.CustomJsOptions) -> Unit
) {
    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_name))
        }
    ) {
        KedgeTextField(
            value = options.name,
            onValueChange = {
                onUpdateOptions(options.copy(name = it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.search_detail_custom_search_placeholder),
            shape = RoundedCornerShape(16.dp)
        )
    }

    val highlighter = LocalCodeHighlighter.current
    val darkMode = LocalDarkMode.current

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_search_script))
        }
    ) {
        KedgeOutlinedTextField(
            value = options.searchScript,
            onValueChange = {
                onUpdateOptions(options.copy(searchScript = it))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            maxLines = 20,
            visualTransformation = HighlightCodeVisualTransformation(
                language = "javascript",
                highlighter = highlighter,
                darkMode = darkMode
            ),
            textStyle = KedgeTextStyles.body().merge(fontFamily = JetbrainsMono),
            shape = RoundedCornerShape(16.dp)
        )
    }

    KedgeFormRow(
        label = {
            Text(stringResource(R.string.search_detail_scrape_script))
        },
        description = {
            Text(stringResource(R.string.search_detail_scrape_script_desc))
        }
    ) {
        KedgeOutlinedTextFieldWithSlots(
            value = options.scrapeScript,
            onValueChange = {
                onUpdateOptions(options.copy(scrapeScript = it))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 20,
            placeholder = {
                Text(
                    text = SearchServiceOptions.CustomJsOptions.DEFAULT_SCRAPE_SCRIPT.trimIndent(),
                    style = KedgeTextStyles.body().merge(fontFamily = JetbrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            visualTransformation = HighlightCodeVisualTransformation(
                language = "javascript",
                highlighter = highlighter,
                darkMode = darkMode
            ),
            textStyle = KedgeTextStyles.body().merge(fontFamily = JetbrainsMono),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
