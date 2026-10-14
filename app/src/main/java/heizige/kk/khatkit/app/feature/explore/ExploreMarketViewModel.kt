package heizige.kk.khatkit.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.core.data.ai.mcp.McpCommonOptions
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.hub.HubActionResult
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.hub.HubResourceEntry
import heizige.kk.khatkit.hub.HubResourceMcpServer
import heizige.kk.khatkit.hub.HubResourcePackage
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.uikit.CardRunResult
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

    /** 资源市场目录：技能合集 / MCP 服务 / 合集包。 */
    private val _resources = MutableStateFlow<List<HubResourceEntry>>(emptyList())
    val resources: StateFlow<List<HubResourceEntry>> = _resources.asStateFlow()

    private val _busyResource = MutableStateFlow<String?>(null)
    val busyResource: StateFlow<String?> = _busyResource.asStateFlow()

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
            withContext(Dispatchers.IO) { reloadInstalled() }
        }
    }

    fun install(entry: CardIndexEntry, onResult: (Boolean) -> Unit = {}) =
        cardAction(entry.name) { onResult(provider.installCard(entry)) }

    fun update(entry: CardIndexEntry, onResult: (Boolean) -> Unit = {}) =
        cardAction(entry.name) { onResult(provider.installCard(entry, force = true)) }

    fun uninstall(name: String, onResult: (Boolean) -> Unit = {}) =
        cardAction(name) { onResult(provider.uninstallCard(name)) }

    fun run(name: String, onResult: (CardRunResult) -> Unit = {}) =
        cardAction(name) { onResult(provider.runCard(name)) }

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

    var uiStyle: heizige.kk.khatkit.uikit.KhatKitUiStyle
        get() = provider.uiStyle
        set(value) {
            provider.uiStyle = value
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
        _busyResource.value = entry.slug
        val message = withContext(Dispatchers.IO) {
            val pkg = provider.downloadHubResource(entry.slug)
            if (pkg == null) "下载失败：${entry.name}" else applyResourcePackage(pkg)
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
        if (pkg.mcpServers.isNotEmpty()) {
            settingsStore.update { settings ->
                val known = settings.mcpServers.mapNotNull { it.serverUrl }.toSet()
                addedServers += pkg.mcpServers
                    .filter { it.url.isNotBlank() && it.url !in known }
                    .map { it.toMcpConfig() }
                settings.copy(mcpServers = settings.mcpServers + addedServers)
            }
        }

        if (installedSkills.isNotEmpty() || addedServers.isNotEmpty()) {
            val assistantId = settingsStore.settingsFlow.value.assistantId
            val serverIds = addedServers.map { it.id }.toSet()
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id != assistantId) {
                            assistant
                        } else {
                            assistant.copy(
                                enabledSkills = assistant.enabledSkills + installedSkills,
                                mcpServers = assistant.mcpServers + serverIds,
                            )
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

        val parts = buildList {
            if (installedSkills.isNotEmpty()) add("${installedSkills.size} 个技能")
            if (addedServers.isNotEmpty()) add("${addedServers.size} 个 MCP 服务")
            if (installedCards > 0) add("$installedCards 张卡片")
        }
        return if (parts.isEmpty()) {
            "已安装 ${pkg.resource.name}"
        } else {
            "已安装 ${pkg.resource.name}：${parts.joinToString("、")}"
        }
    }

    private fun HubResourceMcpServer.toMcpConfig(): McpServerConfig {
        val options = McpCommonOptions(
            enable = true,
            name = name.ifBlank { url },
            headers = headers.mapNotNull { row -> row.takeIf { it.size >= 2 }?.let { it[0] to it[1] } },
        )
        return if (transport.equals("sse", ignoreCase = true)) {
            McpServerConfig.SseTransportServer(commonOptions = options, url = url)
        } else {
            McpServerConfig.StreamableHTTPServer(commonOptions = options, url = url)
        }
    }

    fun applySettings() = provider.applySettings()
}
