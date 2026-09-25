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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.automation.AutomationBus
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.service.ChatNotificationManager
import java.io.File
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
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
class KhatKitApp : Application() {
    @Inject
    lateinit var appScope: AppScope

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var workspaceManager: WorkspaceManager

    @Inject
    lateinit var workspaceRepository: WorkspaceRepository

    @Inject
    lateinit var filesManager: FilesManager

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

        // Start WebServer if enabled in settings
        startWebServerIfEnabled()

        // 自动化状态看板：状态出现时拉起悬浮窗服务（未授予悬浮权限时静默跳过）
        AutomationBus.install(this)
        AutomationBus.setHandsOffProvider { khatKitToolProvider.handsOffMode }

        // Increment launch count
        incrementLaunchCount()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun incrementLaunchCount() {
        appScope.launch {
            runCatching {
                val current = settingsStore.settingsFlowRaw.first()
                settingsStore.update(current.copy(launchCount = current.launchCount + 1))
                Log.i(TAG, "incrementLaunchCount: ${settingsStore.settingsFlowRaw.first().launchCount}")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
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
