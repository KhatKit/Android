package heizige.kk.khatkit.app.data.ai.tools

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import heizige.kk.khatkit.bridge.impl.BridgeFactory
import heizige.kk.khatkit.bridge.impl.DownloadPolicy
import heizige.kk.khatkit.bridge.impl.FileStoreBridge
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.BuiltinCards
import heizige.kk.khatkit.hub.BundledLibProvider
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.hub.HubLibProvider
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiBridgeHost
import heizige.kk.khatkit.uikit.KhatKitController
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.web.McpToolDescriptor
import heizige.kk.khatkit.web.McpToolResult
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart
import java.io.File

/**
 * 卡片工具描述：可能来自本地已安装（[card]），也可能只在 Hub 索引里（[entry]）。
 * 云端卡片在真正被调用时才下载/更新，之后命中本地缓存不再重复下载。
 */
private data class CardToolSpec(
    val name: String,
    val entry: CardIndexEntry?,
    val card: LoadedCard?,
)

private fun CardToolSpec.descriptionText(): String =
    entry?.summary?.takeIf { it.isNotBlank() }
        ?: entry?.description?.takeIf { it.isNotBlank() }
        ?: card?.manifest?.description?.takeIf { it.isNotBlank() }
        ?: name

private fun CardToolSpec.inputSchema(): InputSchema? {
    // 未下载时用索引里的 schema，保证 AI 仍能正确填参
    val parameters = entry?.parameters?.takeIf { it.isNotEmpty() }
        ?: card?.manifest?.parameters
        ?: return null
    val properties = parameters["properties"] as? JsonObject ?: return null
    val required = (parameters["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return InputSchema.Obj(properties, required)
}

/** 卡片 manifest 的 parameters 已是标准 JSON Schema，直接转成宿主 InputSchema。 */
fun manifestInputSchema(manifest: CardManifest): InputSchema? {
    val properties = manifest.parameters["properties"] as? JsonObject ?: return null
    val required = (manifest.parameters["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return InputSchema.Obj(properties, required)
}

/**
 * 卡片工具提供者：组装六个 bridge、加载本机已安装卡片、暴露成 tool。
 *
 * - bridge 能力协商在 [BridgeFactory] 完成（缺 shizuku/root 时对应卡片不可见）
 * - ui bridge 的请求通过 [uiRequest] 交给 Compose 层渲染
 */
class KhatKitToolProvider(
    context: Context,
    private val scope: CoroutineScope,
    private val json: Json,
) : KhatKitController {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "khatkit").apply { mkdirs() }
    private val cache = CardCache(rootDir)
    private val uiHost = UiBridgeHost()
    private val initMutex = Mutex()
    private val settings = appContext.getSharedPreferences("khatkit_settings", Context.MODE_PRIVATE)

    @Volatile
    private var executor: CardExecutor? = null

    @Volatile
    private var hubClient: HubClient? = null

    @Volatile
    private var indexCache: List<CardIndexEntry> = emptyList()

    @Volatile
    private var indexFetchedAt: Long = 0L
    private val indexMutex = Mutex()

    override val uiRequest = uiHost.request
    override val uiProgress = uiHost.progress

    /** 卡片 Hub 地址（设置页可改）。 */
    override var hubBaseUrl: String
        get() = settings.getString(KEY_HUB_URL, DEFAULT_HUB_BASE_URL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_HUB_BASE_URL
        set(value) {
            settings.edit().putString(KEY_HUB_URL, value.trim().ifBlank { DEFAULT_HUB_BASE_URL }).apply()
        }

    /** 是否注入 root bridge（默认关，L2 高风险）。 */
    override var enableRoot: Boolean
        get() = settings.getBoolean(KEY_ENABLE_ROOT, false)
        set(value) = settings.edit().putBoolean(KEY_ENABLE_ROOT, value).apply()

    /** 下载并发上限（策略层，脚本不能覆盖）。 */
    override var downloadConcurrency: Int
        get() = settings.getInt(KEY_DOWNLOAD_CONCURRENCY, 3).coerceIn(1, 5)
        set(value) = settings.edit().putInt(KEY_DOWNLOAD_CONCURRENCY, value.coerceIn(1, 5)).apply()

    /** KhatKit 自带界面的风格（MD3Exp / Miuix），见 :khatkit-ui。 */
    private val uiStyleState = mutableStateOf(
        runCatching {
            KhatKitUiStyle.valueOf(settings.getString(KEY_UI_STYLE, null) ?: "")
        }.getOrDefault(KhatKitUiStyle.MATERIAL)
    )

    override var uiStyle: KhatKitUiStyle
        get() = uiStyleState.value
        set(value) {
            uiStyleState.value = value
            settings.edit().putString(KEY_UI_STYLE, value.name).apply()
        }

    /** 是否接收 beta 更新通道（默认关）。 */
    override var receiveBeta: Boolean
        get() = settings.getBoolean(KEY_RECEIVE_BETA, false)
        set(value) = settings.edit().putBoolean(KEY_RECEIVE_BETA, value).apply()

    /** 设置变更后重建 bridge/客户端（下次 tasks() 生效）。 */
    override fun applySettings() {
        executor = null
        hubClient = null
        indexCache = emptyList()
        indexFetchedAt = 0L
    }

    private suspend fun executor(): CardExecutor {
        executor?.let { return it }
        return initMutex.withLock {
            executor ?: BridgeFactory.create(
                context = appContext,
                scope = scope,
                ui = uiHost,
                enableRoot = enableRoot,
                policy = DownloadPolicy(maxConcurrent = downloadConcurrency),
                libResolver = LibResolver(
                    listOf(
                        BundledLibProvider(appContext),
                        HubLibProvider(hub(), cache),
                    )
                ),
            ).also { executor = it }
        }
    }

    /** 卡片运行状态机：并发上限、状态、取消统一由它管。 */
    val runManager: CardRunManager by lazy {
        CardRunManager(scope = scope) { card, args -> executor().execute(card, args) }
    }

    suspend fun tools(): List<Tool> = withContext(Dispatchers.IO) {
        // 云端优先：索引里有什么，AI 就能看到什么；真正被调用时才下载。
        val remote = remoteCards()
        var installed = loadInstalledCards()
        if (installed.isEmpty() && remote.isEmpty()) {
            // Hub 不可达且本地为空时的离线兜底
            runCatching { BuiltinCards.install(appContext, cache) }
            installed = loadInstalledCards()
        }
        val installedNames = installed.mapTo(mutableSetOf()) { it.manifest.name }
        val specs = buildList {
            installed.forEach { card ->
                add(
                    CardToolSpec(
                        name = card.manifest.name,
                        entry = remote.firstOrNull { it.name == card.manifest.name },
                        card = card,
                    )
                )
            }
            remote.filterNot { it.name in installedNames }.forEach { entry ->
                add(CardToolSpec(name = entry.name, entry = entry, card = null))
            }
        }
        specs.map { spec -> spec.toTool() }
    }

    /** 索引缓存：TTL 内不重复请求；请求失败时保留旧缓存。 */
    private suspend fun remoteCards(force: Boolean = false): List<CardIndexEntry> {
        if (!force && System.currentTimeMillis() - indexFetchedAt < INDEX_TTL_MS && indexCache.isNotEmpty()) {
            return indexCache
        }
        return indexMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - indexFetchedAt < INDEX_TTL_MS && indexCache.isNotEmpty()) {
                return@withLock indexCache
            }
            val fetched = runCatching {
                hub().search("", capabilities(), limit = 200).cards
            }.getOrDefault(emptyList())
            if (fetched.isNotEmpty() || indexCache.isEmpty()) {
                indexCache = fetched
                indexFetchedAt = System.currentTimeMillis()
            }
            indexCache
        }
    }

    /** 用到时才下载：没下过→下载一次；有新版→更新一次；否则直接读本地缓存。 */
    private suspend fun resolveCard(spec: CardToolSpec): LoadedCard? = withContext(Dispatchers.IO) {
        val entry = spec.entry ?: remoteCards().firstOrNull { it.name == spec.name }
            ?: return@withContext spec.card
        val installedVersion = spec.card?.manifest?.version ?: cache.installedVersions()[spec.name]
        val needsUpdate = installedVersion != null && installedVersion != entry.version
        runCatching {
            cache.load(cache.ensure(entry, hub(), force = needsUpdate))
        }.getOrNull() ?: spec.card
    }

    private fun CardToolSpec.toTool(): Tool = Tool(
        name = "khatkit__$name",
        description = descriptionText(),
        parameters = { inputSchema() },
        execute = { args ->
            val arguments = jsonToMap(args)
            // 有状态机就统一走它：并发上限、状态、取消都在宿主侧。
            // 任何异常都收敛成 error 文本，不能让卡片问题打断整轮生成。
            val result = try {
                val card = resolveCard(this)
                if (card == null) {
                    EngineResult.Err("CARD_UNAVAILABLE", "卡片未下载且 Hub 不可达：$name")
                } else {
                    runManager.run(card, arguments)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                EngineResult.Err("CARD_TOOL", e.message ?: e.toString())
            }
            when (result) {
                is EngineResult.Ok -> listOf(UIMessagePart.Text(encode(result.value)))
                is EngineResult.Err -> listOf(UIMessagePart.Text("""{"error":"${result.message.escape()}"}"""))
            }
        },
    )

    override fun submitForm(values: Map<String, Any?>?) = uiHost.submitForm(values)

    override fun answerConfirm(confirmed: Boolean) = uiHost.answerConfirm(confirmed)

    override fun dismissUi() = uiHost.dismiss()

    /** 本机当前具备的 bridge 能力，用于 Hub 硬过滤。 */
    suspend fun capabilities(): Set<String> = executor().bridges.availableBridges()

    /** 语义召回候选卡片（服务端 embedding）；失败时返回空表。 */
    override suspend fun searchCards(query: String, limit: Int): List<CardIndexEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                hub().search(query, capabilities(), limit).cards
            }.getOrDefault(emptyList())
        }

    /** 按需下载并安装卡片；force=true 用于更新覆盖。安装后下一次 [tools] 即可被 AI 调用。 */
    override suspend fun installCard(entry: CardIndexEntry, force: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                cache.ensure(entry, hub(), force)
                true
            }.getOrDefault(false)
        }

    /** 卸载卡片（删掉其所有版本目录）。 */
    override suspend fun uninstallCard(name: String): Boolean = withContext(Dispatchers.IO) {
        cache.uninstall(name)
    }

    /** 已安装卡片 name -> version，市场页据此显示"更新"。 */
    override suspend fun installedCardVersions(): Map<String, String> = withContext(Dispatchers.IO) {
        cache.installedVersions()
    }

    /** 把「会话存成卡片」生成的卡片写成本地已安装卡片。 */
    suspend fun saveGeneratedCard(card: GeneratedCard): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = cache.cardDir(card.name, card.version)
            dir.mkdirs()
            File(dir, "card.json").writeText(card.manifestJson)
            File(dir, "main.js").writeText(card.scriptText)
            true
        }.getOrDefault(false)
    }

    /** 该卡片存了哪些密钥（设计文档 7.4：可展示、可撤销）。 */
    override fun listCardSecrets(cardName: String): List<String> =
        FileStoreBridge(appContext, cardName).secretList()

    override fun removeCardSecret(cardName: String, key: String) {
        FileStoreBridge(appContext, cardName).secretRemove(key)
    }

    /** MCP 出口：已安装卡片 → MCP tool 描述。 */
    suspend fun mcpToolDescriptors(): List<McpToolDescriptor> = withContext(Dispatchers.IO) {
        tools().map { tool ->
            McpToolDescriptor(
                name = tool.name,
                description = tool.description,
                inputSchema = inputSchemaJson(tool.parameters()),
            )
        }
    }

    /** MCP 出口：执行卡片并把文本结果收敛成 MCP content。 */
    suspend fun callMcpTool(name: String, arguments: JsonObject): McpToolResult =
        withContext(Dispatchers.IO) {
            val tool = tools().firstOrNull { it.name == name }
                ?: return@withContext McpToolResult("unknown tool: $name", isError = true)
            runCatching {
                val parts = tool.execute(arguments)
                McpToolResult(parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text })
            }.getOrElse { McpToolResult(it.message ?: "tool call failed", isError = true) }
        }

    private fun inputSchemaJson(schema: InputSchema?): JsonObject = when (schema) {
        is InputSchema.Obj -> buildJsonObject {
            put("type", "object")
            put("properties", schema.properties)
            schema.required?.let { required ->
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }
        }

        null -> buildJsonObject { put("type", "object") }
    }

    private fun hub(): HubClient =
        hubClient ?: synchronized(this) {
            hubClient ?: HubClient(hubBaseUrl).also { hubClient = it }
        }

    private fun loadInstalledCards(): List<LoadedCard> =
        cache.installedVersions().mapNotNull { (name, version) ->
            runCatching { cache.load(cache.cardDir(name, version)) }.getOrNull()
        }

    companion object {
        /** 卡片 Hub 默认指向 KodeHeadServer；可在卡片市场设置里改。 */
        const val DEFAULT_HUB_BASE_URL = "https://heizige.space"
        private const val KEY_HUB_URL = "hub_base_url"
        private const val KEY_ENABLE_ROOT = "enable_root"
        private const val KEY_DOWNLOAD_CONCURRENCY = "download_concurrency"
        private const val KEY_UI_STYLE = "ui_style"
        private const val KEY_RECEIVE_BETA = "receive_beta"

        /** 索引缓存有效期：5 分钟内不重复拉取 */
        private const val INDEX_TTL_MS = 5 * 60 * 1000L
    }
}

private fun jsonToMap(element: JsonElement): Map<String, Any?> =
    (element as? JsonObject)?.mapValues { fromElement(it.value) } ?: emptyMap()

private fun fromElement(element: JsonElement): Any? = when (element) {
    JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" || element.content == "false" -> element.content.toBoolean()
        element.content.contains('.') -> element.content.toDoubleOrNull()
        else -> element.content.toLongOrNull() ?: element.content
    }
    is JsonArray -> element.map { fromElement(it) }
    is JsonObject -> element.mapValues { fromElement(it.value) }
}

private fun encode(value: Any?): String =
    Json.encodeToString(JsonElement.serializer(), toElement(value))

private fun toElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toElement(v) })
    is Iterable<*> -> JsonArray(value.map { toElement(it) })
    else -> JsonPrimitive(value.toString())
}

private fun String.escape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
