package heizige.kk.khatkit.app.core.data.ai.tools

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import heizige.kk.khatkit.bridge.AccessibilityBridge
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AndroidToolBridge
import heizige.kk.khatkit.bridge.impl.BridgeFactory
import heizige.kk.khatkit.bridge.impl.DownloadPolicy
import heizige.kk.khatkit.bridge.impl.FileStoreBridge
import heizige.kk.khatkit.app.feature.automation.ApprovalCategory
import heizige.kk.khatkit.app.feature.automation.AutomationBus
import heizige.kk.khatkit.app.core.data.ai.hub.HubAccountRepository
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.BuiltinCards
import heizige.kk.khatkit.hub.BundledLibProvider
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubAccountStatus
import heizige.kk.khatkit.hub.HubActivateRequest
import heizige.kk.khatkit.hub.HubActionResult
import heizige.kk.khatkit.hub.HubAuthorizeOutcome
import heizige.kk.khatkit.hub.HubCardForkRequest
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.hub.HubCardPublishRequest
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.hub.HubFilters
import heizige.kk.khatkit.hub.HubLibProvider
import heizige.kk.khatkit.hub.HubToolReportRequest
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiBridgeHost
import heizige.kk.khatkit.uikit.CardRunResult
import heizige.kk.khatkit.uikit.KhatKitController
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.network.McpToolDescriptor
import heizige.kk.khatkit.app.core.network.McpToolResult
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

    /** 套餐账户存储：激活令牌走 Keystore 加密通道，额度缓存用于离线展示。 */
    private val accountStore by lazy { HubAccountRepository(appContext) }

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
                // 原生插件从用户配置的 Hub 下载；下载/校验进度发布到自动化看板
                hubBaseUrl = { hubBaseUrl },
                onPluginStatus = { AutomationBus.update(it) },
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
     * [AutomationBus.begin] 持有会话：卡片执行期间即使长时间没有进度更新，看板也不会误判会话结束；
     * 授权被拒 / 取消 / 异常都会在 finally 释放持有，看板必定进入完成态并退场。
     *
     * 套餐计量：本机存有激活令牌时，执行前调用 POST /api/tools/authorize；
     * 仅当服务端「明确拒绝」时阻止运行（中文提示），网络/服务端不可用一律失败开放，
     * 免费本地卡片不受影响；运行结束后 best-effort 上报 POST /api/tools/report。
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
        try {
            val manifest = card.manifest
            val price = manifest.pricing?.price ?: 0.0
            // Hub 的价格单位是「分」，manifest 的 pricing.price 是元
            val priceCents = (price * 100).roundToLong()
            val hubToken = accountStore.token()
            if (!hubToken.isNullOrBlank()) {
                // 授权请求限时，避免 Hub 不可达时拖慢卡片启动；失败开放
                val decision = withTimeoutOrNull(METERING_TIMEOUT_MS) {
                    runCatching { hub().authorizeTool(hubToken, manifest.name, priceCents) }.getOrNull()
                }
                when (decision) {
                    is HubAuthorizeOutcome.Denied -> {
                        Log.w(TAG, "Hub 拒绝工具调用：${manifest.name}（${decision.message}）")
                        return EngineResult.Err(
                            "CARD_QUOTA",
                            "套餐工具次数不足/令牌无效：${decision.message}（卡片：${manifest.name}）",
                        )
                    }

                    is HubAuthorizeOutcome.Unavailable ->
                        Log.w(TAG, "Hub 授权不可用，降级为本地运行：${manifest.name}（${decision.reason}）")

                    else -> Unit
                }
            }

            AutomationBus.update("正在运行卡片：${card.manifest.name}")
            val category = cardApprovalCategory(card)
            if (!AutomationBus.requestApproval("运行卡片：${card.manifest.name}", "触发来源：$trigger", category)) {
                return EngineResult.Err("CARD_DENIED", "用户拒绝授权，已取消运行卡片：${card.manifest.name}")
            }
            val startedAt = System.currentTimeMillis()
            val result = runManager.run(card, args)
            if (!hubToken.isNullOrBlank()) {
                reportToolCallAsync(hubToken, manifest, price, trigger, startedAt, result)
            }
            return result
        } finally {
            AutomationBus.finish()
        }
    }

    /** 运行结果 best-effort 上报，不阻塞调用方；失败只记日志。 */
    private fun reportToolCallAsync(
        token: String,
        manifest: CardManifest,
        price: Double,
        trigger: String,
        startedAt: Long,
        result: EngineResult,
    ) {
        scope.launch {
            runCatching {
                hub().reportTool(
                    token,
                    HubToolReportRequest(
                        cardName = manifest.name,
                        price = price,
                        currency = manifest.pricing?.currency?.takeIf { it.isNotBlank() } ?: "CNY",
                        ok = result is EngineResult.Ok,
                        trigger = trigger,
                        durationMs = System.currentTimeMillis() - startedAt,
                        message = (result as? EngineResult.Err)?.message.orEmpty(),
                    ),
                )
            }.onFailure { Log.w(TAG, "工具调用上报失败：${manifest.name}（${it.message}）") }
        }
    }

    /**
     * 卡片运行的审批类别：声明 elevated、依赖 shizuku/root bridge 或 command 引擎的卡片
     * 会走 shell 执行，归为 [ApprovalCategory.SHELL_ROOT]；其余为普通卡片运行。
     * 脚本内部更细粒度的文件 / 应用操作不在卡片运行层区分（由触发子系统后续补充）。
     */
    private fun cardApprovalCategory(card: LoadedCard): ApprovalCategory {
        val manifest = card.manifest
        val shell = manifest.isCommand ||
            manifest.privilege == "elevated" ||
            manifest.requiredBridges.any { it == "root" || it == "shizuku" }
        return if (shell) ApprovalCategory.SHELL_ROOT else ApprovalCategory.CARD_RUN
    }

    /**
     * 组装全部工具。[model] 为当前生成所用模型：支持图片输入时 device_screen 会把截图作为图片
     * 一并返回，让多模态模型直接「看」屏幕；为 null（如 MCP 出口）时只返回文本。
     */
    suspend fun tools(model: Model? = null): List<Tool> = withContext(Dispatchers.IO) {
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
            val supportsVision = model?.inputModalities?.contains(Modality.IMAGE) == true
            cardTools + listOf(deviceScreenTool(supportsVision), deviceActTool())
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

    /**
     * 内置工具：截屏 + OCR + 无障碍节点清单。
     *
     * [supportsVision] 为 true（当前模型 inputModalities 含 IMAGE）时，工具结果在文本之外
     * 额外附带压缩后的截图图片（[UIMessagePart.Image]），多模态模型可直接查看；否则只返回文本。
     */
    private fun deviceScreenTool(supportsVision: Boolean): Tool = Tool(
        name = "khatkit__device_screen",
        description = "读取当前手机屏幕并返回视图：截图 PNG 路径、截图 OCR 文本、当前窗口无障碍节点清单。" +
            if (supportsVision) {
                "当前模型支持图片输入：截图会压缩后作为图片随结果一并返回，可直接观察界面细节；" +
                    "include_image 默认 true，设为 false 可只返回文本。"
            } else {
                "当前模型不支持图片输入，仅返回文本；请依据 OCR 与节点信息判断界面内容。"
            } +
            "再用 khatkit__device_act 操作。" +
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
                    put("include_image", buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            if (supportsVision) {
                                "是否附带压缩后的截图图片供模型直接查看，默认 true；设为 false 只返回文本。"
                            } else {
                                "当前模型不支持图片输入，此参数无效。"
                            }
                        )
                    })
                },
            )
        },
        execute = { args ->
            val params = jsonToMap(args)
            val includeOcr = params.bool("include_ocr", true)
            val includeNodes = params.bool("include_nodes", true)
            val includeImage = supportsVision && params.bool("include_image", true)
            val capture = withContext(Dispatchers.IO) {
                captureDeviceScreen(includeOcr, includeNodes, includeImage)
            }
            buildList {
                add(UIMessagePart.Text(capture.text))
                capture.imagePath?.let { path ->
                    add(UIMessagePart.Image(url = Uri.fromFile(File(path)).toString()))
                }
            }
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
            "wake（点亮屏幕）、screen_state（返回屏幕是否点亮/锁定）、" +
            "unlock（root/Shizuku 下执行 KEYCODE_WAKEUP + 上滑解锁；安全锁屏无法绕过，会返回中文说明）、" +
            "overlay_hide（临时隐藏自动化悬浮看板，duration_ms 默认 5000、范围 1000..30000，到时自动恢复；" +
            "用户授权请求会强制重新显示看板，且 update/新步骤不会提前恢复）、" +
            "overlay_show（立即恢复看板显示）。" +
            "自动重试：click_text / click_id / set_text 首次未找到目标时会等待约 400ms 再试（最多重试 2 次，共 3 次尝试）；" +
            "wait_text 保持自身超时等待语义，超时预算按 3 次尝试均分。仍失败时返回当前窗口按文本相似度排序的候选节点" +
            "（最多 5 条，含类名/bounds/中心坐标/edit 标记），可据此改用 click_id 或 tap 坐标。" +
            "熄屏保护：tap / swipe / press / click_text / click_id / set_text 执行前若屏幕熄灭，会自动调用 wakeScreen 点亮并重试一次，" +
            "仍失败则返回中文错误。wake / unlock / 触屏动作需要用户授权（ui_action 类别）；screen_state 与 " +
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
                                    "wake", "screen_state", "unlock",
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

    private fun captureDeviceScreen(
        includeOcr: Boolean,
        includeNodes: Boolean,
        includeImage: Boolean,
    ): ScreenCapture {
        if (AutomationBus.isCancelRequested()) return ScreenCapture("已停止：用户取消了自动化")
        val bridge = AccessibilityBridgeHolder.current()
            ?: return ScreenCapture("""{"error":"无障碍服务未开启，请在系统设置中开启 KhatKit 的无障碍服务后再试"}""")
        // 会话中步骤：只更新看板，不结束会话（直接 AI 工具序列由看板空闲判定结束）
        AutomationBus.update("正在读取屏幕")
        return runCatching {
            val shot = bridge.captureScreen()
            val captured = shot.startsWith("/")
            // 多模态模型：截图成功且被请求时，额外压缩出一份可随工具结果回传的 JPEG
            val visionPath = if (includeImage && captured) compressScreenshotForVision(shot) else null
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
            val text = buildJsonObject {
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
            ScreenCapture(text, visionPath)
        }.getOrElse { ScreenCapture("""{"error":"读取屏幕失败：${it.message ?: it.javaClass.simpleName}"}""") }
    }

    /**
     * 把 PNG 截图压成适合图片输入的 JPEG：短边不超过 1080、长边不超过 1920，质量 [VISION_JPEG_QUALITY]。
     * 图片存到应用私有目录 khatkit/vision 下（不额外占用共享存储），并顺带清理过期文件；失败返回 null。
     */
    private fun compressScreenshotForVision(pngPath: String): String? = runCatching {
        val bitmap = BitmapFactory.decodeFile(pngPath) ?: return null
        val scale = minOf(
            1f,
            VISION_MAX_SHORT_EDGE.toFloat() / minOf(bitmap.width, bitmap.height).coerceAtLeast(1),
            VISION_MAX_LONG_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1),
        )
        val scaled = if (scale >= 1f) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        try {
            val dir = File(rootDir, VISION_DIR_NAME).apply { mkdirs() }
            pruneVisionCache(dir)
            val output = File(dir, "screen_${System.currentTimeMillis()}.jpg")
            FileOutputStream(output).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, stream)
            }
            output.takeIf { it.length() > 0 }?.absolutePath
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }.getOrNull()

    /** 清理过期/超量的 vision 截图，避免应用私有目录无限增长。 */
    private fun pruneVisionCache(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val cutoff = System.currentTimeMillis() - VISION_CACHE_TTL_MS
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        files.sortedByDescending { it.lastModified() }
            .drop(VISION_CACHE_MAX_FILES)
            .forEach { it.delete() }
    }

    /** device_screen 结果：给模型的文本 + 可选的多模态图片路径。 */
    private data class ScreenCapture(val text: String, val imagePath: String? = null)

    private suspend fun deviceAct(params: Map<String, Any?>): String {
        if (AutomationBus.isCancelRequested()) return "已停止：用户取消了自动化"
        val action = params.str("action")?.lowercase()
            ?: return "缺少参数：action"
        // 看板显隐不操作屏幕：不需要无障碍服务，也不走授权请求；screen_state 只读同理
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

            "screen_state" -> return screenStateJson()
        }
        // wake / unlock 走 tool / root / shizuku bridge，不依赖无障碍服务，但仍是设备动作走 ui_action 授权
        if (action == "wake" || action == "unlock") {
            AutomationBus.update(deviceActLabel(action, params))
            if (!AutomationBus.requestApproval("操作手机屏幕", deviceActApprovalDetail(action, params), ApprovalCategory.UI_ACTION)) {
                return "用户拒绝授权，已取消操作：$action"
            }
            return if (action == "wake") wakeScreenAction() else unlockScreenAction()
        }
        val bridge = AccessibilityBridgeHolder.current()
            ?: return "无障碍服务未开启，请在系统设置中开启 KhatKit 的无障碍服务后再试"
        AutomationBus.update(deviceActLabel(action, params))
        val category = if (action == "open_app") {
            ApprovalCategory.APP_MANAGE
        } else {
            ApprovalCategory.UI_ACTION
        }
        if (!AutomationBus.requestApproval("操作手机屏幕", deviceActApprovalDetail(action, params), category)) {
            // 会话中步骤：拒绝只取消本步，不结束整个自动化会话
            return "用户拒绝授权，已取消操作：$action"
        }
        // 触屏 / 手势动作前确保屏幕点亮：熄灭时先唤醒一次，仍失败返回中文错误
        if (action in TOUCH_ACTIONS) {
            ensureScreenInteractive()?.let { return it }
        }
        val result = runCatching {
            when (action) {
                "click_text" -> {
                    val text = params.str("text") ?: return@runCatching "缺少参数：text"
                    val found = retryFind { if (bridge.click(mapOf("text" to text))) true else null }
                    if (found.value == true) {
                        "已点击：$text（第 ${found.attempts} 次尝试成功）"
                    } else {
                        "未找到：$text（已尝试 ${found.attempts} 次）" + findAlternativesSuffix(bridge, text)
                    }
                }

                "click_id" -> {
                    val id = params.str("id") ?: return@runCatching "缺少参数：id"
                    val found = retryFind { if (bridge.click(mapOf("viewId" to id))) true else null }
                    if (found.value == true) {
                        "已点击：$id（第 ${found.attempts} 次尝试成功）"
                    } else {
                        "未找到：$id（已尝试 ${found.attempts} 次）" + findAlternativesSuffix(bridge, id)
                    }
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
                    val id = params.str("id")
                    val query = id
                        ?.let { mapOf<String, Any?>("viewId" to it) }
                        ?: mapOf<String, Any?>("editable" to true)
                    val target = id ?: "首个可编辑输入框"
                    val found = retryFind { if (bridge.setText(query, text)) true else null }
                    if (found.value == true) {
                        "已输入：$text（目标：$target，第 ${found.attempts} 次尝试成功）"
                    } else {
                        "未找到输入框：$target（已尝试 ${found.attempts} 次）" +
                            findAlternativesSuffix(bridge, id ?: text)
                    }
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
                    // 保持自身超时语义：总等待预算按尝试次数均分，重试间隔（400ms）另计
                    val slice = timeout / FIND_ATTEMPTS
                    val found = retryFind { bridge.waitForNode(mapOf("text" to text), slice) }
                    val node = found.value
                    if (node == null) {
                        "未找到：$text（等待 ${timeout}ms 超时，已尝试 ${found.attempts} 次）" +
                            findAlternativesSuffix(bridge, text)
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

    /** 熄屏保护：屏幕熄灭时调用工具 bridge 唤醒并等待一次；仍熄灭返回中文错误。 */
    private suspend fun ensureScreenInteractive(): String? {
        if (isScreenInteractive()) return null
        runCatching { ocrBridge.wakeScreen() }
        delay(WAKE_RETRY_DELAY_MS)
        return if (isScreenInteractive()) {
            null
        } else {
            "屏幕处于熄灭状态，尝试唤醒后仍未点亮，已取消操作。" +
                "请检查 KhatKit 的 WAKE_LOCK 权限，或先用 unlock 动作解锁设备。"
        }
    }

    /** wake 动作：调用工具 bridge 点亮屏幕，返回唤醒结果与最新屏幕状态。 */
    private suspend fun wakeScreenAction(): String {
        val result = runCatching { ocrBridge.wakeScreen() }
            .getOrElse { "点亮屏幕失败：${it.message ?: it.javaClass.simpleName}" }
        delay(WAKE_RETRY_DELAY_MS)
        return "$result；${screenStateJson()}"
    }

    /**
     * unlock 动作：设备锁定时优先 root shell、其次 Shizuku shell，执行
     * KEYCODE_WAKEUP + 上滑（`input swipe 540 1800 540 600`）解除普通锁屏；
     * 安全锁屏（PIN / 密码 / 图案）无法用 input 绕过，返回中文说明。
     */
    private suspend fun unlockScreenAction(): String {
        if (!keyguardLocked()) return "设备当前未锁定，无需解锁；${screenStateJson()}"
        val root = executor().bridges.rootBridge()
        val shizuku = executor().bridges.shizukuBridge()
        val runner = when {
            root != null -> "root" to { cmd: String -> root.shell(cmd) }
            shizuku != null -> "shizuku" to { cmd: String -> shizuku.shell(cmd) }
            else -> null
        } ?: return "设备已锁定，但未开启 root / Shizuku，无法通过 shell 唤醒并上滑解锁。" +
            "请在 KhatKit 设置中开启 Shizuku 或 root 后重试，或手动解锁屏幕。"
        val (name, shell) = runner
        runCatching { shell("input keyevent KEYCODE_WAKEUP") }
        delay(UNLOCK_STEP_DELAY_MS)
        val swipe = runCatching { shell("input swipe 540 1800 540 600") }
        delay(UNLOCK_STEP_DELAY_MS)
        return when {
            !keyguardLocked() ->
                "已通过 $name 执行 KEYCODE_WAKEUP + 上滑解锁，当前设备已解锁；${screenStateJson()}"

            keyguardSecure() ->
                "设备仍处于安全锁屏（已设置 PIN / 密码 / 图案），$name 的 input 命令无法绕过安全锁。" +
                    "请手动输入密码解锁；如需自动解锁，需先在系统设置中关闭锁屏密码。"

            else -> "已通过 $name 执行 KEYCODE_WAKEUP + 上滑（$swipe），但设备仍显示锁屏，" +
                "可能被系统安全策略拦截，请手动解锁。"
        }
    }

    /** 屏幕 / 锁屏状态（JSON，含中文描述）。 */
    private fun screenStateJson(): String {
        val interactive = isScreenInteractive()
        val locked = keyguardLocked()
        val secure = keyguardSecure()
        val text = when {
            !interactive -> "屏幕已熄灭"
            locked && secure -> "屏幕已点亮，处于安全锁屏（需要 PIN / 密码 / 图案）"
            locked -> "屏幕已点亮，处于锁屏但未设置安全密码"
            else -> "屏幕已点亮，未锁定"
        }
        return buildJsonObject {
            put("interactive", interactive)
            put("locked", locked)
            put("secure", secure)
            put("text", text)
        }.toString()
    }

    private fun powerManager(): PowerManager? =
        runCatching { appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()

    private fun keyguardManager(): KeyguardManager? =
        runCatching { appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }.getOrNull()

    private fun isScreenInteractive(): Boolean =
        runCatching { powerManager()?.isInteractive ?: true }.getOrDefault(true)

    private fun keyguardLocked(): Boolean =
        runCatching { keyguardManager()?.isKeyguardLocked == true }.getOrDefault(false)

    private fun keyguardSecure(): Boolean =
        runCatching { keyguardManager()?.isDeviceSecure == true }.getOrDefault(false)

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
        "wake" -> "正在点亮屏幕"
        "screen_state" -> "正在读取屏幕状态"
        "unlock" -> "正在尝试解锁屏幕"
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

    // ===== KhatKitHub 套餐 / 市场（失败一律优雅降级，不抛异常）=====

    /** 本机保存的激活令牌；未激活返回 null。 */
    override fun hubToken(): String? = accountStore.token()

    /**
     * 当前套餐账户状态。[refresh] 为 true 时请求 GET /api/me；
     * 网络失败时退回缓存额度并带中文错误信息。
     */
    override suspend fun hubAccountStatus(refresh: Boolean): HubAccountStatus = withContext(Dispatchers.IO) {
        val base = HubAccountStatus(
            activated = false,
            hubBaseUrl = hubBaseUrl,
            deviceId = accountStore.deviceId(),
            deviceName = accountStore.deviceName(),
        )
        val token = accountStore.token()
        if (token.isNullOrBlank()) {
            return@withContext base.copy(message = "未激活套餐，可继续使用免费卡片与本地功能")
        }
        if (!refresh) {
            val cached = accountStore.cachedAccount()
            return@withContext base.copy(
                activated = true,
                plan = cached.plan,
                aiTokensRemaining = cached.aiTokensRemaining,
                toolCallsRemaining = cached.toolCallsRemaining,
                expiresAt = cached.expiresAt,
            )
        }
        val result = runCatching { hub().me(token) }.getOrNull()
        if (result?.ok == true) {
            accountStore.cacheAccount(result.info)
            return@withContext base.copy(
                activated = true,
                plan = result.info.plan,
                aiTokensRemaining = result.info.aiTokensRemaining,
                toolCallsRemaining = result.info.toolCallsRemaining,
                expiresAt = result.info.expiresAt,
            )
        }
        val cached = accountStore.cachedAccount()
        base.copy(
            activated = true,
            plan = cached.plan,
            aiTokensRemaining = cached.aiTokensRemaining,
            toolCallsRemaining = cached.toolCallsRemaining,
            expiresAt = cached.expiresAt,
            message = result?.message ?: "无法连接 Hub，展示缓存额度",
        )
    }

    /** 激活套餐：保存 Hub 地址与令牌，随后尝试拉取额度（失败只影响额度展示）。 */
    override suspend fun activateHub(token: String, hubBaseUrl: String): HubAccountStatus =
        withContext(Dispatchers.IO) {
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                return@withContext HubAccountStatus(
                    hubBaseUrl = this@KhatKitToolProvider.hubBaseUrl,
                    deviceId = accountStore.deviceId(),
                    deviceName = accountStore.deviceName(),
                    message = "请输入激活令牌",
                )
            }
            val base = hubBaseUrl.trim().ifBlank { DEFAULT_HUB_BASE_URL }
            this@KhatKitToolProvider.hubBaseUrl = base
            applySettings()
            val activation = runCatching {
                hub().activate(
                    base,
                    HubActivateRequest(
                        token = trimmed,
                        deviceId = accountStore.deviceId(),
                        deviceName = accountStore.deviceName(),
                    ),
                )
            }.getOrElse {
                return@withContext HubAccountStatus(
                    hubBaseUrl = base,
                    deviceId = accountStore.deviceId(),
                    deviceName = accountStore.deviceName(),
                    message = "无法连接 Hub：${it.message ?: it.javaClass.simpleName}",
                )
            }
            if (!activation.ok) {
                return@withContext HubAccountStatus(
                    hubBaseUrl = base,
                    deviceId = accountStore.deviceId(),
                    deviceName = accountStore.deviceName(),
                    message = activation.message.ifBlank { "激活失败，请检查令牌是否有效" },
                )
            }
            accountStore.saveActivation(activation.token, activation.sessionKey)
            accountStore.cacheAccount(activation.account)
            val refreshed = runCatching { hub().me(activation.token) }.getOrNull()
            if (refreshed?.ok == true) accountStore.cacheAccount(refreshed.info)
            val cached = accountStore.cachedAccount()
            HubAccountStatus(
                activated = true,
                hubBaseUrl = base,
                deviceId = accountStore.deviceId(),
                deviceName = accountStore.deviceName(),
                plan = cached.plan,
                aiTokensRemaining = cached.aiTokensRemaining,
                toolCallsRemaining = cached.toolCallsRemaining,
                expiresAt = cached.expiresAt,
                message = activation.message,
            )
        }

    /** 清除本机激活信息。 */
    override suspend fun clearHubActivation(): HubAccountStatus = withContext(Dispatchers.IO) {
        accountStore.clear()
        HubAccountStatus(
            activated = false,
            hubBaseUrl = hubBaseUrl,
            deviceId = accountStore.deviceId(),
            deviceName = accountStore.deviceName(),
            message = "已清除本机激活信息",
        )
    }

    /** 市场卡片详情（价格 / 协议 / 优选 / 票数）；Hub 不可达返回空表。 */
    override suspend fun hubMarketCards(): List<HubCardMarketInfo> = withContext(Dispatchers.IO) {
        runCatching { hub().listCards(accountStore.token()) }.getOrNull()
            ?.takeIf { it.ok }?.cards.orEmpty()
    }

    /** 发布已安装卡片到 Hub（价格入参为元，上传时转成分）。 */
    override suspend fun publishCard(
        name: String,
        license: String,
        price: Double,
        changelog: String,
    ): HubActionResult = withContext(Dispatchers.IO) {
        val token = accountStore.token()
            ?: return@withContext HubActionResult(false, "请先在「设置 → 套餐/激活」中激活套餐")
        val card = loadInstalledCards().firstOrNull { it.manifest.name == name }
            ?: return@withContext HubActionResult(false, "本机未安装卡片：$name")
        val manifest = card.manifest
        runCatching {
            hub().publishCard(
                token,
                HubCardPublishRequest(
                    name = manifest.name,
                    version = manifest.version,
                    description = manifest.description,
                    script = card.scriptText,
                    manifestJson = CardParser.serialize(manifest),
                    pricePerCall = (price.coerceAtLeast(0.0) * 100).roundToLong(),
                    license = license.ifBlank { manifest.license }.ifBlank { "MIT" },
                    changelog = changelog,
                ),
            )
        }.getOrElse { HubActionResult(false, "发布失败：${it.message ?: it.javaClass.simpleName}") }
            .let { it.copy(message = it.message.ifBlank { if (it.ok) "已发布到 Hub：$name" else "发布失败" }) }
    }

    /** 提交改进：把本机卡片作为 fork 上传，带父版本、新版本号与更新说明。 */
    override suspend fun forkCard(
        name: String,
        parentVersion: String,
        changelog: String,
        newVersion: String,
    ): HubActionResult = withContext(Dispatchers.IO) {
        val token = accountStore.token()
            ?: return@withContext HubActionResult(false, "请先在「设置 → 套餐/激活」中激活套餐")
        val card = loadInstalledCards().firstOrNull { it.manifest.name == name }
            ?: return@withContext HubActionResult(false, "本机未安装卡片：$name")
        val manifest = card.manifest
        runCatching {
            hub().forkCard(
                token,
                name,
                HubCardForkRequest(
                    parentVersion = parentVersion.ifBlank { manifest.version },
                    version = newVersion.trim().ifBlank { bumpPatchVersion(manifest.version) },
                    description = manifest.description,
                    script = card.scriptText,
                    manifestJson = CardParser.serialize(manifest),
                    pricePerCall = ((manifest.pricing?.price ?: 0.0).coerceAtLeast(0.0) * 100).roundToLong(),
                    license = manifest.license.ifBlank { "MIT" },
                    changelog = changelog,
                ),
            )
        }.getOrElse { HubActionResult(false, "提交改进失败：${it.message ?: it.javaClass.simpleName}") }
            .let { it.copy(message = it.message.ifBlank { if (it.ok) "已提交改进：$name" else "提交改进失败" }) }
    }

    /** 新版本号默认按补丁位 +1（1.2.3 -> 1.2.4），解析失败则在末尾追加 .1。 */
    private fun bumpPatchVersion(version: String): String {
        val parts = version.trim().split('.')
        if (parts.size >= 3) {
            val patch = parts[2].toIntOrNull()
            if (patch != null) return "${parts[0]}.${parts[1]}.${patch + 1}"
        }
        return if (version.isBlank()) "1.0.0" else "$version.1"
    }

    /** 给市场卡片投票。 */
    override suspend fun voteCard(name: String): HubActionResult = withContext(Dispatchers.IO) {
        val token = accountStore.token()
            ?: return@withContext HubActionResult(false, "请先在「设置 → 套餐/激活」中激活套餐")
        runCatching { hub().voteCard(token, name) }
            .getOrElse { HubActionResult(false, "投票失败：${it.message ?: it.javaClass.simpleName}") }
            .let { it.copy(message = it.message.ifBlank { if (it.ok) "已投票：$name" else "投票失败" }) }
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

        /** 日志 TAG（套餐计量 / 上报降级）。 */
        const val TAG = "KhatKitTools"

        /** 索引缓存有效期：5 分钟内不重复拉取 */
        private const val INDEX_TTL_MS = 5 * 60 * 1000L

        /** 喂给 AI 的候选卡片上限（设计 9.1：控制在 20 以内） */
        private const val TOOL_CANDIDATE_LIMIT = 20

        /** Hub 索引拉取超时：超时沿用旧缓存，不阻塞生成 */
        private const val INDEX_FETCH_TIMEOUT_MS = 3_000L

        /** 工具授权请求限时：超时按失败开放处理，不拖慢卡片启动 */
        private const val METERING_TIMEOUT_MS = 4_000L
    }
}

/** 屏幕工具输出上限：OCR 行数 / 节点行数 / 单行字符数 */
private const val SCREEN_LINE_LIMIT = 120
private const val SCREEN_LINE_MAX = 300

/** device_screen 多模态截图：短边/长边上限、JPEG 质量与私有目录缓存策略 */
private const val VISION_MAX_SHORT_EDGE = 1080
private const val VISION_MAX_LONG_EDGE = 1920
private const val VISION_JPEG_QUALITY = 80
private const val VISION_DIR_NAME = "vision"
private const val VISION_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
private const val VISION_CACHE_MAX_FILES = 40

/** device_act 触屏 / 手势动作（执行前需确认屏幕点亮） */
private val TOUCH_ACTIONS = setOf("click_text", "click_id", "tap", "swipe", "press", "set_text")

/** 查找失败重试：等待 400ms，最多 2 次（共 3 次尝试） */
private const val FIND_MAX_RETRIES = 2
private const val FIND_RETRY_DELAY_MS = 400L
private const val FIND_ATTEMPTS = FIND_MAX_RETRIES + 1

/** 唤醒屏幕后的等待 / unlock 两步 shell 之间的等待 */
private const val WAKE_RETRY_DELAY_MS = 400L
private const val UNLOCK_STEP_DELAY_MS = 350L

/** 相似候选节点输出上限（条数与总字符数） */
private const val ALTERNATIVE_LIMIT = 5
private const val ALTERNATIVE_MAX_CHARS = 1200

/** 查找结果：value 为 null 表示最终未找到，attempts 为总尝试次数。 */
private data class FindAttempt<T>(val value: T?, val attempts: Int)

/**
 * 首次失败后等待约 400ms 重试，最多 [FIND_MAX_RETRIES] 次。
 * click_text / click_id / set_text / wait_text 的「找不到就重试」共用。
 */
private suspend fun <T : Any> retryFind(
    maxRetries: Int = FIND_MAX_RETRIES,
    delayMs: Long = FIND_RETRY_DELAY_MS,
    find: suspend () -> T?,
): FindAttempt<T> {
    var value = find()
    var attempts = 1
    while (value == null && attempts <= maxRetries) {
        delay(delayMs)
        value = find()
        attempts++
    }
    return FindAttempt(value, attempts)
}

/** 找不到目标时从当前窗口挑相似节点给模型纠偏；无候选返回中文说明。 */
private fun findAlternativesSuffix(bridge: AccessibilityBridge, query: String): String {
    val nodes = runCatching { bridge.dumpWindow() }.getOrDefault(emptyList())
    val hint = buildFindAlternatives(nodes, query)
        ?: return "\n（当前窗口没有可参考的相近节点，可先用 device_screen 查看屏幕）"
    return "\n相近候选（可改用 click_id 或 tap 坐标）：\n$hint"
}

/** 候选节点排序依据：命中查询优先，其次文本更短、相似度更高。 */
private data class ScoredNode(
    val node: Map<String, Any?>,
    val contains: Boolean,
    val similarity: Double,
    val length: Int,
)

/** 从当前窗口节点里取与 query 最相近的 top 5，格式化为中文多行提示；无候选返回 null。 */
private fun buildFindAlternatives(nodes: List<Map<String, Any?>>, query: String): String? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return null
    val scored = nodes.mapNotNull { node ->
        val text = node["text"]?.toString()?.trim().orEmpty()
        val desc = node["desc"]?.toString()?.trim().orEmpty()
        val viewId = node["viewId"]?.toString()?.substringAfterLast('/').orEmpty()
        if (text.isBlank() && desc.isBlank() && viewId.isBlank()) return@mapNotNull null
        val hay = listOf(text, desc, viewId).joinToString(" ").lowercase()
        ScoredNode(
            node = node,
            contains = hay.contains(q),
            similarity = maxOf(
                textSimilarity(text.lowercase(), q),
                textSimilarity(desc.lowercase(), q),
                textSimilarity(viewId.lowercase(), q),
            ),
            length = text.ifBlank { desc.ifBlank { viewId } }.length,
        )
    }.sortedWith(
        compareByDescending<ScoredNode> { it.contains }
            .thenBy { it.length }
            .thenByDescending { it.similarity }
    )
    if (scored.isEmpty()) return null
    val lines = scored.take(ALTERNATIVE_LIMIT).mapIndexedNotNull { index, item ->
        formatAlternativeNode(index + 1, item.node)
    }
    if (lines.isEmpty()) return null
    return lines.joinToString("\n").take(ALTERNATIVE_MAX_CHARS)
}

/** 候选行：`序号. 文本/描述 [类名] (bounds) @中心X,中心Y click,edit`。 */
private fun formatAlternativeNode(index: Int, node: Map<String, Any?>): String? {
    val text = node["text"]?.toString()?.trim().orEmpty()
    val desc = node["desc"]?.toString()?.trim().orEmpty()
    val viewId = node["viewId"]?.toString()?.substringAfterLast('/').orEmpty()
    val label = when {
        text.isNotBlank() && desc.isNotBlank() && desc != text -> "$text | $desc"
        text.isNotBlank() -> text
        desc.isNotBlank() -> desc
        viewId.isNotBlank() -> viewId
        else -> return null
    }
    val className = node["className"]?.toString()?.substringAfterLast('.')
        ?.takeIf { it.isNotBlank() } ?: "?"
    val bounds = node["bounds"]?.toString().orEmpty()
    val centerX = (node["centerX"] as? Number)?.toInt()
    val centerY = (node["centerY"] as? Number)?.toInt()
    val flags = buildList {
        if (node["clickable"] == true) add("click")
        if (node["editable"] == true) add("edit")
    }
    return buildString {
        append(index).append(". ").append(label)
        append(" [").append(className).append(']')
        if (bounds.isNotBlank()) append(" (").append(bounds).append(')')
        if (centerX != null && centerY != null) append(" @").append(centerX).append(',').append(centerY)
        if (flags.isNotEmpty()) append(' ').append(flags.joinToString(","))
    }.take(SCREEN_LINE_MAX)
}

/** 简单文本相似度：相等 1.0，否则按二元组 Jaccard 计算（单字符退化为字符集合）。 */
private fun textSimilarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    if (a.length < 2 || b.length < 2) {
        val ac = a.toSet()
        val bc = b.toSet()
        val union = ac.union(bc).size
        return if (union == 0) 0.0 else ac.intersect(bc).size.toDouble() / union
    }
    val ab = a.windowed(2).toSet()
    val bb = b.windowed(2).toSet()
    val inter = ab.count { it in bb }
    val union = ab.size + bb.size - inter
    return if (union == 0) 0.0 else inter.toDouble() / union
}

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
