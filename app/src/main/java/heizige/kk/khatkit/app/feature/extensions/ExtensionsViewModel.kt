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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 扩展页的插件状态中枢：远程 SSH 插件走 Hub 下载安装（kind=dependency），
 * 安装记录由 [SshPluginStore] 持久化，引擎由 [SshPluginLoader] 动态加载。
 */
@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val appContext: Application,
    private val provider: KhatKitToolProvider,
) : ViewModel() {

    private val _sshPlugin = MutableStateFlow<SshPluginRecord?>(null)
    val sshPlugin: StateFlow<SshPluginRecord?> = _sshPlugin.asStateFlow()

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
}
