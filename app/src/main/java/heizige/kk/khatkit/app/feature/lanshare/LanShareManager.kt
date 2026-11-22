package heizige.kk.khatkit.app.feature.lanshare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.feature.lanshare.data.LAN_TEXT_LIMIT
import heizige.kk.khatkit.app.feature.lanshare.data.LanShareDirectoryRepository
import heizige.kk.khatkit.app.feature.lanshare.data.LanShareDocument
import heizige.kk.khatkit.app.feature.lanshare.data.LanShareSession
import heizige.kk.khatkit.app.feature.lanshare.helper.isLanShareAddress
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class LanShareState(
    val directory: LanShareDocument? = null,
    val url: String? = null,
    val isBusy: Boolean = false,
    val outgoing: String = "",
    val incoming: String = "",
    val error: String? = null,
)

@Singleton
internal class LanShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: AppScope,
) {
    private val directory = LanShareDirectoryRepository(context.contentResolver)
    private val _state = MutableStateFlow(LanShareState())
    val state = _state.asStateFlow()
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var serving: AtomicBoolean? = null
    private var expiration: Job? = null

    fun select(uri: Uri) {
        if (_state.value.isBusy || _state.value.url != null) return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            try {
                val folder = directory.root(uri)
                _state.update { it.copy(directory = folder) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("无法访问所选目录")
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun removeDirectory() {
        if (!_state.value.isBusy && _state.value.url == null) _state.update { it.copy(directory = null) }
    }

    fun editText(text: String) = _state.update { it.copy(outgoing = text.take(LAN_TEXT_LIMIT)) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun start() {
        if (_state.value.isBusy || engine != null) return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            val active = AtomicBoolean(false)
            var candidate: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
            try {
                val address = lanAddress()
                if (address == null) {
                    showError("未检测到可分享的 Wi-Fi 或以太网 IPv4 地址")
                    return@launch
                }
                val deadline = SystemClock.elapsedRealtime() + SESSION_MILLIS
                val session = LanShareSession(directory, _state.value.directory, { _state.value.outgoing }, { text ->
                    _state.update { if (active.get()) it.copy(incoming = text) else it }
                }) { active.get() && SystemClock.elapsedRealtime() < deadline }
                val server = embeddedServer(CIO, host = address, port = 0) {
                    routing { session.mount(this) }
                }
                candidate = server
                val port = withTimeout(5_000) {
                    withContext(Dispatchers.IO) {
                        server.startSuspend(wait = false)
                        server.engine.resolvedConnectors().single().port
                    }
                }
                engine = server
                serving = active
                active.set(true)
                _state.update { it.copy(url = "http://$address:$port${session.path}") }
                expiration = scope.launch {
                    delay(SESSION_MILLIS)
                    stop()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                showError("启动局域网共享超时")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("启动局域网共享失败")
            } finally {
                if (engine == null) withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { candidate?.stopSuspend(0, 500) }
                }
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun stop() {
        val running = engine ?: return
        // Revoke access before shutting down the socket.
        serving?.set(false)
        serving = null
        engine = null
        expiration?.cancel()
        expiration = null
        _state.update { it.copy(url = null, isBusy = true, incoming = "") }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { running.stopSuspend(0, 1_000) }
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun lanAddress(): String? {
        val network = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val candidates = (listOfNotNull(network.activeNetwork) + network.allNetworks).distinct()
        return candidates.firstNotNullOfOrNull { candidate ->
            val capabilities = network.getNetworkCapabilities(candidate) ?: return@firstNotNullOfOrNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@firstNotNullOfOrNull null
            network.getLinkProperties(candidate)?.linkAddresses
                ?.firstOrNull { it.address.isLanShareAddress() }?.address?.hostAddress
        }
    }

    private companion object {
        const val SESSION_MILLIS = 30 * 60 * 1_000L
    }
}
