package heizige.kk.khatkit.app.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 把系统事件源接到 [TriggerEngine]：Wi-Fi / 网络 / 电量 / 屏幕 / 剪贴板 / 蓝牙 / 位置。
 *
 * 权限缺失或系统限制时静默降级（跳过该事件源），绝不影响其他触发器与宿主。
 * 生命周期由 TriggerService 控制：onStartCommand -> [start]，onDestroy -> [stop]。
 */
class TriggerEventSources(
    context: Context,
    private val engine: TriggerEngine,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)

    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var packageReceiver: BroadcastReceiver? = null
    private var pollJob: Job? = null

    fun start() {
        registerNetworkCallbacks()
        registerPowerReceiver()
        registerScreenReceiver()
        registerBluetoothReceiver()
        registerPackageReceiver()
        startPolling()
    }

    fun stop() {
        val cm = connectivityManager ?: return
        defaultNetworkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        wifiNetworkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        defaultNetworkCallback = null
        wifiNetworkCallback = null
        unregister(powerReceiver)
        powerReceiver = null
        unregister(screenReceiver)
        screenReceiver = null
        unregister(bluetoothReceiver)
        bluetoothReceiver = null
        unregister(packageReceiver)
        packageReceiver = null
        pollJob?.cancel()
        pollJob = null
    }

    private fun unregister(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    // ------------------------------------------------------------------ 网络

    private fun registerNetworkCallbacks() {
        val cm = connectivityManager ?: return
        runCatching {
            val defaultCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    dispatch("onNetwork") { engine.onNetwork(online = true) }
                }

                override fun onLost(network: Network) {
                    dispatch("onNetwork") { engine.onNetwork(online = false) }
                }
            }
            cm.registerDefaultNetworkCallback(defaultCallback, mainHandler)
            defaultNetworkCallback = defaultCallback
        }.onFailure { Log.e(TAG, "registerDefaultNetworkCallback failed", it) }

        runCatching {
            val wifiCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    dispatch("onWifi") { engine.onWifi(ssid = currentSsid(), connected = true) }
                }

                override fun onLost(network: Network) {
                    dispatch("onWifi") { engine.onWifi(ssid = currentSsid(), connected = false) }
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, wifiCallback, mainHandler)
            wifiNetworkCallback = wifiCallback
        }.onFailure { Log.e(TAG, "registerNetworkCallback(wifi) failed", it) }
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String {
        val manager = appContext.getSystemService(WifiManager::class.java) ?: return ""
        return runCatching {
            val ssid = manager.connectionInfo?.ssid.orEmpty()
            if (ssid.isBlank() || ssid.contains("<unknown", ignoreCase = true)) "" else ssid
        }.getOrDefault("")
    }

    // ------------------------------------------------------------------ 电量

    private fun registerPowerReceiver() {
        if (powerReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED ->
                        dispatch("onCharging") { engine.onCharging(CardManifest.STATE_CONNECTED) }

                    Intent.ACTION_POWER_DISCONNECTED ->
                        dispatch("onCharging") { engine.onCharging(CardManifest.STATE_DISCONNECTED) }

                    Intent.ACTION_BATTERY_CHANGED -> handleBatteryIntent(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // ACTION_BATTERY_CHANGED 是 sticky：注册返回值即当前状态，立即补一次基线
        val sticky = ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        powerReceiver = receiver
        sticky?.let { handleBatteryIntent(it) }
    }

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        dispatch("onBattery") { engine.onBattery(level = percent, charging = charging) }
    }

    // ------------------------------------------------------------------ 屏幕

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON ->
                        dispatch("onScreen") { engine.onScreen(CardManifest.SCREEN_ON) }

                    Intent.ACTION_SCREEN_OFF -> {
                        val keyguard = appContext.getSystemService(KeyguardManager::class.java)
                        val locked = runCatching { keyguard?.isKeyguardLocked == true }.getOrDefault(false)
                        dispatch("onScreen") {
                            engine.onScreen(
                                if (locked) CardManifest.SCREEN_LOCKED else CardManifest.SCREEN_OFF
                            )
                        }
                    }

                    Intent.ACTION_USER_PRESENT ->
                        dispatch("onScreen") { engine.onScreen(CardManifest.SCREEN_UNLOCKED) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiver = receiver
    }

    // ------------------------------------------------------------------ 蓝牙

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver != null) return
        // API 31+ 接收 ACL 广播需要 BLUETOOTH_CONNECT；未授权则静默跳过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            Log.i(TAG, "BLUETOOTH_CONNECT not granted, bluetooth events disabled")
            return
        }
        val receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(context: Context?, intent: Intent?) {
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val name = deviceName(device)
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED ->
                        dispatch("onBluetooth") {
                            engine.onBluetooth(CardManifest.STATE_CONNECTED, name)
                        }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                        dispatch("onBluetooth") {
                            engine.onBluetooth(CardManifest.STATE_DISCONNECTED, name)
                        }
                }
            }
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            // 蓝牙广播由系统高权限应用发出，Android 14+ 需要 EXPORTED 才能稳定收到；
            // 这些均为 protected broadcast，第三方无法伪造
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            bluetoothReceiver = receiver
        }.onFailure { Log.e(TAG, "register bluetooth receiver failed", it) }
    }

    @Suppress("DEPRECATION")
    private fun deviceName(device: BluetoothDevice?): String {
        if (device == null) return ""
        // 设备名在 API 31+ 需要 BLUETOOTH_CONNECT，拿不到时退化为 MAC 地址
        return runCatching { device.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { device.address }.getOrDefault("")
    }

    // ------------------------------------------------------- 安装 / 卸载事件

    /**
     * 运行期内监听应用安装/卸载（ACTION_PACKAGE_ADDED / REMOVED）。
     *
     * 包变更广播在 Android 8+ 无法静态注册，因此仅在触发服务存活期间生效；
     * 覆盖安装（EXTRA_REPLACING = true）视为更新而非安装/卸载，直接忽略。
     */
    private fun registerPackageReceiver() {
        if (packageReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val packageName = intent.data?.schemeSpecificPart.orEmpty()
                if (packageName.isBlank()) return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                when (action) {
                    Intent.ACTION_PACKAGE_ADDED ->
                        dispatch("onAppInstall") { engine.onAppInstall(packageName) }

                    Intent.ACTION_PACKAGE_REMOVED ->
                        dispatch("onAppUninstall") { engine.onAppUninstall(packageName) }
                }
            }
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            // 包变更属于系统 protected broadcast，第三方无法伪造；用 EXPORTED 保证各版本稳定接收
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            packageReceiver = receiver
        }.onFailure { Log.e(TAG, "register package receiver failed", it) }
    }

    // ------------------------------------------------------------- 轮询事件源

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch(Dispatchers.Default) {
            var clipboardTicks = 0
            var locationTicks = 0
            while (isActive) {
                delay(POLL_TICK_MS)
                clipboardTicks++
                locationTicks++
                if (clipboardTicks % CLIPBOARD_TICKS == 0) pollClipboard()
                if (locationTicks % LOCATION_TICKS == 0) pollLocation()
            }
        }
    }

    @Volatile
    private var lastClipboard: String? = null

    private fun pollClipboard() {
        // Android 10+ 后台应用读取剪贴板会被系统拒绝（返回 null），静默跳过即可
        val manager = appContext.getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching {
            val clip = manager.primaryClip ?: return
            if (clip.itemCount <= 0) return
            clip.getItemAt(0).coerceToText(appContext)?.toString()
        }.getOrNull()
        if (text.isNullOrBlank() || text == lastClipboard) return
        lastClipboard = text
        dispatch("onClipboard") { engine.onClipboard(text) }
    }

    private fun pollLocation() {
        if (!hasLocationPermission()) return
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return
        val location = runCatching { latestLocation(manager) }.getOrNull() ?: return
        dispatch("onLocation") { engine.onLocation(location.latitude, location.longitude) }
    }

    private fun latestLocation(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers
            .mapNotNull { provider ->
                runCatching {
                    if (!manager.isProviderEnabled(provider)) null
                    else manager.getLastKnownLocation(provider)
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    // ------------------------------------------------------------------ 工具

    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private inline fun dispatch(source: String, block: () -> Int) {
        runCatching { block() }.onFailure { Log.e(TAG, "$source dispatch failed", it) }
    }

    companion object {
        private const val TAG = "TriggerEventSources"
        private const val POLL_TICK_MS = 1_000L
        private const val CLIPBOARD_TICKS = 2
        private const val LOCATION_TICKS = 60
    }
}
