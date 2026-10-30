package heizige.kk.khatkit.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import heizige.kk.khatkit.app.navigation.KkDeepLinkRoutes
import heizige.kk.khatkit.app.navigation.KkNavController
import heizige.kk.khatkit.app.navigation.ScreenRoutes
import kotlin.coroutines.cancellation.CancellationException
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.DatabaseMigrationTracker
import heizige.kk.khatkit.app.core.data.db.MigrationState
import heizige.kk.khatkit.app.core.data.event.AppEvent
import heizige.kk.khatkit.app.core.ui.activity.SafeModeActivity
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitUiHost
import heizige.kk.khatkit.app.core.ui.components.ui.TTSController
import heizige.kk.khatkit.app.feature.explore.ExploreCardsPage
import heizige.kk.khatkit.app.feature.explore.ExploreLocalPage
import heizige.kk.khatkit.app.feature.explore.ExploreMarketPage
import heizige.kk.khatkit.app.core.ui.context.LocalASRState
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.context.LocalSharedTransitionScope
import heizige.kk.khatkit.app.core.ui.context.LocalTTSState
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.readBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.readStringPreference
import heizige.kk.khatkit.app.core.ui.hooks.writeBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.rememberCustomAsrState
import heizige.kk.khatkit.app.core.ui.hooks.rememberCustomTtsState
import heizige.kk.khatkit.app.feature.assistant.AssistantPage
import heizige.kk.khatkit.app.feature.download.DownloadCenterPage
import heizige.kk.khatkit.app.feature.tasks.CardRunsPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantBasicPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantDetailPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantExtensionsPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantLocalToolPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantMcpPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantMemoryPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantPromptPage
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantRequestPage
import heizige.kk.khatkit.app.feature.backup.BackupPage
import heizige.kk.khatkit.app.feature.chat.ChatPage
import heizige.kk.khatkit.app.feature.chat.ChatRolePanelPage
import heizige.kk.khatkit.app.feature.debug.DebugPage
import heizige.kk.khatkit.app.feature.extensions.ExtensionsPage
import heizige.kk.khatkit.app.feature.extensions.PromptPage
import heizige.kk.khatkit.app.feature.extensions.QuickMessagesPage
import heizige.kk.khatkit.app.feature.extensions.skills.SkillDetailPage
import heizige.kk.khatkit.app.feature.extensions.skills.SkillsPage
import heizige.kk.khatkit.app.feature.workspace.WorkspacePage
import heizige.kk.khatkit.app.feature.workspace.WorkspaceDetailPage
import heizige.kk.khatkit.app.feature.workspace.WorkspaceFileEditorPage
import heizige.kk.khatkit.app.feature.workspace.WorkspaceTerminalPage
import heizige.kk.khatkit.workspace.WorkspaceStorageArea
import heizige.kk.khatkit.app.feature.onboarding.GreetingPage
import heizige.kk.khatkit.app.feature.imggen.ImageGenPage
import heizige.kk.khatkit.app.feature.log.LogPage
import heizige.kk.khatkit.app.feature.search.SearchPage
import heizige.kk.khatkit.app.feature.settings.SettingAboutPage
import heizige.kk.khatkit.app.feature.settings.SettingPermissionsPage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesThemePage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesNotificationPage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesGeneralPage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesNetworkPage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesUIMessagePage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesUIFontPage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesUICodePage
import heizige.kk.khatkit.app.feature.settings.SettingPreferencesUIPage
import heizige.kk.khatkit.app.feature.settings.SettingDonatePage
import heizige.kk.khatkit.app.feature.settings.SettingFilesPage
import heizige.kk.khatkit.app.feature.settings.SettingMcpPage
import heizige.kk.khatkit.app.feature.settings.SettingMemoryPage
import heizige.kk.khatkit.app.feature.settings.SettingModelPage
import heizige.kk.khatkit.app.feature.settings.SettingPage
import heizige.kk.khatkit.app.feature.settings.SettingProviderDetailPage
import heizige.kk.khatkit.app.feature.settings.SettingProviderPage
import heizige.kk.khatkit.app.feature.settings.SettingSearchDetailPage
import heizige.kk.khatkit.app.feature.settings.SettingSearchPage
import heizige.kk.khatkit.app.feature.settings.SettingSpeechPage
import heizige.kk.khatkit.app.feature.settings.SettingPackagePage
import heizige.kk.khatkit.app.feature.settings.SettingTriggersPage
import heizige.kk.khatkit.app.feature.settings.CreateWorkflowPage
import heizige.kk.khatkit.app.feature.settings.SettingWebPage
import heizige.kk.khatkit.app.feature.share.handler.ShareHandlerPage
import heizige.kk.khatkit.app.feature.translator.TranslatorPage
import heizige.kk.khatkit.app.feature.webview.WebViewPage
import heizige.kk.khatkit.app.feature.automation.data.AutomationBus
import heizige.kk.khatkit.app.feature.automation.AutomationInAppHost
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.core.util.CrashHandler
import heizige.kk.khatkit.app.core.util.openUsageAccessSettings
import dagger.hilt.android.AndroidEntryPoint
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import javax.inject.Inject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

@AndroidEntryPoint
class RouteActivity : ComponentActivity() {
    @Inject
    lateinit var settingsStore: SettingsRepository


    /**
     * 深链 / ACTION_SEND 等 Intent 的目标 route。
     *
     * U7a-2 起栈归 Mochi（KkNavController）管，而 Intent 早于 Compose 就绪，所以 Activity 侧只把
     * [KkDeepLinkRoutes] 算出来的 route 存进这里，Compose 起来后由 AppRoutes 里的 LaunchedEffect 压栈。
     */
    private val pendingScreenRoute = mutableStateOf<String?>(null)

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            KhatKitTheme {
                // P3a：Mochi 的 overscroll（拉伸/回弹）接管全 app 的滚动容器。
                // 放在主题内、路由外 —— 所有页面共享同一份效果，不再各自 remember。
                ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll {
                // Coil 的单例 ImageLoader 不在这里配置：那是进程级单例，
                // 而这里只是其中一个 Activity 的组合，任何先跑的 Coil 用法
                // （AsyncImage / rememberAsyncImagePainter）都会把内置默认 loader 钉死，
                // 之后再调 setSingletonImageLoaderFactory 就抛
                // IllegalStateException: The singleton image loader has already been created...
                // 定义点唯一，放在 Application（KhatKitApp : SingletonImageLoader.Factory）。
                // 详见 KhatKitApp.newImageLoader 的注释与 CoilImageLoaderSourceGuardTest。
                // P3b：Kedge/Miuix 的「第二主题根」已删除（原来是
                // 旧的 Miuix/KhatKitTheme 第二主题根 + ProvideKedgeBlur + MiuixListCardStyle 分支）。
                // 现在运行期只有 MochiTheme 单树：颜色/字阶/形状/系统栏图标都由
                // core/ui/theme/Theme.kt 的 MochiTheme 提供，页面不再按风格分叉。
                val appContent: @Composable () -> Unit = {
                    AppRoutes()
                    AutomationInAppHost()
                }
                appContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AutomationBus.setAppInForeground(true)
    }

    override fun onPause() {
        AutomationBus.setAppInForeground(false)
        super.onPause()
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        // 映射走 KkDeepLinkRoutes（逐条等价于迁移前的 when，单测覆盖 TRANSLATE / SEND / PROCESS_TEXT / conversationId）。
        val route = KkDeepLinkRoutes.routeOfIntentAction(
            action = action,
            conversationId = intent.getStringExtra(KkDeepLinkRoutes.EXTRA_CONVERSATION_ID),
            text = when (action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                else -> null
            },
            streamUri = if (action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_STREAM) else null,
        ) ?: return
        pendingScreenRoute.value = route
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = rememberAppEntryPoint().appEventBus()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.OpenUsageAccessSettings -> this@RouteActivity.openUsageAccessSettings()
                    is AppEvent.ChatGenerationUpdate -> Unit // 由 ChatNotificationManager 消费
                    is AppEvent.ChatGenerationEnded -> Unit // 由 ChatNotificationManager 消费
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        // ── U7a-3：导航栈全量交给 Mochi；Nav3 外壳（页面用 Nav3 渲染的那套）已整体移除 ───
        // 60 条 Screen 全部由 MochiNavHost 渲染（ScreenRoutes 的 route⇄Screen 映射，单测守互逆）。
        // 页面里 94 处 navController.navigate(Screen.X) 一行未改，仍走 LocalNavController，
        // 但它背后是 MochiBackedNavStack：压栈/弹栈直接落到 KkNavController（Mochi 栈）。
        // 引导页不做根：MochiNavigator 没有 replaceRoot，根固定是会话页，没走过引导页时
        // 把 Greeting 压在上面，完成时 pop 回会话页（进程重建靠 rememberSaveable 恢复整条栈）。
        val greetingCompleted = remember { readBooleanPreference("greeting_completed", false) }
        val rootScreen = remember { nextChatScreen(this@RouteActivity) }
        val mochiNavigator = rememberMochiNavigator(ScreenRoutes.routeOf(rootScreen))
        val kkNav = remember(mochiNavigator) { KkNavController(mochiNavigator) }
        LaunchedEffect(mochiNavigator) {
            // rememberMochiNavigator 自己会从 rememberSaveable 恢复；恢复过就别再压引导页。
            if (mochiNavigator.backStack.size > 1) return@LaunchedEffect
            if (!greetingCompleted) kkNav.navigate(Screen.Greeting)
        }
        // 深链 / ACTION_SEND 等 Intent：KkDeepLinkRoutes 只产出 route（单测覆盖），这里解码成 Screen 压栈。
        val pendingRoute = pendingScreenRoute.value
        LaunchedEffect(pendingRoute) {
            if (pendingRoute != null) {
                pendingScreenRoute.value = null
                ScreenRoutes.screenOf(pendingRoute)?.let { kkNav.navigate(it) }
            }
        }
        val mochiBackedNavigator = remember(kkNav) { Navigator(MochiBackedNavStack(kkNav)) }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides mochiBackedNavigator,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                TTSController()
                // Card Markdown and forms need the same settings/toaster/nav
                // locals as pages, including when hosted in a FullscreenPopup.
                KhatKitUiHost()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // ── U7a-3：60 条路由全部由 MochiNavHost 渲染，Nav3 外壳已删除 ──────────
                    // route ⇄ Screen 由 [ScreenRoutes] 保证（单测守 60 条互逆）；未知 route 不画、不崩。
                    MochiNavHost(navigator = mochiNavigator, modifier = Modifier.fillMaxSize()) { route ->
                        // 页内浮层（ChatInput 全屏编辑器）的返回：只有浮层登记时抢在 Mochi 前面，
                        // 否则完全交给 Mochi 的预测性返回处理（取消 rethrow、提交才 pop）。
                        if (route == mochiNavigator.current) {
                            PageBackGuard { if (!kkNav.popBackStack()) finish() }
                        }
                        when (val screen = ScreenRoutes.screenOf(route)) {
                            // 未知 route（旧版本恢复/脏数据）：不画，交给 Mochi 栈回退，别崩。
                            null -> Unit
                            is Screen.Greeting -> GreetingPage(
                                onFinish = {
                                    writeBooleanPreference("greeting_completed", true)
                                    if (!kkNav.popBackStack()) finish()
                                }
                            )
                            is Screen.Chat -> ChatPage(
                                    id = Uuid.parse(screen.id),
                                    text = screen.text,
                                    files = screen.files.map { it.toUri() },
                                    nodeId = screen.nodeId?.let { Uuid.parse(it) },
                                    messageId = screen.messageId?.let { Uuid.parse(it) },
                                )
                            is Screen.ShareHandler -> ShareHandlerPage(
                                    text = screen.text,
                                    image = screen.streamUri
                                )
                            is Screen.RoleChatPanel -> ChatRolePanelPage(
                                    roleId = screen.roleId,
                                    conversationId = screen.conversationId,
                                )
                            is Screen.Assistant -> AssistantPage()
                            is Screen.AssistantDetail -> AssistantDetailPage(screen.id)
                            is Screen.AssistantBasic -> AssistantBasicPage(screen.id)
                            is Screen.AssistantPrompt -> AssistantPromptPage(screen.id)
                            is Screen.AssistantMemory -> AssistantMemoryPage(screen.id)
                            is Screen.AssistantRequest -> AssistantRequestPage(screen.id)
                            is Screen.AssistantMcp -> AssistantMcpPage(screen.id)
                            is Screen.AssistantLocalTool -> AssistantLocalToolPage(screen.id)
                            is Screen.AssistantInjections -> AssistantExtensionsPage(screen.id)
                            is Screen.Translator -> TranslatorPage()
                            is Screen.Setting -> SettingPage()
                            is Screen.Backup -> BackupPage()
                            is Screen.ImageGen -> ImageGenPage()
                            is Screen.WebView -> WebViewPage(screen.url, screen.contentId)
                            is Screen.SettingPreferencesTheme -> SettingPreferencesThemePage()
                            is Screen.SettingPreferencesNotification -> SettingPreferencesNotificationPage()
                            is Screen.SettingPreferencesGeneral -> SettingPreferencesGeneralPage()
                            is Screen.SettingPermissions -> SettingPermissionsPage()
                            is Screen.SettingPreferencesUI -> SettingPreferencesUIPage()
                            is Screen.SettingPreferencesUIMessage -> SettingPreferencesUIMessagePage()
                            is Screen.SettingPreferencesUIFont -> SettingPreferencesUIFontPage()
                            is Screen.SettingPreferencesUICode -> SettingPreferencesUICodePage()
                            is Screen.SettingPreferencesNetwork -> SettingPreferencesNetworkPage()
                            is Screen.SettingProvider -> SettingProviderPage()
                            is Screen.SettingProviderDetail -> {
                                val id = Uuid.parse(screen.providerId)
                                SettingProviderDetailPage(id = id)
                            }
                            is Screen.SettingModels -> SettingModelPage()
                            is Screen.TraceAudit -> heizige.kk.khatkit.app.feature.automation.TraceAuditPage()
                            is Screen.SettingAbout -> SettingAboutPage()
                            is Screen.SettingSearch -> SettingSearchPage()
                            is Screen.SettingSearchDetail -> {
                                val id = Uuid.parse(screen.serviceId)
                                SettingSearchDetailPage(id)
                            }
                            is Screen.SettingSpeech -> SettingSpeechPage()
                            is Screen.SettingMcp -> SettingMcpPage()
                            is Screen.ExploreMarket -> ExploreMarketPage()
                            is Screen.ExploreCards -> ExploreCardsPage(initialQuery = screen.initialQuery)
                            is Screen.ExploreLocal -> ExploreLocalPage()
                            is Screen.CardRuns -> CardRunsPage()
                            is Screen.SettingDonate -> SettingDonatePage()
                            is Screen.SettingFiles -> SettingFilesPage()
                            is Screen.SettingMemory -> SettingMemoryPage()
                            is Screen.SettingWeb -> SettingWebPage()
                            is Screen.SettingTriggers -> SettingTriggersPage()
                            is Screen.CreateWorkflow -> CreateWorkflowPage(screen.workflowId)
                            is Screen.SettingPackage -> SettingPackagePage()
                            is Screen.Debug -> DebugPage()
                            is Screen.Log -> LogPage()
                            is Screen.Extensions -> ExtensionsPage()
                            is Screen.QuickMessages -> QuickMessagesPage()
                            is Screen.Prompts -> PromptPage()
                            is Screen.Skills -> SkillsPage()
                            is Screen.Workspaces -> WorkspacePage()
                            is Screen.WorkspaceDetail -> WorkspaceDetailPage(screen.id)
                            is Screen.WorkspaceTerminal -> WorkspaceTerminalPage(screen.id)
                            is Screen.WorkspaceFileEditor -> WorkspaceFileEditorPage(
                                    id = screen.id,
                                    area = WorkspaceStorageArea.valueOf(screen.area),
                                    path = screen.path,
                                )
                            is Screen.SkillDetail -> SkillDetailPage(skillName = screen.skillName)
                            is Screen.MessageSearch -> SearchPage()
                            is Screen.DownloadCenter -> DownloadCenterPage()
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[开发模式]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                KkProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}




/** 引导页完成后要进的会话页（逻辑与迁移前的 greetingChatScreen 一致）。 */
private fun nextChatScreen(context: Context): Screen.Chat = Screen.Chat(
    id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
        Uuid.random().toString()
    } else {
        context.readStringPreference("lastConversationId", Uuid.random().toString()) ?: Uuid.random().toString()
    }
)

/**
 * 把 Mochi 栈包成 [MutableList]<[NavKey]>，让页面里既有的 94 处 \`navController.navigate(Screen.X)\`
 * 一行都不用改就落到 Mochi 栈上。只实现 [Navigator] 真正用到的那几个操作：
 * 压栈顶 / 弹栈顶 / 读 size 与元素；\`clearAndNavigate\` 的 \`clear()\` 退到根页为止（根页不删，
 * 与 Mochi「根页不可 pop」的不变量一致）。
 */
private class MochiBackedNavStack(private val kkNav: KkNavController) : AbstractMutableList<NavKey>() {
    override val size: Int get() = kkNav.backStack.size

    override fun get(index: Int): NavKey = kkNav.backStack[index]

    override fun add(index: Int, element: NavKey) {
        require(index == size) { "Mochi 栈只支持压栈顶" }
        val screen = element as? Screen ?: return
        kkNav.navigate(screen)
    }

    override fun removeAt(index: Int): NavKey {
        require(index == size - 1) { "Mochi 栈只支持弹栈顶" }
        val removed = kkNav.backStack.last()
        kkNav.popBackStack()
        return removed
    }

    override fun set(index: Int, element: NavKey): NavKey =
        throw UnsupportedOperationException("Mochi 栈不支持替换栈内页")

    /** [Navigator.clearAndNavigate] 的 clear()：退到根页为止（根页不可 pop，与 Mochi 不变量一致）。 */
    override fun clear() {
        while (kkNav.canPop) {
            kkNav.popBackStack()
        }
    }
}

/**
 * 核心页的返回前置钩子：只在有页内浮层登记返回拦截时注册（否则完全不注册，返回照旧由
 * [MochiNavHost] 的预测性返回处理）。等价于迁移前 Nav3 onBack 里那层
 * [PageBackInterceptor]，保证 ChatInput 全屏编辑器这类浮层还能先关自己。
 */
@Composable
private fun PageBackGuard(onPop: () -> Unit) {
    val hasOverlay = PageBackInterceptor.hasHandlers
    BackHandler(enabled = hasOverlay) {
        if (!PageBackInterceptor.intercept()) onPop()
    }
    PredictiveBackHandler(enabled = hasOverlay) { events ->
        try {
            events.collect { }
        } catch (c: CancellationException) {
            throw c
        }
        if (!PageBackInterceptor.intercept()) onPop()
    }
}


sealed interface Screen : NavKey {
    @Serializable
    data object Greeting : Screen

    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        val messageId: String? = null,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    /**
     * 角色聊天面板：聊天页右上角三横杠的落地页。
     *
     * @param roleId 当前角色（assistant id）。面板里的「配置 / 历史聊天 / 新建聊天」
     *   全部按它过滤，所以它是必填项。
     * @param conversationId 打开面板时所在会话的 id：面板里「重命名当前聊天」改的就是
     *   这一条。为空（例如将来有别的入口）时该 option item 不出现。
     */
    @Serializable
    data class RoleChatPanel(
        val roleId: String,
        val conversationId: String? = null,
    ) : Screen

    @Serializable
    data object TraceAudit : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesUIMessage : Screen

    @Serializable
    data object SettingPreferencesUIFont : Screen

    @Serializable
    data object SettingPreferencesUICode : Screen

    @Serializable
    data object SettingPermissions : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object ExploreMarket : Screen

    /** 卡片市场二级页：[initialQuery] 从一级页带过来，保住已经输入的关键词。 */
    @Serializable
    data class ExploreCards(val initialQuery: String = "") : Screen

    /** 本地二级页：本机内容与已安装内容（不走远端市场索引）。 */
    @Serializable
    data object ExploreLocal : Screen

    @Serializable
    data object CardRuns : Screen

    @Serializable
    data object SettingDonate : Screen

    @Serializable
    data object SettingFiles : Screen

    /** 设置 → 数据设置 → 记忆：先选角色，再进该角色记忆面板。 */
    @Serializable
    data object SettingMemory : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object SettingTriggers : Screen

    @Serializable
    data class CreateWorkflow(val workflowId: String? = null) : Screen

    @Serializable
    data object SettingPackage : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object DownloadCenter : Screen

}

/**
 * 页内浮层的返回拦截登记处（结构性地保证浮层打开时返回不会穿透到导航栈）。
 *
 * 背景：ChatInput 的全屏编辑器是**同一 window 内的页内浮层**（Khromia `FullscreenPopup`
 * 挂在 decorView 上），它自己的 PredictiveBackHandler 与 Nav3 onBack 的返回处理注册在
 * 同一个页面 dispatcher 上。「后注册者优先」是 androidx 文档写明的规则，但为了让返回
 * 结果不依赖这个顺序假设，这里再兜一层：浮层登记后，返回接管点先问拦截器。
 *
 * 这是普通可变状态而不是 Compose state：`onBack` 与浮层回调都在事件里同步读写它，
 * 不需要参与重组；浮层的显示/隐藏由各自的 Compose 状态驱动。
 */
internal object PageBackInterceptor {
    private val handlers = linkedMapOf<Any, () -> Boolean>()

    /**
     * 当前有没有浮层登记了返回拦截。U7a-2 起返回先经 Mochi 的预测性返回处理，只有这里为 true 时
     * 才由 [PageBackGuard] 抢在 Mochi 前面问一句 —— 所以它必须是 Compose 的 snapshot 状态。
     */
    private var handlerCount by mutableIntStateOf(0)

    /** [handlers] 的非空状态（Compose 可观察）；读它决定要不要抢在 Mochi 前面处理返回。 */
    val hasHandlers: Boolean get() = handlerCount > 0

    /** 用 [token] 登记一个「返回先给我」的回调；返回 true 表示已消费。后登记者优先。 */
    fun push(token: Any, handler: () -> Boolean) {
        handlers.remove(token)
        handlers[token] = handler
        handlerCount = handlers.size
    }

    fun pop(token: Any) {
        handlers.remove(token)
        handlerCount = handlers.size
    }

    /** 从最新登记的往回问，任一消费即返回 true（未登记任何回调时返回 false）。 */
    fun intercept(): Boolean {
        val pending = handlers.values.toList()
        for (index in pending.indices.reversed()) {
            if (pending[index].invoke()) return true
        }
        return false
    }
}
