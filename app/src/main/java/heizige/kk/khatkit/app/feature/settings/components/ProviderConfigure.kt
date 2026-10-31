package heizige.kk.khatkit.app.feature.settings.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.ai.provider.ClaudePromptCacheTtl
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
import heizige.kk.khatkit.common.http.okhttp.toHttpUrlOrNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import heizige.kk.khatkit.app.core.ui.icons.visibility
import heizige.kk.khatkit.app.core.ui.icons.visibilityOff
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme

/**
 * 提供商的连接配置，按 Mochi 设置页的分组卡片排：接口类型 / 连接 / 选项（各类型再加自己的分组）。
 * 调用方只负责外层滚动和左右留白；各分组之间的间距在这里统一给。
 */
@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ProviderSectionGap),
        modifier = modifier
    ) {
        provider.description()

        if (!provider.builtIn) {
            val types = ProviderSetting.Types
            MochiListSection(header = "接口类型") {
                ProviderSectionBlock {
                    MochiSegmentedControl(
                        selectedIndex = types.indexOf(provider::class).coerceAtLeast(0),
                        onSelect = { onEdit(provider.convertTo(types[it])) },
                        items = types.map { it.simpleName ?: "" }.toImmutableList(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        when (provider) {
            is ProviderSetting.OpenAI -> ProviderConfigureOpenAI(provider, onEdit)
            is ProviderSetting.Google -> ProviderConfigureGoogle(provider, onEdit)
            is ProviderSetting.Claude -> ProviderConfigureClaude(provider, onEdit)
            // 账号类提供商的配置界面由 OA-3（t161）接；这里先不渲染
            is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> Unit
        }
    }
}

/** 提供商配置页里相邻两个分组卡片的间距（[ProviderConfigure] 内部和详情页外层共用）。 */
internal val ProviderSectionGap = 24.dp

/** 表单行标签宽度：「API Key」「Base URL」都放得下，同一张卡片里输入区对齐成一列。 */
private val ProviderLabelWidth = 96.dp

/** 分组卡片里的自定义内容（分段选择器）：左右与行文字对齐，卡片按它登记的起点画分隔线。 */
@Composable
internal fun ProviderSectionBlock(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth()
            .padding(MochiListDefaults.RowPadding),
    ) {
        content()
    }
}

/** 开关行：点整行也能切换。 */
@Composable
internal fun ProviderSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { MochiSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** 密钥输入行：默认打码，行尾的眼睛切换明文。 */
@Composable
private fun SecretFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiLine: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .mochiListDivider(MochiListDefaults.TextRowPadding)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MochiListTextFieldRow(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = "必填",
            secure = !visible,
            // 打码时按单行显示圆点；明文时私钥这类长内容可以换行
            singleLine = !(multiLine && visible),
            maxLines = if (multiLine && visible) 6 else 1,
            labelWidth = ProviderLabelWidth,
        )
        MochiIconButton(
            onClick = { visible = !visible },
            style = MochiButtonStyle.Text,
            size = 40.dp,
            modifier = Modifier.padding(end = 6.dp),
        ) {
            Icon(
                imageVector = if (visible) visibilityOff else visibility,
                contentDescription = if (visible) "隐藏" else "显示",
                tint = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    }
    val sourceBaseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl,
            useInteractionsApi = (this as? ProviderSetting.Google)?.useInteractionsApi ?: false,
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        else -> error("Unsupported provider type: $type")
    }
}

internal fun ProviderSetting.defaultBaseUrlForReset(): String {
    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == id }
    if (defaultProvider != null) {
        when (this) {
            is ProviderSetting.OpenAI -> if (defaultProvider is ProviderSetting.OpenAI) return defaultProvider.baseUrl
            is ProviderSetting.Google -> if (defaultProvider is ProviderSetting.Google) return defaultProvider.baseUrl
            is ProviderSetting.Claude -> if (defaultProvider is ProviderSetting.Claude) return defaultProvider.baseUrl
            is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> Unit
        }
    }
    return when (this) {
        is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().baseUrl
        is ProviderSetting.Google -> ProviderSetting.Google().baseUrl
        is ProviderSetting.Claude -> ProviderSetting.Claude().baseUrl
        // 账号类提供商地址写死，没有可重置的 baseUrl
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    }
}

internal fun ProviderSetting.resetBaseUrlToDefault(): ProviderSetting {
    val defaultBaseUrl = defaultBaseUrlForReset()
    return when (this) {
        is ProviderSetting.OpenAI -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Google -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Claude -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> this
    }
}

internal fun ProviderSetting.isUsingDefaultBaseUrl(): Boolean {
    val baseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    }
    return baseUrl == defaultBaseUrlForReset()
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

/**
 * 离开编辑面（输入框失焦）时把提供商 `name` 规范化成 `trim()` 后的值并落库**一次**。
 *
 * 背景：保存按钮删掉之后，`name` 只剩下 `onValueChange` 一条落库路径，而同文件里
 * apiKey / baseUrl 都是逐键 `.trim()` —— 于是同一个字段在列表页会被 trim、在详情页不会。
 * 这里把 `name` 的规范化补回来，但**不能**照抄 apiKey 的逐键 trim：
 *
 * - **不逐键 trim**：`name` 是人类可读名字（「Open AI」），在 `onValueChange` 里 trim 会让
 *   空格一敲下就被剥掉，词间空格根本打不进去；apiKey/baseUrl 不含词间空格才无所谓。
 * - **看 `hasFocus` 而不是 `isFocused`**：MD3 分支下调用方的 modifier 挂在输入框自身，
 *   事件节点是焦点目标的祖先，聚焦时会收到 `Active`（`isFocused == true`）；Miuix 分支下
 *   `Md3OutlinedTextFieldWithSlots` 把 modifier 挂到**外层 Column**，若中间还隔着别的焦点
 *   目标，Compose 只派发 `ActiveParent`（`isFocused == false`，`hasFocus == true`，
 *   见 `FocusOwnerImpl` / `FocusInvalidationManager.invalidateNodes`）。用 `hasFocus`
 *   两条分支语义一致：为 true 表示「这个编辑面（含输入框）还握着焦点」，失焦才为 false。
 * - **无差异不提交**：失焦、重组都会派发焦点事件，`trim()` 结果与当前值相同就直接返回，
 *   不产生无意义的落库 / 重组。
 */
private fun Modifier.normalizeNameOnBlur(
    name: String,
    onCommit: (String) -> Unit,
): Modifier = onFocusChanged { focusState ->
    if (focusState.hasFocus) return@onFocusChanged
    val trimmed = name.trim()
    if (trimmed != name) onCommit(trimmed)
}

/** 名称输入行：失焦时 trim 一次（见 [normalizeNameOnBlur]）。 */
@Composable
private fun NameFieldRow(name: String, onCommit: (String) -> Unit) {
    MochiListTextFieldRow(
        value = name,
        onValueChange = onCommit,
        modifier = Modifier.normalizeNameOnBlur(name, onCommit),
        label = stringResource(R.string.setting_provider_page_name),
        placeholder = "提供商名称",
        labelWidth = ProviderLabelWidth,
    )
}

@Composable
private fun BaseUrlFieldRow(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    MochiListTextFieldRow(
        value = value,
        onValueChange = onValueChange,
        label = "Base URL",
        placeholder = placeholder,
        labelWidth = ProviderLabelWidth,
    )
}

@Composable
private fun ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val invalidUrl = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl()
    MochiListSection(
        header = "连接",
        footer = if (invalidUrl) "Base URL 格式不正确" else null,
    ) {
        NameFieldRow(provider.name) { onEdit(provider.copy(name = it)) }
        SecretFieldRow(
            label = stringResource(R.string.setting_provider_page_api_key),
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        )
        BaseUrlFieldRow(provider.baseUrl, "https://api.openai.com/v1") {
            onEdit(provider.copy(baseUrl = it.trim()))
        }
        MochiListTextFieldRow(
            value = if (provider.useResponseApi) provider.responsesPath else provider.chatCompletionsPath,
            onValueChange = {
                onEdit(
                    if (provider.useResponseApi) {
                        provider.copy(responsesPath = it.trim())
                    } else {
                        provider.copy(chatCompletionsPath = it.trim())
                    }
                )
            },
            label = stringResource(R.string.setting_provider_page_api_path),
            enabled = !provider.builtIn,
            labelWidth = ProviderLabelWidth,
        )
    }

    val responseAPIWarning = stringResource(R.string.setting_provider_page_response_api_warning)
    MochiListSection(header = "选项") {
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_enable),
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_response_api),
            subtitle = "使用 /responses 接口代替 /chat/completions",
            checked = provider.useResponseApi,
            onCheckedChange = {
                onEdit(provider.copy(useResponseApi = it))
                if (it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    Toast.show(message = responseAPIWarning, isError = false)
                }
            },
        )
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_include_history_reasoning),
            checked = provider.includeHistoryReasoning,
            onCheckedChange = { onEdit(provider.copy(includeHistoryReasoning = it)) },
        )
    }
}

@Composable
private fun ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    val invalidUrl = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl()
    MochiListSection(
        header = "连接",
        footer = if (invalidUrl) "Base URL 格式不正确" else null,
    ) {
        NameFieldRow(provider.name) { onEdit(provider.copy(name = it)) }
        SecretFieldRow(
            label = stringResource(R.string.setting_provider_page_api_key),
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        )
        BaseUrlFieldRow(provider.baseUrl, "https://api.anthropic.com/v1") {
            onEdit(provider.copy(baseUrl = it.trim()))
        }
    }

    val ttlOptions = ClaudePromptCacheTtl.entries
    val ttlLabels = persistentListOf(
        stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_5m),
        stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_1h),
    )
    MochiListSection(header = "选项") {
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_enable),
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_claude_prompt_caching),
            checked = provider.promptCaching,
            onCheckedChange = { onEdit(provider.copy(promptCaching = it)) },
        )
        if (provider.promptCaching) {
            MochiListPickerRow(
                title = stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl),
                options = ttlLabels,
                selectedIndex = ttlOptions.indexOf(provider.promptCacheTtl).coerceAtLeast(0),
                onSelect = { onEdit(provider.copy(promptCacheTtl = ttlOptions[it])) },
            )
        }
    }
}

@Composable
private fun ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val context = LocalContext.current
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.privateKey,
                )
            )
            Toast.show("Service account imported", isError = false)
        } catch (e: Exception) {
            Toast.show("Failed to import: ${e.message}", isError = true)
        }
    }

    val serviceAccount = provider.vertexAI && provider.useServiceAccount
    val urlFooter = when {
        provider.vertexAI || provider.baseUrl.isBlank() -> null
        !provider.baseUrl.isValidBaseUrl() -> "Base URL 格式不正确"
        !provider.baseUrl.endsWith("/v1beta") -> "Base URL 通常以 /v1beta 结尾"
        else -> null
    }
    MochiListSection(header = "连接", footer = urlFooter) {
        NameFieldRow(provider.name) { onEdit(provider.copy(name = it)) }
        if (!serviceAccount) {
            SecretFieldRow(
                label = stringResource(R.string.setting_provider_page_api_key),
                value = provider.apiKey,
                onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
            )
        }
        if (!provider.vertexAI) {
            BaseUrlFieldRow(provider.baseUrl, "https://generativelanguage.googleapis.com/v1beta") {
                onEdit(provider.copy(baseUrl = it.trim()))
            }
        }
    }

    MochiListSection(header = "选项") {
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_enable),
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
        if (!provider.vertexAI) {
            ProviderSwitchRow(
                title = "Interactions API",
                subtitle = "Beta",
                checked = provider.useInteractionsApi,
                onCheckedChange = { onEdit(provider.copy(useInteractionsApi = it)) },
            )
        }
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_vertex_ai),
            checked = provider.vertexAI,
            onCheckedChange = { onEdit(provider.copy(vertexAI = it)) },
        )
        if (provider.vertexAI) {
            ProviderSwitchRow(
                title = stringResource(R.string.setting_provider_page_use_service_account),
                checked = provider.useServiceAccount,
                onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) },
            )
        }
    }

    if (serviceAccount) {
        MochiListSection(header = "服务账号") {
            MochiListTextFieldRow(
                value = provider.serviceAccountEmail,
                onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
                label = "邮箱",
                placeholder = stringResource(R.string.setting_provider_page_service_account_email),
                labelWidth = ProviderLabelWidth,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                ),
            )
            SecretFieldRow(
                label = stringResource(R.string.setting_provider_page_private_key),
                value = provider.privateKey,
                onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
                multiLine = true,
            )
            MochiListTextFieldRow(
                value = provider.location,
                onValueChange = { onEdit(provider.copy(location = it.trim())) },
                label = "区域",
                placeholder = "us-central1",
                labelWidth = ProviderLabelWidth,
            )
            MochiListTextFieldRow(
                value = provider.projectId,
                onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
                label = stringResource(R.string.setting_provider_page_project_id),
                labelWidth = ProviderLabelWidth,
            )
            MochiListButton(
                text = stringResource(R.string.setting_provider_page_import_service_account_json),
                onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        }
    }
}
