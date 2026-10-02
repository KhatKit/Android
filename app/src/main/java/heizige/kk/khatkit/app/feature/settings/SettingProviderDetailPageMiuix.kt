package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.components.ui.ShareSheet
import heizige.kk.khatkit.app.core.ui.components.ui.rememberShareSheetState
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.share
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khromia.helper.Toast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text

/**
 * 供应商详情页的 Miuix 风格版。
 *
 * 内部两个页签（配置表单 / 模型列表）都已双风格化 —— 表单走 Kedge 双风格组件与
 * [SwitchRow]，模型列表走已双风格化的 provider 子组件 —— 这里只换外壳与页签栏。
 */
@Composable
fun SettingProviderDetailPageMiuix(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    val settingProviderPageSaveSuccess = stringResource(R.string.setting_provider_page_save_success)
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 2 }
    val shareSheetState = rememberShareSheetState()
    ShareSheet(shareSheetState)

    var page by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    MiuixSettingsPage(
        title = provider.name,
        navigationIcon = { BackButton() },
        actions = {
            KedgeIconButton(onClick = { shareSheetState.show(provider) }) {
                Icon(imageVector = share, contentDescription = null)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = {
                        page = 0
                        scope.launch { pager.animateScrollToPage(0) }
                    },
                    icon = build,
                    label = stringResource(R.string.setting_provider_page_configuration),
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = {
                        page = 1
                        scope.launch { pager.animateScrollToPage(1) }
                    },
                    icon = package2,
                    label = stringResource(R.string.setting_provider_page_models),
                )
            }
        },
    ) {
        if (page == 0) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.9f)) {
                    SettingProviderConfigPage(
                        provider = provider,
                        onEdit = {
                            onEdit(it)
                            Toast.show(
                                message = settingProviderPageSaveSuccess,
                                isError = false,
                            )
                        },
                        onDelete = onDelete,
                    )
                }
            }
        } else {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.9f)) {
                    SettingProviderModelPage(
                        provider = provider,
                        onEdit = onEdit,
                    )
                }
            }
        }
    }
}
