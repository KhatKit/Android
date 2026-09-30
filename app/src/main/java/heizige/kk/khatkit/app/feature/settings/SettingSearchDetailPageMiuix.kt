package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.search.SearchService
import heizige.kk.khatkit.search.SearchServiceOptions
import kotlin.uuid.Uuid
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text

/**
 * 搜索服务详情页的 Miuix 风格版。
 *
 * 参数表单（SearchServiceOptionsEditor）与连通性测试（SearchTestSection）本身已双风格化
 * —— 输入框走 KedgeTextField、分段选择走 Khromia SegmentedRow —— 这里只换外壳与分组卡片。
 */
@Composable
fun SettingSearchDetailPageMiuix(
    serviceId: Uuid,
    vm: SettingViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current

    val service = settings.searchServices.find { it.id == serviceId } ?: return
    val serviceIndex = settings.searchServices.indexOf(service)
    var options by remember(service) { mutableStateOf<SearchServiceOptions>(service) }

    fun save(updated: SearchServiceOptions) {
        options = updated
        val newServices = settings.searchServices.toMutableList()
        newServices[serviceIndex] = updated
        vm.updateSettings(settings.copy(searchServices = newServices))
    }

    MiuixSettingsPage(
        title = options.displayName,
        navigationIcon = { BackButton() },
        actions = {
            if (settings.searchServices.size > 1) {
                KedgeIconButton(onClick = {
                    val newServices = settings.searchServices.toMutableList()
                    newServices.removeAt(serviceIndex)
                    vm.updateSettings(settings.copy(searchServices = newServices))
                    nav.popBackStack()
                }) {
                    Icon(
                        imageVector = delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        },
    ) {
        item {
            KedgeCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = stringResource(R.string.setting_page_search_config))
                    SearchServiceOptionsEditor(
                        options = options,
                        onUpdateOptions = ::save,
                    )
                    SearchService.getService(options).Description()
                }
            }
        }

        item {
            SearchTestSection(
                options = options,
                commonOptions = settings.searchCommonOptions,
            )
        }
    }
}
