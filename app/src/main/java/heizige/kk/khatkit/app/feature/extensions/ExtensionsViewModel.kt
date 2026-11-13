package heizige.kk.khatkit.app.feature.extensions

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.ssh.SshPluginHub
import heizige.kk.khatkit.app.core.data.ssh.SshPluginInstaller
import heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader
import heizige.kk.khatkit.app.core.data.ssh.SshPluginRecord
import heizige.kk.khatkit.app.core.data.ssh.SshPluginStore
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginHub
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginInstaller
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginLoader
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginRecord
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 扩展页的远程插件状态中枢。插件包由 Hub 下载，安装记录由各自的 Store 持久化。 */
@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val appContext: Application,
    private val provider: KhatKitToolProvider,
) : ViewModel() {

    private val _sshPlugin = MutableStateFlow<SshPluginRecord?>(null)
    val sshPlugin: StateFlow<SshPluginRecord?> = _sshPlugin.asStateFlow()

    private val _terminalPlugin = MutableStateFlow<TerminalPluginRecord?>(null)
    val terminalPlugin: StateFlow<TerminalPluginRecord?> = _terminalPlugin.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    /** 重读安装状态；扩展页回到前台时调用（插件页里可能刚卸载）。 */
    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        val record = SshPluginStore(appContext).read()
        SshPluginLoader.updateRecord(record)
        _sshPlugin.value = record
        _terminalPlugin.value = TerminalPluginStore(appContext).read()
    }

    fun install() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                SshPluginHub.installLatest(appContext, provider.hubBaseUrl)
            }
            _busy.value = false
            result.fold(
                onSuccess = { record ->
                    SshPluginLoader.updateRecord(record)
                    _sshPlugin.value = record
                    _message.value = "已安装 " + record.version
                },
                onFailure = { error ->
                    _message.value = "安装失败：" + (error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    fun uninstall() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { SshPluginInstaller.uninstall(appContext) }
            _busy.value = false
            if (ok) {
                SshPluginLoader.updateRecord(null)
                _sshPlugin.value = null
                _message.value = "插件已卸载"
            } else {
                _message.value = "卸载失败：插件目录删不掉"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun installTerminal() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                TerminalPluginHub.installLatest(appContext, provider.hubBaseUrl)
            }
            _busy.value = false
            result.fold(
                onSuccess = { record ->
                    TerminalPluginLoader.invalidate()
                    _terminalPlugin.value = record
                    _message.value = "终端插件已安装 " + record.version
                },
                onFailure = { error -> _message.value = "终端插件安装失败：${error.message ?: error.javaClass.simpleName}" },
            )
        }
    }

    fun uninstallTerminal() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { TerminalPluginInstaller.uninstall(appContext) }
            _busy.value = false
            if (ok) {
                TerminalPluginLoader.invalidate()
                _terminalPlugin.value = null
                _message.value = "终端插件已卸载"
            } else {
                _message.value = "终端插件卸载失败"
            }
        }
    }
}
