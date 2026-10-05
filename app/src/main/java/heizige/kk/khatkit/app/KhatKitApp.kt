package heizige.kk.khatkit.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.feature.automation.AutomationBus
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.feature.chat.ChatNotificationManager
import java.io.File
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.sync.BackupManager
import heizige.kk.khatkit.app.core.data.sync.RestoreFailedException
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.network.WebServerService
import heizige.kk.khatkit.app.core.util.CrashHandler
import heizige.kk.khatkit.app.core.util.DatabaseUtil
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.workspace.WorkspaceManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KhatKitApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"

@HiltAndroidApp
class KhatKitApp : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var appScope: AppScope

    @Inject
    lateinit var settingsStore: SettingsRepository

    @Inject
    lateinit var ktorHttpClient: HttpClient

    @Inject
    lateinit var workspaceManager: WorkspaceManager

    @Inject
    lateinit var workspaceRepository: WorkspaceRepository

    @Inject
    lateinit var filesManager: FilesManager

    @Inject
    lateinit var skillManager: SkillManager

    @Inject
    lateinit var khatKitToolProvider: KhatKitToolProvider

    // createdAtStart 语义：恢复完成后再触发实例化，保证后台生成事件不会丢失
    @Inject
    lateinit var chatNotificationManager: Lazy<ChatNotificationManager>

    override fun onCreate() {
        super.onCreate()
        // Restore files and settings before eager singletons or workers can access them.
        try {
            val restored = runBlocking(Dispatchers.IO) {
                BackupManager.applyPendingRestore(this@KhatKitApp, JsonInstant)
            }
            if (restored) {
                Toast.makeText(this, R.string.backup_page_restore_success, Toast.LENGTH_LONG).show()
            }
        } catch (e: RestoreFailedException) {
            Log.e(TAG, "Backup restore rolled back", e)
            Toast.makeText(this, "备份恢复失败，已保留原数据。请重新导入备份。", Toast.LENGTH_LONG).show()
        }
        chatNotificationManager.get()
        this.createNotificationChannel()

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        // install crash handler
        CrashHandler.install(this)

        // delete temp files
        deleteTempFiles()

        // cleanup stale tool output files
        cleanupToolOutputs()

        // cleanup workspace temp dirs (proot + rootfs /tmp)
        cleanupWorkspaceTempDirs()

        // check workspace integrity (mark workspaces with missing files as broken after backup restore)
        checkWorkspaceIntegrity()

        // sync upload files to DB
        syncManagedFiles()

        // 从 assets 解压内置技能（安装/更新后刷新）
        extractBuiltinSkills()

        // Start WebServer if enabled in settings
        startWebServerIfEnabled()

        // 自动化状态看板：状态出现时拉起悬浮窗服务（未授予悬浮权限时静默跳过）
        AutomationBus.install(this)
        AutomationBus.setHandsOffProvider { khatKitToolProvider.handsOffMode }

        // Increment launch count
        incrementLaunchCount()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    /**
     * 全 app **唯一**一处定义 Coil 的 `ImageLoader`。
     *
     * ## 为什么必须挂在 Application 上，而不是某个 Activity 的组合里
     *
     * Coil 3 的单例是一个 **进程级** `AtomicReference`（`coil3.SingletonImageLoader`），
     * 里面有三种可能的值：`null` / `Factory` / `ImageLoader`。两个 API 的语义是：
     *
     * - `get(context)`（所有 Coil 用法的入口：`AsyncImage`、`rememberAsyncImagePainter`、
     *   `SubcomposeAsyncImage` …）—— 为空时按
     *   「已存的 Factory → `applicationContext as? Factory` → 内置默认工厂」的顺序建一个，
     *   并把它**记住**；
     * - `setSafe(factory)`（即 `coil3.compose.setSingletonImageLoaderFactory`）——
     *   只有当当前记住的是「**内置默认**建出来的 ImageLoader」时才抛
     *   `IllegalStateException: The singleton image loader has already been created...`。
     *
     * 也就是说：**只要进程里有任何一处 Coil 用法先跑过，内置默认 loader 就被钉死了，
     * 之后再设置就必崩**。而 `setSingletonImageLoaderFactory` 是在**组合过程中**同步调用的
     * （`SingletonImageLoaders.kt:17`，没有 `SideEffect`/`LaunchedEffect` 兜底），
     * 所以「谁先跑」完全取决于进程里第一个碰到 Coil 的地方是不是这个 Activity。
     *
     * 这正是 `:app:connectedDebugAndroidTest` 全量崩掉的原因：`NodeTreeSmokeTest` 用
     * `createComposeRule()` 渲染 `AsyncImage`，在**同一个进程**里先把默认 loader 建出来了；
     * 随后 `BrowserRuntimeTest` 的 `ActivityScenarioRule(RouteActivity::class.java)` 拉起
     * `RouteActivity.onCreate`，`setSingletonImageLoaderFactory` 当场抛异常，
     * 而它是在 `super.onCreate()` 之后、`setContent` 之前挂掉的 —— 整个 instrumentation 进程死掉。
     *
     * 实现 `SingletonImageLoader.Factory`（Coil 3 里 `ImageLoaderFactory` 的替代品）后，
     * Coil 走的是 `newImageLoader()` 这条**只建一次、且认 ApplicationContext** 的路径：
     * 既不用赌「谁先跑」，也保证进程内拿到的永远是这份自定义 loader
     * （自带 Ktor 网络栈 + GIF/SVG 解码，而不是内置默认那套）。
     *
     * Hilt 字段注入发生在 `Application.onCreate` 之前（`inject()` 先于 `super.onCreate()`），
     * 而 `newImageLoader()` 是首次 Coil 用法时才惰性调用的，所以 [ktorHttpClient] 一定已就绪。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { ktorHttpClient }))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory(scaleToDensity = true))
            }
            .build()

    private fun incrementLaunchCount() {
        appScope.launch {
            runCatching {
                val count = settingsStore.incrementLaunchCount()
                Log.i(TAG, "incrementLaunchCount: $count")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun extractBuiltinSkills() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                skillManager.ensureBuiltinSkillsExtracted()
            }.onFailure {
                Log.e(TAG, "extractBuiltinSkills failed", it)
            }
        }
    }

    private fun cleanupWorkspaceTempDirs() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                workspaceManager.cleanupAllTempDirs()
            }.onFailure {
                Log.e(TAG, "cleanupWorkspaceTempDirs failed", it)
            }
        }
    }

    private fun checkWorkspaceIntegrity() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                workspaceRepository.checkIntegrity()
            }.onFailure {
                Log.e(TAG, "checkWorkspaceIntegrity failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        appScope.launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun cleanupToolOutputs() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = File(filesDir, FileFolders.TOOL_OUTPUTS)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }.onFailure {
                Log.e(TAG, "cleanupToolOutputs failed", it)
            }
        }
    }

    private fun syncManagedFiles() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                filesManager.syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }

    private fun startWebServerIfEnabled() {
        appScope.launch {
            runCatching {
                delay(500)
                val settings = settingsStore.settingsFlowRaw.first()
                if (settings.webServerEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@KhatKitApp,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: notification permission not granted, skipping")
                        return@launch
                    }
                    if (Build.VERSION.SDK_INT >= 37 &&
                        !settings.webServerLocalhostOnly &&
                        ContextCompat.checkSelfPermission(
                            this@KhatKitApp,
                            android.Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: local network permission not granted, skipping")
                        return@launch
                    }
                    val intent = Intent(this@KhatKitApp, WebServerService::class.java).apply {
                        action = WebServerService.ACTION_START
                        putExtra(WebServerService.EXTRA_PORT, settings.webServerPort)
                        putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
                    }
                    startForegroundService(intent)
                }
            }.onFailure {
                Log.e(TAG, "startWebServerIfEnabled failed", it)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)

        val webServerChannel = NotificationChannelCompat
            .Builder(WEB_SERVER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_web_server))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(webServerChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
        stopService(Intent(this, WebServerService::class.java))
    }
}

@Singleton
class AppScope @Inject constructor() : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)
