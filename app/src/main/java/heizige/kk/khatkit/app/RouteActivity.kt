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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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

private const val TAG = "RouteActivity"

@AndroidEntryPoint
class RouteActivity : ComponentActivity() {
    @Inject
    lateinit var settingsStore: SettingsRepository

    /**
     * 全局导航栈的所有者。`by viewModels()` 在 `onCreate` 之前就建好，所以
     * [handleIntent]（`onCreate`/`onNewIntent` 都会调）可以直接压栈。
     */
    private val navViewModel: NavViewModel by viewModels()

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

        // ── U7a-2：导航栈改由 Mochi 的 MochiNavigator 持有 ─────────────────────────
        // 本步只把 4 个核心页（引导/聊天/设置/设置详情）接进 MochiNavHost；其余路由
        // fallback 到下面 LegacyNav3Shell 里的原 Nav3 NavDisplay（页面主体一行未改）。
        // 返回始终只有一处接管：核心页由 Mochi 的预测性返回处理，fallback 页先退自己的
        // fallback 栈，退到底再把返回交回 Mochi 栈（Nav3 侧不新增 BackHandler）。
        //
        // NavViewModel 只剩「初始栈种子」一个职责（它在 onCreate 前就从 SavedState/偏好恢复好），
        // U7a-3 全量迁完连它一起删。
        val seedScreens = remember { navViewModel.stack.toList().mapNotNull { it as? Screen } }
        // Mochi 的根页不能是引导页：MochiNavigator 没有 replaceRoot，引导页做根就永远删不掉。
        // 所以根固定是会话页，引导页叠在它上面，完成时直接 pop 回会话页。
        val rootScreen = remember(seedScreens) {
            seedScreens.firstOrNull { it is Screen.Chat } as? Screen.Chat ?: nextChatScreen(this@RouteActivity)
        }
        val mochiNavigator = rememberMochiNavigator(ScreenRoutes.routeOf(rootScreen))
        val kkNav = remember(mochiNavigator) { KkNavController(mochiNavigator) }
        // 初始栈里除根之外的页按原顺序压回 Mochi 栈（进程恢复 / 深链冷启动）。
        // rememberMochiNavigator 自己也会从 rememberSaveable 恢复，恢复过就别重复压。
        LaunchedEffect(mochiNavigator) {
            if (mochiNavigator.backStack.size > 1) return@LaunchedEffect
            // NavViewModel 不再跟着用户走，引导页完成后的重建里它的栈还停在 Greeting；
            // 用偏好兜一层，免得每次重建都把引导页再压回来。
            val greetingDone = readBooleanPreference("greeting_completed", false)
            seedScreens
                .filter { it != rootScreen && !(it is Screen.Greeting && greetingDone) }
                .forEach { kkNav.navigate(it) }
        }
        // 深链 / ACTION_SEND 等 Intent：KkDeepLinkRoutes 只产出 route（单测覆盖），这里解码成 Screen 压栈。
        val pendingRoute = pendingScreenRoute.value
        LaunchedEffect(pendingRoute) {
            if (pendingRoute != null) {
                pendingScreenRoute.value = null
                ScreenRoutes.screenOf(pendingRoute)?.let { kkNav.navigate(it) }
            }
        }
        // 页面里 94 处 navController.navigate(Screen.X) 不用改：LocalNavController 换成 Mochi 支撑的
        // Navigator（同一份 Navigator 语义），压栈/弹栈直接落到 Mochi 栈上。
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
                    // ── U7a-2：核心 4 页走 MochiNavHost，其余路由 fallback 到原 Nav3 外壳 ──
                    // 判断依据是 [ScreenRoutes] 产出的 route（U7a-1 的映射表，单测守 60 条互逆）。
                    MochiNavHost(navigator = mochiNavigator, modifier = Modifier.fillMaxSize()) { route ->
                        // 页内浮层（ChatInput 全屏编辑器）的返回仍然先于 Mochi 的弹栈：只在有浮层登记时
                        // 注册，等价于原来 NavDisplay.onBack 里的 PageBackInterceptor 那一层。
                        if (route == mochiNavigator.current) {
                            PageBackGuard { if (!kkNav.popBackStack()) finish() }
                        }
                        when (route) {
                            "greeting" -> GreetingPage(
                                onFinish = {
                                    writeBooleanPreference("greeting_completed", true)
                                    if (!kkNav.popBackStack()) finish()
                                }
                            )

                            "chat" -> (ScreenRoutes.screenOf(route) as? Screen.Chat)?.let { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) },
                                    messageId = key.messageId?.let { Uuid.parse(it) },
                                )
                            }

                            "setting" -> SettingPage()

                            "setting-provider-detail" ->
                                (ScreenRoutes.screenOf(route) as? Screen.SettingProviderDetail)?.let { key ->
                                    SettingProviderDetailPage(id = Uuid.parse(key.providerId))
                                }

                            // 其余 56 条路由（U7a-3 迁完就删掉这条分支）：整页交给原 Nav3 外壳。
                            // 不按「是否栈顶」裁剪：Nav3 小栈退到底时会把返回交回 Mochi，裁剪反而会让
                            // pop 转场的那段时间里页面变空白。
                            else -> LegacyNav3Shell(
                                seed = ScreenRoutes.screenOf(route) ?: Screen.Greeting,
                                onExit = { if (!kkNav.popBackStack()) finish() },
                            )
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

/**
 * U7a-2 过渡态 fallback 外壳：4 个核心页已由 [MochiNavHost] 渲染，其余路由整页落到这里 ——
 * 内容就是迁移前的 Nav3 [NavDisplay]（页面主体一行未改），换的只有「栈从哪来」和「返回到哪去」。
 *
 * 行为边界（U7a-3 全量迁完删掉整个函数）：
 * - 每条 fallback 路由一份独立小栈，页内跳转/返回全在小栈里，和迁移前完全一致；
 * - 小栈退到根页后返回交回 Mochi 栈（Mochi 优先），Mochi 也退不动才 finish；
 * - 不新增 BackHandler / PredictiveBackHandler，返回仍只有一处接管。
 */
@Composable
private fun LegacyNav3Shell(seed: Screen, onExit: () -> Unit) {
    // key(seed)：不同 fallback 路由不复用同一条小栈（否则会看到上一条路由的旧栈）。
    key(seed) {
        val context = LocalContext.current
        val fallbackStack = rememberNavBackStack(seed)
        val fallbackNav = remember(fallbackStack) { Navigator(fallbackStack) }
        // 页内 94 处 navigate 在 fallback 里仍然落到这条小栈上。
        CompositionLocalProvider(LocalNavController provides fallbackNav) {

            NavDisplay(
                backStack = fallbackStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                modifier = Modifier.fillMaxSize(),
                // ── fallback 页的返回：仍然是单点 ────────────────────────────
                //  · 页内浮层（ChatInput 全屏编辑器等）先经 PageBackInterceptor 消费；
                //  · fallback 自己还有页就退自己；
                //  · 退到 fallback 根页后把返回交回 Mochi 栈（Mochi 优先），Mochi 也退不动才 finish。
                // Nav3 侧不新增 BackHandler，页面里也不要再挂。
                onBack = {
                    if (!PageBackInterceptor.intercept()) {
                        if (fallbackStack.size > 1) fallbackNav.popBackStack()
                        else onExit()
                    }
                },
                transitionSpec = {
                    if (fallbackStack.size == 1) fadeIn() togetherWith fadeOut()
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
                                context.writeBooleanPreference("greeting_completed", true)
                                fallbackNav.navigate(nextChatScreen(context))
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
 * [MochiNavHost] 的预测性返回处理）。等价于迁移前 \`NavDisplay.onBack\` 里那层
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
 * 挂在 decorView 上），它自己的 `PredictiveBackHandler` 与 `NavDisplay` 的返回处理注册在
 * 同一个页面 dispatcher 上。「后注册者优先」是 androidx 文档写明的规则，但为了让返回
 * 结果不依赖这个顺序假设，这里再兜一层：浮层登记后，`NavDisplay.onBack` 先问拦截器。
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
