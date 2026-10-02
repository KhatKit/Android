package heizige.kk.khatkit.bridge.impl

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import heizige.kk.khatkit.bridge.SystemBridge

class SystemBridgeImpl(
    private val context: Context,
) : SystemBridge {
    override fun openUrl(url: String): Boolean {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https")) return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    override fun share(text: String, title: String): Boolean = runCatching {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            title.ifBlank { null },
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    override fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    override fun vibrate(durationMs: Int) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val duration = durationMs.coerceAtLeast(1).toLong()
        // minSdk 26，只走 VibrationEffect 分支；旧 vibrate(long) 已不可用
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun launchApp(packageName: String, activity: String): Boolean = runCatching {
        val intent = if (activity.isBlank()) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent().setClassName(packageName, activity)
        } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    override fun postNotification(
        channelId: String,
        channelName: String,
        title: String,
        text: String,
        actions: List<String>,
    ): Boolean {
        // minSdk 26：通知渠道无需判版本
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        return runCatching {
            NotificationManagerCompat.from(context).notify((channelId + title).hashCode(), builder.build())
            true
        }.getOrDefault(false)
    }

    override fun screenState(): String {
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return when {
            power?.isInteractive != true -> "off"
            keyguard?.isKeyguardLocked == true -> "locked"
            else -> "unlocked"
        }
    }

    override fun battery(): Map<String, Any?> {
        val battery = context.getSystemService(BatteryManager::class.java)
        return mapOf(
            "level" to battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to battery?.isCharging,
            "temperature" to null,
        )
    }

    override fun storage(): Map<String, Any?> {
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        return mapOf("total" to stat.totalBytes, "available" to stat.availableBytes)
    }

    override fun connectivity(): Map<String, Any?> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        val type = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "none"
        }
        return mapOf("online" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true), "type" to type, "metered" to (manager?.isActiveNetworkMetered == true))
    }
}
