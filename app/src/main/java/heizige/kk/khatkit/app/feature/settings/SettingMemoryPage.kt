package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.findModelProviderName
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryRepository
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.util.plus
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * 记忆的按角色入口页（设置 → 数据设置 → 记忆）。
 *
 * 记忆本身是按角色存的（[Screen.AssistantMemory] 收 assistantId），所以这里先列角色、
 * 点进去才是该角色的记忆面板。行渲染交给 [CardGroup]（内部即 `KedgeOptionItem`），
 * 圆角/行距与设置页其它组同源，不手搓 `Row` + `clickable`。
 */
@Composable
fun SettingMemoryPage() {
    val settingVm: SettingViewModel = hiltViewModel()
    val memoryVm: SettingMemoryViewModel = hiltViewModel()
    val settings by settingVm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // stringResource 不能进 CardGroup 的收集期（那是普通 lambda，不是 @Composable），先取出来。
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_page_memory),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    if (settings.assistants.isEmpty()) {
                        // 空态：不给白屏，同样走一行选项行。
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_memory_page_empty)) },
                        )
                    } else {
                        settings.assistants.forEach { assistant ->
                            val displayName = assistant.name.ifBlank { defaultAssistantName }
                            item(
                                onClick = {
                                    navController.navigate(Screen.AssistantMemory(assistant.id.toString()))
                                },
                                leadingContent = {
                                    UIAvatar(
                                        name = displayName,
                                        value = assistant.avatar,
                                        avatarShape = assistant.avatarShape,
                                        // 角色没设自定义头像时画模型提供商的图标，而不是首字母：
                                        // 取值口径同 ChatPage.kt:802（角色 chatModelId 回退全局 chatModelId）。
                                        defaultIconName = settings.findModelProviderName(
                                            assistant.chatModelId ?: settings.chatModelId
                                        ),
                                        avatarColor = assistant.avatarColor,
                                        modifier = Modifier.size(32.dp),
                                    )
                                },
                                supportingContent = {
                                    // 条数来自 RoleMemoryRepository.memoryCount（已按角色分文件计数），
                                    // 不额外查库；collectAsStateWithLifecycle 跟随页面生命周期。
                                    val count by remember(assistant.id) {
                                        memoryVm.memoryCount(assistant.id)
                                    }.collectAsStateWithLifecycle(initialValue = 0)
                                    Text(stringResource(R.string.setting_page_memory_count, count))
                                },
                                headlineContent = { Text(displayName) },
                                trailingContent = { Icon(arrowForward, null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 只为按角色取记忆条数而存在：`SettingViewModel` 不持有 [RoleMemoryRepository]，
 * 而给本页单独加一个只读 flow 的 VM 比让设置总 VM 多背一个依赖更干净。
 */
@HiltViewModel
class SettingMemoryViewModel @Inject constructor(
    private val roleMemoryRepository: RoleMemoryRepository,
) : ViewModel() {
    fun memoryCount(roleId: Uuid): Flow<Int> = roleMemoryRepository.memoryCount(roleId)
}
