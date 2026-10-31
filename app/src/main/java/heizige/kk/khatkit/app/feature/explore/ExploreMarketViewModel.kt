package heizige.kk.khatkit.app.feature.explore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.hub.HubActionResult
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.hub.HubResourceEntry
import heizige.kk.khatkit.hub.HubResourcePackage
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.uikit.CardRunResult
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginInstaller
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginLoader
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginRecord
import heizige.kk.khatkit.app.core.data.ocr.OcrPluginStore
import heizige.kk.khatkit.dependency.DependencyCacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * 探索市场（唯一市场页）的数据中枢：
 * 技能 / MCP / 模型能力来自 Settings，卡片市场全部操作走 [KhatKitToolProvider]。
 */
@HiltViewModel
class ExploreMarketViewModel @Inject constructor(
    /** 结果文案一律走资源：VM 不持有任何中文字面量（doc §9）。 */
    private val appContext: Application,
    private val settingsStore: SettingsRepository,
    private val skillManager: SkillManager,
    private val mcpManager: McpManager,
    private val provider: KhatKitToolProvider,
) : ViewModel() {

    val settings = settingsStore.settingsFlow

    val mcpStatus: StateFlow<Map<Uuid, McpStatus>> = mcpManager.syncingStatus

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills: StateFlow<List<SkillMetadata>> = _skills.asStateFlow()

    private val _cards = MutableStateFlow<List<CardIndexEntry>>(emptyList())
    val cards: StateFlow<List<CardIndexEntry>> = _cards.asStateFlow()

    private val _installed = MutableStateFlow<Map<String, String>>(emptyMap())
    val installed: StateFlow<Map<String, String>> = _installed.asStateFlow()

    private val _triggers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val triggers: StateFlow<Map<String, List<String>>> = _triggers.asStateFlow()

    private val _hubInfo = MutableStateFlow<Map<String, HubCardMarketInfo>>(emptyMap())
    val hubInfo: StateFlow<Map<String, HubCardMarketInfo>> = _hubInfo.asStateFlow()

    private val _activated = MutableStateFlow(false)
    val activated: StateFlow<Boolean> = _activated.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busyCard = MutableStateFlow<String?>(null)
    val busyCard: StateFlow<String?> = _busyCard.asStateFlow()

    private val _dependencies = MutableStateFlow<List<DependencyCacheEntry>>(emptyList())
    val dependencies: StateFlow<List<DependencyCacheEntry>> = _dependencies.asStateFlow()

    /** 已安装卡片声明的依赖包（name -> 声明的版本），含尚未下载的。 */
    private val _declaredDependencies = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val declaredDependencies: StateFlow<Map<String, List<String>>> = _declaredDependencies.asStateFlow()

    /** 资源市场目录：技能合集 / MCP 服务 / 合集包。 */
    private val _resources = MutableStateFlow<List<HubResourceEntry>>(emptyList())
    val resources: StateFlow<List<HubResourceEntry>> = _resources.asStateFlow()

    private val _busyResource = MutableStateFlow<String?>(null)
    val busyResource: StateFlow<String?> = _busyResource.asStateFlow()

    // OCR2：已安装 OCR 原生插件（市场条目据此显示「已安装/更新/卸载」）。
    private val _ocrPlugin = MutableStateFlow<OcrPluginRecord?>(null)
    val ocrPlugin: StateFlow<OcrPluginRecord?> = _ocrPlugin.asStateFlow()

    // OCR4：市场扩展/依赖包条目（/api/items?kind=dependency），由 hub 客户端拉取后映射成资源行。
    private val _extensionItems = MutableStateFlow<List<HubResourceEntry>>(emptyList())
    val extensionItems: StateFlow<List<HubResourceEntry>> = _extensionItems.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            OcrPluginLoader.prepare(appContext)
            _ocrPlugin.value = OcrPluginStore(appContext).read()
        }
        refreshExtensionItems()
    }

    /**
     * OCR4：拉取 kind=dependency 的扩展条目（OCR 原生插件等）。
     *
     * URL：GET /api/items?kind=dependency&app_version=<客户端版本>（hub 客户端 extensionItems）。
     * 空列表 / 断网 / 老服务端没有该接口 → 保持空，市场里整组不渲染，其它分组照常（降级）。
     */
    fun refreshExtensionItems() = viewModelScope.launch(Dispatchers.IO) {
        val appVersion = runCatching { heizige.kk.khatkit.app.BuildConfig.VERSION_NAME }.getOrDefault("")
        val items = runCatching {
            HubClient(provider.hubBaseUrl.trim().trimEnd('/'))
                .extensionItems(kind = "dependency", appVersion = appVersion)
                .filter { it.status.isBlank() || it.status == "approved" }
                .map { summary ->
                    HubResourceEntry(
                        slug = summary.id.ifBlank { summary.name },
                        kind = "dependency",
                        name = summary.name.ifBlank { summary.slug },
                        description = summary.description.toString(),
                        version = summary.version,
                        icon = "dns",
                        itemCount = 1,
                        // /api/items 不直接给总大小，按 files[].size 求和（缺字段按 0）。
                        sizeBytes = summary.files.sumOf { el ->
                            (el as? kotlinx.serialization.json.JsonObject)
                                ?.get("size")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
                                ?: 0L
                        },
                        featured = summary.preferred,
                    )
                }
        }.getOrDefault(emptyList())
        _extensionItems.value = items
    }

    /** 市场里 kind=dependency 且 plugin_id=ocr 的条目：下载 zip → sha256 校验 → 安装。 */
    fun installOcrPlugin(entry: HubResourceEntry, onResult: (String) -> Unit = {}) =
        viewModelScope.launch {
            _busyResource.value = entry.slug
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val base = provider.hubBaseUrl.trim().trimEnd('/')
                    require(base.isNotEmpty()) { "Hub 未配置" }
                    val url = java.net.URL(base + "/api/items/" + entry.slug + "/download")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 60_000
                    val tmp = java.io.File(appContext.cacheDir, "ocr_plugin_" + System.currentTimeMillis() + ".zip")
                    try {
                        conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                        val sha = conn.getHeaderField("X-Content-SHA256")
                        OcrPluginInstaller.install(appContext, tmp, sha, entry.slug).getOrThrow()
                    } finally {
                        tmp.delete()
                    }
                }.fold(
                    onSuccess = { "OCR 插件已安装 " + it.version },
                    onFailure = { "OCR 插件安装失败：" + (it.message ?: it.javaClass.simpleName) },
                )
            }
            _ocrPlugin.value = OcrPluginStore(appContext).read()
            _busyResource.value = null
            onResult(message)
        }

    /** 卸载 OCR 插件（删目录 + 清 DataStore + 清加载器缓存）。 */
    fun uninstallOcrPlugin(onResult: (String) -> Unit = {}) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { OcrPluginInstaller.uninstall(appContext) }
        _ocrPlugin.value = OcrPluginStore(appContext).read()
        onResult(if (ok) "OCR 插件已卸载" else "OCR 插件卸载失败")
    }

    init {
        refresh("")
    }

    fun refresh(query: String = "") {
        viewModelScope.launch {
            _loading.value = true
            withContext(Dispatchers.IO) {
                _skills.value = runCatching { skillManager.listSkills() }.getOrDefault(emptyList())
                _cards.value = runCatching { provider.searchCards(query, limit = 50) }.getOrDefault(emptyList())
                reloadInstalled()
                reloadDependencies()
                runCatching { provider.hubMarketCards() }
                    .onSuccess { list -> _hubInfo.value = list.associateBy { it.name } }
                runCatching { provider.hubResources() }
                    .onSuccess { list -> _resources.value = list }
                runCatching { provider.hubAccountStatus(refresh = false) }
                    .onSuccess { _activated.value = it.activated }
            }
            _loading.value = false
        }
    }

    private suspend fun reloadInstalled() {
        runCatching { provider.installedCardVersions() }
            .onSuccess { _installed.value = it }
        runCatching { provider.installedCardTriggers() }
            .onSuccess { _triggers.value = it }
    }

    private suspend fun reloadDependencies() {
        _dependencies.value = runCatching { provider.dependencyCacheEntries() }.getOrDefault(emptyList())
        _declaredDependencies.value = runCatching { provider.installedCardDependencies() }.getOrDefault(emptyMap())
    }

    fun deleteDependency(entry: DependencyCacheEntry, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = provider.deleteDependency(entry.name, entry.version)
        reloadDependencies()
        onResult(ok)
    }

    fun clearDependencyCache(onResult: (Int) -> Unit = {}) = viewModelScope.launch {
        val count = provider.clearDependencyCache()
        reloadDependencies()
        onResult(count)
    }

    private fun cardAction(name: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _busyCard.value = name
            runCatching { action() }
            _busyCard.value = null
            // 安装/卸载会改变「哪张卡存在」，声明的依赖清单跟着一起刷，否则本地页会留着已卸载卡片的依赖。
            withContext(Dispatchers.IO) {
                reloadInstalled()
                reloadDependencies()
            }
        }
    }

    fun install(entry: CardIndexEntry, onResult: (Boolean) -> Unit = {}) =
        cardAction(entry.name) { onResult(provider.installCard(entry)) }

    fun update(entry: CardIndexEntry, onResult: (Boolean) -> Unit = {}) =
        cardAction(entry.name) { onResult(provider.installCard(entry, force = true)) }

    fun uninstall(name: String, onResult: (Boolean) -> Unit = {}) =
        cardAction(name) { onResult(provider.uninstallCard(name)) }

    fun run(name: String, onResult: (CardRunResult) -> Unit = {}) {
        _busyCard.value = name
        provider.startCard(name) { result ->
            _busyCard.value = null
            onResult(result)
            // Refresh only the view model's derived lists; the actual run is
            // owned by AppScope and continues after this page is closed.
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    reloadInstalled()
                    reloadDependencies()
                }
            }
        }
    }

    fun vote(name: String, onResult: (HubActionResult) -> Unit = {}) =
        cardAction(name) { onResult(provider.voteCard(name)) }

    fun publish(
        name: String,
        license: String,
        price: Double,
        changelog: String,
        onResult: (HubActionResult) -> Unit = {},
    ) = cardAction(name) { onResult(provider.publishCard(name, license, price, changelog)) }

    fun fork(
        name: String,
        parentVersion: String,
        changelog: String,
        newVersion: String,
        onResult: (HubActionResult) -> Unit = {},
    ) = cardAction(name) { onResult(provider.forkCard(name, parentVersion, changelog, newVersion)) }

    fun listCardSecrets(name: String): List<String> = provider.listCardSecrets(name)

    fun removeCardSecret(cardName: String, key: String) = provider.removeCardSecret(cardName, key)

    var hubBaseUrl: String
        get() = provider.hubBaseUrl
        set(value) {
            provider.hubBaseUrl = value
        }

    var enableRoot: Boolean
        get() = provider.enableRoot
        set(value) {
            provider.enableRoot = value
        }

    var downloadConcurrency: Int
        get() = provider.downloadConcurrency
        set(value) {
            provider.downloadConcurrency = value
        }

    var receiveBeta: Boolean
        get() = provider.receiveBeta
        set(value) {
            provider.receiveBeta = value
        }


    // ===== 资源市场：下载并落地技能合集 / MCP 服务 / 合集包 =====

    /**
     * 安装一个资源包。落地策略是「装完即用」：
     * - 技能写进用户技能目录，并加进**当前助手**的 [Assistant.enabledSkills]；
     * - MCP 服务写进全局设置，并挂到当前助手的启用列表上；
     * - 附带的卡片走原有卡片通道下载。
     * 只写盘/写设置、不替用户勾选的话，用户还得自己进设置再点一遍才算装上。
     */
    fun installResource(entry: HubResourceEntry, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        // OCR2：原生插件包（kind=dependency）走安装器，不再当成技能/MCP 合集解析。
        if (entry.kind == "dependency") {
            installOcrPlugin(entry, onResult)
            return@launch
        }
        _busyResource.value = entry.slug
        val message = withContext(Dispatchers.IO) {
            val pkg = provider.downloadHubResource(entry.slug)
            if (pkg == null) {
                appContext.getString(R.string.explore_market_resource_download_failed, entry.name)
            } else {
                applyResourcePackage(pkg)
            }
        }
        _busyResource.value = null
        withContext(Dispatchers.IO) {
            _skills.value = runCatching { skillManager.listSkills() }.getOrDefault(emptyList())
            reloadInstalled()
        }
        onResult(message)
    }

    private suspend fun applyResourcePackage(pkg: HubResourcePackage): String {
        val installedSkills = mutableListOf<String>()
        for (skill in pkg.skills) {
            if (skill.name.isBlank() || skill.files.isEmpty()) continue
            val files = skill.files.mapValues { (_, body) -> body.toByteArray() }
            if (skillManager.saveSkillFileBytesAtomically(skill.name, files)) {
                installedSkills += skill.name
            }
        }

        val addedServers = mutableListOf<McpServerConfig>()
        val pendingServers = mutableListOf<McpServerConfig>()
        if (pkg.mcpServers.isNotEmpty()) {
            settingsStore.update { settings ->
                var next = settings
                for (server in pkg.mcpServers) {
                    if (server.url.isBlank()) continue
                    // 同 URL 已在设置里就不重复落一份（也不改用户已填的值）
                    if (next.mcpServers.any { it.serverUrl == server.url }) continue
                    // P0-5：缺必填时只保存候选配置（enable=false），不写入当前助手
                    val plan = planMcpInstall(server)
                    if (plan.enable) addedServers += plan.config else pendingServers += plan.config
                    next = applyMcpInstallDecision(next, plan)
                }
                next
            }
        }

        if (installedSkills.isNotEmpty()) {
            val assistantId = settingsStore.settingsFlow.value.assistantId
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id != assistantId) {
                            assistant
                        } else {
                            assistant.copy(enabledSkills = assistant.enabledSkills + installedSkills)
                        }
                    }
                )
            }
        }

        var installedCards = 0
        for (slug in pkg.cards) {
            val card = cards.value.firstOrNull { it.name == slug } ?: continue
            if (provider.installCard(card)) installedCards++
        }

        val listSeparator = appContext.getString(R.string.explore_market_resource_list_separator)
        val parts = buildList {
            if (installedSkills.isNotEmpty()) {
                add(appContext.getString(R.string.explore_market_resource_summary_skills, installedSkills.size))
            }
            if (addedServers.isNotEmpty()) {
                add(appContext.getString(R.string.explore_market_resource_summary_mcp_servers, addedServers.size))
            }
            if (installedCards > 0) {
                add(appContext.getString(R.string.explore_market_resource_summary_cards, installedCards))
            }
        }
        // 缺必填的 MCP 只落了候选配置：提示里点名缺哪些 key，且摘要一律脱敏
        if (pendingServers.isNotEmpty()) {
            val missing = pendingServers.flatMap { mcpMissingRequiredKeys(it) }.distinct()
            val masked = pendingServers.first().let { maskedMcpSummary(it) }
            return if (parts.isEmpty()) {
                appContext.getString(
                    R.string.explore_market_resource_saved_needs_config,
                    pkg.resource.name,
                    missing.joinToString(listSeparator),
                    masked,
                )
            } else {
                appContext.getString(
                    R.string.explore_market_resource_installed_needs_config,
                    pkg.resource.name,
                    parts.joinToString(listSeparator),
                    pendingServers.size,
                    missing.joinToString(listSeparator),
                    masked,
                )
            }
        }
        return if (parts.isEmpty()) {
            appContext.getString(R.string.explore_market_resource_installed, pkg.resource.name)
        } else {
            appContext.getString(
                R.string.explore_market_resource_installed_parts,
                pkg.resource.name,
                parts.joinToString(listSeparator),
            )
        }
    }

    // ===== P0-5：条目级「配置」入口 =====

    /**
     * 拉取条目对应的资源包（与安装同一条下载路径），给出预填的 MCP 配置与缺失必填项。
     * 返回 null 表示条目里没有可配置的 MCP 端点（技能合集 / 下载失败）。
     */
    fun prepareMcpConfig(entry: HubResourceEntry, onResult: (McpInstallPlan?) -> Unit = {}) =
        viewModelScope.launch {
            _busyResource.value = entry.slug
            val plan = withContext(Dispatchers.IO) {
                provider.downloadHubResource(entry.slug)
                    ?.mcpServers
                    ?.firstOrNull { it.url.isNotBlank() }
                    ?.let { planMcpInstall(it) }
            }
            _busyResource.value = null
            onResult(plan)
        }

    /**
     * 保存条目级配置：缺必填则强制 `enable = false` 且**不写入当前助手**，返回提示文案。
     * 与安装路径共用 [applyMcpInstallDecision]，行为一致。
     */
    fun saveMcpServerConfig(config: McpServerConfig, onResult: (String) -> Unit = {}) =
        viewModelScope.launch {
            val plan = rePlanMcpInstall(config)
            withContext(Dispatchers.IO) {
                settingsStore.update { applyMcpInstallDecision(it, plan) }
                provider.applySettings()
            }
            onResult(
                if (plan.needsConfig) {
                    appContext.getString(
                        R.string.explore_market_mcp_config_candidate_saved,
                        plan.missingRequired.joinToString(
                            appContext.getString(R.string.explore_market_resource_list_separator),
                        ),
                    )
                } else {
                    appContext.getString(
                        R.string.explore_market_mcp_config_saved,
                        maskedMcpSummary(plan.config),
                    )
                }
            )
        }

    companion object {
        /**
         * 安装 / 保存 MCP 配置的唯一落盘入口（纯函数，单测直接调用）。
         *
         * - 配置按 `id` 或同一 `serverUrl` 覆盖，不重复追加；
         * - `plan.enable == true` 才把该 server 挂到当前助手；
         * - 缺必填（`plan.needsConfig`）时只保存候选配置，**助手 mcpServers 原样不动**。
         */
        internal fun applyMcpInstallDecision(settings: Settings, plan: McpInstallPlan): Settings {
            val incoming = plan.config
            val index = settings.mcpServers.indexOfFirst { existing ->
                existing.id == incoming.id ||
                    (incoming.serverUrl.isNotBlank() && existing.serverUrl == incoming.serverUrl)
            }
            val servers = if (index >= 0) {
                settings.mcpServers.toMutableList().also { it[index] = incoming }
            } else {
                settings.mcpServers + incoming
            }
            val assistants = if (plan.enable) {
                settings.assistants.map { assistant ->
                    if (assistant.id != settings.assistantId) {
                        assistant
                    } else {
                        assistant.copy(mcpServers = assistant.mcpServers + incoming.id)
                    }
                }
            } else {
                settings.assistants
            }
            return settings.copy(mcpServers = servers, assistants = assistants)
        }
    }

    fun applySettings() = provider.applySettings()
}
