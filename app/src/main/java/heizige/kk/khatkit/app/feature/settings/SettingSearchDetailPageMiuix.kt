package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Box
import heizige.kk.kedge.components.KedgeIconButton
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
import heizige.kk.khatkit.search.SearchOptions
import kotlin.uuid.Uuid
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixItemSpacing
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.feature.settings.data.removeSearchRepository

/**
 * 搜索服务详情页的 Miuix 风格版。
 *
 * 参数表单（[SearchServiceOptionsEditor]）与连通性测试（[SearchTestSection]）本身已双风格化
 * —— 输入框走 KedgeTextField、分段单选走 Khromia SegmentedRow、表单行走
 * `KedgeFormRow` → `FormItem` → `KedgeOptionItem` —— 这里只换外壳与分组容器。
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
    var options by remember(service) { mutableStateOf<SearchOptions>(service) }

    fun save(updated: SearchOptions) {
        options = updated
        val newServices = settings.searchServices.toMutableList()
        newServices[serviceIndex] = updated
        vm.updateSettings(settings.copy(searchServices = newServices))
    }

    MiuixSettingsPage(
        title = options.displayName,
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
        actions = {
            if (settings.searchServices.size > 1) {
                KedgeIconButton(onClick = {
                    // 走统一入口：删掉的服务若正好是「当前使用的搜索服务」，选中下标要跟着重绑。
                    removeSearchRepository(vm = vm, settings = settings, service = service)
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
            // 参数分组走 KedgeFormCard（InlineCardGroup）：组底色/圆角由组统一出，
            // 行是 SearchServiceOptionsEditor 里的 KedgeFormRow → FormItem → KedgeOptionItem，
            // 组内只留内侧小圆角。以前这里套的是 MD3 的 KedgeCard + 12dp 间距补丁。
            SearchConfigCard(
                options = options,
                onUpdate = ::save,
            )
        }

        item {
            // 连通性测试是**内容卡**（输入 + 进度 + 结果），不是选项行：外壳仍是 KedgeCard，
            // 组间距在这里补（KSU 每个分组 Card 都带 padding(top = 12.dp)）。
            Box(modifier = Modifier.miuixItemSpacing()) {
                SearchTestSection(
                    options = options,
                    commonOptions = settings.searchCommonOptions,
                )
            }
        }
    }
}
