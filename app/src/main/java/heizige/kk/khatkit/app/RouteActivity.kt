package heizige.kk.khatkit.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.serialization.Serializable
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.DatabaseMigrationTracker
import heizige.kk.khatkit.app.core.data.db.MigrationState
import heizige.kk.khatkit.app.core.data.event.AppEvent
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.ui.activity.SafeModeActivity
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitUiHost
import heizige.kk.khatkit.app.core.ui.components.ui.TTSController
import heizige.kk.khatkit.app.feature.explore.ExploreCardsPage
import heizige.kk.khatkit.app.feature.explore.ExploreLocalPage
import heizige.kk.khatkit.app.feature.explore.ExploreMarketPage
import heizige.kk.khatkit.app.core.ui.context.LocalASRState
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.nav.NavViewModel
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
import heizige.kk.kedge.theme.KedgeTextStyles

private const val TAG = "RouteActivity"
private const val ACTION_TRANSLATE = "heizige.kk.khatkit.app.action.TRANSLATE"

@AndroidEntryPoint
class RouteActivity : ComponentActivity() {
    @Inject
    lateinit var settingsStore: SettingsRepository

    /**
     * 全局导航栈的所有者。`by viewModels()` 在 `onCreate` 之前就建好，所以
     * [handleIntent]（`onCreate`/`onNewIntent` 都会调）可以直接压栈。
     */
    private val navViewModel: NavViewModel by viewModels()

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
                // uiStyle==MIUIX → KhatKitTheme(uikit) + ProvideKedgeBlur + MiuixListCardStyle 分支）。
                // 现在运行期只有 MochiTheme 单树：颜色/字阶/形状/系统栏图标都由
                // core/ui/theme/Theme.kt 的 MochiTheme 提供，页面不再按风格分叉。
                val appContent: @Composable () -> Unit = {
                    AppRoutes()
                    AutomationInAppHost()
                }
                appContent()
                }
                heizige.kk.khromia.components.GlobalToastHost()
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
        val destination = when (intent.action) {
            ACTION_TRANSLATE -> Screen.Translator
            Intent.ACTION_SEND -> Screen.ShareHandler(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                streamUri = intent.getStringExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> intent.getStringExtra("conversationId")?.let { Screen.Chat(it) }
        }
        // 栈归 NavViewModel 管了，而 ViewModel 在 Activity 的 onCreate 之前就绪，
        // 所以这里不需要「等 Compose 建栈 + 排队」的中间态了。
        if (destination != null) {
            navViewModel.navigateTo(destination)
        }
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

        // 引导页完成时要进的会话页：只在还没完成引导、也就是栈顶确实是引导页时才算一次，
        // 免得每次重组都生成新的随机 UUID。
        var greetingChatScreen by remember { mutableStateOf<NavKey?>(null) }
        if (navViewModel.currentPage == Screen.Greeting && greetingChatScreen == null) {
            greetingChatScreen = Screen.Chat(
                id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                    Uuid.random().toString()
                } else {
                    readStringPreference(
                        "lastConversationId",
                        Uuid.random().toString()
                    ) ?: Uuid.random().toString()
                }
            )
        }

        // P4a：Kedge 的窗口级全屏搜索层已随 Kedge 搜索框一起下线（Mochi 搜索框
        // 走页内展开，不再需要 window 级 popupHost）。展开/收起不再涉及 window 增删。

        val backStack = navViewModel.stack

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
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
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        // ── 返回键：全进程唯一接管点就在这一处 ──────────────────────
                        // 边界（P3b 定死，别再新增第二个）：
                        //  · 系统 back / 预测性返回手势 → NavDisplay.onBack（本 lambda）；
                        //  · 页内浮层（如 ChatInput 全屏编辑器）先经 PageBackInterceptor 消费，
                        //    它只回答「这次返回我要不要」，**不自己 pop 栈**；
                        //  · 浮层消费 → 弹栈由本处统一执行；
                        //  · 不要再用 BackHandler / PredictiveBackHandler 在页面里抢返回，
                        //    否则会和这里形成两套 navigator（返回两次才退出）。
                        onBack = {
                            if (!PageBackInterceptor.intercept()) {
                                // 与 Navigator 的弹栈同一不变量：根页不清空栈 ——
                                // 清空会让 NavDisplay 无 entry、Activity 立刻 finish
                                // （表现为「一按 back 直回 launcher」）。
                                // 注：5efde353a 曾加过 400ms 墙钟节流，P3V 定性 clearAndNavigate
                                // 不是双 pop 后已删除；双回调根因至今**未实测**，不要再加回来
                                // （节流会吞掉 400ms 内的第二次真实返回）。
                                if (backStack.size > 1) navViewModel.popBackStack() else finish()
                            }
                        },
                        transitionSpec = {
                            if (backStack.size == 1) fadeIn() togetherWith fadeOut()
                            else {
                                slideInHorizontally { it } togetherWith
                                    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                            }
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        entryProvider = entryProvider {
                            entry<Screen.Greeting> {
                                GreetingPage(
                                    onFinish = {
                                        writeBooleanPreference("greeting_completed", true)
                                        greetingChatScreen?.let(navViewModel::onGreetingFinished)
                                    }
                                )
                            }

                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                    + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) },
                                    messageId = key.messageId?.let { Uuid.parse(it) },
                                )
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            // 角色聊天面板：聊天页右上角三横杠进来，返回即回到该会话。
                            entry<Screen.RoleChatPanel> { key ->
                                ChatRolePanelPage(
                                    roleId = key.roleId,
                                    conversationId = key.conversationId,
                                )
                            }

                            entry<Screen.Assistant> {
                                AssistantPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                AssistantDetailPage(key.id)
                            }

                            entry<Screen.AssistantBasic> { key ->
                                AssistantBasicPage(key.id)
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                AssistantPromptPage(key.id)
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }

                            entry<Screen.Translator> {
                                TranslatorPage()
                            }

                            entry<Screen.Setting> {
                                SettingPage()
                            }

                            entry<Screen.Backup> {
                                BackupPage()
                            }

                            entry<Screen.ImageGen> {
                                ImageGenPage()
                            }

                            entry<Screen.WebView> { key ->
                                WebViewPage(key.url, key.contentId)
                            }

                            entry<Screen.SettingPreferencesTheme> {
                                SettingPreferencesThemePage()
                            }

                            entry<Screen.SettingPreferencesNotification> {
                                SettingPreferencesNotificationPage()
                            }

                            entry<Screen.SettingPreferencesGeneral> {
                                SettingPreferencesGeneralPage()
                            }

                            entry<Screen.SettingPermissions> {
                                SettingPermissionsPage()
                            }

                            entry<Screen.SettingPreferencesUI> {
                                SettingPreferencesUIPage()
                            }

                            entry<Screen.SettingPreferencesUIMessage> {
                                SettingPreferencesUIMessagePage()
                            }

                            entry<Screen.SettingPreferencesUIFont> {
                                SettingPreferencesUIFontPage()
                            }

                            entry<Screen.SettingPreferencesUICode> {
                                SettingPreferencesUICodePage()
                            }

                            entry<Screen.SettingPreferencesNetwork> {
                                SettingPreferencesNetworkPage()
                            }

                            entry<Screen.SettingProvider> {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> {
                                SettingModelPage()
                            }

                            entry<Screen.TraceAudit> {
                                heizige.kk.khatkit.app.feature.automation.TraceAuditPage()
                            }

                            entry<Screen.SettingAbout> {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch> {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMcp> {
                                SettingMcpPage()
                            }

                            entry<Screen.ExploreMarket> {
                                ExploreMarketPage()
                            }

                            entry<Screen.ExploreCards> { key ->
                                ExploreCardsPage(initialQuery = key.initialQuery)
                            }

                            entry<Screen.ExploreLocal> {
                                ExploreLocalPage()
                            }

                            entry<Screen.CardRuns> {
                                CardRunsPage()
                            }

                            entry<Screen.SettingDonate> {
                                SettingDonatePage()
                            }

                            entry<Screen.SettingFiles> {
                                SettingFilesPage()
                            }

                            entry<Screen.SettingMemory> {
                                SettingMemoryPage()
                            }

                            entry<Screen.SettingWeb> {
                                SettingWebPage()
                            }

                            entry<Screen.SettingTriggers> {
                                SettingTriggersPage()
                            }

                            entry<Screen.CreateWorkflow> { key ->
                                CreateWorkflowPage(key.workflowId)
                            }

                            entry<Screen.SettingPackage> {
                                SettingPackagePage()
                            }

                            entry<Screen.Debug> {
                                DebugPage()
                            }

                            entry<Screen.Log> {
                                LogPage()
                            }

                            entry<Screen.Extensions> {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages> {
                                QuickMessagesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.Workspaces> {
                                WorkspacePage()
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceDetailPage(key.id)
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceFileEditorPage(
                                    id = key.id,
                                    area = WorkspaceStorageArea.valueOf(key.area),
                                    path = key.path,
                                )
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.DownloadCenter> {
                                DownloadCenterPage()
                            }

                        }
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[开发模式]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = KedgeTextStyles.footnoteSmall(),
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
                                KedgeProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = KedgeTextStyles.body()
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = KedgeTextStyles.body(),
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
 * 挂在 decorView 上），它自己的 `PredictiveBackHandler` 与 `NavDisplay` 的返回处理注册在
 * 同一个页面 dispatcher 上。「后注册者优先」是 androidx 文档写明的规则，但为了让返回
 * 结果不依赖这个顺序假设，这里再兜一层：浮层登记后，`NavDisplay.onBack` 先问拦截器。
 *
 * 这是普通可变状态而不是 Compose state：`onBack` 与浮层回调都在事件里同步读写它，
 * 不需要参与重组；浮层的显示/隐藏由各自的 Compose 状态驱动。
 */
internal object PageBackInterceptor {
    private val handlers = linkedMapOf<Any, () -> Boolean>()

    /** 用 [token] 登记一个「返回先给我」的回调；返回 true 表示已消费。后登记者优先。 */
    fun push(token: Any, handler: () -> Boolean) {
        handlers.remove(token)
        handlers[token] = handler
    }

    fun pop(token: Any) {
        handlers.remove(token)
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
