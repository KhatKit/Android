package heizige.kk.khatkit.bridge.impl

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * 绑定并缓存 Shizuku UserService 连接。
 *
 * 绑定是异步的，ServiceConnection 回调在主线程；脚本线程用 [exec] 阻塞等待。
 */
internal object ShizukuUserServiceManager {

    private val serviceDeferred = CompletableDeferred<IKhatKitUserService>()
    private val lock = Any()

    @Volatile
    private var bound = false

    fun ensureBound(context: Context) {
        if (bound || serviceDeferred.isCompleted) return
        synchronized(lock) {
            if (bound) return
            bound = true
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, KhatKitUserService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("khatkit")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service != null) {
                        serviceDeferred.complete(IKhatKitUserService.Stub.asInterface(service))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // 断开后由下次进程重启重新绑定；当前调用会超时返回。
                }
            })
        }
    }

    fun exec(command: Array<String>): String = runBlocking {
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            serviceDeferred.await().exec(command)
        } ?: "[timeout] Shizuku 用户服务未就绪"
    }

    private const val TIMEOUT_MILLIS = 15_000L
}
