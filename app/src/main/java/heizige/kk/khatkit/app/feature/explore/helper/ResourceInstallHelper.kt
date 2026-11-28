package heizige.kk.khatkit.app.feature.explore.helper

import android.content.Context
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubResourcePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 市场资源安装与设置写入；页面 busy/job/列表刷新仍由 ViewModel 持有。 */
internal class ResourceInstallHelper(
    private val context: Context,
    private val settingsStore: SettingsRepository,
    private val skillManager: SkillManager,
    private val provider: KhatKitToolProvider,
) {
    suspend fun applyResourcePackage(
        pkg: HubResourcePackage,
        availableCards: () -> List<CardIndexEntry>,
    ): String {
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
                    if (next.mcpServers.any { it.serverUrl == server.url }) continue
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
                        if (assistant.id != assistantId) assistant
                        else assistant.copy(enabledSkills = assistant.enabledSkills + installedSkills)
                    },
                )
            }
        }

        var installedCards = 0
        for (slug in pkg.cards) {
            val card = availableCards().firstOrNull { it.name == slug } ?: continue
            if (provider.installCard(card)) installedCards++
        }

        val listSeparator = context.getString(R.string.explore_market_resource_list_separator)
        val parts = buildList {
            if (installedSkills.isNotEmpty()) {
                add(context.getString(R.string.explore_market_resource_summary_skills, installedSkills.size))
            }
            if (addedServers.isNotEmpty()) {
                add(context.getString(R.string.explore_market_resource_summary_mcp_servers, addedServers.size))
            }
            if (installedCards > 0) {
                add(context.getString(R.string.explore_market_resource_summary_cards, installedCards))
            }
        }
        if (pendingServers.isNotEmpty()) {
            val missing = pendingServers.flatMap { mcpMissingRequiredKeys(it) }.distinct()
            val masked = maskedMcpSummary(pendingServers.first())
            return if (parts.isEmpty()) {
                context.getString(
                    R.string.explore_market_resource_saved_needs_config,
                    pkg.resource.name,
                    missing.joinToString(listSeparator),
                    masked,
                )
            } else {
                context.getString(
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
            context.getString(R.string.explore_market_resource_installed, pkg.resource.name)
        } else {
            context.getString(
                R.string.explore_market_resource_installed_parts,
                pkg.resource.name,
                parts.joinToString(listSeparator),
            )
        }
    }

    suspend fun saveMcpServerConfig(config: McpServerConfig): String {
        val plan = rePlanMcpInstall(config)
        withContext(Dispatchers.IO) {
            settingsStore.update { applyMcpInstallDecision(it, plan) }
            provider.applySettings()
        }
        return if (plan.needsConfig) {
            context.getString(
                R.string.explore_market_mcp_config_candidate_saved,
                plan.missingRequired.joinToString(
                    context.getString(R.string.explore_market_resource_list_separator),
                ),
            )
        } else {
            context.getString(
                R.string.explore_market_mcp_config_saved,
                maskedMcpSummary(plan.config),
            )
        }
    }
}
