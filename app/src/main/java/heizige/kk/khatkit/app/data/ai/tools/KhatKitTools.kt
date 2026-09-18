package heizige.kk.khatkit.app.data.ai.tools

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AndroidToolBridge
import heizige.kk.khatkit.bridge.impl.BridgeFactory
import heizige.kk.khatkit.bridge.impl.DownloadPolicy
import heizige.kk.khatkit.bridge.impl.FileStoreBridge
import heizige.kk.khatkit.app.automation.AutomationBus
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.BuiltinCards
import heizige.kk.khatkit.hub.BundledLibProvider
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.hub.HubFilters
import heizige.kk.khatkit.hub.HubLibProvider
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiBridgeHost
import heizige.kk.khatkit.uikit.CardRunResult
import heizige.kk.khatkit.uikit.KhatKitController
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

private fun CardToolSpec.supportsAi(): Boolean =
    card?.manifest?.supportsAi()
        ?: entry?.triggers?.contains(CardManifest.TRIGGER_AI)
        ?: false

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
    private val uiHost = UiBridgeHost(
        onAutomationStatus = { label, detail -> AutomationBus.update(label, detail) },
        onCancelled = { AutomationBus.isCancelRequested() },
    )
    private val initMutex = Mutex()
    private val settings = appContext.getSharedPreferences("khatkit_settings", Context.MODE_PRIVATE)

    /** OCR 走 tool bridge；只有 device_screen 需要时才懒加载，避免多养一个 HttpClient。 */
    private val ocrBridge by lazy { AndroidToolBridge(appContext, HttpClient(CIO.create())) }

    @Volatile
    private var executor: CardExecutor? = null

    @Volatile
    private var builtinsInstalled = false

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

    /** 放手模式：高权限/高风险卡片不再逐条审批，全部交给 AI。 */
    var handsOffMode: Boolean
        get() = settings.getBoolean(KEY_HANDS_OFF, false)
        set(value) = settings.edit().putBoolean(KEY_HANDS_OFF, value).apply()

    /** 是否接收 beta 更新通道（默认关）。 */
    override var receiveBeta: Boolean
        get() = settings.getBoolean(KEY_RECEIVE_BETA, false)
        set(value) = settings.edit().putBoolean(KEY_RECEIVE_BETA, value).apply()

    /** 下载中心任务（含脚本创建的下载）。 */
    val downloadTasks = MutableStateFlow<List<DownloadTaskInfo>>(emptyList())
    private var downloadBindJob: Job? = null

    /** 设置变更后重建 bridge/客户端（下次 tasks() 生效）。 */
    override fun applySettings() {
        executor = null
        hubClient = null
        indexCache = emptyList()
        indexFetchedAt = 0L
        downloadBindJob?.cancel()
        downloadTasks.value = emptyList()
    }

    private fun bindDownloadCenter(cardExecutor: CardExecutor) {
        val bridge = cardExecutor.bridges.downloadBridge() ?: return
        downloadBindJob?.cancel()
        downloadBindJob = scope.launch {
            bridge.observeTasks().collect { downloadTasks.value = it }
        }
    }

    /** 进入下载中心时确保下载管理器已创建并绑定任务流。 */
    suspend fun ensureDownloadsBound() {
        executor()
    }

    suspend fun pauseDownload(id: String) {
        executor().bridges.downloadBridge()?.pause(id)
    }

    suspend fun resumeDownload(id: String) {
        executor().bridges.downloadBridge()?.resume(id)
    }

    suspend fun cancelDownload(id: String) {
        executor().bridges.downloadBridge()?.cancel(id)
    }

    suspend fun removeDownload(id: String) {
        executor().bridges.downloadBridge()?.remove(id)
    }

    /** 供更新包等宿主功能下单，任务会出现在下载中心。 */
    fun enqueueDownloadAsync(url: String, name: String) {
        scope.launch {
            runCatching {
                executor().bridges.downloadBridge()?.start(
                    mapOf("url" to url, "name" to name)
                )
            }
        }
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
            ).also {
                executor = it
                bindDownloadCenter(it)
            }
        }
    }

    /** 卡片运行状态机：并发上限、状态、取消统一由它管。 */
    val runManager: CardRunManager by lazy {
        CardRunManager(scope = scope) { card, args -> executor().execute(card, args) }
    }

    /**
     * 带悬浮看板的卡片执行：运行期间发布「正在运行卡片：<name>」，结束（含失败）后进入完成态。
     * AI tool / 用户手动 / 事件触发三个入口都汇聚到这里；用户已请求停止时不再开新运行。
     * 执行前通过 [AutomationBus.requestApproval] 在看板上请求用户授权（放手模式或已授权则跳过）。
     * [AutomationBus.begin] 持有会话：卡片执行期间即使长时间没有进度更新，看板也不会误判会话结束。
     */
    suspend fun runCardWithStatus(
        card: LoadedCard,
        args: Map<String, Any?>,
        trigger: String = "AI",
    ): EngineResult {
        if (AutomationBus.isCancelRequested()) {
            AutomationBus.finish()
            return EngineResult.Err("CARD_CANCELLED", "用户已停止自动化")
        }
        AutomationBus.begin()
        AutomationBus.update("正在运行卡片：${card.manifest.name}")
        if (!AutomationBus.requestApproval("运行卡片：${card.manifest.name}", "触发来源：$trigger")) {
            AutomationBus.finish()
            return EngineResult.Err("CARD_DENIED", "用户拒绝授权，已取消运行卡片：${card.manifest.name}")
        }
        return try {
            runManager.run(card, args)
        } finally {
            AutomationBus.finish()
        }
    }

    suspend fun tools(): List<Tool> = withContext(Dispatchers.IO) {
        // 内置卡片先落地（已存在的跳过），保证随版本新增的示例卡片可见
        if (!builtinsInstalled) {
            runCatching { BuiltinCards.install(appContext, cache) }
            builtinsInstalled = true
        }
        // 云端优先：索引里有什么，AI 就能看到什么；真正被调用时才下载。
        // 硬过滤（设计 9.1）：设备不具备的 bridge 对应卡片直接不可见。
        val remote = HubFilters.byCapability(remoteCards(), capabilities())
        val installed = loadInstalledCards()
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
        val cardTools = specs.filter { it.supportsAi() }.take(TOOL_CANDIDATE_LIMIT).map { spec -> spec.toTool() }
        // 无障碍可用时额外挂两个内置工具：看屏幕 + 直接动作，让纯文本模型也能驱动手机
        if (AccessibilityBridgeHolder.current() == null) {
            cardTools
        } else {
            cardTools + listOf(deviceScreenTool(), deviceActTool())
        }
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
            // Hub 不可达时最多等 3 秒，失败沿用旧缓存，避免阻塞首条消息
            val fetched = runCatching {
                withTimeoutOrNull(INDEX_FETCH_TIMEOUT_MS) {
                    hub().search("", capabilities(), limit = TOOL_CANDIDATE_LIMIT).cards
                }
            }.getOrNull().orEmpty()
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

    private fun CardToolSpec.toTool(): Tool {
        // 审批已统一移到自动化悬浮看板（runCardWithStatus），这里不再走会话内审批
        return Tool(
            name = "khatkit__$name",
            description = descriptionText(),
            parameters = { inputSchema() },
            execute = { args ->
                val arguments = jsonToMap(args)
                // 任何异常都收敛成 error 文本，不能让卡片问题打断整轮生成。
                val result = try {
                    val card = resolveCard(this)
                    when {
                        card == null ->
                            EngineResult.Err("CARD_UNAVAILABLE", "卡片未下载且 Hub 不可达：$name")
                        !card.manifest.supportsAi() ->
                            EngineResult.Err("TRIGGER_DENIED", "卡片未启用 ai 触发：$name")
                        else -> runCardWithStatus(card, arguments, trigger = "AI")
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
    }

    /** 内置工具：截屏 + OCR + 无障碍节点清单，给纯文本模型「看」当前屏幕。 */
    private fun deviceScreenTool(): Tool = Tool(
        name = "khatkit__device_screen",
        description = "读取当前手机屏幕并返回文本视图：截图 PNG 路径、截图 OCR 文本、当前窗口无障碍节点清单。" +
            "模型本身看不到图片，请依据 OCR 与节点信息判断界面内容，再用 khatkit__device_act 操作。" +
            "节点每行格式：文本 | 描述 | #viewId [类名] (左,上,右,下) @中心X,中心Y 标记，" +
            "标记 click=可点击、edit=可输入、scroll=可滚动、long=可长按。" +
            "include_ocr / include_nodes 默认 true，可按需关闭以减少输出。" +
            "注意：截图可能包含 KhatKit 的自动化悬浮看板；若看板遮挡了要读取的内容，" +
            "可先调用 khatkit__device_act 的 overlay_hide 临时隐藏看板（到时自动恢复）。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("include_ocr", buildJsonObject {
                        put("type", "boolean")
                        put("description", "是否对截图做本地 OCR，默认 true。")
                    })
                    put("include_nodes", buildJsonObject {
                        put("type", "boolean")
                        put("description", "是否附带当前窗口节点清单，默认 true。")
                    })
                },
            )
        },
        execute = { args ->
            val params = jsonToMap(args)
            val includeOcr = params.bool("include_ocr", true)
            val includeNodes = params.bool("include_nodes", true)
            listOf(
                UIMessagePart.Text(
                    withContext(Dispatchers.IO) { captureDeviceScreen(includeOcr, includeNodes) }
                )
            )
        },
    )

    /** 内置工具：一步执行一个无障碍动作，省去为每步操作编写卡片。 */
    private fun deviceActTool(): Tool = Tool(
        name = "khatkit__device_act",
        description = "对当前手机界面执行一个无障碍动作，返回简短中文结果（已点击…/未找到…/已输入…）。" +
            "action 取值：click_text（按文本点击）、click_id（按 viewId 点击）、tap（坐标点击）、" +
            "swipe（坐标滑动）、press（坐标长按，duration_ms 默认 600）、set_text（写入 id 指定或首个可编辑输入框）、" +
            "back / home / recents / notifications（系统全局动作）、open_app（包名用 text 传）、" +
            "wait_text（等待文本出现，timeout_ms 默认 5000；找到会返回节点 bounds/centerX/centerY 的 JSON，可据此 tap）、" +
            "overlay_hide（临时隐藏自动化悬浮看板，duration_ms 默认 5000、范围 1000..30000，到时自动恢复；" +
            "用户授权请求会强制重新显示看板，且 update/新步骤不会提前恢复）、" +
            "overlay_show（立即恢复看板显示）。" +
            "overlay_hide / overlay_show 不操作屏幕，无需用户授权。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            JsonArray(
                                listOf(
                                    "click_text", "click_id", "tap", "swipe", "press", "set_text",
                                    "back", "home", "recents", "notifications", "open_app", "wait_text",
                                    "overlay_hide", "overlay_show",
                                ).map { JsonPrimitive(it) }
                            )
                        )
                        put("description", "要执行的动作。")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "click_text / set_text / wait_text 的目标文本；open_app 时为包名。")
                    })
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "click_id 的 viewId；set_text 时用它指定输入框。")
                    })
                    put("x", buildJsonObject { put("type", "number"); put("description", "tap / press 的 X 坐标；swipe 的起点 X。") })
                    put("y", buildJsonObject { put("type", "number"); put("description", "tap / press 的 Y 坐标；swipe 的起点 Y。") })
                    put("x2", buildJsonObject { put("type", "number"); put("description", "swipe 的终点 X。") })
                    put("y2", buildJsonObject { put("type", "number"); put("description", "swipe 的终点 Y。") })
                    put("duration_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "press / swipe 的手势时长毫秒，默认 press 600、swipe 300；" +
                            "overlay_hide 的隐藏时长毫秒，默认 5000，范围 1000..30000。")
                    })
                    put("timeout_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "wait_text 的超时毫秒，默认 5000。")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val params = jsonToMap(args)
            listOf(UIMessagePart.Text(withContext(Dispatchers.IO) { deviceAct(params) }))
        },
    )

    private fun captureDeviceScreen(includeOcr: Boolean, includeNodes: Boolean): String {
        if (AutomationBus.isCancelRequested()) return "已停止：用户取消了自动化"
        val bridge = AccessibilityBridgeHolder.current()
            ?: return """{"error":"无障碍服务未开启，请在系统设置中开启 KhatKit 的无障碍服务后再试"}"""
        // 会话中步骤：只更新看板，不结束会话（直接 AI 工具序列由看板空闲判定结束）
        AutomationBus.update("正在读取屏幕")
        return runCatching {
            val shot = bridge.captureScreen()
            val captured = shot.startsWith("/")
            val ocr = if (includeOcr && captured) {
                runCatching { ocrBridge.ocrText(shot) }
                    .getOrElse { """{"error":"OCR 失败：${it.message ?: it.javaClass.simpleName}"}""" }
            } else {
                null
            }
            val nodes = if (includeNodes) {
                runCatching { bridge.dumpWindow() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            buildJsonObject {
                if (captured) put("screenshot", shot) else put("screenshot_error", shot)
                bridge.currentPackage()?.let { put("package", it) }
                ocr?.let { text ->
                    if (text.contains("\"error\"")) {
                        put("ocr_error", text)
                    } else {
                        val lines = text.lineSequence()
                            .map { it.trim().take(SCREEN_LINE_MAX) }
                            .filter { it.isNotEmpty() }
                            .take(SCREEN_LINE_LIMIT)
                            .toList()
                        put("ocr", lines.joinToString("\n"))
                    }
                }
                if (includeNodes) {
                    val lines = nodes.asSequence()
                        .mapNotNull { formatScreenNode(it) }
                        .distinct()
                        .take(SCREEN_LINE_LIMIT)
                        .map { JsonPrimitive(it) }
                        .toList()
                    put("nodes", JsonArray(lines))
                }
            }.toString()
        }.getOrElse { """{"error":"读取屏幕失败：${it.message ?: it.javaClass.simpleName}"}""" }
    }

    private suspend fun deviceAct(params: Map<String, Any?>): String {
        if (AutomationBus.isCancelRequested()) return "已停止：用户取消了自动化"
        val action = params.str("action")?.lowercase()
            ?: return "缺少参数：action"
        // 看板显隐不操作屏幕：不需要无障碍服务，也不走授权请求
        when (action) {
            "overlay_hide" -> {
                val duration = (params.long("duration_ms") ?: AutomationBus.DEFAULT_OVERLAY_HIDE_MS)
                    .coerceIn(1_000L, 30_000L)
                AutomationBus.hideOverlayTemporarily(duration)
                return "看板已临时隐藏，${duration}ms 后自动恢复；用户授权请求会强制显示看板"
            }

            "overlay_show" -> {
                AutomationBus.showOverlay()
                return "看板已恢复显示"
            }
        }
        val bridge = AccessibilityBridgeHolder.current()
            ?: return "无障碍服务未开启，请在系统设置中开启 KhatKit 的无障碍服务后再试"
        AutomationBus.update(deviceActLabel(action, params))
        if (!AutomationBus.requestApproval("操作手机屏幕", deviceActApprovalDetail(action, params))) {
            // 会话中步骤：拒绝只取消本步，不结束整个自动化会话
            return "用户拒绝授权，已取消操作：$action"
        }
        val result = runCatching {
            when (action) {
                "click_text" -> {
                    val text = params.str("text") ?: return@runCatching "缺少参数：text"
                    if (bridge.click(mapOf("text" to text))) "已点击：$text" else "未找到：$text"
                }

                "click_id" -> {
                    val id = params.str("id") ?: return@runCatching "缺少参数：id"
                    if (bridge.click(mapOf("viewId" to id))) "已点击：$id" else "未找到：$id"
                }

                "tap" -> {
                    val x = params.float("x") ?: return@runCatching "缺少参数：x"
                    val y = params.float("y") ?: return@runCatching "缺少参数：y"
                    if (bridge.tap(x, y)) "已点击：($x, $y)" else "点击失败：($x, $y)"
                }

                "swipe" -> {
                    val x1 = params.float("x") ?: return@runCatching "缺少参数：x"
                    val y1 = params.float("y") ?: return@runCatching "缺少参数：y"
                    val x2 = params.float("x2") ?: return@runCatching "缺少参数：x2"
                    val y2 = params.float("y2") ?: return@runCatching "缺少参数：y2"
                    val duration = params.long("duration_ms") ?: 300L
                    if (bridge.swipe(x1, y1, x2, y2, duration)) "已滑动：($x1,$y1) → ($x2,$y2)" else "滑动失败"
                }

                "press" -> {
                    val x = params.float("x") ?: return@runCatching "缺少参数：x"
                    val y = params.float("y") ?: return@runCatching "缺少参数：y"
                    val duration = params.long("duration_ms") ?: 600L
                    if (bridge.press(x, y, duration)) "已长按：($x, $y)" else "长按失败：($x, $y)"
                }

                "set_text" -> {
                    val text = params.str("text") ?: return@runCatching "缺少参数：text"
                    val query = params.str("id")
                        ?.let { mapOf<String, Any?>("viewId" to it) }
                        ?: mapOf<String, Any?>("editable" to true)
                    if (bridge.setText(query, text)) "已输入：$text" else "未找到输入框：$text"
                }

                "back", "home", "recents", "notifications" ->
                    if (bridge.globalAction(action)) "已执行：$action" else "执行失败：$action"

                "open_app" -> {
                    val pkg = params.str("text") ?: params.str("id")
                        ?: return@runCatching "缺少参数：text（目标应用包名）"
                    if (bridge.openApp(pkg)) "已打开：$pkg" else "打开失败：$pkg"
                }

                "wait_text" -> {
                    val text = params.str("text") ?: return@runCatching "缺少参数：text"
                    val timeout = (params.long("timeout_ms") ?: 5_000L).coerceIn(0L, 120_000L)
                    val node = bridge.waitForNode(mapOf("text" to text), timeout)
                    if (node == null) {
                        "未找到：$text（等待 ${timeout}ms 超时）"
                    } else {
                        buildJsonObject {
                            put("found", true)
                            put("text", node["text"]?.toString() ?: text)
                            node["bounds"]?.let { put("bounds", it.toString()) }
                            (node["centerX"] as? Number)?.let { put("centerX", it.toInt()) }
                            (node["centerY"] as? Number)?.let { put("centerY", it.toInt()) }
                        }.toString()
                    }
                }

                else -> "不支持的动作：$action"
            }
        }.getOrElse { "操作失败：${it.message ?: it.javaClass.simpleName}" }
        // 会话中步骤：本步结束只保留看板状态，整段 AI 工具序列由看板空闲判定结束
        return result
    }

    /** 授权请求里展示的动作摘要：动作 + 文本 / id / 坐标。 */
    private fun deviceActApprovalDetail(action: String, params: Map<String, Any?>): String {
        val target = when (action) {
            "click_text", "set_text", "wait_text", "open_app" ->
                params.str("text") ?: params.str("id")
            "click_id" -> params.str("id")
            "tap", "press" -> {
                val x = params.float("x")
                val y = params.float("y")
                if (x != null && y != null) "($x, $y)" else null
            }
            "swipe" -> {
                val x1 = params.float("x")
                val y1 = params.float("y")
                val x2 = params.float("x2")
                val y2 = params.float("y2")
                if (x1 != null && y1 != null && x2 != null && y2 != null) "($x1,$y1) → ($x2,$y2)" else null
            }
            else -> null
        }
        return if (target.isNullOrBlank()) "动作：$action" else "动作：$action $target"
    }

    /** 设备动作的看板文案（中文）。 */
    private fun deviceActLabel(action: String, params: Map<String, Any?>): String = when (action) {
        "click_text" -> "正在点击「${params.str("text").orEmpty()}」"
        "click_id" -> "正在点击控件「${params.str("id").orEmpty()}」"
        "tap" -> "正在点击坐标…"
        "swipe" -> "正在滑动…"
        "press" -> "正在长按…"
        "set_text" -> "正在输入「${params.str("text").orEmpty()}」"
        "back" -> "正在返回"
        "home" -> "正在回到桌面"
        "recents" -> "正在打开最近任务"
        "notifications" -> "正在打开通知栏"
        "open_app" -> "正在打开应用「${params.str("text") ?: params.str("id").orEmpty()}」"
        "wait_text" -> "正在等待「${params.str("text").orEmpty()}」出现"
        else -> "正在执行：$action"
    }

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

    /** 已安装卡片的触发方式（name -> triggers），市场页据此显示徽标与运行入口。 */
    override suspend fun installedCardTriggers(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        loadInstalledCards().associate { it.manifest.name to it.manifest.triggers }
    }

    /** 用户手动运行已安装卡片：不带参数，需要输入时由脚本自己弹 ui.form。 */
    override suspend fun runCard(name: String): CardRunResult = withContext(Dispatchers.IO) {
        val card = loadInstalledCards().firstOrNull { it.manifest.name == name }
            ?: return@withContext CardRunResult(false, "卡片未安装：$name")
        if (!card.manifest.supportsUser()) {
            return@withContext CardRunResult(false, "卡片未启用用户触发：$name")
        }
        when (val result = runCardWithStatus(card, emptyMap(), trigger = "用户")) {
            is EngineResult.Ok -> {
                // 脚本约定 return { error = "..." } 表示失败，宿主侧转成错误提示
                val error = result.value["error"]?.toString()
                if (error.isNullOrBlank()) CardRunResult(true) else CardRunResult(false, error)
            }
            is EngineResult.Err -> CardRunResult(false, result.message)
        }
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
        const val DEFAULT_HUB_BASE_URL = "https://heizige.top"
        private const val KEY_HUB_URL = "hub_base_url"
        private const val KEY_ENABLE_ROOT = "enable_root"
        private const val KEY_DOWNLOAD_CONCURRENCY = "download_concurrency"
        private const val KEY_UI_STYLE = "ui_style"
        private const val KEY_RECEIVE_BETA = "receive_beta"
        private const val KEY_HANDS_OFF = "hands_off_mode"

        /** 索引缓存有效期：5 分钟内不重复拉取 */
        private const val INDEX_TTL_MS = 5 * 60 * 1000L

        /** 喂给 AI 的候选卡片上限（设计 9.1：控制在 20 以内） */
        private const val TOOL_CANDIDATE_LIMIT = 20

        /** Hub 索引拉取超时：超时沿用旧缓存，不阻塞生成 */
        private const val INDEX_FETCH_TIMEOUT_MS = 3_000L
    }
}

/** 屏幕工具输出上限：OCR 行数 / 节点行数 / 单行字符数 */
private const val SCREEN_LINE_LIMIT = 120
private const val SCREEN_LINE_MAX = 300

private fun formatScreenNode(node: Map<String, Any?>): String? {
    val text = node["text"]?.toString()?.takeIf { it.isNotBlank() }
    val desc = node["desc"]?.toString()?.takeIf { it.isNotBlank() && it != text }
    val viewId = node["viewId"]?.toString()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    val clickable = node["clickable"] == true
    val editable = node["editable"] == true
    if (text == null && desc == null && viewId == null && !clickable && !editable) return null
    val className = node["className"]?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
    val bounds = node["bounds"]?.toString().orEmpty()
    val centerX = (node["centerX"] as? Number)?.toInt()
    val centerY = (node["centerY"] as? Number)?.toInt()
    return buildString {
        text?.let { append(it) }
        desc?.let { if (isNotEmpty()) append(" | "); append(it) }
        viewId?.let { if (isNotEmpty()) append(" | "); append('#').append(it) }
        append(" [").append(className ?: "?").append(']')
        append(" (").append(bounds).append(')')
        if (centerX != null && centerY != null) append(" @").append(centerX).append(',').append(centerY)
        val flags = buildList {
            if (clickable) add("click")
            if (editable) add("edit")
            if (node["scrollable"] == true) add("scroll")
            if (node["longClickable"] == true) add("long")
        }
        if (flags.isNotEmpty()) append(' ').append(flags.joinToString(","))
    }.take(SCREEN_LINE_MAX)
}

private fun Map<String, Any?>.str(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean =
    this[key] as? Boolean ?: default

private fun Map<String, Any?>.float(key: String): Float? = when (val value = this[key]) {
    is Number -> value.toFloat()
    is String -> value.toFloatOrNull()
    else -> null
}

private fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
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
